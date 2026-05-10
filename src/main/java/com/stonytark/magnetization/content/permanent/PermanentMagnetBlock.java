package com.stonytark.magnetization.content.permanent;

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
 * Always-on magnetic field source — no redstone, no kinetic input. Has a settable
 * polarity (NORTH or SOUTH) stored as a blockstate property; right-click flips
 * the polarity and plays a small click sound.
 *
 * <p>The intended gameplay use is propulsion: place a row of fixed-world
 * magnets, then mount opposing-polarity magnets on a ship. Like-poles repel
 * (pushing the ship along the track) and opposite-poles attract (pulling the
 * ship toward the track). Onboard-only setups do nothing — the
 * {@link com.stonytark.magnetization.physics.FieldApplicator} excludes each emitter's
 * own host ship, so internal forces can't propel a ship under its own power.
 * That's the realistic part of the unrealistic system.
 */
public class PermanentMagnetBlock extends Block implements EntityBlock {

    /** Two-state polarity property; NONE is excluded so the block always emits. */
    public static final EnumProperty<MagneticPolarity> POLARITY =
            EnumProperty.create("polarity", MagneticPolarity.class,
                    MagneticPolarity.NORTH, MagneticPolarity.SOUTH);

    public PermanentMagnetBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(POLARITY, MagneticPolarity.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POLARITY);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        // Hold-shift while placing to start with SOUTH polarity. Quality-of-life so a
        // player building a propulsion track doesn't need to right-click every block.
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
        return new PermanentMagnetBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.PERMANENT_MAGNET.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<PermanentMagnetBlockEntity>)
                PermanentMagnetBlockEntity::serverTick;
    }
}
