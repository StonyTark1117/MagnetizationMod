package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.menu.MachineGuiData;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.component.BarComponent;
import mcp.mobius.waila.api.component.PairComponent;
import mcp.mobius.waila.api.component.WrappedComponent;
import mcp.mobius.waila.api.data.EnergyData;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Renders the stored-FE bar for our energy blocks (powered emitters + the
 * tokamak / MHD jet / micro-thruster) from each block's OWN client-synced
 * buffer, so the value is always the hovered block's.
 *
 * <p>Registered at a priority above WTHIT's built-in energy renderer (500) so
 * our line wins the shared {@link EnergyData#ID} slot. WTHIT's built-in reads
 * the capability through a single shared {@code BlockCapabilityCache} that can
 * surface a neighbouring block's value; rendering it ourselves sidesteps that
 * and also lets us clear the bar from our non-FE blocks (e.g. magnets) so it
 * can't linger there.
 */
public enum MachineEnergyBarProvider implements IBlockComponentProvider {
    INSTANCE;

    /** Run after WTHIT's own EnergyProvider (priority 500) so our setLine wins. */
    public static final int PRIORITY = 600;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        final long[] fe = ownEnergy(be);
        if (fe != null) {
            final float ratio = fe[1] <= 0 ? 0f : (float) fe[0] / fe[1];
            final Component text = Component.literal(String.format("%,d / %,d FE", fe[0], fe[1]));
            tooltip.setLine(EnergyData.ID, new PairComponent(
                    new WrappedComponent(EnergyData.DEFAULT_NAME),
                    new BarComponent(ratio, 0xFF000000 | EnergyData.DEFAULT_COLOR, text)));
        } else if (tooltip.getLine(EnergyData.ID) != null && !hasEnergy(accessor)) {
            // WTHIT's built-in energy provider reads through a shared
            // BlockCapabilityCache that can surface a previously-viewed block's
            // value on an unrelated one (e.g. our magnets, or even a vanilla
            // chest). If this block genuinely has no energy capability, that
            // line is bogus — clear it.
            tooltip.setLine(EnergyData.ID);
        }
    }

    /** True only if the hovered block really exposes an energy capability. */
    private static boolean hasEnergy(final IBlockAccessor accessor) {
        return accessor.getLevel().getCapability(
                Capabilities.EnergyStorage.BLOCK, accessor.getPosition(), (Direction) null) != null;
    }

    /** {stored, max} for our FE-storing blocks, or null if it has no FE buffer. */
    private static long[] ownEnergy(final BlockEntity be) {
        if (be instanceof AbstractEmitterBlockEntity emitter && emitter.acceptsPowerInput()) {
            final var buffer = emitter.getEnergyBuffer();
            return new long[]{buffer.getEnergyStored(), buffer.getMaxEnergyStored()};
        }
        if (be instanceof MachineGuiData machine && machine.guiEnergyStored() >= 0) {
            return new long[]{machine.guiEnergyStored(), machine.guiEnergyMax()};
        }
        return null;
    }
}
