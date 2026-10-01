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

    private static final WeakHashMap<Level, LevelIndex> BY_LEVEL = new WeakHashMap<>();

    private static final class LevelIndex extends HashMap<Long, ChunkBucket> {
        private int nativeCount;
        private int externalCount;
        private final java.util.NavigableMap<Integer, java.util.NavigableMap<Integer, ChunkBucket>> rows =
                new java.util.TreeMap<>();

        private ChunkBucket getOrCreate(final long key) {
            return computeIfAbsent(key, ignored -> {
                final ChunkBucket created = new ChunkBucket();
                rows.computeIfAbsent(ChunkPos.getX(key), x -> new java.util.TreeMap<>())
                        .put(ChunkPos.getZ(key), created);
                return created;
            });
        }
    }

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
        if (bucket(level, ChunkPos.asLong(pos)).nativeEmitters.add(pos.immutable())) BY_LEVEL.get(level).nativeCount++;
    }

    /** Unregister a native block-entity emitter. */
    public static synchronized void unregister(final Level level, final BlockPos pos) {
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (chunks == null) return;
        final long key = ChunkPos.asLong(pos);
        final ChunkBucket bucket = chunks.get(key);
        if (bucket == null) return;
        if (bucket.nativeEmitters.remove(pos)) chunks.nativeCount--;
        removeEmpty(level, chunks, key, bucket);
    }

    /**
     * Atomically replace the optional-mod emitters discovered in one loaded
     * chunk. An empty replacement also clears a prior scan result.
     */
    public static synchronized void replaceExternalChunk(final Level level, final ChunkPos chunkPos,
                                                         final Collection<BlockPos> positions) {
        final long key = chunkPos.toLong();
        final LevelIndex chunks = BY_LEVEL.computeIfAbsent(level, ignored -> new LevelIndex());
        final ChunkBucket bucket = chunks.getOrCreate(key);
        chunks.externalCount -= bucket.externalEmitters.size();
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
        chunks.externalCount += bucket.externalEmitters.size();
        removeEmpty(level, chunks, key, bucket);
    }

    /** Add one externally supplied emitter after a block-place event. */
    public static synchronized void registerExternal(final Level level, final BlockPos pos) {
        final ChunkBucket bucket = bucket(level, ChunkPos.asLong(pos));
        if (bucket.externalEmitters.isEmpty()) bucket.externalEmitters = new HashSet<>();
        if (bucket.externalEmitters.add(pos.immutable())) BY_LEVEL.get(level).externalCount++;
    }

    /** Remove one external emitter after a break or stale-entry check. */
    public static synchronized void unregisterExternal(final Level level, final BlockPos pos) {
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (chunks == null) return;
        final long key = ChunkPos.asLong(pos);
        final ChunkBucket bucket = chunks.get(key);
        if (bucket == null || bucket.externalEmitters.isEmpty()) return;
        if (bucket.externalEmitters.remove(pos)) chunks.externalCount--;
        if (bucket.externalEmitters.isEmpty()) bucket.externalEmitters = Collections.emptySet();
        removeEmpty(level, chunks, key, bucket);
    }

    /** Unconditionally discard the external bucket for an unloading chunk. */
    public static synchronized void dropExternalChunk(final Level level, final ChunkPos chunkPos) {
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (chunks == null) return;
        final long key = chunkPos.toLong();
        final ChunkBucket bucket = chunks.get(key);
        if (bucket == null) return;
        chunks.externalCount -= bucket.externalEmitters.size();
        bucket.externalEmitters = Collections.emptySet();
        removeEmpty(level, chunks, key, bucket);
    }

    /** Iterate a defensive snapshot of every native and external emitter. */
    public static void forEach(final Level level, final BiConsumer<Level, BlockPos> callback) {
        for (final BlockPos pos : snapshot(level)) callback.accept(level, pos);
    }

    /** Full union snapshot retained for commands and diagnostics. */
    public static synchronized Set<BlockPos> snapshot(final Level level) {
        final LevelIndex chunks = BY_LEVEL.get(level);
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
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptySet();
        final Set<BlockPos> result = new HashSet<>();
        for (final ChunkBucket bucket : chunks.values()) result.addAll(bucket.nativeEmitters);
        return result;
    }

    /** Occupied chunks within the same square expansion used by fluid discovery.
     * Ordered rows retain anchor order, then ascending X/Z, including overlap
     * deduplication. Only intersecting occupied buckets are visited. */
    public static synchronized Set<Long> occupiedChunksNear(final Level level,
            final Set<Long> anchors, final int radiusChunks, final boolean external) {
        if (radiusChunks < 0) throw new IllegalArgumentException("Negative chunk radius");
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (anchors.isEmpty() || chunks == null
                || (external ? chunks.externalCount : chunks.nativeCount) == 0) return Set.of();
        final Set<Long> result = new LinkedHashSet<>();
        for (final long anchor : anchors) {
            final int x = ChunkPos.getX(anchor), z = ChunkPos.getZ(anchor);
            for (final var row : chunks.rows.subMap(x - radiusChunks, true, x + radiusChunks, true).entrySet()) {
                for (final var entry : row.getValue().subMap(z - radiusChunks, true, z + radiusChunks, true).entrySet()) {
                    if (level instanceof net.minecraft.server.level.ServerLevel server) {
                        PerformanceDiagnostics.record(server, PerformanceDiagnostics.Work.EMITTER_BUCKETS_INSPECTED, 1);
                    }
                    final ChunkBucket bucket = entry.getValue();
                    if (!(external ? bucket.externalEmitters : bucket.nativeEmitters).isEmpty()) {
                        result.add(ChunkPos.asLong(row.getKey(), entry.getKey()));
                    }
                }
            }
        }
        return result;
    }

    /** Native positions from an explicit target-local chunk set. */
    public static synchronized Set<BlockPos> snapshotNativeInChunks(
            final Level level, final Collection<Long> chunkKeys) {
        if (chunkKeys.isEmpty()) return Collections.emptySet();
        final LevelIndex chunks = BY_LEVEL.get(level);
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
        final LevelIndex chunks = BY_LEVEL.get(level);
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
        final LevelIndex chunks = BY_LEVEL.get(level);
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

    /** Bounded defensive candidate list in caller chunk order. A set of chunk
     * keys guarantees uniqueness: each position belongs to exactly one bucket,
     * so no intermediate position set is necessary. */
    public static synchronized java.util.List<BlockPos> snapshotExternalListInChunks(
            final Level level, final Set<Long> chunkKeys, final int limit) {
        if (limit <= 0 || chunkKeys.isEmpty()) return java.util.List.of();
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.externalCount == 0) return java.util.List.of();
        final java.util.List<BlockPos> result = new java.util.ArrayList<>(Math.min(limit, 256));
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
        if (limit <= 0 || !hasExternal(level)) return Collections.emptySet();
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
        final LevelIndex chunks = BY_LEVEL.get(level);
        if (chunks == null || chunks.nativeCount == 0) return Collections.emptySet();
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
        final LevelIndex index = BY_LEVEL.get(level);
        return index == null ? 0 : index.externalCount;
    }

    public static synchronized boolean hasNative(final Level level) {
        final LevelIndex index = BY_LEVEL.get(level);
        return index != null && index.nativeCount > 0;
    }

    public static synchronized boolean hasExternal(final Level level) {
        final LevelIndex index = BY_LEVEL.get(level);
        return index != null && index.externalCount > 0;
    }

    private static ChunkBucket bucket(final Level level, final long chunkKey) {
        return BY_LEVEL.computeIfAbsent(level, ignored -> new LevelIndex())
                .getOrCreate(chunkKey);
    }

    private static void removeEmpty(final Level level, final LevelIndex chunks,
                                    final long key, final ChunkBucket bucket) {
        if (bucket.isEmpty()) {
            chunks.remove(key);
            final int x = ChunkPos.getX(key);
            final var row = chunks.rows.get(x);
            if (row != null) {
                row.remove(ChunkPos.getZ(key));
                if (row.isEmpty()) chunks.rows.remove(x);
            }
        }
        if (chunks.isEmpty()) BY_LEVEL.remove(level);
    }
}
