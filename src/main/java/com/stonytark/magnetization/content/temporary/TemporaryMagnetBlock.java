package com.stonytark.magnetization.content.temporary;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Cheap, decaying counterpart to {@link com.stonytark.magnetization.content.permanent.PermanentMagnetBlock}.
 * Always-on while alive (no redstone), reverts to iron block after the BE's
 * lifetime elapses.
 *
 * <p>Polarity defaults to NORTH on placement (sneak-place starts SOUTH), and
 * right-click flips it — same UX as the Permanent Magnet so propulsion-track
 * setups feel consistent regardless of which variant you're using.
 */
public class TemporaryMagnetBlock extends Block implements EntityBlock {

    public static final EnumProperty<MagneticPolarity> POLARITY =
            EnumProperty.create("polarity", MagneticPolarity.class,
                    MagneticPolarity.NORTH, MagneticPolarity.SOUTH);

    public TemporaryMagnetBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(POLARITY, MagneticPolarity.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POLARITY);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(POLARITY,
                context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                        ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            final ItemStack stack, final BlockState state, final Level level,
            final BlockPos pos, final Player player, final InteractionHand hand,
            final BlockHitResult hit
    ) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        final MagneticPolarity flipped = state.getValue(POLARITY).opposite();
        level.setBlock(pos, state.setValue(POLARITY, flipped), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.LODESTONE_HIT, SoundSource.BLOCKS, 0.5f, 1.4f);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new TemporaryMagnetBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.TEMPORARY_MAGNET.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TemporaryMagnetBlockEntity>)
                TemporaryMagnetBlockEntity::serverTick;
    }
}
