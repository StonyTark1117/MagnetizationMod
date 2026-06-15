package com.stonytark.magnetization.content.inducer;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.menu.EmitterMenuProvider;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Structural Inducer block — plumbing for {@link StructuralInducerBlockEntity}.
 * Grabs the structure in front of its active face and launches it along that
 * face (defaults to up). Wrench rotates the face; right-click opens a range
 * dial that sets the scan depth.
 */
public final class StructuralInducerBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<StructuralInducerBlock> CODEC = simpleCodec(StructuralInducerBlock::new);

    public StructuralInducerBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.UP)
                .setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.POWERED, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new StructuralInducerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.STRUCTURAL_INDUCER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<StructuralInducerBlockEntity>)
                StructuralInducerBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // Range dial doubles as the scan-depth control; redstone-fuel slot powers
        // it without an external signal (mirrors the excavator).
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos,
                EmitterMenu.CAP_RANGE | EmitterMenu.CAP_REDSTONE_FUEL,
                Component.translatable("block.magnetization.structural_inducer")).openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof StructuralInducerBlockEntity be) {
            be.dropRedstoneFuelSlot(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        if (!level.isClientSide) applyExternalSignal(level, pos);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) applyExternalSignal(level, pos);
    }

    private static void applyExternalSignal(final Level level, final BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StructuralInducerBlockEntity be) {
            be.setExternalSignal(level.hasNeighborSignal(pos));
        }
    }
}
