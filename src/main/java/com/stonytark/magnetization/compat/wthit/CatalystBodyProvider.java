package com.stonytark.magnetization.compat.wthit;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WTHIT body lines for a {@link PyrrhotiteCatalystBlock}. Catalysts have no
 * block entity (intentional — they're a pure-blockstate detector for nearby
 * pyrrhotite reactors to walk), so the {@link EmitterBodyProvider}'s
 * BE-based filter skips them. This provider computes the hover info on the
 * fly: transmit radius (set at registration) and the current heat reading
 * the Catalyst is forwarding to nearby pyrrhotite.
 *
 * <p>Heat detection mirrors {@link com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity#heatOf}
 * — Create's blaze burner, lava, fire, soul_fire, magma block, lit campfires.
 * Without WTHIT this lookup runs only when the player hovers the block, so
 * the per-tick cost is zero.
 */
public enum CatalystBodyProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(final ITooltip tooltip, final IBlockAccessor accessor, final IPluginConfig config) {
        final BlockState state = accessor.getBlockState();
        if (!(state.getBlock() instanceof PyrrhotiteCatalystBlock catalyst)) return;

        tooltip.addLine(Component.translatable("tooltip.magnetization.catalyst.radius",
                        catalyst.transmitRadius())
                .withStyle(ChatFormatting.GRAY));

        final BlazeBurnerBlock.HeatLevel heat = scanHeat(accessor.getWorld(), accessor.getPosition());
        final ChatFormatting colour = switch (heat) {
            case NONE -> ChatFormatting.DARK_GRAY;
            case SMOULDERING, FADING -> ChatFormatting.GRAY;
            case KINDLED -> ChatFormatting.GOLD;
            case SEETHING -> ChatFormatting.RED;
        };
        tooltip.addLine(Component.translatable("tooltip.magnetization.catalyst.heat", heat.name())
                .withStyle(colour));
    }

    private static BlazeBurnerBlock.HeatLevel scanHeat(final Level level, final BlockPos pos) {
        BlazeBurnerBlock.HeatLevel max = BlazeBurnerBlock.HeatLevel.NONE;
        for (final Direction dir : Direction.values()) {
            final BlazeBurnerBlock.HeatLevel observed = heatOf(level.getBlockState(pos.relative(dir)));
            if (observed.ordinal() > max.ordinal()) max = observed;
        }
        return max;
    }

    /** Mirrors PyrrhotiteBlockEntity.heatOf — kept in sync by hand because
     *  WTHIT integration lives in a separate package gated on the soft dep. */
    private static BlazeBurnerBlock.HeatLevel heatOf(final BlockState state) {
        if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL)) {
            return state.getValue(BlazeBurnerBlock.HEAT_LEVEL);
        }
        final var block = state.getBlock();
        if (block == Blocks.LAVA) return BlazeBurnerBlock.HeatLevel.SEETHING;
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE || block == Blocks.MAGMA_BLOCK) {
            return BlazeBurnerBlock.HeatLevel.KINDLED;
        }
        if ((block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE)
                && state.hasProperty(CampfireBlock.LIT)
                && state.getValue(CampfireBlock.LIT)) {
            return BlazeBurnerBlock.HeatLevel.SMOULDERING;
        }
        return BlazeBurnerBlock.HeatLevel.NONE;
    }
}
