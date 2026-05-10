package com.stonytark.magnetization.content.anchor;

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
 * Strong omnidirectional attractive field that pulls ships toward this exact
 * spot. Intended for docking pads. Active while powered.
 */
public class MagneticAnchorBlock extends Block implements EntityBlock {

    public MagneticAnchorBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MagneticAnchorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.MAGNETIC_ANCHOR.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MagneticAnchorBlockEntity>)
                MagneticAnchorBlockEntity::serverTick;
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
        if (be instanceof MagneticAnchorBlockEntity anchor) {
            anchor.setPowered(nowPowered);
        }
    }
}
