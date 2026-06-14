package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Controller for the tokamak fusion reactor multiblock. See
 * {@link TokamakControllerBlockEntity}. Right-click opens a GUI with a fuel slot
 * that auto-feeds Deuterium Cells; {@code LIT} reflects active fusion.
 */
public final class TokamakControllerBlock extends Block implements EntityBlock {

    public TokamakControllerBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new TokamakControllerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.TOKAMAK_CONTROLLER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TokamakControllerBlockEntity>)
                TokamakControllerBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof TokamakControllerBlockEntity be)) {
            return InteractionResult.PASS;
        }
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(
                        id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.TOKAMAK, be.fuelContainer()),
                net.minecraft.network.chat.Component.translatable("block.magnetization.tokamak_controller")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.TOKAMAK));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof TokamakControllerBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.fuelContainer());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
