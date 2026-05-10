package com.stonytark.magnetization.content.electromagnet;

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
 * Omnidirectional magnetic field source. Active while redstone-powered.
 * Polarity inverts when powered with a comparator-style "weak" signal (encoded by
 * a strong adjacent signal vs. a redstone-block-sourced signal isn't reliable, so
 * we just use full power = NORTH and the sub-block toggle via right-click later if needed).
 */
public class ElectromagnetBlock extends Block implements EntityBlock {

    public ElectromagnetBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ElectromagnetBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.ELECTROMAGNET.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ElectromagnetBlockEntity>)
                ElectromagnetBlockEntity::serverTick;
    }

    @Override
    public void neighborChanged(
            final BlockState state, final Level level, final BlockPos pos,
            final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston
    ) {
        if (level.isClientSide) return;
        final boolean nowPowered = level.hasNeighborSignal(pos);
        if (state.getValue(BlockStateProperties.POWERED) != nowPowered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, nowPowered), Block.UPDATE_CLIENTS);
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ElectromagnetBlockEntity emag) {
            emag.setPowered(nowPowered);
        }
    }
}
