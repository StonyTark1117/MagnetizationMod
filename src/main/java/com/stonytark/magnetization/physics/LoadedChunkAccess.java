package com.stonytark.magnetization.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Non-loading reads used by tracked-emitter hot paths. */
public final class LoadedChunkAccess {
    private static final Direction[] DIRECTIONS = Direction.values();
    // Weak neighbors and their direct neighbors occupy only the 25 cells at
    // Manhattan distance <= 2, not all 125 cells of the enclosing cube.
    private static final int[] SIGNAL_CACHE_INDICES = signalCacheIndices();

    private static int[] signalCacheIndices() {
        final int[] indices = new int[125];
        java.util.Arrays.fill(indices, -1);
        int index = 0;
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) + Math.abs(y) + Math.abs(z) <= 2) indices[((x + 2) * 5 + y + 2) * 5 + z + 2] = index++;
        }
        return indices;
    }
    private LoadedChunkAccess() {}

    /** Returns the already-full chunk or {@code null}; never creates a ticket. */
    public static @Nullable LevelChunk chunkNow(final ServerLevel level, final BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
    }

    public static @Nullable BlockState blockState(final ServerLevel level, final BlockPos pos) {
        final LevelChunk chunk = chunkNow(level, pos);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    public static @Nullable BlockEntity blockEntity(final ServerLevel level, final BlockPos pos) {
        final LevelChunk chunk = chunkNow(level, pos);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    /**
     * Loaded-neighbour equivalent of {@code SignalGetter.hasNeighborSignal}.
     * Missing chunks contribute zero power rather than being synchronously loaded.
     */
    public static boolean hasNeighborSignal(final ServerLevel level, final BlockPos pos) {
        return hasNeighborSignal(level, pos, new NeighborStateReader(level, pos));
    }

    /** Reuse chunk and immutable block-state reads within one read-only signal
     * query. Nothing survives this evaluation; signal methods themselves remain live. */
    private static final class NeighborStateReader implements Function<BlockPos, BlockState> {
        private final ServerLevel level;
        private int chunkX;
        private int chunkZ;
        private LevelChunk chunk;
        private final int originX, originY, originZ;
        private final BlockState[] states = new BlockState[25];
        private int read;

        private NeighborStateReader(final ServerLevel level, final BlockPos origin) {
            this.level = level;
            originX = origin.getX(); originY = origin.getY(); originZ = origin.getZ();
            chunkX = Math.floorDiv(origin.getX(), 16);
            chunkZ = Math.floorDiv(origin.getZ(), 16);
            chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        }

        @Override
        public @Nullable BlockState apply(final BlockPos pos) {
            final int dx = pos.getX() - originX + 2, dy = pos.getY() - originY + 2,
                    dz = pos.getZ() - originZ + 2;
            final int index = dx >= 0 && dx < 5 && dy >= 0 && dy < 5 && dz >= 0 && dz < 5
                    ? SIGNAL_CACHE_INDICES[(dx * 5 + dy) * 5 + dz] : -1;
            if (index >= 0 && (read & (1 << index)) != 0) return states[index];
            final int x = Math.floorDiv(pos.getX(), 16), z = Math.floorDiv(pos.getZ(), 16);
            if (x != chunkX || z != chunkZ) {
                chunkX = x;
                chunkZ = z;
                chunk = level.getChunkSource().getChunkNow(x, z);
            }
            final BlockState result = chunk == null ? null : chunk.getBlockState(pos);
            if (index >= 0) { read |= 1 << index; states[index] = result; }
            return result;
        }
    }

    /** Package-visible core that keeps the loaded-state reader injectable. */
    static boolean hasNeighborSignal(final SignalGetter getter, final BlockPos pos,
                                     final Function<BlockPos, @Nullable BlockState> loadedState) {
        for (final Direction direction : DIRECTIONS) {
            if (signal(getter, pos.relative(direction), direction, loadedState) > 0) return true;
        }
        return false;
    }

    private static int signal(final SignalGetter getter, final BlockPos pos, final Direction direction,
                              final Function<BlockPos, @Nullable BlockState> loadedState) {
        final BlockState state = loadedState.apply(pos);
        if (state == null) return 0;
        final int weak = state.getSignal(getter, pos, direction);
        return state.shouldCheckWeakPower(getter, pos, direction)
                ? Math.max(weak, directSignalTo(getter, pos, loadedState)) : weak;
    }

    private static int directSignalTo(final SignalGetter getter, final BlockPos pos,
                                      final Function<BlockPos, @Nullable BlockState> loadedState) {
        int best = 0;
        for (final Direction direction : DIRECTIONS) {
            final BlockPos neighbor = pos.relative(direction);
            final BlockState state = loadedState.apply(neighbor);
            if (state == null) continue;
            best = Math.max(best, state.getDirectSignal(getter, neighbor, direction));
            if (best >= 15) return 15;
        }
        return best;
    }
}
