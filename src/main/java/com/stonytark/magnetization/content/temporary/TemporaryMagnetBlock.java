package com.stonytark.magnetization.content.temporary;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
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
import org.jetbrains.annotations.Nullable;

/**
 * Cheap, decaying counterpart to {@link com.stonytark.magnetization.content.permanent.PermanentMagnetBlock}.
 * Always-on while alive (no redstone), reverts to iron block after the BE's
 * lifetime elapses.
 *
 * <p>Polarity is rolled randomly at placement (sneak-place forces SOUTH for
 * symmetry with the Permanent Magnet's quality-of-life behavior); unlike the
 * Permanent, the player can't right-click flip it after placement — picking it
 * back up and re-placing rolls again, but in-place flipping would erase the
 * Permanent's main advantage.
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
        final MagneticPolarity pol;
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            pol = MagneticPolarity.SOUTH;
        } else if (context.getLevel().getRandom().nextBoolean()) {
            pol = MagneticPolarity.SOUTH;
        } else {
            pol = MagneticPolarity.NORTH;
        }
        return defaultBlockState().setValue(POLARITY, pol);
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
