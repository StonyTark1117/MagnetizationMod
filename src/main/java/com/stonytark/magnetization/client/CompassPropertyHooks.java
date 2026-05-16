package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.worldgen.AnomalyBiome;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Registers item-property functions for both compasses:
 *
 * <ul>
 *   <li><b>Vanilla compass</b> — wraps the existing {@code minecraft:angle}
 *       property so the needle spins erratically inside the Magnetic Anomaly
 *       biome. Outside the anomaly the original function runs unmodified
 *       (lodestone tracking, spawn pointing, off-world wobble all behave as
 *       vanilla). Gated by {@link MagConfig#ANOMALY_AFFECTS_VANILLA_COMPASS}.</li>
 *   <li><b>Field compass</b> — registers our own {@code magnetization:angle}
 *       predicate that returns 0..1 mapping the needle frame for direction to
 *       the nearest active emitter. Anomaly biome scrambles the needle the
 *       same way the vanilla one is scrambled, so both compasses behave
 *       consistently when the player is standing in the field flux.</li>
 * </ul>
 *
 * <p>Implementation note: 1.21.1 still uses the legacy {@link ItemProperties}
 * predicate system for compass-angle dispatch. The new range-dispatch item
 * model definitions ({@code items/<name>.json}) were introduced in 1.21.4 and
 * don't exist in our target version. Calling
 * {@link ItemProperties#register} a second time with the same key replaces
 * the entry, which is exactly what we want for the vanilla compass wrap.
 */
public final class CompassPropertyHooks {

    private static final ResourceLocation VANILLA_ANGLE =
            ResourceLocation.withDefaultNamespace("angle");
    private static final ResourceLocation FIELD_ANGLE =
            ResourceLocation.fromNamespaceAndPath("magnetization", "angle");

    private CompassPropertyHooks() {}

    public static void install() {
        wrapVanillaCompass();
        registerFieldCompass();
    }

    // ---------------- vanilla compass anomaly scramble ----------------

    private static void wrapVanillaCompass() {
        final @Nullable ItemPropertyFunction original =
                ItemProperties.getProperty(Items.COMPASS.getDefaultInstance(), VANILLA_ANGLE);
        ItemProperties.register(Items.COMPASS, VANILLA_ANGLE, (stack, level, entity, seed) -> {
            if (anomalyOverrideEnabled() && entity != null && level != null) {
                if (level.getBiome(entity.blockPosition()).is(AnomalyBiome.KEY)) {
                    return scrambledAngle(level, entity);
                }
            }
            return original != null ? original.call(stack, level, entity, seed) : 0.0f;
        });
    }

    private static boolean anomalyOverrideEnabled() {
        try { return MagConfig.ANOMALY_AFFECTS_VANILLA_COMPASS.get(); }
        catch (final Throwable t) { return true; }
    }

    // ---------------- our field compass ----------------

    private static void registerFieldCompass() {
        ItemProperties.register(MagItems.FIELD_COMPASS.get(), FIELD_ANGLE,
                (stack, level, entity, seed) -> {
                    if (level == null || entity == null) return 0.0f;
                    if (level.getBiome(entity.blockPosition()).is(AnomalyBiome.KEY)) {
                        return scrambledAngle(level, entity);
                    }
                    return targetedAngle(level, entity);
                });
    }

    /** Compute the needle frame fraction (0..1) pointing toward the closest
     *  active emitter within {@link MagConfig#COMPASS_RANGE}. Returns 0 when
     *  no target — needle stays at frame 0 ("north / no signal"). */
    private static float targetedAngle(final Level level, final Entity holder) {
        final BlockPos target = findNearestEmitter(level, holder.position());
        if (target == null) return 0.0f;
        final Vec3 holderPos = holder.position();
        final double dx = target.getX() + 0.5 - holderPos.x;
        final double dz = target.getZ() + 0.5 - holderPos.z;
        final double bearingRad = Math.atan2(dz, dx);
        // Yaw 0 = south in Minecraft convention; offset so frame 0 is "needle up".
        final double yawRad = Math.toRadians(holder.getYRot() - 90.0);
        final double angleRad = bearingRad - yawRad;
        return (float) Mth.positiveModulo(angleRad / (Math.PI * 2.0), 1.0);
    }

    /** Chaotic spin shared by both compasses while inside the anomaly. ~1.6
     *  rotations/sec base + per-frame jitter so the needle visibly twitches
     *  rather than smoothly cycling — reads as "the field flux is scrambling
     *  the reading", not "the compass is broken". */
    private static float scrambledAngle(final Level level, final Entity holder) {
        final long t = level.getGameTime();
        final double base = (t * 0.08d) % 1.0d;
        final double jitter = (holder.getRandom().nextFloat() - 0.5f) * 0.15d;
        final double v = base + jitter;
        return (float) (v - Math.floor(v));
    }

    private static @Nullable BlockPos findNearestEmitter(final Level level, final Vec3 from) {
        final double range = compassRange();
        BlockPos best = null;
        double bestDistSqr = range * range;
        for (final BlockPos pos : EmitterRegistry.snapshot(level)) {
            final double d2 = pos.getCenter().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource source)) continue;
            final MagneticField field = source.currentField();
            if (field == null) continue;
            best = pos;
            bestDistSqr = d2;
        }
        return best;
    }

    private static double compassRange() {
        try { return MagConfig.COMPASS_RANGE.get(); } catch (Throwable t) { return 16.0d; }
    }
}
