package com.stonytark.magnetization.content.jet;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * MHD Jet Thruster block — see {@link MhdJetBlockEntity}. Faces the direction it
 * thrusts (set at placement); right-click with a magnet to slot it, empty-hand
 * to pop it out. {@code LIT} reflects an actively-firing engine.
 */
public final class MhdJetBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<MhdJetBlock> CODEC = simpleCodec(MhdJetBlock::new);

    public MhdJetBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.UP)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.LIT, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MhdJetBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.MHD_JET.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MhdJetBlockEntity>) MhdJetBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!MhdJetBlockEntity.isMagnet(stack)
                || !(level.getBlockEntity(pos) instanceof MhdJetBlockEntity jet)
                || !jet.getMagnet().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            jet.setMagnet(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof MhdJetBlockEntity jet)) {
            return InteractionResult.PASS;
        }
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(
                        id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.JET, jet.magnetContainer()),
                net.minecraft.network.chat.Component.translatable("block.magnetization.mhd_jet")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.JET));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MhdJetBlockEntity jet
                && !jet.getMagnet().isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), jet.getMagnet());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
