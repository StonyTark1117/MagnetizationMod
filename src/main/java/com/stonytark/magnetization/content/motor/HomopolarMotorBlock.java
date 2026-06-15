package com.stonytark.magnetization.content.motor;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Homopolar Motor / Magnetic Flywheel — see {@link HomopolarMotorBlockEntity}.
 * A Create kinetic <em>generator</em>: install a magnet (GUI or right-click) and
 * it drives a shaft out of its facing side. Placed so the shaft socket faces the
 * player; wrench/rotation handled by {@link DirectionalKineticBlock}.
 */
public final class HomopolarMotorBlock extends DirectionalKineticBlock implements IBE<HomopolarMotorBlockEntity> {

    public static final MapCodec<HomopolarMotorBlock> CODEC = simpleCodec(HomopolarMotorBlock::new);

    public HomopolarMotorBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalKineticBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        // Shaft socket (FACING face) points at the player.
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state, final Direction face) {
        // Output a shaft on BOTH ends of the facing axis (the model has a socket
        // on each), so a shaft can be driven off either side; both turn together
        // since they share the one rotation axis.
        final Direction facing = state.getValue(FACING);
        return face == facing || face == facing.getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!HomopolarMotorBlockEntity.isMagnet(stack)
                || !(level.getBlockEntity(pos) instanceof HomopolarMotorBlockEntity motor)
                || !motor.getMagnet().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            motor.setMagnet(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof HomopolarMotorBlockEntity motor)) {
            return InteractionResult.PASS;
        }
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(
                        id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.MOTOR, motor.magnetContainer()),
                net.minecraft.network.chat.Component.translatable("block.magnetization.homopolar_motor")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.MOTOR));
        return InteractionResult.CONSUME;
    }

    @Override
    public Class<HomopolarMotorBlockEntity> getBlockEntityClass() {
        return HomopolarMotorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HomopolarMotorBlockEntity> getBlockEntityType() {
        return MagBlockEntities.HOMOPOLAR_MOTOR.get();
    }

    @Override
    public void onRemove(final BlockState state, final Level level, final BlockPos pos,
                         final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HomopolarMotorBlockEntity motor
                && !motor.getMagnet().isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), motor.getMagnet());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
