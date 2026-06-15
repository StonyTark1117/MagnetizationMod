package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared gallium helpers. Gallium melts at barely above room temperature, so in
 * the world it's liquid unless something cold sits next to it: a cooling source
 * (the ice family, snow, or powder snow) freezes liquid gallium into the solid
 * block, and the solid melts back to a fluid source once that cooling is gone.
 */
public final class Gallium {

    /** Ticks for liquid gallium to freeze once a cooling source is adjacent. */
    public static final int FREEZE_DELAY = 40;
    /** Ticks for solid gallium to melt once no cooling source is adjacent. */
    public static final int MELT_DELAY = 120;

    private Gallium() {}

    /** A block cold enough to keep gallium solid. */
    public static boolean isCoolingSource(final BlockState state) {
        return state.is(BlockTags.ICE)            // ice / packed / blue / frosted
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW);
    }

    /** True if any of the six neighbours of {@code pos} is a cooling source. */
    public static boolean coolingAdjacent(final LevelReader level, final BlockPos pos) {
        for (final Direction d : Direction.values()) {
            if (isCoolingSource(level.getBlockState(pos.relative(d)))) return true;
        }
        return false;
    }
}
