package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body line for the Barkhausen block. Shows its live analog redstone output
 * (0–15) — which jitters randomly while a magnet is adjacent and reads 0 when idle.
 * The value is synced off the BE, so it flickers in the tooltip exactly as the
 * Barkhausen noise does, making the block readable as a noise source.
 */
public enum BarkhausenBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof BarkhausenBlockEntity barkhausen)) return;
        final int signal = barkhausen.getSignal();
        final Component value = signal > 0
                ? Component.translatable("tooltip.magnetization.barkhausen.jitter", signal)
                : Component.translatable("tooltip.magnetization.barkhausen.idle");
        tooltip.addLine(value.copy().withStyle(ChatFormatting.AQUA));
    }
}
