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
            living.removeEffect(MagEffects.RADON_EXPOSURE);
            return;
        }
        final boolean immersed = living.level().getFluidState(living.blockPosition().atY(
                net.minecraft.util.Mth.floor(living.getEyeY()))).getType().isSame(MagFluids.RADON.get());
        final long gameTime = living.level().getGameTime();
        final boolean recentExhaust = living.getPersistentData().contains(LAST_EXHAUST_KEY, net.minecraft.nbt.Tag.TAG_LONG)
                && living.getPersistentData().getLong(LAST_EXHAUST_KEY) >= gameTime - 1L;
        int dose = living.getPersistentData().getInt(KEY);
        if (immersed) dose++;
        else if (!recentExhaust) dose = Math.max(0, dose - MagConfig.radonExposureDecayPerTick());
        if (!recentExhaust) living.getPersistentData().remove(LAST_EXHAUST_KEY);
        setDose(living, dose);
    }

    /** Adds exhaust exposure without requiring the entity to occupy a gas cell. */
    public static void addExposure(final LivingEntity living, final int ticks) {
        if (living.level().isClientSide || !MagConfig.radonRadiationEnabled()) return;
        living.getPersistentData().putLong(LAST_EXHAUST_KEY, living.level().getGameTime());
        setDose(living, living.getPersistentData().getInt(KEY) + Math.max(0, ticks));
    }

    public static int exposure(final LivingEntity living) { return living.getPersistentData().getInt(KEY); }

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
