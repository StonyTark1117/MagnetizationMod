package com.stonytark.magnetization.content.gyro;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
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
 * Magnetic Gyrostabilizer block. Redstone-powered; on a ship it freezes rotation.
 * See {@link GyrostabilizerBlockEntity}.
 */
public final class GyrostabilizerBlock extends Block implements EntityBlock {

    public GyrostabilizerBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new GyrostabilizerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.GYROSTABILIZER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GyrostabilizerBlockEntity>)
                GyrostabilizerBlockEntity::serverTick;
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            updatePowered(state, level, pos);
        }
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        if (!level.isClientSide) updatePowered(state, level, pos);
    }

    private static void updatePowered(final BlockState state, final Level level, final BlockPos pos) {
        final boolean now = level.hasNeighborSignal(pos);
        if (state.getValue(BlockStateProperties.POWERED) != now) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, now), Block.UPDATE_CLIENTS);
        }
    }
}
