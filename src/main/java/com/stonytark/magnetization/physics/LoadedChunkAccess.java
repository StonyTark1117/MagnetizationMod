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

    /** Reuse the last loaded chunk within one read-only signal query, while
     * reading each block state live. Nothing survives this evaluation. */
    private static final class NeighborStateReader implements Function<BlockPos, BlockState> {
        private final ServerLevel level;
        private int chunkX;
        private int chunkZ;
        private LevelChunk chunk;

        private NeighborStateReader(final ServerLevel level, final BlockPos origin) {
            this.level = level;
            chunkX = Math.floorDiv(origin.getX(), 16);
            chunkZ = Math.floorDiv(origin.getZ(), 16);
            chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        }

        @Override
        public @Nullable BlockState apply(final BlockPos pos) {
            final int x = Math.floorDiv(pos.getX(), 16), z = Math.floorDiv(pos.getZ(), 16);
            if (x != chunkX || z != chunkZ) {
                chunkX = x;
                chunkZ = z;
                chunk = level.getChunkSource().getChunkNow(x, z);
            }
            return chunk == null ? null : chunk.getBlockState(pos);
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
