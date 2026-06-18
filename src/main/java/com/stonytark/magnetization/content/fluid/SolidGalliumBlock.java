package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Solid gallium — the frozen form of liquid {@link GalliumBlock}. Gallium melts
 * just above room temperature, so this block only holds its shape while a cooling
 * source (ice/snow/powder snow) sits next to it; remove the cooling and it melts
 * back into a gallium fluid source after a configurable melt delay. It also
 * doubles as the gallium storage block / crafting material for gallium gear.
 *
 * <p>Like liquid gallium it conducts redstone (see {@link FluidRedstone}), so a
 * frozen gallium bridge still carries a signal.
 */
public final class SolidGalliumBlock extends Block implements FluidRedstone.Conductor {

    public SolidGalliumBlock(final Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(FluidRedstone.POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FluidRedstone.POWER);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        scheduleMeltCheck(level, pos);
        FluidRedstone.onNeighborChanged(level, pos, this);
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        scheduleMeltCheck(level, pos);
        FluidRedstone.onNeighborChanged(level, pos, this);
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

    private void scheduleMeltCheck(final Level level, final BlockPos pos) {
        if (!level.isClientSide && !Gallium.coolingAdjacent(level, pos)
                && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, com.stonytark.magnetization.config.MagConfig.galliumMeltDelayTicks());
        }
    }

    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        if (Gallium.coolingAdjacent(level, pos)) return; // still cooled — stay solid
        level.setBlock(pos, MagBlocks.GALLIUM_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}
