package com.stonytark.magnetization.content.itemframe;

import com.mojang.serialization.MapCodec;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Magnetic Item Frame — a thin magnetic plate that mounts flat on any surface
 * and holds a single item/tool/armor stuck to it for display (no rotation).
 * Right-click with an item to stick it on; empty-hand right-click pops it off.
 */
public final class MagneticItemFrameBlock extends DirectionalBlock implements EntityBlock {

    public static final MapCodec<MagneticItemFrameBlock> CODEC = simpleCodec(MagneticItemFrameBlock::new);

    // 1px-thick plate on the face opposite FACING (FACING points away from the wall).
    private static final VoxelShape[] SHAPES = new VoxelShape[6];
    static {
        SHAPES[Direction.DOWN.ordinal()]  = Shapes.box(0, 0,    0, 1, 0.06, 1);
        SHAPES[Direction.UP.ordinal()]    = Shapes.box(0, 0.94, 0, 1, 1,    1);
        SHAPES[Direction.NORTH.ordinal()] = Shapes.box(0, 0, 0,    1, 1, 0.06);
        SHAPES[Direction.SOUTH.ordinal()] = Shapes.box(0, 0, 0.94, 1, 1, 1);
        SHAPES[Direction.WEST.ordinal()]  = Shapes.box(0,    0, 0, 0.06, 1, 1);
        SHAPES[Direction.EAST.ordinal()]  = Shapes.box(0.94, 0, 0, 1,    1, 1);
    }

    public MagneticItemFrameBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        // FACING points out of the surface the frame is stuck to.
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final net.minecraft.world.level.BlockGetter level,
                                  final BlockPos pos, final CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).getOpposite().ordinal()];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MagneticItemFrameBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MagneticItemFrameBlockEntity frame)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!frame.getDisplayedItem().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // already holding — empty-hand pops it off
        }
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            final ItemStack one = stack.copyWithCount(1);
            frame.setDisplayedItem(one);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.playSound(null, pos, SoundType.METAL.getPlaceSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MagneticItemFrameBlockEntity frame)) return InteractionResult.PASS;
        if (frame.getDisplayedItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            final ItemStack popped = frame.removeDisplayedItem();
            if (!player.addItem(popped)) player.drop(popped, false);
            level.playSound(null, pos, SoundType.METAL.getBreakSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moved) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof MagneticItemFrameBlockEntity frame
                && !frame.getDisplayedItem().isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    frame.getDisplayedItem());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
