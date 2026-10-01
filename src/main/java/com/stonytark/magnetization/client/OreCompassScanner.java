package com.stonytark.magnetization.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.function.Predicate;

/**
 * Client-side cached scan for the Ore Dowsing Compass needle. The item-model
 * angle property runs every frame, but a world block scan can't — so this keeps
 * a per-tick-throttled cache of the nearest matching ore {@link BlockPos}.
 *
 * <p>The scan visits loaded chunk sections in the advertised box, skipping
 * sections whose palette contains no matching ore. It compares actual 3D
 * distance rather than stopping at the first matching column. A single holder
 * is assumed (the compass in the local player's hand).
 */
public final class OreCompassScanner {

    /** Horizontal + vertical reach of the dowse, in blocks. */
    private static final int RADIUS = 32;
    /** Ticks between rescans. */
    private static final long SCAN_INTERVAL = 20L;

    private static @Nullable WeakReference<Level> lastLevel = null;
    private static @Nullable BlockPos lastOrigin = null;
    private static long lastScanTick = Long.MIN_VALUE;
    private static @Nullable String lastKey = null;
    private static @Nullable BlockPos cached = null;

    private OreCompassScanner() {}

    /**
     * Nearest block satisfying {@code match} within {@link #RADIUS} of {@code origin},
     * throttled + cached. {@code key} identifies the target type (e.g. "any" or a
     * specific block id) so changing the tuned ore forces a fresh scan.
     */
    public static @Nullable BlockPos nearest(final Level level, final BlockPos origin,
                                              final String key, final Predicate<BlockState> match) {
        final long now = level.getGameTime();
        if (lastLevel != null && level == lastLevel.get()
                && origin.equals(lastOrigin) && key.equals(lastKey)
                && now >= lastScanTick && now - lastScanTick < SCAN_INTERVAL
                && (cached == null || (level.isLoaded(cached) && match.test(level.getBlockState(cached))))) {
            return cached;
        }
        lastLevel = new WeakReference<>(level);
        lastOrigin = origin.immutable();
        lastScanTick = now;
        lastKey = key;
        cached = scan(level, origin, match);
        return cached;
    }

    private static @Nullable BlockPos scan(final Level level, final BlockPos origin,
                                           final Predicate<BlockState> match) {
        final int minY = Math.max(level.getMinBuildHeight(), origin.getY() - RADIUS);
        final int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + RADIUS);
        if (minY > maxY) return null;
        final int minX = origin.getX() - RADIUS;
        final int maxX = origin.getX() + RADIUS;
        final int minZ = origin.getZ() - RADIUS;
        final int maxZ = origin.getZ() + RADIUS;
        final BlockPos.MutableBlockPos chunkCheck = new BlockPos.MutableBlockPos();
        BlockPos nearest = null;
        int bestDistanceSqr = Integer.MAX_VALUE;

        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                chunkCheck.set(chunkX << 4, origin.getY(), chunkZ << 4);
                if (!level.hasChunkAt(chunkCheck)) continue;
                final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                final LevelChunkSection[] sections = chunk.getSections();
                final int xStart = Math.max(minX, chunkX << 4);
                final int xEnd = Math.min(maxX, (chunkX << 4) + 15);
                final int zStart = Math.max(minZ, chunkZ << 4);
                final int zEnd = Math.min(maxZ, (chunkZ << 4) + 15);

                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    final int sectionY = (chunk.getMinSection() + sectionIndex) << 4;
                    final int yStart = Math.max(minY, sectionY);
                    final int yEnd = Math.min(maxY, sectionY + 15);
                    if (yStart > yEnd) continue;
                    final LevelChunkSection section = sections[sectionIndex];
                    if (section.hasOnlyAir() || !section.maybeHas(match)) continue;

                    for (int x = xStart; x <= xEnd; x++) {
                        final int dx = x - origin.getX();
                        for (int z = zStart; z <= zEnd; z++) {
                            final int dz = z - origin.getZ();
                            final int horizontalDistanceSqr = dx * dx + dz * dz;
                            if (horizontalDistanceSqr >= bestDistanceSqr) continue;
                            for (int y = yStart; y <= yEnd; y++) {
                                final int dy = y - origin.getY();
                                final int distanceSqr = horizontalDistanceSqr + dy * dy;
                                if (distanceSqr >= bestDistanceSqr) continue;
                                if (!match.test(section.getBlockState(x & 15, y & 15, z & 15))) continue;
                                nearest = new BlockPos(x, y, z);
                                bestDistanceSqr = distanceSqr;
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }
}
