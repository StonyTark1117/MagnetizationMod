package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exact chunk bounds for the whole-chunk radii used by creep emitter discovery. */
final class FerrofluidChunkSearch {
    private FerrofluidChunkSearch() {}

    static Set<Long> occupiedChunks(final Collection<BlockPos> anchors) {
        final Set<Long> occupied = new LinkedHashSet<>();
        for (final BlockPos anchor : anchors) occupied.add(ChunkPos.asLong(anchor));
        return occupied;
    }

    static void expand(final Set<Long> destination, final Set<Long> occupied, final int radiusBlocks) {
        if (radiusBlocks < 0 || radiusBlocks % 16 != 0) {
            throw new IllegalArgumentException("Exact source-chunk expansion requires a nonnegative multiple of 16");
        }
        final int radius = radiusBlocks / 16;
        for (final long chunk : occupied) {
            final int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) destination.add(ChunkPos.asLong(x, z));
            }
        }
    }
}
