package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.api.FieldTooltipFormatter;
import com.stonytark.magnetization.api.MagneticFieldSource;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body lines for any block whose BE implements {@link MagneticFieldSource}.
 * Mirrors the goggles / Jade output by reusing {@link FieldTooltipFormatter}.
 */
public enum EmitterBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MagneticFieldSource source)) return;
        for (Component line : FieldTooltipFormatter.format(source.currentField(), true)) {
            tooltip.addLine(line);
        }
    }
}
