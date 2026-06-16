package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.worldgen.AnomalyBiome;
import com.stonytark.magnetization.api.EquippedArmor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Random magnetic chaos field across the {@code magnetization:anomaly} biome.
 * On a periodic tick, all magnetism-receptive things inside the anomaly take
 * an impulse in a direction that smoothly varies over space and time —
 * superimposed sine waves at incommensurate frequencies, so the player feels
 * coherent "gusts" rather than tick-by-tick jitter.
 *
 * <p>Affects three things:
 * <ul>
 *   <li><b>Sable sub-levels</b> whose pose center is in the anomaly — push/pull
 *       impulses via {@link SableBridge#applyWorldImpulse} like a giant
 *       wandering emitter.</li>
 *   <li><b>Players wearing magnetized armor</b> — direct velocity injection;
 *       susceptibility scales with the count of currently-magnetized armor
 *       pieces (matching {@link com.stonytark.magnetization.physics.FieldApplicator}'s
 *       susceptibility math).</li>
 *   <li><b>Dropped ferromagnetic items</b> — direct velocity injection so the
 *       biome feels alive with stuff being tugged around. Player-radius
 *       restricted so distant chunks don't get scanned.</li>
 * </ul>
 *
 * <p>The whole effect is gated on {@link AnomalyBiome#enabled()}, so the
 * {@code worldgen.anomalyBiomeEnabled} config flag controls whether the
 * anomaly is "live" or just a visual biome.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AnomalyMagneticChaos {


    /** Peak per-tick velocity-injection magnitude (m/s) for players + items.
     *  Scaled by per-target susceptibility before application. */

    /** Peak Newtons applied to Sable ships per chaos update. Roughly half a
     *  STRONG emitter at point-blank (~2400 N) so the anomaly feels like a
     *  comparable-but-erratic disturbance rather than a polite nudge. The
     *  per-tick acceleration cap in {@link MagConfig#MAX_ACCEL_PER_TICK}
     *  clamps the effective dV; this just sets the unclamped ceiling. */

    /** Search radius around each player for affected ItemEntities. Items in
     *  unloaded chunks won't tick and don't matter; this keeps the scan
     *  cheap by ignoring items the player can't see anyway. */

    private AnomalyMagneticChaos() {}

    private static double chaosStrength() {
        try { return MagConfig.ANOMALY_CHAOS_STRENGTH.get(); } catch (Throwable t) { return 1.0d; }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!AnomalyBiome.enabled()) return;
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final long now = server.getGameTime();
        if ((now % com.stonytark.magnetization.config.MagConfig.anomalyChaosTicks()) != 0L) return;

        final double strength = chaosStrength();
        if (strength <= 0.0d) return;

        // Players + their nearby items. The outer onLevelTick already gated on
        // AnomalyBiome.enabled() — use the assume-enabled variant in these inner
        // per-entity / per-ship loops so we don't pay the config getter per
        // iteration. With many ships + many items in flight this matters.
        for (final ServerPlayer player : server.players()) {
            if (player.isSpectator() || player.isDeadOrDying()) continue;
            if (!AnomalyBiome.isAtAssumeEnabled(server, player.blockPosition())) continue;
            applyChaosToPlayer(player, now, strength);
            applyChaosToNearbyItems(server, player, now, strength);
        }

        // Sable sub-levels anywhere in this level — biome check per ship.
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return;
        for (final SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship)) continue;
            if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0) continue;
            final org.joml.Vector3dc poseVec = ship.logicalPose().position();
            final Vec3 shipPos = new Vec3(poseVec.x(), poseVec.y(), poseVec.z());
            if (!AnomalyBiome.isAtAssumeEnabled(server, BlockPos.containing(shipPos))) continue;
            applyChaosToShip(ship, shipPos, now, strength);
        }
    }

    private static void applyChaosToPlayer(final ServerPlayer player, final long now, final double strength) {
        // Susceptibility mirrors FieldApplicator's count: each magnetized armor
        // piece adds to a unitless multiplier so a fully-kitted player gets
        // tossed harder than a bare-handed wanderer.
        double susceptibility = 0.0d;
        for (final ItemStack armor : EquippedArmor.all(player)) {
            if (!armor.is(MagTags.METAL_ARMOR)) continue;
            final MagneticPolarity pol = armor.get(MagDataComponents.ARMOR_POLARITY.get());
            if (pol == null || pol == MagneticPolarity.NONE) continue;
            susceptibility += Lirm.strength(armor, now);
        }
        if (susceptibility <= 0) return;
        final Vec3 chaos = chaosVectorAt(now, player.position());
        final Vec3 delta = chaos.scale(com.stonytark.magnetization.config.MagConfig.anomalyEntityImpulse() * Math.min(susceptibility, 2.0d) * strength);
        player.setDeltaMovement(player.getDeltaMovement().add(delta));
        player.hurtMarked = true;
    }

    private static void applyChaosToNearbyItems(final ServerLevel server, final ServerPlayer player, final long now, final double strength) {
        final AABB box = AABB.ofSize(player.position(),
                2 * com.stonytark.magnetization.config.MagConfig.anomalyItemScanRadius(), 2 * com.stonytark.magnetization.config.MagConfig.anomalyItemScanRadius(), 2 * com.stonytark.magnetization.config.MagConfig.anomalyItemScanRadius());
        // Filter during traversal (skip pickup-delayed + non-ferromagnetic items)
        // instead of materialising every item in this large box and filtering after.
        for (final ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, box,
                i -> !i.hasPickUpDelay() && i.getItem().is(MagTags.FERROMAGNETIC_ITEMS))) {
            if (!AnomalyBiome.isAtAssumeEnabled(server, item.blockPosition())) continue;
            final Vec3 chaos = chaosVectorAt(now, item.position());
            item.setDeltaMovement(item.getDeltaMovement().add(chaos.scale(com.stonytark.magnetization.config.MagConfig.anomalyEntityImpulse() * strength)));
            item.hasImpulse = true;
        }
    }

    private static void applyChaosToShip(final ServerSubLevel ship, final Vec3 shipPos, final long now, final double strength) {
        final Vec3 chaos = chaosVectorAt(now, shipPos);
        final Vec3 force = chaos.scale(com.stonytark.magnetization.config.MagConfig.anomalyShipForce() * strength);
        SableBridge.applyWorldImpulse(ship, shipPos, force);
    }

    /**
     * Smooth chaos vector at {@code (t, pos)}: superimposed sine waves at
     * incommensurate spatial + temporal frequencies. Output magnitude is
     * roughly in [0, 1.5] — sum of two sines per axis. Not normalized
     * intentionally, since variable magnitude is *also* part of chaos.
     */
    private static Vec3 chaosVectorAt(final long t, final Vec3 pos) {
        final double phase = t * 0.05d;
        final double sx = pos.x * 0.07d;
        final double sy = pos.y * 0.05d;
        final double sz = pos.z * 0.07d;
        final double x = Math.sin(phase + sx)
                + 0.5d * Math.sin(phase * 1.7d + sx * 1.3d);
        final double y = Math.sin(phase + sy + 1.0d)
                + 0.5d * Math.sin(phase * 1.7d + sy * 1.3d + 1.7d);
        final double z = Math.sin(phase + sz + 2.0d)
                + 0.5d * Math.sin(phase * 1.7d + sz * 1.3d + 2.7d);
        return new Vec3(x, y, z);
    }
}
