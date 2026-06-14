package com.stonytark.magnetization.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Client-side cached scan for the Ore Dowsing Compass needle. The item-model
 * angle property runs every frame, but a world block scan can't — so this keeps
 * a per-tick-throttled cache of the nearest matching ore {@link BlockPos}.
 *
 * <p>The scan is an expanding box sweep (nearest-first by Chebyshev shell) that
 * stops at the first match, so a nearby vein is found almost for free; only the
 * worst case (no ore in range) walks the whole bounded region, and even that is
 * capped at {@link #MAX_BLOCKS} examined and only re-run every {@link #SCAN_INTERVAL}
 * ticks. A single holder is assumed (the compass in the local player's hand).
 */
public final class OreCompassScanner {

    /** Horizontal + vertical reach of the dowse, in blocks. */
    private static final int RADIUS = 32;
    /** Hard cap on blocks examined per scan (safety against the no-ore worst case). */
    private static final int MAX_BLOCKS = 24_000;
    /** Ticks between rescans. */
    private static final long SCAN_INTERVAL = 20L;

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
        if (key.equals(lastKey) && now - lastScanTick < SCAN_INTERVAL) {
            return cached;
        }
        lastScanTick = now;
        lastKey = key;
        cached = scan(level, origin, match);
        return cached;
    }

    private static @Nullable BlockPos scan(final Level level, final BlockPos origin,
                                           final Predicate<BlockState> match) {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int minY = Math.max(level.getMinBuildHeight(), origin.getY() - RADIUS);
        final int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + RADIUS);
        int examined = 0;
        // Expanding Chebyshev shells → nearest-first; stop at the first hit.
        for (int r = 0; r <= RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    // Only the surface of the current shell (avoid re-scanning the interior).
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    final int x = origin.getX() + dx;
                    final int z = origin.getZ() + dz;
                    for (int y = minY; y <= maxY; y++) {
                        if (++examined > MAX_BLOCKS) return null;
                        cursor.set(x, y, z);
                        if (!level.isLoaded(cursor)) continue;
                        final BlockState state = level.getBlockState(cursor);
                        if (match.test(state)) return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }
}
