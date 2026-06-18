package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.golem.GalliumGolem;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * WTHIT body lines for the Gallium Golem — beyond the default health bar. Shows
 * that it's a soft gallium golem and its current thermal state: in a warm biome
 * it slowly melts away (to a fluid source, no drops); in a cold biome it's
 * stable. Biome temperature is read client-side, so the state is live.
 */
public enum GalliumGolemBodyProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IEntityAccessor accessor, final IPluginConfig config) {
        final Entity entity = accessor.getEntity();
        if (!(entity instanceof GalliumGolem golem)) return;
        tooltip.addLine(Component.translatable("tooltip.magnetization.gallium_golem.soft")
                .withStyle(ChatFormatting.GRAY));
        final boolean cold = golem.level().getBiome(golem.blockPosition()).value().getBaseTemperature() < 0.2f;
        if (cold) {
            tooltip.addLine(Component.translatable("tooltip.magnetization.gallium_golem.cold")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.addLine(Component.translatable("tooltip.magnetization.gallium_golem.warm")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
