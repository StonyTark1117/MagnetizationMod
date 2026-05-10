package com.stonytark.magnetization.content.repulsor;

import com.stonytark.magnetization.registry.MagBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Conical repulsive field aligned with the coil's facing direction. Place
 * upward for a hover pad; place sideways or downward to line a tunnel and
 * shove ships through. Active while powered.
 */
public class RepulsorCoilBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<RepulsorCoilBlock> CODEC = simpleCodec(RepulsorCoilBlock::new);

    public RepulsorCoilBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.UP)
                .setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        // Place with cone pointing OPPOSITE the player's looking direction —
        // matches "place against this surface, push outward from it" expectation.
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.POWERED, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new RepulsorCoilBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.REPULSOR_COIL.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<RepulsorCoilBlockEntity>)
                RepulsorCoilBlockEntity::serverTick;
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
        if (be instanceof RepulsorCoilBlockEntity repulsor) {
            repulsor.setPowered(nowPowered);
        }
    }
}
