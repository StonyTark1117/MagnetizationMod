package com.stonytark.magnetization.content.railgun;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
 * Railgun emitter block — the powered control block at a rail's breech. {@code FACING}
 * = breech→muzzle firing direction; {@code POWERED} drives the lit visual + the
 * redstone-power flag. Right-click empty-hand opens the shared GUI (remote slot +
 * FE/length/state readouts). See {@link RailgunEmitterBlockEntity}.
 */
public final class RailgunEmitterBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<RailgunEmitterBlock> CODEC = simpleCodec(RailgunEmitterBlock::new);

    public RailgunEmitterBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
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
        return new RailgunEmitterBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.RAILGUN_EMITTER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<RailgunEmitterBlockEntity>) RailgunEmitterBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be)) {
            return InteractionResult.PASS;
        }
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(
                        id, inv, ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.RAILGUN, be.remoteContainer()),
                Component.translatable("block.magnetization.railgun_emitter")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.RAILGUN));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighbor, final BlockPos neighborPos, final boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level.isClientSide) return;
        final boolean signal = level.hasNeighborSignal(pos);
        if (level.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be) {
            be.setRedstonePowered(signal);
        }
        if (state.getValue(BlockStateProperties.POWERED) != signal) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, signal), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be) {
            be.setRedstonePowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.remoteContainer());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
