package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidBlock;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

/**
 * WTHIT line for magnetized ferrofluid: surfaces the pole it's magnetized to,
 * read straight from the {@code POLARITY} blockstate (no BE — the block carries
 * the pole itself). Plain ferrofluid has no such block, so nothing shows.
 */
public enum FerrofluidBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final Component line = MagnetizedFerrofluidBlock.polarityTooltip(accessor.getBlockState());
        if (line != null) tooltip.addLine(line);
    }
}
