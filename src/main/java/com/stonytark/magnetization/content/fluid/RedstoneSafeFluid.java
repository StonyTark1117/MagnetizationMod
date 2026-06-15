package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Flowing-fluid variants for the mod's conductive fluids (ferrofluid, magnetized
 * ferrofluid, MR fluid) that refuse to wash away redstone wiring. Since these
 * fluids conduct a signal like liquid dust (see {@link FluidRedstone}), flowing
 * them across a circuit must not destroy the torches, dust, or repeaters that
 * make up the circuit — so {@code spreadTo} simply skips any protected component
 * instead of replacing it with fluid. Plain and magnetized ferrofluid share this
 * class, so their redstone integration is identical.
 */
public final class RedstoneSafeFluid {

    private RedstoneSafeFluid() {}

    /** Redstone components a conductive fluid must flow around, never destroy. */
    public static boolean isProtected(final BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE)
                || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH)
                || state.is(Blocks.REPEATER)
                || state.is(Blocks.COMPARATOR)
                || state.is(Blocks.LEVER);
    }

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(final Properties props) { super(props); }

        @Override
        protected void spreadTo(final LevelAccessor level, final BlockPos pos, final BlockState state,
                                final Direction direction, final FluidState target) {
            if (isProtected(state)) return;
            super.spreadTo(level, pos, state, direction, target);
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(final Properties props) { super(props); }

        @Override
        protected void spreadTo(final LevelAccessor level, final BlockPos pos, final BlockState state,
                                final Direction direction, final FluidState target) {
            if (isProtected(state)) return;
            super.spreadTo(level, pos, state, direction, target);
        }
    }
}
