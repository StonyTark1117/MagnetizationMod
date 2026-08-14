package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Server-authoritative cumulative Radon dose from placed gas and thruster exhaust. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class RadonExposureHandler {
    private static final String KEY = "magnetization:radon_exposure";
    private static final String LAST_EXHAUST_KEY = "magnetization:last_radon_exhaust_tick";
    private static final String LAST_EXHAUST_CLEARANCE_KEY = "magnetization:last_radon_exhaust_clearance";
    private RadonExposureHandler() {}

    @SubscribeEvent
    public static void onEntityTick(final EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living.level().isClientSide) return;
        tickExposure(living);
    }

    /** Applies one server tick of immersion/clean-air processing. Kept separate
     * from the event entry point so the tick-order edge case is testable. */
    public static void tickExposure(final LivingEntity living) {
        if (living.level().isClientSide) return;
        if (!MagConfig.radonRadiationEnabled()) {
            living.getPersistentData().remove(KEY);
            living.getPersistentData().remove(LAST_EXHAUST_KEY);
            living.getPersistentData().remove(LAST_EXHAUST_CLEARANCE_KEY);
            living.removeEffect(MagEffects.RADON_EXPOSURE);
            return;
        }
        final boolean immersed = isImmersed(living);
        final long gameTime = living.level().getGameTime();
        final boolean recentExhaust = living.getPersistentData().contains(LAST_EXHAUST_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && living.getPersistentData().getLong(LAST_EXHAUST_KEY) >= gameTime - 1L;
        int dose = living.getPersistentData().getInt(KEY);
        if (immersed) dose++;
        else if (!recentExhaust) dose = Math.max(0, dose - MagConfig.radonExposureDecayPerTick());
        if (!recentExhaust) {
            living.getPersistentData().remove(LAST_EXHAUST_KEY);
            living.getPersistentData().remove(LAST_EXHAUST_CLEARANCE_KEY);
        }
        setDose(living, dose);
    }

    /** Adds exhaust exposure without requiring the entity to occupy a gas cell. */
    public static void addExposure(final LivingEntity living, final int ticks) {
        addExposure(living, ticks, 0.0d);
    }

    /** Adds exhaust exposure and records the shortest current escape distance for detector readouts. */
    public static void addExposure(final LivingEntity living, final int ticks, final double distanceToSafety) {
        if (living.level().isClientSide || !MagConfig.radonRadiationEnabled()) return;
        final long gameTime = living.level().getGameTime();
        final long previousTick = living.getPersistentData().getLong(LAST_EXHAUST_KEY);
        final double previousClearance = previousTick == gameTime
                ? living.getPersistentData().getDouble(LAST_EXHAUST_CLEARANCE_KEY) : 0.0d;
        living.getPersistentData().putLong(LAST_EXHAUST_KEY, gameTime);
        living.getPersistentData().putDouble(LAST_EXHAUST_CLEARANCE_KEY,
                Math.max(previousClearance, Math.max(0.0d, distanceToSafety)));
        setDose(living, living.getPersistentData().getInt(KEY) + Math.max(0, ticks));
    }

    public static int exposure(final LivingEntity living) { return living.getPersistentData().getInt(KEY); }

    /** Current server-owned dose, recovery, and hazard-clearance state for a detector holder. */
    public static ExposureSnapshot snapshot(final LivingEntity living) {
        final boolean enabled = MagConfig.radonRadiationEnabled();
        final int dose = enabled ? exposure(living) : 0;
        final int threshold = MagConfig.radonExposureThresholdTicks();
        final int recovery = MagConfig.radonExposureDecayPerTick();
        if (!enabled) return new ExposureSnapshot(false, 0, threshold, recovery, false, 0.0d);

        final boolean immersed = isImmersed(living);
        final long gameTime = living.level().getGameTime();
        final boolean recentExhaust = living.getPersistentData().contains(
                LAST_EXHAUST_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && living.getPersistentData().getLong(LAST_EXHAUST_KEY) >= gameTime - 1L;
        double distanceToSafety = immersed ? immersedDistanceToSafety(living) : 0.0d;
        if (recentExhaust) {
            distanceToSafety = Math.max(distanceToSafety,
                    living.getPersistentData().getDouble(LAST_EXHAUST_CLEARANCE_KEY));
        }
        return new ExposureSnapshot(true, dose, threshold, recovery,
                immersed || recentExhaust, distanceToSafety);
    }

    private static boolean isImmersed(final LivingEntity living) {
        return living.level().getFluidState(living.blockPosition().atY(
                net.minecraft.util.Mth.floor(living.getEyeY()))).getType().isSame(MagFluids.RADON.get());
    }

    /** Shortest distance from the entity's eye to a face of its current Radon cell. */
    private static double immersedDistanceToSafety(final LivingEntity living) {
        final double x = living.getX();
        final double y = living.getEyeY();
        final double z = living.getZ();
        final double localX = x - Math.floor(x);
        final double localY = y - Math.floor(y);
        final double localZ = z - Math.floor(z);
        return Math.max(0.0d, Math.min(Math.min(localX, 1.0d - localX),
                Math.min(Math.min(localY, 1.0d - localY), Math.min(localZ, 1.0d - localZ))));
    }

    public record ExposureSnapshot(boolean radiationEnabled, int dose, int threshold,
                                   int recoveryPerTick, boolean exposed,
                                   double distanceToSafety) {}

    private static void setDose(final LivingEntity living, final int dose) {
        final int previousDose = living.getPersistentData().getInt(KEY);
        living.getPersistentData().putInt(KEY, Math.max(0, dose));
        final int threshold = MagConfig.radonExposureThresholdTicks();
        if (dose >= threshold) {
            living.addEffect(new MobEffectInstance(MagEffects.RADON_EXPOSURE, 120, 0, false, true, true));
            final int excess = dose - threshold;
            if (dose > previousDose && excess % MagConfig.radonDamageIntervalTicks() == 0) {
                living.hurt(living.damageSources().magic(), (float) MagConfig.radonDamageAmount());
            }
        } else {
            living.removeEffect(MagEffects.RADON_EXPOSURE);
        }
    }
}
