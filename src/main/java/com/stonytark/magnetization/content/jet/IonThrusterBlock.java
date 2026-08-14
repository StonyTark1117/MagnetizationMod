package com.stonytark.magnetization.content.jet;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

/** Ship-mounted electric thruster whose performance depends on its noble-gas propellant. */
public final class IonThrusterBlock extends DirectionalBlock implements EntityBlock, IWrenchable {
    public static final MapCodec<IonThrusterBlock> CODEC = simpleCodec(IonThrusterBlock::new);

    public IonThrusterBlock(final Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.UP)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.LIT);
    }
    @Override public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }
    @Override protected BlockState rotate(final BlockState state, final net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected BlockState mirror(final BlockState state, final net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new IonThrusterBlockEntity(pos, state);
    }

    @Override @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (type != MagBlockEntities.ION_THRUSTER.get()) return null;
        if (level.isClientSide) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<IonThrusterBlockEntity>) IonThrusterBlockEntity::clientTick;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<IonThrusterBlockEntity>) IonThrusterBlockEntity::serverTick;
    }

    @Override protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state,
                                                         final Level level, final BlockPos pos,
                                                         final Player player, final InteractionHand hand,
                                                         final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof IonThrusterBlockEntity be)
                || !IonThrusterBlockEntity.isPropellantBucket(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!be.fillFromBucket(stack)) return ItemInteractionResult.CONSUME;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!player.addItem(new ItemStack(Items.BUCKET))) player.drop(new ItemStack(Items.BUCKET), false);
        }
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.15F);
        return ItemInteractionResult.CONSUME;
    }

    @Override protected net.minecraft.world.InteractionResult useWithoutItem(final BlockState state,
                                                                              final Level level,
                                                                              final BlockPos pos,
                                                                              final Player player,
                                                                              final BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof IonThrusterBlockEntity be)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(id, inv,
                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.ION_THRUSTER, be.bucketContainer()),
                net.minecraft.network.chat.Component.translatable("block.magnetization.ion_thruster")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.ION_THRUSTER));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                                      final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof IonThrusterBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.bucketContainer());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
