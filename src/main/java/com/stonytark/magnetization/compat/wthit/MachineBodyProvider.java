package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.menu.MachineGuiData;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body lines for the shared machines (motor / MHD jet / tokamak /
 * micro-thruster) — shows the hovered block's OWN live status (energy, fuel
 * runtime + FE output, ferrofluid, RPM, active) from {@link MachineGuiData}.
 * The BE syncs its state to the client every ~0.5s so these read correctly.
 */
public enum MachineBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MachineGuiData data)) return;
        for (final Component line : data.hudLines()) {
            tooltip.addLine(line);
        }
    }
}
