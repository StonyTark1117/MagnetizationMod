package com.stonytark.magnetization.content.electromagnet;

import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.menu.EmitterMenuProvider;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    protected InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        final int caps = EmitterMenu.CAP_ARMOR | EmitterMenu.CAP_POLARITY
                       | EmitterMenu.CAP_STRENGTH | EmitterMenu.CAP_RANGE;
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos, caps,
                Component.translatable("block.magnetization.electromagnet")).openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(
            final BlockState state, final Level level, final BlockPos pos,
            final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston
    ) {
        if (level.isClientSide) return;
        applyExternalSignal(state, level, pos);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.is(oldState.getBlock())) return;
        applyExternalSignal(state, level, pos);
    }

    private static void applyExternalSignal(final BlockState state, final Level level, final BlockPos pos) {
        final boolean nowPowered = level.hasNeighborSignal(pos);
        if (state.getValue(BlockStateProperties.POWERED) != nowPowered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, nowPowered), Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(pos) instanceof ElectromagnetBlockEntity emag) {
            emag.setPowered(nowPowered);
        }
    }
}
