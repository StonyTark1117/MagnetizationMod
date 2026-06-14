package com.stonytark.magnetization.content.motor;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Homopolar Motor / Magnetic Flywheel block — see {@link HomopolarMotorBlockEntity}.
 * A Create kinetic generator: install a magnet (right-click with one; empty-hand
 * right-click pops it back out) and it spins a shaft on its placement axis.
 */
public final class HomopolarMotorBlock extends KineticBlock implements IBE<HomopolarMotorBlockEntity> {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final MapCodec<HomopolarMotorBlock> CODEC = simpleCodec(HomopolarMotorBlock::new);

    public HomopolarMotorBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state, final Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!HomopolarMotorBlockEntity.isMagnet(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof HomopolarMotorBlockEntity motor)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!motor.getMagnet().isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            final ItemStack one = stack.copyWithCount(1);
            motor.setMagnet(one);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(final BlockState state, final Level level,
                                                                   final BlockPos pos, final Player player,
                                                                   final BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof HomopolarMotorBlockEntity motor)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        // Open the magnet-slot GUI.
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.HomopolarMotorMenu(
                        id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        motor.magnetContainer()),
                net.minecraft.network.chat.Component.translatable("block.magnetization.homopolar_motor")),
                buf -> buf.writeBlockPos(pos));
        return net.minecraft.world.InteractionResult.CONSUME;
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HomopolarMotorBlockEntity motor) {
            if (!motor.getMagnet().isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), motor.getMagnet());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
