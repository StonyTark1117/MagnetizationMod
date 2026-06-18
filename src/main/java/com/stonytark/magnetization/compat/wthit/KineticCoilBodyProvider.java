package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.induction.KineticCoilBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body line for the Kinetic Induction Coil. Shows its FE buffer and whether
 * it's currently generating (a magnetic ship is passing — signal &gt; 0). Both
 * values are synced off the BE so the readout is live.
 */
public enum KineticCoilBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof KineticCoilBlockEntity coil)) return;
        tooltip.addLine(Component.translatable("tooltip.magnetization.kinetic_coil.fe",
                coil.getEnergyStored(), coil.getEnergyCapacity()).withStyle(ChatFormatting.AQUA));
        if (coil.getSignal() > 0) {
            tooltip.addLine(Component.translatable("tooltip.magnetization.kinetic_coil.generating")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
