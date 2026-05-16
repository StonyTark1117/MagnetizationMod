package com.stonytark.magnetization.content.inverter;

import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block that flips the polarity of any adjacent emitter's field. Two inverters
 * around the same emitter cancel (even count = no flip), giving redstone-free
 * sequencing options.
 *
 * <p>The block itself is a passive marker — no BE, no ticker. Detection happens
 * in {@link com.stonytark.magnetization.content.AbstractEmitterBlockEntity#tickEmitter}
 * via {@link #shouldInvert(BlockGetter, BlockPos)}.
 *
 * <p>Inverters also contribute to a ship's whole-contraption polarity (parity
 * of the count on board). The place/break hooks below invalidate the
 * {@link ShipMagneticRegistry} cache for the level so that flip is visible on
 * the next emitter tick rather than waiting up to {@code shipScanIntervalTicks}
 * for the polling re-scan. We invalidate the whole level rather than the
 * containing ship because looking up a ship from a sub-level-local blockpos
 * across the Sable boundary is fiddly, and ships re-fill lazily on demand.
 */
public class PolarityInverterBlock extends Block {

    public PolarityInverterBlock(final Properties props) {
        super(props);
    }

    /**
     * @return {@code true} if an odd number of polarity inverters touch the
     *         given position, {@code false} otherwise.
     */
    public static boolean shouldInvert(final BlockGetter level, final BlockPos pos) {
        int count = 0;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof PolarityInverterBlock) {
                count++;
            }
        }
        return (count & 1) == 1;
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel server) ShipMagneticRegistry.invalidateAll(server);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            ShipMagneticRegistry.invalidateAll(server);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
