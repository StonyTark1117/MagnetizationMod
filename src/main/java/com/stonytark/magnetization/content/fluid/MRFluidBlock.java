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
 * Magnetorheological (MR) fluid block — an iron-particle smart fluid. It flows
 * like a liquid until it sits inside a magnetic field, where it snaps rigid into
 * {@link com.stonytark.magnetization.registry.MagBlocks#HARDENED_MR_FLUID}
 * (walkable — temporary bridges/walkways) and melts back when the field is gone.
 * The field reaction is driven by {@link MrFluidHardenHandler}; this block just
 * registers its source positions so the handler can find the fluid cheaply.
 *
 * <p>It conducts redstone like liquid dust (see {@link FluidRedstone}); the
 * conduction survives hardening, since {@link HardenedMrFluidBlock} conducts too.
 *
 * <p>(It no longer stiffens on a redstone signal — hardening is field-only.)
 */
public final class MRFluidBlock extends LiquidBlock implements FluidRedstone.Conductor {

    public MRFluidBlock(final FlowingFluid fluid, final Properties props) {
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
            MrFluidSourceRegistry.add(level, pos);
        }
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MrFluidSourceRegistry.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            level.updateNeighborsAt(pos, this);
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
