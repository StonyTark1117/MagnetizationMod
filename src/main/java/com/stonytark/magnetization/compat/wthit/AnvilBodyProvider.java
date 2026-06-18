package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.AnvilDampenerHandler;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WTHIT body line for magnetic anvils. Shows the anvil's per-metal break chance
 * and, when a dampener magnet ({@code #magnetization:anvil_dampeners}) is
 * adjacent, that the chance is forced to zero (never degrades). Reads the same
 * config-driven values and adjacency scan {@link AnvilDampenerHandler} uses at
 * repair time, so the readout matches the live mechanic.
 */
public enum AnvilBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockState state = accessor.getBlockState();
        final Float baseChance = AnvilDampenerHandler.breakChanceFor(state);
        if (baseChance == null) return; // not one of our magnetic anvils
        final boolean dampened = AnvilDampenerHandler.hasAdjacentDampener(accessor.getWorld(), accessor.getPosition());
        if (dampened) {
            tooltip.addLine(Component.translatable("tooltip.magnetization.anvil.dampened",
                    String.format("%.0f", baseChance * 100f)).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.addLine(Component.translatable("tooltip.magnetization.anvil.break_chance",
                    String.format("%.0f", baseChance * 100f)).withStyle(ChatFormatting.GRAY));
        }
    }
}
