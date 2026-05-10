package com.stonytark.magnetization.content.switchblock;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Emits a redstone signal proportional to how close the nearest Sable sub-level
 * (CA ship) is. 0 if no ship in range, 15 if a ship is touching the switch.
 * Useful as the trigger half of a docking automation: pair with a magnetic
 * anchor on a comparator to lock a ship the moment it arrives.
 */
public class MagneticSwitchBlock extends Block implements EntityBlock {

    public MagneticSwitchBlock(final Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MagneticSwitchBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.MAGNETIC_SWITCH.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MagneticSwitchBlockEntity>)
                MagneticSwitchBlockEntity::serverTick;
    }

    @Override
    public boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    public int getSignal(final BlockState state, final net.minecraft.world.level.BlockGetter level,
                         final BlockPos pos, final net.minecraft.core.Direction dir) {
        final BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MagneticSwitchBlockEntity sw ? sw.signal() : 0;
    }
}
