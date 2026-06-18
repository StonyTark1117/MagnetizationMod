package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.fluid.Gallium;
import com.stonytark.magnetization.registry.MagBlocks;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WTHIT body line for gallium's thermal state. Liquid gallium freezes when a
 * cooling source (ice/snow) is adjacent; solid gallium melts back once the
 * cooling is gone. The line shows whether the block is currently cooled and the
 * configured time it takes to flip. (The delay shown is the configured duration,
 * not a live remaining countdown — block scheduled-ticks aren't synced to the
 * client, so a true per-block timer would need a block entity on the fluid.)
 */
public enum GalliumStatusBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockState state = accessor.getBlockState();
        final Block block = state.getBlock();
        final boolean cooled = Gallium.coolingAdjacent(accessor.getWorld(), accessor.getPosition());

        if (block == MagBlocks.GALLIUM_BLOCK.get() || block == MagBlocks.MIXED_GALLIUM_BLOCK.get()) {
            if (cooled && state.getFluidState().isSource()) {
                final int secs = Math.max(1, Math.round(MagConfig.galliumFreezeDelayTicks() / 20f));
                tooltip.addLine(Component.translatable("tooltip.magnetization.gallium.freezing", secs)
                        .withStyle(ChatFormatting.AQUA));
            } else {
                tooltip.addLine(Component.translatable("tooltip.magnetization.gallium.liquid")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (block == MagBlocks.SOLID_GALLIUM.get()) {
            if (cooled) {
                tooltip.addLine(Component.translatable("tooltip.magnetization.gallium.frozen")
                        .withStyle(ChatFormatting.AQUA));
            } else {
                final int secs = Math.max(1, Math.round(MagConfig.galliumMeltDelayTicks() / 20f));
                tooltip.addLine(Component.translatable("tooltip.magnetization.gallium.melting", secs)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }
}
