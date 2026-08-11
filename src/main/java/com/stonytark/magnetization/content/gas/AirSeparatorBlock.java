package com.stonytark.magnetization.content.gas;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Five-fraction Create-powered atmospheric separator. */
public final class AirSeparatorBlock extends KineticBlock implements IBE<AirSeparatorBlockEntity> {
    public static final MapCodec<AirSeparatorBlock> CODEC = simpleCodec(AirSeparatorBlock::new);

    public AirSeparatorBlock(final Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends KineticBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
    }
    @Override protected BlockState rotate(final BlockState state, final net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
    @Override protected BlockState mirror(final BlockState state, final net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
    @Override public boolean hasShaftTowards(final LevelReader level, final BlockPos pos,
                                             final BlockState state, final Direction face) {
        return face == state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
    }
    @Override public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
    }

    @Override protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state,
                                                         final Level level, final BlockPos pos,
                                                         final Player player, final InteractionHand hand,
                                                         final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AirSeparatorBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(com.stonytark.magnetization.registry.MagItems.ISOTOPE_SEPARATION_MODULE.get())) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (!be.installUpgrade()) return ItemInteractionResult.CONSUME;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Sneak-click a port for quick in-world reassignment; normal use opens the
     * full process, inventory, and port-management screen. */
    @Override protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                                          final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof AirSeparatorBlockEntity be)) return InteractionResult.PASS;
        final Direction shaft = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        if (player.isShiftKeyDown()) {
            if (hit.getDirection() == shaft) {
                serverPlayer.displayClientMessage(Component.translatable("message.magnetization.air_separator.shaft"), true);
                return InteractionResult.CONSUME;
            }
            final int next = be.cycleFace(hit.getDirection(), 1);
            serverPlayer.displayClientMessage(Component.translatable("message.magnetization.air_separator.port",
                    Component.translatable(AirSeparatorBlockEntity.gasTranslationKey(next))), true);
            return InteractionResult.CONSUME;
        }

        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, p) -> new com.stonytark.magnetization.menu.AirSeparatorMenu(id, inventory,
                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        be.upgradeContainer(), be.crystalOutputContainer()),
                Component.translatable("block.magnetization.air_separator")),
                buffer -> com.stonytark.magnetization.menu.AirSeparatorMenu.writeOpen(buffer, pos));
        return InteractionResult.CONSUME;
    }

    @Override public void onRemove(final BlockState state, final Level level, final BlockPos pos,
                                      final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AirSeparatorBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.upgradeContainer());
            net.minecraft.world.Containers.dropContents(level, pos, be.crystalOutputContainer());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override public Class<AirSeparatorBlockEntity> getBlockEntityClass() { return AirSeparatorBlockEntity.class; }
    @Override public BlockEntityType<? extends AirSeparatorBlockEntity> getBlockEntityType() {
        return MagBlockEntities.AIR_SEPARATOR.get();
    }
}
