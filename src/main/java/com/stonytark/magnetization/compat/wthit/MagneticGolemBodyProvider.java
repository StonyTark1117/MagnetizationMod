package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.golem.MagneticGolem;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;

/** Live polarity, material state, field and ownership lines for magnetic golems. */
public enum MagneticGolemBodyProvider implements IEntityComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IEntityAccessor accessor, final IPluginConfig config) {
        if (!(accessor.getEntity() instanceof MagneticGolem golem)) return;
        for (final var line : com.stonytark.magnetization.content.golem.MagneticGolemHud.lines(golem)) {
            tooltip.addLine(line);
        }
    }
}
