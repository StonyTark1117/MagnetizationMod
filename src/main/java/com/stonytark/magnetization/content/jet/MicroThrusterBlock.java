package com.stonytark.magnetization.content.jet;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagItems;
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

/**
 * Ferrofluid Micro-Thruster block — see {@link MicroThrusterBlockEntity}. Faces
 * its thrust direction; right-click with a ferrofluid bucket to top up its tank
 * (returns an empty bucket). {@code LIT} = actively firing.
 */
public final class MicroThrusterBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<MicroThrusterBlock> CODEC = simpleCodec(MicroThrusterBlock::new);

    public MicroThrusterBlock(final Properties props) {
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
        return new MicroThrusterBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.MICRO_THRUSTER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MicroThrusterBlockEntity>) MicroThrusterBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!stack.is(MagItems.FERROFLUID_BUCKET.get())
                || !(level.getBlockEntity(pos) instanceof MicroThrusterBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!be.fillFromBucket()) return ItemInteractionResult.CONSUME; // tank full
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (!player.addItem(new ItemStack(Items.BUCKET))) {
                    player.drop(new ItemStack(Items.BUCKET), false);
                }
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(final BlockState state, final Level level,
                                                                   final BlockPos pos, final Player player,
                                                                   final BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof MicroThrusterBlockEntity be)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(
                        id, inv, net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.THRUSTER, be.bucketContainer()),
                net.minecraft.network.chat.Component.translatable("block.magnetization.micro_thruster")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, pos,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.THRUSTER));
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MicroThrusterBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.bucketContainer());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
