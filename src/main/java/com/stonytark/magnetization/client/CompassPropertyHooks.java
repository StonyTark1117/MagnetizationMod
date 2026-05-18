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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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
    private static final ResourceLocation COSMIC_ANGLE =
            ResourceLocation.fromNamespaceAndPath("magnetization", "cosmic_angle");

    /** Search radius for the Cosmic Compass meteorite scan. Much larger than
     *  the Field Compass range — meteorites are rare worldgen, so a wider
     *  arc justifies the dedicated item. Server-overridable via
     *  {@code MagConfig.COSMIC_COMPASS_RANGE}; this is the fallback default. */
    private static final double COSMIC_COMPASS_RANGE = 512.0;

    private static double liveCosmicRange() {
        try {
            return com.stonytark.magnetization.config.MagConfig.COSMIC_COMPASS_RANGE.get();
        } catch (final Throwable t) {
            return COSMIC_COMPASS_RANGE;
        }
    }

    private static final ResourceLocation NATURES_COMPASS_ITEM =
            ResourceLocation.fromNamespaceAndPath("naturescompass", "naturescompass");
    private static final ResourceLocation NATURES_COMPASS_ANGLE =
            ResourceLocation.fromNamespaceAndPath("naturescompass", "angle");
    private static final ResourceLocation EXPLORERS_COMPASS_ITEM =
            ResourceLocation.fromNamespaceAndPath("explorerscompass", "explorerscompass");
    private static final ResourceLocation EXPLORERS_COMPASS_ANGLE =
            ResourceLocation.fromNamespaceAndPath("explorerscompass", "angle");

    /** One-shot guard for {@link #installModCompasses()} — both target mods
     *  register their angle property inside {@code FMLClientSetupEvent}, so
     *  we wait for first-login and wrap then to guarantee we run last. */
    private static final AtomicBoolean MOD_COMPASSES_INSTALLED = new AtomicBoolean(false);

    private CompassPropertyHooks() {}

    public static void install() {
        wrapVanillaCompass();
        registerFieldCompass();
        registerCosmicCompass();
    }

    /** Wrap third-party compass needle properties so they scramble inside the
     *  anomaly biome. Idempotent — safe to call multiple times (later calls
     *  no-op). Must run AFTER the host mods' own FMLClientSetupEvent has
     *  registered their original property, hence the deferred call from the
     *  login hook in {@link MagClientRegistration}. */
    public static void installModCompasses() {
        if (!MOD_COMPASSES_INSTALLED.compareAndSet(false, true)) return;
        wrapModCompass("naturescompass", NATURES_COMPASS_ITEM, NATURES_COMPASS_ANGLE,
                CompassPropertyHooks::naturesCompassEnabled);
        wrapModCompass("explorerscompass", EXPLORERS_COMPASS_ITEM, EXPLORERS_COMPASS_ANGLE,
                CompassPropertyHooks::explorersCompassEnabled);
    }

    @SuppressWarnings("deprecation")
    private static void wrapModCompass(final String modId,
                                       final ResourceLocation itemRl,
                                       final ResourceLocation propRl,
                                       final BooleanSupplier configGate) {
        if (!ModList.get().isLoaded(modId)) return;
        final Item item = BuiltInRegistries.ITEM.get(itemRl);
        if (item == null || item == Items.AIR) return;
        final @Nullable ItemPropertyFunction original =
                ItemProperties.getProperty(item.getDefaultInstance(), propRl);
        ItemProperties.register(item, propRl, (stack, level, entity, seed) -> {
            if (configGate.getAsBoolean() && entity != null && level != null) {
                if (level.getBiome(entity.blockPosition()).is(AnomalyBiome.KEY)) {
                    return scrambledAngle(level, entity);
                }
            }
            return original != null ? original.call(stack, level, entity, seed) : 0.0f;
        });
    }

    private static boolean naturesCompassEnabled() {
        try { return MagConfig.ANOMALY_AFFECTS_NATURES_COMPASS.get(); }
        catch (final Throwable t) { return true; }
    }

    private static boolean explorersCompassEnabled() {
        try { return MagConfig.ANOMALY_AFFECTS_EXPLORERS_COMPASS.get(); }
        catch (final Throwable t) { return true; }
    }

    // ---------------- vanilla compass anomaly scramble ----------------

    // MC 1.21.1 only ships the legacy ItemProperties + ItemPropertyFunction
    // dispatch system. The 1.21.4+ replacement (RangeSelectItemModelProperty +
    // items/<name>.json definitions) doesn't exist in this version's classpath,
    // so we have to use the deprecated API. See memory note
    // [[1.21.1 item-model API]] for the long version.
    @SuppressWarnings("deprecation")
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

    private static void registerCosmicCompass() {
        ItemProperties.register(MagItems.COSMIC_COMPASS.get(), COSMIC_ANGLE,
                (stack, level, entity, seed) -> {
                    if (level == null || entity == null) return 0.0f;
                    // Cosmic Compass is NOT scrambled by the anomaly biome —
                    // meteorite cores read clean above the flux noise.
                    return cosmicAngle(level, entity);
                });
    }

    /** Needle angle pointing at the closest active meteorite_core within
     *  {@link #COSMIC_COMPASS_RANGE}. Returns 0 (north / no signal) when no
     *  meteorite is in range OR when all candidates are fully decayed. */
    private static float cosmicAngle(final Level level, final Entity holder) {
        final BlockPos target = findNearestActiveMeteorite(level, holder.position());
        if (target == null) return 0.0f;
        final Vec3 holderPos = holder.position();
        final double dx = target.getX() + 0.5 - holderPos.x;
        final double dz = target.getZ() + 0.5 - holderPos.z;
        final double bearingRad = Math.atan2(dz, dx);
        final double yawRad = Math.toRadians(holder.getYRot() - 90.0);
        final double angleRad = bearingRad - yawRad;
        return (float) Mth.positiveModulo(angleRad / (Math.PI * 2.0), 1.0);
    }

    /** Walk the emitter registry for any MeteoriteCoreBlockEntity within
     *  range whose {@code currentField()} is non-null (i.e. still has charge
     *  remaining). Cosmic Compass intentionally ignores inert / decayed
     *  cores so the player isn't sent on a wild goose chase to a dead one. */
    private static @Nullable BlockPos findNearestActiveMeteorite(final Level level, final Vec3 from) {
        BlockPos best = null;
        final double range = liveCosmicRange();
        double bestDistSqr = range * range;
        for (final BlockPos pos : EmitterRegistry.snapshot(level)) {
            final double d2 = pos.getCenter().distanceToSqr(from);
            if (d2 >= bestDistSqr) continue;
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity meteorite)) continue;
            if (meteorite.currentField() == null) continue;
            best = pos;
            bestDistSqr = d2;
        }
        return best;
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
