package com.stonytark.magnetization.content.fluid;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Directional gas fluids. {@link Source}/{@link Flowing} model gases lighter
 * than air and climb toward ceilings; {@link DenseSource}/{@link DenseFlowing}
 * mirror that behaviour downward for gases heavier than air.
 */
public final class GasFlowingFluid {
    private GasFlowingFluid() {}

    /** Fluid placement and level recomputation normally rebuild the legacy
     * liquid block state, which drops custom block properties. Carry the
     * excitation snapshot forward in the same server update so actively
     * flowing gas never renders one frame in its dormant colour. */
    private static void inheritExcitation(final net.minecraft.world.level.LevelAccessor level,
                                          final BlockPos targetPos, final BlockState sourceState) {
        final BlockState targetState = level.getBlockState(targetPos);
        if (!(targetState.getBlock() instanceof ExcitableGasBlock)
                || targetState.getBlock() != sourceState.getBlock()) return;
        final boolean excited = targetState.getValue(ExcitableGasBlock.EXCITED)
                || sourceState.getValue(ExcitableGasBlock.EXCITED);
        final int grace = Math.max(targetState.getValue(ExcitableGasBlock.EXCITATION_GRACE),
                sourceState.getValue(ExcitableGasBlock.EXCITATION_GRACE));
        if (targetState.getValue(ExcitableGasBlock.EXCITED) != excited
                || targetState.getValue(ExcitableGasBlock.EXCITATION_GRACE) != grace) {
            level.setBlock(targetPos, targetState.setValue(ExcitableGasBlock.EXCITED, excited)
                    .setValue(ExcitableGasBlock.EXCITATION_GRACE, grace),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(final Properties properties) { super(properties); }

        @Override
        protected Map<Direction, FluidState> getSpread(final Level level, final BlockPos pos,
                                                        final BlockState blockState) {
            return Map.of(Direction.UP, getSource().defaultFluidState());
        }

        @Override
        protected void spreadTo(final net.minecraft.world.level.LevelAccessor level, final BlockPos pos,
                                 final BlockState state, final Direction direction, final FluidState target) {
            if (direction == Direction.DOWN) return;
            final BlockState sourceState = level.getBlockState(pos.relative(direction.getOpposite()));
            super.spreadTo(level, pos, state, direction, target);
            inheritExcitation(level, pos, sourceState);
        }

        @Override
        protected boolean canBeReplacedWith(final FluidState state, final BlockGetter level,
                                             final BlockPos pos, final Fluid fluidIn,
                                             final Direction direction) {
            return direction != Direction.DOWN;
        }

        @Override
        protected void spread(final Level level, final BlockPos pos, final FluidState state) {
            if (state.isEmpty()) {
                return;
            }
            if (pos.getY() >= level.getMaxBuildHeight() - 1) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                return;
            }
            final BlockPos above = pos.above();
            final BlockState targetState = level.getBlockState(above);
            if (targetState.getFluidState().isEmpty() && targetState.canBeReplaced()) {
                final BlockState sourceState = level.getBlockState(pos);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                super.spreadTo(level, above, targetState, Direction.UP, getSource().defaultFluidState());
                inheritExcitation(level, above, sourceState);
                return;
            }
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos side = pos.relative(direction);
                final BlockState sideState = level.getBlockState(side);
                final BlockState sideAboveState = level.getBlockState(side.above());
                if (sideState.getFluidState().isEmpty() && (sideState.isAir() || sideState.canBeReplaced())
                        && !sideAboveState.canBeReplaced()) {
                    super.spreadTo(level, side, sideState, direction, getFlowing(7, false));
                    inheritExcitation(level, side, level.getBlockState(pos));
                }
            }
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(final Properties properties) { super(properties); }

        @Override
        protected Map<Direction, FluidState> getSpread(final Level level, final BlockPos pos,
                                                        final BlockState blockState) {
            return Map.of();
        }

        @Override
        protected void spreadTo(final net.minecraft.world.level.LevelAccessor level, final BlockPos pos,
                                 final BlockState state, final Direction direction, final FluidState target) {
            if (direction == Direction.DOWN) return;
            final BlockState sourceState = level.getBlockState(pos.relative(direction.getOpposite()));
            super.spreadTo(level, pos, state, direction, target);
            inheritExcitation(level, pos, sourceState);
        }

        @Override
        protected boolean canBeReplacedWith(final FluidState state, final BlockGetter level,
                                             final BlockPos pos, final Fluid fluidIn,
                                             final Direction direction) {
            return direction != Direction.DOWN;
        }

        @Override
        public void tick(final Level level, final BlockPos pos, final FluidState state) {
            final BlockState previousState = level.getBlockState(pos);
            if (state.getValue(FALLING)) {
                final FluidState feed = level.getFluidState(pos.below());
                if (!feed.getType().isSame(this)) {
                    // Upward waterfalls are fed from below. Once the horizontal
                    // flow or lower column cell disappears, retract the contiguous
                    // falling tail. Waiting one fluid delay per cell leaves a tall
                    // orphan column long after its feed is gone.
                    BlockPos cursor = pos;
                    while (cursor.getY() < level.getMaxBuildHeight()) {
                        final FluidState cursorState = level.getFluidState(cursor);
                        if (cursorState.isEmpty() || !cursorState.getType().isSame(this)
                                || !cursorState.getValue(FALLING)) {
                            break;
                        }
                        level.setBlockAndUpdate(cursor, Blocks.AIR.defaultBlockState());
                        cursor = cursor.above();
                    }
                    return;
                }
                // Vanilla recomputes unsupported flowing cells from horizontal
                // neighbours before spreading them. That is correct for a
                // horizontal layer, but it deletes an upward gas waterfall from
                // the middle. A vertical gas cell keeps its falling strength and
                // advances upward instead.
                spread(level, pos, state);
                return;
            }
            final FluidState above = level.getFluidState(pos.above());
            if (!above.isEmpty() && above.getType().isSame(this)
                    && hasHorizontalFeed(level, pos, state)) {
                // This is the unsupported root at a ceiling edge. Vanilla's
                // downward-gravity recomputation can briefly erase it even while
                // a stronger horizontal cell is feeding it, which makes the
                // entire upward column blink. Preserve it only while that real
                // feed exists; source removal still lets it decay normally.
                return;
            }
            super.tick(level, pos, state);
            inheritExcitation(level, pos, previousState);
        }

        private boolean hasHorizontalFeed(final Level level, final BlockPos pos, final FluidState state) {
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final FluidState neighbour = level.getFluidState(pos.relative(direction));
                if (!neighbour.isEmpty() && neighbour.getType().isSame(this)
                        && (neighbour.isSource() || neighbour.getAmount() > state.getAmount())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void spread(final Level level, final BlockPos pos, final FluidState state) {
            if (state.isEmpty()) {
                return;
            }
            if (pos.getY() >= level.getMaxBuildHeight() - 1) {
                // A source drifting through open air despawns at the sky limit.
                // A source-fed waterfall instead terminates there, mirroring a
                // water waterfall terminating at the bottom of the world.
                return;
            }
            final BlockPos above = pos.above();
            final BlockState aboveState = level.getBlockState(above);
            final FluidState aboveFluid = aboveState.getFluidState();
            if (aboveFluid.isEmpty() && aboveState.canBeReplaced()) {
                // Falling water keeps full vertical strength while descending.
                // Use that state as an upward gas waterfall and leave the
                // current cell in place so the column stays continuous.
                super.spreadTo(level, above, aboveState, Direction.UP, getFlowing(8, true));
                inheritExcitation(level, above, level.getBlockState(pos));
                return;
            }
            if (!aboveFluid.isEmpty() && aboveFluid.getType().isSame(this)) {
                // This cell is already part of a vertical gas column. It must not
                // reinterpret the gas above as a ceiling and spread sideways.
                return;
            }
            final int nextAmount = state.getAmount() - 1;
            if (nextAmount <= 0) {
                return;
            }
            // Horizontal travel is only possible while this cell is held under a
            // ceiling. At the ceiling edge, allow one unsupported side cell to be
            // seeded; its next tick moves upward, while the resulting vertical
            // column cannot fan out through open air.
            if (aboveState.canBeReplaced()) {
                return;
            }
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos side = pos.relative(direction);
                final BlockState sideState = level.getBlockState(side);
                final BlockState sideAboveState = level.getBlockState(side.above());
                if (sideState.getFluidState().isEmpty() && (sideState.isAir() || sideState.canBeReplaced())
                        && sideAboveState.getFluidState().isEmpty()) {
                    final FluidState next = getFlowing(nextAmount, false);
                    level.setBlockAndUpdate(side, next.createLegacyBlock());
                    inheritExcitation(level, side, level.getBlockState(pos));
                    level.scheduleTick(side, next.getType(), next.getType().getTickDelay(level));
                }
            }
        }
    }

    /** Source cell for a gas denser than air. It drifts down until supported,
     * then spreads sideways across the floor. */
    public static final class DenseSource extends BaseFlowingFluid.Source {
        public DenseSource(final Properties properties) { super(properties); }

        @Override
        protected Map<Direction, FluidState> getSpread(final Level level, final BlockPos pos,
                                                        final BlockState blockState) {
            return Map.of(Direction.DOWN, getSource().defaultFluidState());
        }

        @Override
        protected void spreadTo(final net.minecraft.world.level.LevelAccessor level, final BlockPos pos,
                                 final BlockState state, final Direction direction, final FluidState target) {
            if (direction == Direction.UP) return;
            final BlockState sourceState = level.getBlockState(pos.relative(direction.getOpposite()));
            super.spreadTo(level, pos, state, direction, target);
            inheritExcitation(level, pos, sourceState);
        }

        @Override
        protected boolean canBeReplacedWith(final FluidState state, final BlockGetter level,
                                             final BlockPos pos, final Fluid fluidIn,
                                             final Direction direction) {
            return direction != Direction.UP;
        }

        @Override
        protected void spread(final Level level, final BlockPos pos, final FluidState state) {
            if (state.isEmpty()) return;
            if (pos.getY() <= level.getMinBuildHeight()) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                return;
            }
            final BlockPos below = pos.below();
            final BlockState targetState = level.getBlockState(below);
            if (targetState.getFluidState().isEmpty() && targetState.canBeReplaced()) {
                final BlockState sourceState = level.getBlockState(pos);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                super.spreadTo(level, below, targetState, Direction.DOWN, getSource().defaultFluidState());
                inheritExcitation(level, below, sourceState);
                return;
            }
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos side = pos.relative(direction);
                final BlockState sideState = level.getBlockState(side);
                final BlockState sideBelowState = level.getBlockState(side.below());
                if (sideState.getFluidState().isEmpty() && (sideState.isAir() || sideState.canBeReplaced())
                        && !sideBelowState.canBeReplaced()) {
                    super.spreadTo(level, side, sideState, direction, getFlowing(7, false));
                    inheritExcitation(level, side, level.getBlockState(pos));
                }
            }
        }
    }

    /** Flowing cell for a dense gas; vertical tails are fed from above and
     * retract immediately when their feed disappears. */
    public static final class DenseFlowing extends BaseFlowingFluid.Flowing {
        public DenseFlowing(final Properties properties) { super(properties); }

        @Override
        protected Map<Direction, FluidState> getSpread(final Level level, final BlockPos pos,
                                                        final BlockState blockState) {
            return Map.of();
        }

        @Override
        protected void spreadTo(final net.minecraft.world.level.LevelAccessor level, final BlockPos pos,
                                 final BlockState state, final Direction direction, final FluidState target) {
            if (direction == Direction.UP) return;
            final BlockState sourceState = level.getBlockState(pos.relative(direction.getOpposite()));
            super.spreadTo(level, pos, state, direction, target);
            inheritExcitation(level, pos, sourceState);
        }

        @Override
        protected boolean canBeReplacedWith(final FluidState state, final BlockGetter level,
                                             final BlockPos pos, final Fluid fluidIn,
                                             final Direction direction) {
            return direction != Direction.UP;
        }

        @Override
        public void tick(final Level level, final BlockPos pos, final FluidState state) {
            final BlockState previousState = level.getBlockState(pos);
            if (state.getValue(FALLING)) {
                final FluidState feed = level.getFluidState(pos.above());
                if (!feed.getType().isSame(this)) {
                    BlockPos cursor = pos;
                    while (cursor.getY() >= level.getMinBuildHeight()) {
                        final FluidState cursorState = level.getFluidState(cursor);
                        if (cursorState.isEmpty() || !cursorState.getType().isSame(this)
                                || !cursorState.getValue(FALLING)) break;
                        level.setBlockAndUpdate(cursor, Blocks.AIR.defaultBlockState());
                        cursor = cursor.below();
                    }
                    return;
                }
                spread(level, pos, state);
                return;
            }
            final FluidState below = level.getFluidState(pos.below());
            if (!below.isEmpty() && below.getType().isSame(this)
                    && hasHorizontalFeed(level, pos, state)) return;
            super.tick(level, pos, state);
            inheritExcitation(level, pos, previousState);
        }

        private boolean hasHorizontalFeed(final Level level, final BlockPos pos, final FluidState state) {
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final FluidState neighbour = level.getFluidState(pos.relative(direction));
                if (!neighbour.isEmpty() && neighbour.getType().isSame(this)
                        && (neighbour.isSource() || neighbour.getAmount() > state.getAmount())) return true;
            }
            return false;
        }

        @Override
        protected void spread(final Level level, final BlockPos pos, final FluidState state) {
            if (state.isEmpty() || pos.getY() <= level.getMinBuildHeight()) return;
            final BlockPos below = pos.below();
            final BlockState belowState = level.getBlockState(below);
            final FluidState belowFluid = belowState.getFluidState();
            if (belowFluid.isEmpty() && belowState.canBeReplaced()) {
                super.spreadTo(level, below, belowState, Direction.DOWN, getFlowing(8, true));
                inheritExcitation(level, below, level.getBlockState(pos));
                return;
            }
            if (!belowFluid.isEmpty() && belowFluid.getType().isSame(this)) return;
            final int nextAmount = state.getAmount() - 1;
            if (nextAmount <= 0 || belowState.canBeReplaced()) return;
            for (final Direction direction : Direction.Plane.HORIZONTAL) {
                final BlockPos side = pos.relative(direction);
                final BlockState sideState = level.getBlockState(side);
                final BlockState sideBelowState = level.getBlockState(side.below());
                if (sideState.getFluidState().isEmpty() && (sideState.isAir() || sideState.canBeReplaced())
                        && sideBelowState.getFluidState().isEmpty()) {
                    final FluidState next = getFlowing(nextAmount, false);
                    level.setBlockAndUpdate(side, next.createLegacyBlock());
                    inheritExcitation(level, side, level.getBlockState(pos));
                    level.scheduleTick(side, next.getType(), next.getType().getTickDelay(level));
                }
            }
        }
    }
}
