package com.stonytark.magnetization.content.jet;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
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
 * Fusion Thruster interior block — see {@link FusionThrusterBlockEntity}. Tiled
 * inside a Tokamak-Coil ring to form a flat panel; {@code FACING} is the exhaust
 * side and reaction thrust moves the ship opposite it. Right-click with a
 * fusion-fluid bucket to top up the panel's tank; empty-hand opens the shared
 * panel GUI. {@code LIT} = the panel is actively firing.
 */
public final class FusionThrusterBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<FusionThrusterBlock> CODEC = simpleCodec(FusionThrusterBlock::new);

    public FusionThrusterBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.LIT);
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
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.LIT, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new FusionThrusterBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.FUSION_THRUSTER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<FusionThrusterBlockEntity>) FusionThrusterBlockEntity::serverTick;
    }

    /** Resolve the panel master's BE for the clicked block, or the clicked BE itself
     *  if the panel isn't valid (so a lone block still works). */
    private static @Nullable FusionThrusterBlockEntity resolveTarget(final Level level, final BlockPos pos,
                                                                     final BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof FusionThrusterBlockEntity self)) return null;
        final Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        final BlockPos master = FusionThrusterPanel.findMaster(level, pos, facing, MagConfig.fusionThrusterMaxEdge());
        if (master != null && level.getBlockEntity(master) instanceof FusionThrusterBlockEntity m) return m;
        return self;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!FusionThrusterBlockEntity.isFusionFluidBucket(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        final FusionThrusterBlockEntity be = resolveTarget(level, pos, state);
        if (be == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!be.fillFromBucket(stack)) return ItemInteractionResult.CONSUME;    // tank full / mismatch
        if (!level.isClientSide) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (!player.addItem(new ItemStack(Items.BUCKET))) {
                    player.drop(new ItemStack(Items.BUCKET), false);
                }
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 0.7f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return InteractionResult.PASS;
        final FusionThrusterBlockEntity be = resolveTarget(level, pos, state);
        if (be == null) return InteractionResult.PASS;
        final BlockPos target = be.getBlockPos();
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new com.stonytark.magnetization.menu.MachineMenu(
                        id, inv, ContainerLevelAccess.create(level, target), target,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.FUSION_THRUSTER, be.bucketContainer()),
                Component.translatable("block.magnetization.fusion_thruster")),
                buf -> com.stonytark.magnetization.menu.MachineMenu.writeOpen(buf, target,
                        com.stonytark.magnetization.menu.MachineMenu.Kind.FUSION_THRUSTER));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FusionThrusterBlockEntity be) {
                be.invalidateCachedFrameCapabilities();
                net.minecraft.world.Containers.dropContents(level, pos, be.bucketContainer());
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
