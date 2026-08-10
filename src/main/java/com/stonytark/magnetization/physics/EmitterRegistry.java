package com.stonytark.magnetization.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Per-level, per-chunk index of currently loaded magnetic emitters.
 *
 * <p>Native block-entity emitters and optional-mod block emitters are kept in
 * separate sets inside each chunk bucket. Native emitters retain the lifecycle
 * registration API used by their block entities. External emitters are replaced
 * atomically when a chunk loads and their entire set is discarded when it
 * unloads, so a delayed registration can never resurrect stale positions.
 *
 * <p>The chunk map is the authoritative spatial index. Full snapshots remain for
 * commands and client diagnostics, while hot server paths use bounded chunk
 * queries instead of copying every emitter in a pregenerated world.
 */
public final class EmitterRegistry {

    private static final WeakHashMap<Level, Map<Long, ChunkBucket>> BY_LEVEL = new WeakHashMap<>();

    private static final class ChunkBucket {
        private final Set<BlockPos> nativeEmitters = new HashSet<>();
        private Set<BlockPos> externalEmitters = Collections.emptySet();

        private boolean isEmpty() {
            return nativeEmitters.isEmpty() && externalEmitters.isEmpty();
        }
    }

    private EmitterRegistry() {}

    /** Register a native block-entity emitter. */
    public static synchronized void register(final Level level, final BlockPos pos) {
        bucket(level, ChunkPos.asLong(pos)).nativeEmitters.add(pos.immutable());
    }

    /** Unregister a native block-entity emitter. */
    public static synchronized void unregister(final Level level, final BlockPos pos) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null) return;
        final long key = ChunkPos.asLong(pos);
        final ChunkBucket bucket = chunks.get(key);
        if (bucket == null) return;
        bucket.nativeEmitters.remove(pos);
        removeEmpty(level, chunks, key, bucket);
    }

    /**
     * Atomically replace the optional-mod emitters discovered in one loaded
     * chunk. An empty replacement also clears a prior scan result.
     */
    public static synchronized void replaceExternalChunk(final Level level, final ChunkPos chunkPos,
                                                         final Collection<BlockPos> positions) {
        final long key = chunkPos.toLong();
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        final ChunkBucket bucket = chunks.computeIfAbsent(key, ignored -> new ChunkBucket());
        if (positions.isEmpty()) {
            bucket.externalEmitters = Collections.emptySet();
        } else {
            final Set<BlockPos> replacement = new HashSet<>(positions.size());
            for (final BlockPos pos : positions) {
                if (ChunkPos.asLong(pos) == key) replacement.add(pos.immutable());
            }
            bucket.externalEmitters = replacement.isEmpty()
                    ? Collections.emptySet() : replacement;
        }
        removeEmpty(level, chunks, key, bucket);
    }

    /** Add one externally supplied emitter after a block-place event. */
    public static synchronized void registerExternal(final Level level, final BlockPos pos) {
        final ChunkBucket bucket = bucket(level, ChunkPos.asLong(pos));
        if (bucket.externalEmitters.isEmpty()) bucket.externalEmitters = new HashSet<>();
        bucket.externalEmitters.add(pos.immutable());
    }

    /** Remove one external emitter after a break or stale-entry check. */
    public static synchronized void unregisterExternal(final Level level, final BlockPos pos) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null) return;
        final long key = ChunkPos.asLong(pos);
        final ChunkBucket bucket = chunks.get(key);
        if (bucket == null || bucket.externalEmitters.isEmpty()) return;
        bucket.externalEmitters.remove(pos);
        if (bucket.externalEmitters.isEmpty()) bucket.externalEmitters = Collections.emptySet();
        removeEmpty(level, chunks, key, bucket);
    }

    /** Unconditionally discard the external bucket for an unloading chunk. */
    public static synchronized void dropExternalChunk(final Level level, final ChunkPos chunkPos) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null) return;
        final long key = chunkPos.toLong();
        final ChunkBucket bucket = chunks.get(key);
        if (bucket == null) return;
        bucket.externalEmitters = Collections.emptySet();
        removeEmpty(level, chunks, key, bucket);
    }

    /** Iterate a defensive snapshot of every native and external emitter. */
    public static void forEach(final Level level, final BiConsumer<Level, BlockPos> callback) {
        for (final BlockPos pos : snapshot(level)) callback.accept(level, pos);
    }

    /** Full union snapshot retained for commands and diagnostics. */
    public static synchronized Set<BlockPos> snapshot(final Level level) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final Set<BlockPos> result = new HashSet<>();
        for (final ChunkBucket bucket : chunks.values()) {
            result.addAll(bucket.nativeEmitters);
            result.addAll(bucket.externalEmitters);
        }
        return result;
    }

    /** Native-only snapshot; native BE populations are normally small. */
    public static synchronized Set<BlockPos> snapshotNative(final Level level) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final Set<BlockPos> result = new HashSet<>();
        for (final ChunkBucket bucket : chunks.values()) result.addAll(bucket.nativeEmitters);
        return result;
    }

    /** Native positions from an explicit target-local chunk set. */
    public static synchronized Set<BlockPos> snapshotNativeInChunks(
            final Level level, final Collection<Long> chunkKeys) {
        if (chunkKeys.isEmpty()) return Collections.emptySet();
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final Set<BlockPos> result = new HashSet<>();
        for (final long key : chunkKeys) {
            final ChunkBucket bucket = chunks.get(key);
            if (bucket != null) result.addAll(bucket.nativeEmitters);
        }
        return result;
    }

    /** External-only full snapshot for tests and diagnostics, never hot ticks. */
    public static synchronized Set<BlockPos> snapshotExternal(final Level level) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final Set<BlockPos> result = new HashSet<>();
        for (final ChunkBucket bucket : chunks.values()) result.addAll(bucket.externalEmitters);
        return result;
    }

    /**
     * Return at most {@code limit} external positions from the requested chunk
     * keys. The insertion order of {@code chunkKeys} is preserved, allowing the
     * caller to rotate target priority without ever scanning unrelated chunks.
     */
    public static synchronized Set<BlockPos> snapshotExternalInChunks(
            final Level level, final Collection<Long> chunkKeys, final int limit) {
        if (limit <= 0 || chunkKeys.isEmpty()) return Collections.emptySet();
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final Set<BlockPos> result = new LinkedHashSet<>(Math.min(limit, 256));
        for (final long key : chunkKeys) {
            final ChunkBucket bucket = chunks.get(key);
            if (bucket == null) continue;
            for (final BlockPos pos : bucket.externalEmitters) {
                result.add(pos);
                if (result.size() >= limit) return result;
            }
        }
        return result;
    }

    /** External positions in a square chunk radius around one target. */
    public static Set<BlockPos> snapshotExternalNear(final Level level, final BlockPos target,
                                                     final int radiusBlocks, final int limit) {
        final int minChunkX = Math.floorDiv(target.getX() - radiusBlocks, 16);
        final int maxChunkX = Math.floorDiv(target.getX() + radiusBlocks, 16);
        final int minChunkZ = Math.floorDiv(target.getZ() - radiusBlocks, 16);
        final int maxChunkZ = Math.floorDiv(target.getZ() + radiusBlocks, 16);
        final Set<Long> keys = new LinkedHashSet<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) keys.add(ChunkPos.asLong(x, z));
        }
        return snapshotExternalInChunks(level, keys, limit);
    }

    /** Native positions in a square block radius around one target. */
    public static synchronized Set<BlockPos> snapshotNativeNear(final Level level, final BlockPos target,
                                                               final int radiusBlocks) {
        final Map<Long, ChunkBucket> chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final int minChunkX = Math.floorDiv(target.getX() - radiusBlocks, 16);
        final int maxChunkX = Math.floorDiv(target.getX() + radiusBlocks, 16);
        final int minChunkZ = Math.floorDiv(target.getZ() - radiusBlocks, 16);
        final int maxChunkZ = Math.floorDiv(target.getZ() + radiusBlocks, 16);
        final Set<BlockPos> result = new HashSet<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                final ChunkBucket bucket = chunks.get(ChunkPos.asLong(x, z));
                if (bucket != null) result.addAll(bucket.nativeEmitters);
            }
        }
        return result;
    }

    public static synchronized int size(final Level level) {
        return snapshot(level).size();
    }

    public static synchronized int externalSize(final Level level) {
        return snapshotExternal(level).size();
    }

    private static ChunkBucket bucket(final Level level, final long chunkKey) {
        return BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(chunkKey, ignored -> new ChunkBucket());
    }

    private static void removeEmpty(final Level level, final Map<Long, ChunkBucket> chunks,
                                    final long key, final ChunkBucket bucket) {
        if (bucket.isEmpty()) chunks.remove(key);
        if (chunks.isEmpty()) BY_LEVEL.remove(level);
    }
}
