package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.EquippedArmor;
import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.effect.LightningRemnantMagnetism;
import com.stonytark.magnetization.content.effect.LirmDecayHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Effect identity distinguishes electrical discharges from Slugterra's generic damage ticks. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class SlugterraElectricCompat {
    private static final ResourceLocation SHOCK = ResourceLocation.parse("slugterra:electric_shock");
    private static final String LAST_DISCHARGE = "magnetization:slugterra_last_discharge";

    private SlugterraElectricCompat() {}

    @SubscribeEvent
    public static void onShock(final MobEffectEvent.Added event) {
        final LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !MagConfig.slugterraElectricEnabled()
                || !MagConfig.LIRM_ENABLED.get() || event.getOldEffectInstance() != null
                || !SHOCK.equals(BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect().value()))) return;
        final var data = target.getPersistentData();
        final long now = level.getGameTime();
        if (data.contains(LAST_DISCHARGE)) {
            final long last = data.getLong(LAST_DISCHARGE);
            if (now >= last && now - last < MagConfig.SLUGTERRA_ELECTRIC_COOLDOWN.get()) return;
        }
        data.putLong(LAST_DISCHARGE, now);
        // Equipment-only helper: the synthetic lightning path also petrifies wood.
        LightningRemnantMagnetism.applyLirmStamp(target, "slugterra:electric_shock");
    }

    @SubscribeEvent
    public static void onLivingTick(final EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof ServerPlayer
                || !(target.level() instanceof ServerLevel level) || target.tickCount % 100 != 0) return;
        final var data = target.getPersistentData();
        if (!data.contains(LAST_DISCHARGE)) return;
        final long now = level.getGameTime();
        // Finish expiration even if compatibility is disabled after the discharge.
        EquippedArmor.all(target).forEach(stack -> LirmDecayHandler.clearIfExpired(stack, now));
        LirmDecayHandler.clearIfExpired(target.getMainHandItem(), now);
        LirmDecayHandler.clearIfExpired(target.getOffhandItem(), now);
        if (now - data.getLong(LAST_DISCHARGE) >= Math.max(Lirm.DURATION_TICKS, MagConfig.SLUGTERRA_ELECTRIC_COOLDOWN.get())) {
            data.remove(LAST_DISCHARGE);
        }
    }
}
