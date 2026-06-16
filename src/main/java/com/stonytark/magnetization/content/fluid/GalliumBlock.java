package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

/**
 * Liquid gallium — a soft, silvery liquid metal. It conducts redstone like the
 * mod's other fluids (see {@link FluidRedstone}), and when a powered gallium body
 * sits inside a magnetic field the Lorentz force drives a water-current that
 * pushes entities through it (see {@link GalliumLorentzHandler}). Because gallium
 * melts barely above room temperature, a cooling source placed next to it freezes
 * it into {@link com.stonytark.magnetization.registry.MagBlocks#SOLID_GALLIUM}.
 */
public final class GalliumBlock extends LiquidBlock implements FluidRedstone.Conductor {

    public GalliumBlock(final FlowingFluid fluid, final Properties props) {
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
            GalliumRegistry.add(level, pos);
            scheduleFreezeCheck(level, pos);
        }
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            GalliumRegistry.remove(level, pos);
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
        if (!level.isClientSide && state.getFluidState().isSource()) scheduleFreezeCheck(level, pos);
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    private void scheduleFreezeCheck(final Level level, final BlockPos pos) {
        if (Gallium.coolingAdjacent(level, pos) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, com.stonytark.magnetization.config.MagConfig.galliumFreezeDelayTicks());
        }
    }

    /** Freeze a source cell to solid gallium once a cooling source has stayed adjacent. */
    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        super.tick(state, level, pos, random);
        if (state.getFluidState().isSource() && Gallium.coolingAdjacent(level, pos)) {
            level.setBlock(pos, MagBlocks.SOLID_GALLIUM.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
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
