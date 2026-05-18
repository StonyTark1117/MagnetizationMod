package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * WTHIT body lines for the meteorite sapling — exposes growth progress so
 * players can tell how much longer until it matures into a full
 * {@code meteorite_core}. {@link MeteoriteSaplingBlockEntity} is not a
 * {@link com.stonytark.magnetization.api.MagneticFieldSource} (it doesn't
 * emit a field while growing), so {@link EmitterBodyProvider} skips it and
 * we add this dedicated provider instead.
 */
public enum SaplingBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockEntity be = accessor.getBlockEntity();
        if (!(be instanceof MeteoriteSaplingBlockEntity sapling)) return;
        final long now = accessor.getWorld().getGameTime();
        final float pct = sapling.growthProgress(now) * 100f;
        tooltip.addLine(Component.translatable("magnetization.tooltip.sapling_growth",
                String.format(java.util.Locale.ROOT, "%.0f", pct))
                .withStyle(ChatFormatting.GREEN));
    }
}
