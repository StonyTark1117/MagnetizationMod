package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

/**
 * Plain ferrofluid. Behaves like a vanilla liquid block, but registers its
 * <em>source</em> positions in {@link FerrofluidSourceRegistry} so
 * {@link FerrofluidCreepHandler} can iterate the (small) set of fluid sources
 * and test each against active fields — instead of cube-scanning a magnet's
 * whole field volume. Plain ferrofluid still emits no field of its own.
 *
 * <p>It also conducts redstone like liquid dust (see {@link FluidRedstone}).
 */
public final class FerrofluidBlock extends LiquidBlock implements FluidRedstone.Conductor {

    public FerrofluidBlock(final FlowingFluid fluid, final Properties props) {
        super(fluid, props);
        registerDefaultState(defaultBlockState().setValue(FluidRedstone.POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FluidRedstone.POWER);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getFluidState().isSource()) {
            FerrofluidSourceRegistry.add(level, pos);
        }
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            FerrofluidSourceRegistry.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            level.updateNeighborsAt(pos, this); // let the network drop our contribution
        }
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos,
                            final net.minecraft.util.RandomSource random) {
        super.animateTick(state, level, pos, random);
        FluidRedstone.spawnSignalParticles(state, level, pos, random);
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction direction) {
        return FluidRedstone.signal(state);
    }

    @Override
    public boolean canConnectRedstone(final BlockState state, final BlockGetter level,
                                      final BlockPos pos, final @Nullable Direction direction) {
        return true;
    }
}
