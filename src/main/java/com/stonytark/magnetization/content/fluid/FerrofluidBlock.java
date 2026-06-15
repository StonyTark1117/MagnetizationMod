package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Plain ferrofluid. Behaves like a vanilla liquid block, but registers its
 * <em>source</em> positions in {@link FerrofluidSourceRegistry} so
 * {@link FerrofluidCreepHandler} can iterate the (small) set of fluid sources
 * and test each against active fields — instead of cube-scanning a magnet's
 * whole field volume. Plain ferrofluid still emits no field of its own.
 */
public final class FerrofluidBlock extends LiquidBlock {

    public FerrofluidBlock(final FlowingFluid fluid, final Properties props) {
        super(fluid, props);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getFluidState().isSource()) {
            FerrofluidSourceRegistry.add(level, pos);
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            FerrofluidSourceRegistry.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
