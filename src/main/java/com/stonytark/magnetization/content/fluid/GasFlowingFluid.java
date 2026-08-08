package com.stonytark.magnetization.content.fluid;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** BaseFlowingFluid variant whose gas rises instead of falling. */
public final class GasFlowingFluid {
    private GasFlowingFluid() {}

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(final Properties properties) { super(properties); }

        @Override
        protected Map<Direction, FluidState> getSpread(final Level level, final BlockPos pos,
                                                        final BlockState blockState) {
            final Map<Direction, FluidState> spread = super.getSpread(level, pos, blockState);
            spread.remove(Direction.DOWN);
            spread.put(Direction.UP, getFlowing(7, false));
            return spread;
        }

        @Override
        protected void spreadTo(final net.minecraft.world.level.LevelAccessor level, final BlockPos pos,
                                 final BlockState state, final Direction direction, final FluidState target) {
            if (direction == Direction.DOWN) return;
            super.spreadTo(level, pos, state, direction, target);
        }

        @Override
        protected void spread(final Level level, final BlockPos pos, final FluidState state) {
            if (pos.getY() >= level.getMaxBuildHeight() - 1) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                return;
            }
            final BlockPos above = pos.above();
            final BlockState targetState = level.getBlockState(above);
            if (targetState.getFluidState().isEmpty() && targetState.canBeReplaced()) {
                level.setBlock(above, getFlowing(7, false).createLegacyBlock(), 3);
                return;
            }
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos side = pos.relative(direction);
                final BlockState sideState = level.getBlockState(side);
                if (sideState.getFluidState().isEmpty() && sideState.canBeReplaced()) {
                    super.spreadTo(level, side, sideState, direction, getFlowing(7, false));
                }
            }
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(final Properties properties) { super(properties); }

        @Override
        protected Map<Direction, FluidState> getSpread(final Level level, final BlockPos pos,
                                                        final BlockState blockState) {
            final Map<Direction, FluidState> spread = super.getSpread(level, pos, blockState);
            spread.remove(Direction.DOWN);
            spread.put(Direction.UP, getFlowing(7, false));
            return spread;
        }

        @Override
        protected void spreadTo(final net.minecraft.world.level.LevelAccessor level, final BlockPos pos,
                                 final BlockState state, final Direction direction, final FluidState target) {
            if (direction == Direction.DOWN) return;
            super.spreadTo(level, pos, state, direction, target);
        }

        @Override
        protected void spread(final Level level, final BlockPos pos, final FluidState state) {
            if (pos.getY() >= level.getMaxBuildHeight() - 1) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                return;
            }
            final BlockPos above = pos.above();
            final BlockState targetState = level.getBlockState(above);
            if (targetState.getFluidState().isEmpty() && targetState.canBeReplaced()) {
                level.setBlock(above, getFlowing(7, false).createLegacyBlock(), 3);
                return;
            }
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos side = pos.relative(direction);
                final BlockState sideState = level.getBlockState(side);
                if (sideState.getFluidState().isEmpty() && sideState.canBeReplaced()) {
                    super.spreadTo(level, side, sideState, direction, getFlowing(7, false));
                }
            }
        }
    }
}
