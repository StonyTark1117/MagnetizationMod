package com.stonytark.magnetization.content.dipole;

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
 * Dipole Electromagnet block — a directional, wrench-rotatable electromagnet with a
 * NORTH pole on the {@code +FACING} end and a SOUTH pole on the {@code -FACING} end.
 * Structurally a {@link DirectionalBlock} + {@link IWrenchable} (like the Repulsor
 * Coil) wired to the omnidirectional/powered/GUI behaviour of the Electromagnet.
 */
public final class DipoleElectromagnetBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<DipoleElectromagnetBlock> CODEC = simpleCodec(DipoleElectromagnetBlock::new);

    public DipoleElectromagnetBlock(final Properties props) {
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
    protected BlockState rotate(final BlockState state, final net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final net.minecraft.world.level.block.Mirror mir) {
        return state.rotate(mir.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        // Facing (the +/NORTH pole end) points OUT from the surface placed against.
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.POWERED, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new DipoleElectromagnetBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.DIPOLE_ELECTROMAGNET.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<DipoleElectromagnetBlockEntity>)
                DipoleElectromagnetBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // Strength + range only — no polarity control (the two poles are inherent).
        final int caps = EmitterMenu.CAP_STRENGTH | EmitterMenu.CAP_RANGE;
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos, caps,
                Component.translatable("block.magnetization.dipole_electromagnet")).openFor(sp);
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
        // Analog read; see ElectromagnetBlock.applyExternalSignal for the rationale.
        final int signal = level.getBestNeighborSignal(pos);
        final boolean nowPowered = signal > 0;
        if (state.getValue(BlockStateProperties.POWERED) != nowPowered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, nowPowered), Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(pos) instanceof DipoleElectromagnetBlockEntity dipole) {
            dipole.setRedstoneLevel(signal);
        }
    }
}
