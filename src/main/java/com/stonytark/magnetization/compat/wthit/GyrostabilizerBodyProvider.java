package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.gyro.GyrostabilizerBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * WTHIT status line for the Gyrostabilizer: whether it's actively locking a
 * craft's rotation, powered-but-idle (not mounted on a ship), or off. The FE bar
 * itself is drawn by WTHIT's built-in energy provider from the registered
 * capability.
 */
public enum GyrostabilizerBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockState state = accessor.getBlockState();
        if (!state.is(MagBlocks.GYROSTABILIZER.get())) return;
        final boolean powered = state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
        final boolean stabilizing = accessor.getBlockEntity() instanceof GyrostabilizerBlockEntity g
                && g.isStabilizing();

        final Component line;
        if (stabilizing) {
            line = Component.translatable("tooltip.magnetization.gyro_stabilizing").withStyle(ChatFormatting.GREEN);
        } else if (powered) {
            line = Component.translatable("tooltip.magnetization.gyro_idle").withStyle(ChatFormatting.YELLOW);
        } else {
            line = Component.translatable("tooltip.magnetization.gyro_off").withStyle(ChatFormatting.GRAY);
        }
        tooltip.addLine(line);
    }
}
