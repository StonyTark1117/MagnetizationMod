package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;

/** Gas target and live redstone-aware operating state for WTHIT. */
public enum GasExciterBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof GasExciterBlockEntity exciter)) return;
        exciter.hudLines().forEach(tooltip::addLine);
    }
}
