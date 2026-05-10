package com.stonytark.magnetization.content.inverter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

/**
 * Block that flips the polarity of any adjacent emitter's field. Two inverters
 * around the same emitter cancel (even count = no flip), giving redstone-free
 * sequencing options.
 *
 * <p>The block itself is a passive marker — no BE, no ticker. Detection happens
 * in {@link com.stonytark.magnetization.content.AbstractEmitterBlockEntity#tickEmitter}
 * via {@link #shouldInvert(BlockGetter, BlockPos)}.
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
}
