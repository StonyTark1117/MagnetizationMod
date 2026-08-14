package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.golem.MrFluidGolem;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;

/** Live hardening and mitigation line for MR Fluid Golems. */
public enum MrFluidGolemBodyProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IEntityAccessor accessor, final IPluginConfig config) {
        if (!(accessor.getEntity() instanceof MrFluidGolem golem)) return;
        for (final var line : com.stonytark.magnetization.content.golem.MrFluidGolemHud.lines(golem)) {
            tooltip.addLine(line);
        }
    }
}
