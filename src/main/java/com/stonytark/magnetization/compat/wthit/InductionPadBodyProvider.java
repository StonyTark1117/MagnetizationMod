package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.induction.InductionPadBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body line for the Induction Pad. The stored-FE bar is drawn by WTHIT's
 * built-in energy renderer (the pad exposes {@code EnergyStorage.BLOCK}); this
 * just adds a static "what it does" line — wireless charge range + FE/tick — so
 * the block reads as a charger rather than an inert plate. Values come from the
 * live config so they track admin tuning.
 */
public enum InductionPadBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof InductionPadBlockEntity)) return;
        tooltip.addLine(Component.translatable("tooltip.magnetization.induction_pad.status",
                String.format("%.0f", MagConfig.inductionPadRange()),
                MagConfig.inductionPadChargePerTick()).withStyle(ChatFormatting.AQUA));
    }
}
