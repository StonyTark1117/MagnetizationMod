package com.stonytark.magnetization.content.electrolyzer;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stonytark.magnetization.menu.MachineMenu;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Electrolyzer block — a cauldron that electrolyses water into hydrogen with FE
 * (see {@link ElectrolyzerBlockEntity}). Right-click a water bucket to fill, an
 * empty bucket to draw hydrogen, or an empty hand to open the GUI. The water
 * level is rendered inside the basin by {@link com.stonytark.magnetization.client.render.ElectrolyzerRenderer}.
 */
public final class ElectrolyzerBlock extends Block implements EntityBlock, IWrenchable {

    public static final MapCodec<ElectrolyzerBlock> CODEC = simpleCodec(ElectrolyzerBlock::new);

    // Vanilla cauldron collision/outline shape.
    private static final VoxelShape INSIDE = box(2.0, 4.0, 2.0, 14.0, 16.0, 14.0);
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(),
            Shapes.or(box(0.0, 0.0, 4.0, 16.0, 3.0, 12.0),
                    box(4.0, 0.0, 0.0, 12.0, 3.0, 16.0),
                    box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
                    INSIDE),
            BooleanOp.ONLY_FIRST);

    public ElectrolyzerBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(final BlockState state, final BlockGetter level, final BlockPos pos) {
        return Shapes.block();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new ElectrolyzerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.ELECTROLYZER.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ElectrolyzerBlockEntity>) ElectrolyzerBlockEntity::serverTick;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Fill from a water bucket.
        if (stack.is(Items.WATER_BUCKET)) {
            if (!be.fillWaterFromBucket()) return ItemInteractionResult.CONSUME;
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    giveOrDrop(player, new ItemStack(Items.BUCKET));
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Draw hydrogen into an empty bucket.
        if (stack.is(Items.BUCKET)) {
            if (!be.drainHydrogenToBucket()) return ItemInteractionResult.CONSUME;
            if (!level.isClientSide) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    giveOrDrop(player, new ItemStack(com.stonytark.magnetization.registry.MagItems.HYDROGEN_BUCKET.get()));
                }
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)
                || !(level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity be)) {
            return InteractionResult.PASS;
        }
        sp.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new MachineMenu(id, inv, ContainerLevelAccess.create(level, pos), pos,
                        MachineMenu.Kind.ELECTROLYZER, be.bucketContainer()),
                Component.translatable("block.magnetization.electrolyzer")),
                buf -> MachineMenu.writeOpen(buf, pos, MachineMenu.Kind.ELECTROLYZER));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ElectrolyzerBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.bucketContainer());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private static void giveOrDrop(final Player player, final ItemStack stack) {
        if (!player.addItem(stack)) player.drop(stack, false);
    }
}
