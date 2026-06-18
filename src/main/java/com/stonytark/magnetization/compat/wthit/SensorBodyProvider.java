package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body line for the Magnetostrictive Sensor. Shows the live analog
 * redstone output (0–15) and the detection range so the block reads as a
 * motion-driven redstone source. The signal value comes straight off the BE,
 * so it updates as entities move past.
 */
public enum SensorBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MagnetostrictiveSensorBlockEntity sensor)) return;
        tooltip.addLine(Component.translatable("tooltip.magnetization.sensor.status",
                sensor.getSignal(),
                String.format("%.0f", sensor.effectiveRange())).withStyle(ChatFormatting.AQUA));
    }
}
