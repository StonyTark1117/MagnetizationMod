package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.IMagnetizable;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.effect.MagnetizedEffect;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
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
     *  the pull of plain iron. */
    public static final double PER_MAGNETIZED_BONUS = 0.6d;

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

        final double range = field.range();
        final Vec3 origin = field.origin();
        final BoundingBox3d searchBox = new BoundingBox3d(
                origin.x - range, origin.y - range, origin.z - range,
                origin.x + range, origin.y + range, origin.z + range
        );

        // Diagnostics — only allocated when the debug log gate is open.
        final boolean diag = MagConfig.debugLogging();
        int candidates = 0, passedFilter = 0, impulsesApplied = 0;
        double maxImpulseMag = 0.0;
        final java.util.List<String> candidateIds = diag ? new java.util.ArrayList<>() : null;
        for (SubLevel sub : container.queryIntersecting(searchBox)) {
            if (diag) candidates++;
            if (diag && sub instanceof ServerSubLevel s) candidateIds.add(s.getUniqueId().toString().substring(0, 8));
            if (!(sub instanceof ServerSubLevel server) || server == exclude) continue;
            // Reject phantom sub-levels: stale entries Sable's spatial index
            // can return after a shatter/unload. Applying velocity to one of
            // these crashes Rapier with "No rigid body for id".
            if (server.getMassTracker().isInvalid() || server.getMassTracker().getMass() <= 0.0) continue;
            if (container.getSubLevel(server.getUniqueId()) == null) continue;
            if (shipFilter != null && !shipFilter.test(server)) continue;
            if (diag) passedFilter++;

            // Pick the closest point inside the sub-level's bounding box to the field origin
            // and apply the impulse there. This is a coarse approximation — real interaction
            // would integrate force over the ship's volume — but it's stable enough for
            // gameplay and avoids visiting interior blocks one at a time.
            final BoundingBox3dc subBox = sub.boundingBox();
            final Vec3 closest = new Vec3(
                    Math.max(subBox.minX(), Math.min(origin.x, subBox.maxX())),
                    Math.max(subBox.minY(), Math.min(origin.y, subBox.maxY())),
                    Math.max(subBox.minZ(), Math.min(origin.z, subBox.maxZ()))
            );
            if (closest.distanceToSqr(origin) > range * range) continue;

            Vec3 impulse = forceAt(field, closest);
            if (impulse.lengthSqr() < 1.0e-6) continue;

            // Sable's solver applies F = ma internally, so a constant impulse
            // already produces less acceleration on heavier ships. The cap below
            // additionally bounds peak acceleration so a STRONG anchor can't
            // launch a tiny test ship into the next chunk.
            final double cap = maxAccelPerTick();
            if (cap > 0) {
                final double mass = server.getMassTracker().getMass();
                if (mass > 0) {
                    final double currentAccel = impulse.length() / mass;
                    if (currentAccel > cap) {
                        impulse = impulse.scale(cap * mass / impulse.length());
                    }
                }
            }
            SableBridge.applyWorldImpulse(server, closest, impulse);
            if (diag) {
                impulsesApplied++;
                maxImpulseMag = Math.max(maxImpulseMag, impulse.length());
            }
        }
        if (diag) {
            // Per-tick velocity delta corresponding to the strongest impulse this
            // call produced — at 1/20s/tick, that's the m/s/tick the rigid body
            // gets injected directly. Useful for sanity-checking calibration.
            final double dvPerTick = maxImpulseMag * 0.05;
            debugLog(level, "applyToSubLevels: candidates={} ids={} passedFilter={} impulsesApplied={} maxImpulse={} dv/tick={} hasFilter={} fieldOrigin={} range={}",
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

    private static void applyToEntities(final ServerLevel level, final MagneticField field) {
        final double r = field.range();
        final AABB box = AABB.ofSize(field.origin(), 2 * r, 2 * r, 2 * r);
        final List<Entity> nearby = level.getEntities((Entity) null, box, FieldApplicator::isMagnetizable);

        for (Entity entity : nearby) {
            final Vec3 entityPos = entity.position().add(0, entity.getBbHeight() * 0.5d, 0);
            if (entityPos.distanceToSqr(field.origin()) > r * r) continue;

            final double susceptibility = susceptibilityOf(entity);
            if (susceptibility <= 0) continue;

            // Like polarities repel, unlike attract. Entity NORTH (default) preserves
            // forceAt's sign; SOUTH flips it; NONE produces no force regardless of susceptibility.
            final MagneticPolarity entityPol = polarityOf(entity);
            if (entityPol == MagneticPolarity.NONE) continue;
            final double polaritySign = entityPol == MagneticPolarity.SOUTH ? -1.0d : 1.0d;

            final Vec3 impulse = forceAt(field, entityPos).scale(susceptibility * polaritySign);
            entity.setDeltaMovement(entity.getDeltaMovement().add(impulse.scale(entityVelocityScale())));
            entity.hurtMarked = true;
        }
    }

    private static boolean isMagnetizable(final Entity e) {
        if (e instanceof IMagnetizable) return true;
        if (e.getType().is(MagTags.MAGNETIZABLE_ENTITIES)) return true;
        if (e instanceof ItemEntity item) {
            if (item.getItem().getItem() instanceof IMagnetizable) return true;
            return item.getItem().is(MagTags.FERROMAGNETIC_ITEMS);
        }
        return false;
    }

    private static double susceptibilityOf(final Entity e) {
        final double base = baseSusceptibility(e);
        if (base <= 0) return 0;
        // Magnetized status effect multiplies pull strength.
        if (e instanceof LivingEntity living) {
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
            for (final ItemStack armor : living.getArmorSlots()) {
                if (!armor.is(MagTags.METAL_ARMOR)) continue;
                sum += PER_ARMOR_SUSCEPTIBILITY;
                if (armor.has(MagDataComponents.ARMOR_POLARITY.get())) sum += PER_MAGNETIZED_BONUS;
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
            for (final ItemStack armor : living.getArmorSlots()) {
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
        final Vec3 toSample = samplePos.subtract(field.origin());
        final double distance = toSample.length();
        if (distance < 1.0e-6 || distance > field.range()) return Vec3.ZERO;

        final double scalar = field.strength().force() * field.polarity().sign() * strengthMultiplier();

        return switch (field.shape()) {
            case OMNIDIRECTIONAL -> {
                // Inverse-square; clamp at distance=1.0 to avoid singular forces at the source block.
                // Convention assumes a default NORTH-poled target: NORTH emitter (positive scalar)
                // produces force in +toSample (away from origin = repel); SOUTH emitter (negative
                // scalar) produces force in -toSample (toward origin = attract). Targets with
                // SOUTH polarity flip this sign at the call site.
                final double r = Math.max(distance, 1.0d);
                final double mag = scalar / (r * r);
                yield toSample.normalize().scale(mag);
            }
            case DIRECTIONAL -> {
                final double along = toSample.dot(field.axis());
                if (along < 0) yield Vec3.ZERO; // no force behind the emitter
                final double falloff = Math.max(0.0d, 1.0d - distance / field.range());
                yield field.axis().scale(scalar * falloff);
            }
            case CONICAL -> {
                final Vec3 dir = toSample.normalize();
                final double cosTheta = dir.dot(field.axis());
                if (cosTheta < conicalHalfAngleCos()) yield Vec3.ZERO;
                final double falloff = Math.max(0.0d, 1.0d - distance / field.range());
                yield field.axis().scale(scalar * falloff * cosTheta);
            }
        };
    }
}
