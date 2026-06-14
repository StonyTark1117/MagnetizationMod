package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.IMagnetizable;
import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.content.effect.MagnetizedEffect;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.worldgen.AnomalyBiome;
import com.stonytark.magnetization.api.EquippedArmor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

/**
 * Walks a {@link MagneticField} and applies its force to:
 * <ul>
 *   <li>any Sable sub-level (Create: Aeronautics ship) overlapping the field's range</li>
 *   <li>any vanilla {@link Entity} marked {@link IMagnetizable}, or any item entity
 *       holding a stack with an item that implements it</li>
 * </ul>
 *
 * <p>Sub-level discovery is done by sampling: we step through a coarse grid of
 * world positions inside the field's bounding box and ask Sable which sub-level
 * (if any) contains each sample. Each sub-level is touched at most once per tick,
 * with the impulse applied at the closest sampled point on it.
 */
public final class FieldApplicator {

    private static final Logger DEBUG_LOG = LoggerFactory.getLogger("magnetization/FieldApplicator");
    private static long lastDebugTick = -1L;

    /** Susceptibility added per worn metal armor piece (#magnetization:metal_armor). */
    public static final double PER_ARMOR_SUSCEPTIBILITY = 0.4d;
    /** Bonus susceptibility added per worn piece that has been magnetized (data component
     *  {@link com.stonytark.magnetization.registry.MagDataComponents#ARMOR_POLARITY}).
     *  Stacks on top of {@link #PER_ARMOR_SUSCEPTIBILITY} — magnetized full plate ≈ 4×
     *  the pull of plain iron. Scaled by {@link Lirm#strength(ItemStack, long)} when the
     *  piece carries an active LIRM stamp (temporary magnetism). */
    public static final double PER_MAGNETIZED_BONUS = 0.6d;
    /** Petrified wood items are <i>weakly</i> ferromagnetic — distinctly less than a
     *  full ferromagnetic ingot. Per the temporary-magnetism framing, even a fully-
     *  decayed LIRM stamp wouldn't reduce them past this floor: they're intrinsically
     *  weak, not LIRM-decayed. */
    public static final double PETRIFIED_WOOD_SUSCEPTIBILITY = 0.3d;

    /** Diamagnetic repulsion gain — weak, so a diamagnetic item hovers a short
     *  way above a magnet rather than rocketing off. */
    public static final double DIAMAGNETIC_SUSCEPTIBILITY = 1.1d;

    private FieldApplicator() {}

    // Each accessor falls back to a default when the SPEC hasn't been loaded yet
    // (e.g. unit tests, or before mod construction). The defaults match the
    // declared default in MagConfig.
    private static double strengthMultiplier() {
        try { return MagConfig.STRENGTH_MULTIPLIER.get(); } catch (Throwable t) { return 1.0d; }
    }

    private static double entityVelocityScale() {
        try { return MagConfig.ENTITY_VELOCITY_SCALE.get(); } catch (Throwable t) { return 0.05d; }
    }

    private static double conicalHalfAngleCos() {
        try { return MagConfig.CONICAL_HALF_ANGLE_COS.get(); } catch (Throwable t) { return 0.7071d; }
    }

    private static double maxAccelPerTick() {
        try { return MagConfig.MAX_ACCEL_PER_TICK.get(); } catch (Throwable t) { return 50.0d; }
    }

    private static double shipLinearDrag() {
        try { return MagConfig.SHIP_LINEAR_DRAG.get(); } catch (Throwable t) { return 0.02d; }
    }

    private static double shipAngularDrag() {
        try { return MagConfig.SHIP_ANGULAR_DRAG.get(); } catch (Throwable t) { return 0.05d; }
    }

    private static int shipSampleSteps() {
        try { return MagConfig.SHIP_SAMPLE_STEPS.get(); } catch (Throwable t) { return 3; }
    }

    public static void apply(final ServerLevel level, final MagneticField field) {
        apply(level, field, null, null);
    }

    public static void apply(
            final ServerLevel level,
            final MagneticField field,
            final @Nullable ServerSubLevel exclude
    ) {
        apply(level, field, exclude, null);
    }

    /**
     * Apply the field with optional filters.
     *
     * @param exclude        sub-level to skip (typically the emitter's host ship)
     * @param shipFilter     predicate restricting which sub-levels receive force
     *                       (e.g. an anchor's sticky binding); {@code null} = accept all
     */
    public static void apply(
            final ServerLevel level,
            final MagneticField field,
            final @Nullable ServerSubLevel exclude,
            final @Nullable Predicate<ServerSubLevel> shipFilter
    ) {
        if (field.polarity() == MagneticPolarity.NONE || field.strength().force() <= 0) return;
        applyToSubLevels(level, field, exclude, shipFilter);
        applyToEntities(level, field);
    }

    // ---------------- ships (Sable sub-levels) ----------------

    private static void applyToSubLevels(
            final ServerLevel level,
            final MagneticField field,
            final @Nullable ServerSubLevel exclude,
            final @Nullable Predicate<ServerSubLevel> shipFilter
    ) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        // Sable's container can be null very early in load (before its
        // capability attaches) — guard so the emitter pass is a silent no-op
        // rather than an NPE there. Fast path also bails when no sub-levels
        // exist at all (vanilla saves with C:Aero installed but nothing built),
        // sparing every emitter the cost of queryIntersecting.
        if (container == null || container.getAllSubLevels().isEmpty()) return;

        final long now = level.getGameTime();

        // Expand the single host sub-level into every sub-level of its assembly, so
        // an emitter on a multi-part craft (body + bearing/spring-mounted subgroups,
        // each its own rigid body) doesn't pull on the rest of its own craft. Walked
        // (and cached, per host) only when there's actually something built — hence
        // after the empty-container fast path above. With the toggle off we fall back
        // to the pre-1.1.4 behaviour of excluding only the host itself.
        final java.util.Set<java.util.UUID> excludedIds;
        if (exclude == null) {
            excludedIds = null;
        } else if (MagConfig.excludeConnectedSubLevels()) {
            excludedIds = SableBridge.connectedChainIds(exclude, now);
        } else {
            excludedIds = java.util.Set.of(exclude.getUniqueId());
        }

        final double range = field.range();
        final Vec3 origin = field.origin();
        final BoundingBox3d searchBox = new BoundingBox3d(
                origin.x - range, origin.y - range, origin.z - range,
                origin.x + range, origin.y + range, origin.z + range
        );

        final double cap = maxAccelPerTick();
        final int steps = Math.max(1, shipSampleSteps());
        final double drag = shipLinearDrag();
        final double angDrag = shipAngularDrag();
        final double rangeSqr = range * range;
        // Per-tick constants — pull them out of the per-sample loop so we don't
        // pay the biome lookup + config getters 27 times per ship per emitter.
        // On a server with N emitters × M ships, that's a 27× reduction on the
        // dominant fixed cost of the inner loop.
        final double globalScalar = computeGlobalScalar(level, field);
        final double cosHalfAngle = conicalHalfAngleCos();

        // Diagnostics — only allocated when the debug log gate is open.
        final boolean diag = MagConfig.debugLogging();
        int candidates = 0, passedFilter = 0, impulsesApplied = 0;
        double maxImpulseMag = 0.0;
        final java.util.List<String> candidateIds = diag ? new java.util.ArrayList<>() : null;
        for (SubLevel sub : container.queryIntersecting(searchBox)) {
            if (diag) candidates++;
            if (diag && sub instanceof ServerSubLevel s) candidateIds.add(s.getUniqueId().toString().substring(0, 8));
            if (!(sub instanceof ServerSubLevel server)) continue;
            if (excludedIds != null && excludedIds.contains(server.getUniqueId())) continue;
            // Reject phantom sub-levels: stale entries Sable's spatial index
            // can return after a shatter/unload. Applying velocity to one of
            // these crashes Rapier with "No rigid body for id".
            if (server.getMassTracker().isInvalid() || server.getMassTracker().getMass() <= 0.0) continue;
            if (container.getSubLevel(server.getUniqueId()) == null) continue;
            if (shipFilter != null && !shipFilter.test(server)) continue;
            if (diag) passedFilter++;

            final double mass = server.getMassTracker().getMass();
            final BoundingBox3dc subBox = sub.boundingBox();

            // Resolve the ship's own polarity + susceptibility. Like polarities
            // repel, unlike attract — same convention the entity path uses. A
            // SOUTH-poled ship flips force sign so a NORTH emitter pulls instead
            // of pushing. Susceptibility scales the magnitude, so ferrous-heavy
            // ships respond more strongly to the same field. ShipMagneticState
            // is cached per ship; this is an O(1) lookup most ticks.
            final com.stonytark.magnetization.api.ShipMagneticState shipState =
                    ShipMagneticRegistry.get(level, server);
            final double shipSign = shipState.polarity().sign();
            // ship.polarity().sign() = +1 for NORTH (default-target convention,
            // no flip), -1 for SOUTH (flip), 0 for NONE (never returned by the
            // scanner today, but if it ever does, the ship feels no force).
            if (shipSign == 0.0) continue;
            final double shipGain = shipState.susceptibility() * shipSign;

            // Integrate the field over a coarse grid of sample points spanning the
            // ship's AABB. Each sample contributes an impulse at its own world point,
            // so non-uniform fields (OMNIDIRECTIONAL fall-off, CONICAL gating) naturally
            // produce torque on extended ships — the closer end pulls harder than the
            // far end, generating a moment about the COM. steps=1 falls back to the
            // 1.0.0 closest-point sample. Lists are pre-sized to the max possible
            // grid (steps³) so the typical case never grows the underlying array.
            final int maxSamples = steps * steps * steps;
            final java.util.List<Vec3> samplePoints = new java.util.ArrayList<>(maxSamples);
            final java.util.List<Vec3> sampleForces = new java.util.ArrayList<>(maxSamples);
            double totalForceMag = 0.0;
            if (steps == 1) {
                final Vec3 closest = new Vec3(
                        Math.max(subBox.minX(), Math.min(origin.x, subBox.maxX())),
                        Math.max(subBox.minY(), Math.min(origin.y, subBox.maxY())),
                        Math.max(subBox.minZ(), Math.min(origin.z, subBox.maxZ()))
                );
                if (closest.distanceToSqr(origin) <= rangeSqr) {
                    final Vec3 force = forceAtPrecomputed(field, closest, globalScalar, cosHalfAngle).scale(shipGain);
                    if (force.lengthSqr() >= 1.0e-6) {
                        samplePoints.add(closest);
                        sampleForces.add(force);
                        totalForceMag = force.length();
                    }
                }
            } else {
                final double sx = subBox.maxX() - subBox.minX();
                final double sy = subBox.maxY() - subBox.minY();
                final double sz = subBox.maxZ() - subBox.minZ();
                final double invSteps = 1.0 / (steps - 1);
                final double weight = 1.0 / (steps * steps * steps);
                for (int ix = 0; ix < steps; ix++) {
                    final double fx = subBox.minX() + sx * ix * invSteps;
                    final double dxOriginSqr = (fx - origin.x) * (fx - origin.x);
                    for (int iy = 0; iy < steps; iy++) {
                        final double fy = subBox.minY() + sy * iy * invSteps;
                        final double dyOriginSqr = (fy - origin.y) * (fy - origin.y);
                        for (int iz = 0; iz < steps; iz++) {
                            final double fz = subBox.minZ() + sz * iz * invSteps;
                            // Range check via primitives — skips the Vec3 allocation
                            // entirely for out-of-range samples. For elongated ships
                            // where most of the AABB sits outside the field, this
                            // eliminates the bulk of allocations in the inner loop.
                            final double dzOriginSqr = (fz - origin.z) * (fz - origin.z);
                            if (dxOriginSqr + dyOriginSqr + dzOriginSqr > rangeSqr) continue;
                            final Vec3 sample = new Vec3(fx, fy, fz);
                            // Each grid cell represents a fraction (1/steps³) of the
                            // ship's volume; weighting the sampled force by that
                            // fraction keeps total integrated force comparable to the
                            // single-sample path regardless of grid density.
                            final Vec3 force = forceAtPrecomputed(field, sample, globalScalar, cosHalfAngle)
                                    .scale(weight * shipGain);
                            if (force.lengthSqr() < 1.0e-8) continue;
                            samplePoints.add(sample);
                            sampleForces.add(force);
                            totalForceMag += force.length();
                        }
                    }
                }
            }

            if (totalForceMag <= 1.0e-6) continue;

            // Apply the per-ship-per-tick acceleration cap once across the
            // *summed* force this emitter wants to deliver, then distribute the
            // granted slice proportionally over the individual samples. Doing the
            // cap per-sample would let whichever grid cell happened to be visited
            // first consume the entire budget — wrong, because cap is a total-accel
            // limit, not a per-sample limit.
            double scaleAll = 1.0;
            if (cap > 0.0 && mass > 0.0) {
                final double wantedAccel = totalForceMag / mass;
                final double granted = ShipTickBudget.grant(server, now, cap, wantedAccel);
                if (granted <= 0.0) continue;
                if (granted < wantedAccel) scaleAll = granted / wantedAccel;
            }

            for (int i = 0; i < samplePoints.size(); i++) {
                final Vec3 scaled = scaleAll == 1.0 ? sampleForces.get(i) : sampleForces.get(i).scale(scaleAll);
                SableBridge.applyWorldImpulse(server, samplePoints.get(i), scaled);
            }

            // Apply linear + angular drag at most once per ship per tick (multiple
            // emitters touching the same ship don't multiply the drag). Reaches a
            // terminal velocity (linear) and a bounded spin rate (angular) under
            // sustained pull rather than diverging. markTouched gates both so the
            // budget is consumed for the whole touched-this-tick state, not per
            // axis — calling it twice would burn it on the first call.
            if ((drag > 0.0 || angDrag > 0.0) && ShipTickBudget.markTouched(server, now)) {
                if (drag    > 0.0) SableBridge.dampLinearVelocity(server, drag);
                if (angDrag > 0.0) SableBridge.dampAngularVelocity(server, angDrag);
            }
            if (diag) {
                impulsesApplied += samplePoints.size();
                maxImpulseMag = Math.max(maxImpulseMag, totalForceMag * scaleAll);
            }
        }
        if (diag) {
            // Per-tick velocity delta corresponding to the strongest impulse this
            // call produced — at 1/20s/tick, that's the m/s/tick the rigid body
            // gets injected directly. Useful for sanity-checking calibration.
            final double dvPerTick = maxImpulseMag * 0.05;
            debugLog(level, "applyToSubLevels: candidates={} ids={} passedFilter={} samplesApplied={} maxSumImpulse={} dv/tick={} hasFilter={} fieldOrigin={} range={}",
                    candidates, candidateIds, passedFilter, impulsesApplied,
                    String.format("%.2fN", maxImpulseMag),
                    String.format("%.3fm/s", dvPerTick),
                    shipFilter != null, origin, range);
        }
    }


    private static void debugLog(final ServerLevel level, final String fmt, final Object... args) {
        if (!MagConfig.debugLogging()) return;
        final long tick = level.getGameTime();
        if (tick - lastDebugTick < 20L) return;
        lastDebugTick = tick;
        DEBUG_LOG.info(fmt, args);
    }

    // ---------------- entities ----------------

    /** Entities-only field application — used by the Repulsor Gun, which
     *  handles ships via its own mass-scaled impulse path (so small ships fly
     *  while the entity path still gives mob knockback). */
    public static void applyEntitiesOnly(final ServerLevel level, final MagneticField field) {
        if (field.polarity() == MagneticPolarity.NONE || field.strength().force() <= 0) return;
        applyToEntities(level, field);
    }

    private static void applyToEntities(final ServerLevel level, final MagneticField field) {
        final double r = field.range();
        final AABB box = AABB.ofSize(field.origin(), 2 * r, 2 * r, 2 * r);
        final List<Entity> nearby = level.getEntities((Entity) null, box, FieldApplicator::isMagnetizable);
        if (nearby.isEmpty()) return;

        // Per-emitter-tick constants — pulled out of the per-entity loop so we
        // pay the biome lookup + config getters once instead of per-entity.
        final double globalScalar = computeGlobalScalar(level, field);
        final double cosHalfAngle = conicalHalfAngleCos();
        final double velScale = entityVelocityScale();
        final double rSqr = r * r;

        for (Entity entity : nearby) {
            final Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5d, 0);
            if (entityPos.distanceToSqr(field.origin()) > rSqr) continue;

            // Diamagnetic items are repelled by BOTH poles — pushed away from the
            // source regardless of field polarity. The field weakens with distance,
            // so the repulsion balances gravity at a stable hover height; we also
            // damp existing velocity each tick to settle the float instead of bob.
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity di
                    && di.getItem().is(MagTags.DIAMAGNETIC_ITEMS)) {
                final double mag = forceAtPrecomputed(field, entityPos, globalScalar, cosHalfAngle).length();
                if (mag > 1.0e-6) {
                    Vec3 away = entityPos.subtract(field.origin());
                    away = away.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : away.normalize();
                    final Vec3 impulse = away.scale(mag * DIAMAGNETIC_SUSCEPTIBILITY * velScale);
                    entity.setDeltaMovement(entity.getDeltaMovement().scale(0.55).add(impulse));
                    entity.hurtMarked = true;
                }
                continue;
            }

            final double susceptibility = susceptibilityOf(entity);
            if (susceptibility <= 0) continue;

            // Like polarities repel, unlike attract. Entity NORTH (default) preserves
            // forceAt's sign; SOUTH flips it; NONE produces no force regardless of susceptibility.
            final MagneticPolarity entityPol = polarityOf(entity);
            if (entityPol == MagneticPolarity.NONE) continue;
            final double polaritySign = entityPol == MagneticPolarity.SOUTH ? -1.0d : 1.0d;

            final Vec3 impulse = forceAtPrecomputed(field, entityPos, globalScalar, cosHalfAngle)
                    .scale(susceptibility * polaritySign);
            entity.setDeltaMovement(entity.getDeltaMovement().add(impulse.scale(velScale)));
            entity.hurtMarked = true;
        }
    }

    private static boolean isMagnetizable(final Entity e) {
        // Cross-mod opt-out: respect Magnetizing's unmoveable list so admin/server
        // owners only need to curate one tag for both mods. Checked first because
        // it's a hard veto.
        if (e.getType().is(MagTags.MAGNETIZING_UNMOVEABLE)) return false;
        if (e instanceof IMagnetizable) return true;
        if (e.getType().is(MagTags.MAGNETIZABLE_ENTITIES)) return true;
        if (e instanceof ItemEntity item) {
            if (item.getItem().getItem() instanceof IMagnetizable) return true;
            return item.getItem().is(MagTags.FERROMAGNETIC_ITEMS)
                    || item.getItem().is(MagTags.DIAMAGNETIC_ITEMS);
        }
        // Any living entity wearing tagged metal armor is magnetizable through
        // its gear — players (not in the entity tag by default) and modded
        // humanoids included. {@link #baseSusceptibility} reads the armor and
        // returns a non-zero value; this gate just keeps them in the candidate
        // set so that pass runs at all. Without this branch, the susceptibility
        // code would be unreachable for everything except tagged mobs.
        if (e instanceof LivingEntity living) {
            final boolean armorReacts = MagConfig.armorReactsToFields();
            for (final ItemStack armor : EquippedArmor.all(living)) {
                // A piece reacts if it's metal armor, OR if it's been explicitly
                // magnetized (carries a polarity stamp) — the latter lets gear
                // that's otherwise field-inert by design (e.g. the Magnetoresistive
                // Boots) opt in once a player magnetizes it.
                final boolean magnetized = armor.has(MagDataComponents.ARMOR_POLARITY.get());
                if (!armor.is(MagTags.METAL_ARMOR) && !magnetized) continue;
                // The magnetic elytra is an exception to armorReactsToFields:
                // magnetic reaction is its core function (rail-riding), so it
                // stays a candidate even when the toggle is off. Explicitly
                // magnetized pieces are likewise always candidates.
                if (armorReacts || isMagneticElytra(armor) || magnetized) return true;
            }
        }
        return false;
    }

    /** The magnetic elytra reacts to fields even when {@code armorReactsToFields}
     *  is off. That toggle exists so incidental metal armor doesn't yank a player
     *  around; it must not disable a deliberately-crafted magnetic item whose
     *  whole purpose is to surf along magnetic rails. */
    private static boolean isMagneticElytra(final ItemStack stack) {
        return stack.getItem() instanceof com.stonytark.magnetization.content.item.MagneticElytraItem;
    }

    private static double susceptibilityOf(final Entity e) {
        double base = baseSusceptibility(e);
        if (base <= 0) return 0;
        if (e instanceof LivingEntity living) {
            // Magnetic elytra rail-ride: while gliding with the magnetic
            // elytra in the chest slot, fields tug the wearer harder so they
            // can surf between emitters like riding magnetic rails.
            if (living.isFallFlying()) {
                final ItemStack chest = living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                if (chest.getItem() instanceof com.stonytark.magnetization.content.item.MagneticElytraItem) {
                    base *= com.stonytark.magnetization.content.item.MagneticElytraItem.GLIDE_SUSCEPTIBILITY_BONUS;
                }
            }
            // Magnetized status effect multiplies pull strength.
            final MobEffectInstance effect = living.getEffect(MagEffects.MAGNETIZED);
            if (effect != null) {
                return base * MagnetizedEffect.multiplierFor(effect.getAmplifier());
            }
        }
        return base;
    }

    private static double baseSusceptibility(final Entity e) {
        if (e instanceof IMagnetizable m) return m.magneticSusceptibility();
        if (e instanceof ItemEntity item) {
            if (item.getItem().getItem() instanceof IMagnetizable m) return m.magneticSusceptibility();
            // Petrified wood is intrinsically weak — checked before the generic
            // ferromagnetic_items pass so the 1.0 baseline doesn't override it.
            if (item.getItem().is(MagItems.PETRIFIED_WOOD.get())) return PETRIFIED_WOOD_SUSCEPTIBILITY;
            if (item.getItem().is(MagTags.FERROMAGNETIC_ITEMS)) return 1.0d;
        }
        // Tagged-entity baseline (zombies, iron golems, item drops marked
        // ferromagnetic-by-type, etc.) — counted before the armor pass so a
        // tagged mob wearing magnetized iron stacks both contributions.
        double sum = 0.0d;
        if (e.getType().is(MagTags.MAGNETIZABLE_ENTITIES)) sum += 1.0d;
        // Any LivingEntity wearing tagged metal armor is pulled in proportion to
        // how many pieces it has on. Magnetized pieces (stamped with a polarity
        // by the electromagnet GUI) get an additional bonus on top — so a zombie
        // in magnetized iron plate, an iron golem with a chestplate stuffed on
        // it via /summon, or a player in fresh ferromagnetic armor all behave
        // identically. Used to be Player-only.
        if (e instanceof LivingEntity living) {
            final long now = living.level().getGameTime();
            final boolean armorReacts = MagConfig.armorReactsToFields();
            for (final ItemStack armor : EquippedArmor.all(living)) {
                final boolean magnetized = armor.has(MagDataComponents.ARMOR_POLARITY.get());
                if (!armor.is(MagTags.METAL_ARMOR) && !magnetized) continue;
                // armorReactsToFields off → only the magnetic elytra and
                // explicitly-magnetized pieces still contribute, so plain armor
                // won't yank the player but the elytra rail-ride / opted-in
                // magnetized gear keep working.
                if (!armorReacts && !isMagneticElytra(armor) && !magnetized) continue;
                sum += PER_ARMOR_SUSCEPTIBILITY;
                if (armor.has(MagDataComponents.ARMOR_POLARITY.get())) {
                    // Permanent stamps return strength=1.0; LIRM stamps decay from 1.0 → 0.0.
                    sum += PER_MAGNETIZED_BONUS * Lirm.strength(armor, now);
                }
            }
        }
        return sum;
    }

    private static MagneticPolarity polarityOf(final Entity e) {
        if (e instanceof IMagnetizable m) return m.magneticPolarity();
        if (e instanceof ItemEntity item && item.getItem().getItem() instanceof IMagnetizable m) {
            return m.magneticPolarity();
        }
        // Any living entity carrying magnetized armor (player, zombie, golem,
        // skeleton, etc.) presents the net pole of its armor. Tie / no
        // magnetized armor → NORTH (default convention).
        if (e instanceof LivingEntity living) {
            int net = 0;
            for (final ItemStack armor : EquippedArmor.all(living)) {
                final MagneticPolarity pol = armor.get(MagDataComponents.ARMOR_POLARITY.get());
                if (pol != null) net += pol.sign();
            }
            if (net > 0) return MagneticPolarity.NORTH;
            if (net < 0) return MagneticPolarity.SOUTH;
        }
        // Default convention: untagged entities present a NORTH face, so a SOUTH
        // emitter pulls them and a NORTH emitter pushes them.
        return MagneticPolarity.NORTH;
    }

    // ---------------- field math ----------------

    /**
     * Compute the world-space impulse the field exerts on a unit-mass test particle
     * located at {@code samplePos}. Returns zero if the sample is out of range or
     * outside the shape's effective region.
     */
    public static Vec3 forceAt(final MagneticField field, final Vec3 samplePos) {
        return forceAt(null, field, samplePos);
    }

    /** Variant that consults the world to apply the anomaly-biome strength bonus when
     *  the emitter origin lies inside the {@code magnetization:anomaly} biome. */
    public static Vec3 forceAt(final @Nullable ServerLevel level, final MagneticField field, final Vec3 samplePos) {
        // Hot-path callers should pre-compute the scalar + cone cap once per
        // emitter tick and call forceAtPrecomputed instead — this overload pays
        // the biome lookup + 2 config getters on every call.
        return forceAtPrecomputed(field, samplePos,
                computeGlobalScalar(level, field), conicalHalfAngleCos());
    }

    /**
     * Hot-loop fast path. Caller pre-computes the global scalar (which folds
     * field.strength.force × polarity.sign × {@link #strengthMultiplier()}, plus
     * the {@link AnomalyBiome#STRENGTH_BONUS} when the emitter is inside the
     * anomaly biome) and the cone cosine cap. Eliminates a per-sample biome
     * lookup + 2 per-sample config getters from {@code forceAt} — meaningful on
     * servers where many emitters each sample many ships many times per tick.
     *
     * <p>All vector arithmetic is done in primitives — only the final result
     * Vec3 is allocated. Cuts the per-sample allocation count from ~3 (the
     * old {@code subtract → normalize → scale} chain) down to 1 (or 0 when
     * the sample is out-of-range / out-of-cone, short-circuiting to
     * {@link Vec3#ZERO}). On servers with many emitters touching many ships
     * this is the dominant per-tick allocation cost in this method.
     */
    static Vec3 forceAtPrecomputed(final MagneticField field, final Vec3 samplePos,
                                    final double scalar, final double cosHalfAngle) {
        final Vec3 origin = field.origin();
        final double dx = samplePos.x - origin.x;
        final double dy = samplePos.y - origin.y;
        final double dz = samplePos.z - origin.z;
        final double distSqr = dx * dx + dy * dy + dz * dz;
        final double range = field.range();
        if (distSqr < 1.0e-12 || distSqr > range * range) return Vec3.ZERO;
        final double distance = Math.sqrt(distSqr);

        return switch (field.shape()) {
            case OMNIDIRECTIONAL -> {
                // Inverse-square; clamp at distance=1.0 to avoid singular forces at the source block.
                // Convention assumes a default NORTH-poled target: NORTH emitter (positive scalar)
                // produces force in +toSample (away from origin = repel); SOUTH emitter (negative
                // scalar) produces force in -toSample (toward origin = attract). Targets with
                // SOUTH polarity flip this sign at the call site.
                final double r = Math.max(distance, 1.0d);
                // Fuse normalize (/ distance) and scale (× mag) into one
                // multiplier on the displacement vector. One Vec3 allocation.
                final double mag = scalar / (r * r);
                final double k = mag / distance;
                yield new Vec3(dx * k, dy * k, dz * k);
            }
            case DIRECTIONAL -> {
                // Dot via primitives to avoid a Vec3 alloc for toSample.
                final Vec3 axis = field.axis();
                final double along = dx * axis.x + dy * axis.y + dz * axis.z;
                if (along < 0) yield Vec3.ZERO; // no force behind the emitter
                final double falloff = Math.max(0.0d, 1.0d - distance / range);
                yield axis.scale(scalar * falloff);
            }
            case CONICAL -> {
                // Compute the dir/axis dot via primitives (no normalize allocation).
                // cosTheta = (dir · axis) where dir = (dx,dy,dz)/distance.
                final Vec3 axis = field.axis();
                final double invDist = 1.0d / distance;
                final double cosTheta = (dx * axis.x + dy * axis.y + dz * axis.z) * invDist;
                if (cosTheta < cosHalfAngle) yield Vec3.ZERO;
                final double falloff = Math.max(0.0d, 1.0d - distance / range);
                yield axis.scale(scalar * falloff * cosTheta);
            }
        };
    }

    /** Per-emitter-tick constant: bundles strength × polarity × global multiplier,
     *  with the anomaly biome bonus folded in when the emitter origin is inside
     *  the anomaly. Computed once at the start of each {@code applyTo*} pass and
     *  reused across every sample / entity in that pass. */
    static double computeGlobalScalar(final @Nullable ServerLevel level, final MagneticField field) {
        double scalar = field.strength().force() * field.polarity().sign() * strengthMultiplier();
        if (level != null && AnomalyBiome.isAt(level, BlockPos.containing(field.origin()))) {
            scalar *= AnomalyBiome.STRENGTH_BONUS;
        }
        return scalar;
    }
}
