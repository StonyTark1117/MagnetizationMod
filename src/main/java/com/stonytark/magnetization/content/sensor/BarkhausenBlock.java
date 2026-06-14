package com.stonytark.magnetization.content.sensor;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Barkhausen noise generator — a true-RNG block. While a magnet (a block in
 * {@code #magnetization:anvil_dampeners}) sits against it, its iron domains flip
 * chaotically and it spits a rapid, unpredictable stream of random redstone
 * signal strengths. Idle (no adjacent magnet) it outputs 0. Perfect for random
 * lighting, casino logic, or unpredictable spawners.
 */
public final class BarkhausenBlock extends Block implements EntityBlock {

    public BarkhausenBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new BarkhausenBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.BARKHAUSEN.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<BarkhausenBlockEntity>)
                BarkhausenBlockEntity::serverTick;
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction dir) {
        return level.getBlockEntity(pos) instanceof BarkhausenBlockEntity be ? be.getSignal() : 0;
    }

    @Override
    protected int getDirectSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}
