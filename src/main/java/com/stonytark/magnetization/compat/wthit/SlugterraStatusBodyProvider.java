package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.effect.MagnetizedEffect;
import com.stonytark.magnetization.registry.MagEffects;
import mcp.mobius.waila.api.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Shows an actual remaining magnetic effect; trajectory deflection itself has no lingering status. */
public enum SlugterraStatusBodyProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        if (!MagConfig.slugterraCompatEnabled() || !(accessor.getEntity() instanceof LivingEntity living)) return;
        if (!SlugterraStatusDataProvider.supported(living)) return;
        final var data = accessor.getData().raw().getCompound(SlugterraStatusDataProvider.KEY);
        if (!data.hasUUID("entity") || !living.getUUID().equals(data.getUUID("entity"))) return;
        final long remaining = data.getLong("expires") - living.level().getGameTime();
        final boolean infinite = data.getBoolean("infinite");
        if (!infinite && remaining <= 0) return;
        final var effect = new MobEffectInstance(MagEffects.MAGNETIZED,
                infinite ? -1 : (int) Math.min(Integer.MAX_VALUE, remaining), data.getInt("amplifier"));
        tooltip.addLine(Component.translatable("tooltip.magnetization.slugterra.remaining",
                MobEffectUtil.formatDuration(effect, 1.0f, living.level().tickRateManager().tickrate()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (effect.getAmplifier() >= MagnetizedEffect.PIN_AMPLIFIER) {
            tooltip.addLine(Component.translatable("tooltip.magnetization.slugterra.pinned").withStyle(ChatFormatting.GRAY));
        }
    }
}
