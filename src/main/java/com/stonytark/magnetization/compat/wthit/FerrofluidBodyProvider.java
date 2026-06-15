package com.stonytark.magnetization.compat.wthit;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidBlock;
import com.stonytark.magnetization.registry.MagBlocks;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WTHIT line for magnetized ferrofluid: surfaces the pole it's magnetized to,
 * read straight from the {@code POLARITY} blockstate (no BE — the block carries
 * the pole itself). Plain ferrofluid has no such block, so nothing shows.
 */
public enum FerrofluidBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockState state = accessor.getBlockState();
        if (!state.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())
                || !state.hasProperty(MagnetizedFerrofluidBlock.POLARITY)) {
            return;
        }
        final MagneticPolarity pole = state.getValue(MagnetizedFerrofluidBlock.POLARITY);
        final ChatFormatting colour = pole == MagneticPolarity.NORTH ? ChatFormatting.RED : ChatFormatting.AQUA;
        tooltip.addLine(Component.translatable("tooltip.magnetization.ferrofluid_magnetized",
                        Component.translatable("tooltip.magnetization.polarity." + pole.name().toLowerCase())
                                .withStyle(colour))
                .withStyle(ChatFormatting.GRAY));
    }
}
