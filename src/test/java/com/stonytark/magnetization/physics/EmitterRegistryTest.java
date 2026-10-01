package com.stonytark.magnetization.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lifecycle invariants for the per-level emitter index. The registry's job is
 *  to give the client tick scanner an O(emitters) iterable instead of walking
 *  every loaded BE in chunks within view distance. A bug here either leaks
 *  positions (stale forces / phantom emitters) or loses them (HUD shows
 *  nothing where an emitter is active).
 *
 *  <p>Tests use {@code null} as the Level key — {@link Level} is a heavy MC
 *  class to construct, but the registry only uses it as a {@code WeakHashMap}
 *  key (identity), and {@code WeakHashMap} accepts null keys. A real
 *  multi-level test would need MC bootstrap. */
class EmitterRegistryTest {

    private static final Level LVL = null;
    private static final BlockPos A = new BlockPos(1, 2, 3);
    private static final BlockPos B = new BlockPos(4, 5, 6);
    private static final BlockPos C = new BlockPos(-7, 8, -9);

    @AfterEach
    void clearBucket() {
        // Leave the registry empty between tests so order doesn't matter.
        EmitterRegistry.forEach(LVL, (lvl, pos) -> EmitterRegistry.unregister(lvl, pos));
        for (final BlockPos pos : EmitterRegistry.snapshotExternal(LVL)) {
            EmitterRegistry.unregisterExternal(LVL, pos);
        }
    }

    @Test
    void pointQueriesMatchExpandedReferenceAtBoundariesAndAfterRemoval() {
        var random = new java.util.Random(571);
        for (int i = 0; i < 2000; i++) {
            var p = new BlockPos(random.nextInt(2048) - 1024, i, random.nextInt(2048) - 1024);
            EmitterRegistry.register(LVL, p);
            EmitterRegistry.registerExternal(LVL, p);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int center : new int[]{-513, -512, -17, -16, -1, 0, 15, 16, 511, 512}) {
                var target = new BlockPos(center, 0, -center);
                for (int radius : new int[]{0, 1, 16, 32, 512}) {
                    var keys = new java.util.LinkedHashSet<Long>();
                    for (int x = Math.floorDiv(target.getX() - radius, 16); x <= Math.floorDiv(target.getX() + radius, 16); x++)
                        for (int z = Math.floorDiv(target.getZ() - radius, 16); z <= Math.floorDiv(target.getZ() + radius, 16); z++)
                            keys.add(ChunkPos.asLong(x, z));
                    assertEquals(new java.util.ArrayList<>(EmitterRegistry.snapshotNativeInChunks(LVL, keys)),
                            new java.util.ArrayList<>(EmitterRegistry.snapshotNativeNear(LVL, target, radius)));
                    for (int cap : new int[]{0, 1, 7, 256, Integer.MAX_VALUE})
                        assertEquals(new java.util.ArrayList<>(EmitterRegistry.snapshotExternalInChunks(LVL, keys, cap)),
                                new java.util.ArrayList<>(EmitterRegistry.snapshotExternalNear(LVL, target, radius, cap)));
                }
            }
            EmitterRegistry.snapshotNative(LVL).stream().filter(p -> p.getY() % 2 == 0).toList()
                    .forEach(p -> { EmitterRegistry.unregister(LVL, p); EmitterRegistry.unregisterExternal(LVL, p); });
        }
    }

    @Test
    void occupiedQueriesPreserveExpandedCoverageAndIterationOrder() {
        final var random = new java.util.Random(956);
        for (int i = 0; i < 1500; i++) {
            final var pos = new BlockPos(random.nextInt(4096) - 2048, i % 200, random.nextInt(4096) - 2048);
            EmitterRegistry.registerExternal(LVL, pos);
            if (i % 3 == 0) EmitterRegistry.register(LVL, pos);
        }
        final var anchors = new java.util.LinkedHashSet<Long>(java.util.List.of(
                ChunkPos.asLong(-1, -1), ChunkPos.asLong(1, 1), ChunkPos.asLong(-60, 70)));
        for (int radius : new int[]{0, 2, 32}) {
            final var expanded = new java.util.LinkedHashSet<Long>();
            for (long anchor : anchors) {
                for (int x = ChunkPos.getX(anchor) - radius; x <= ChunkPos.getX(anchor) + radius; x++) {
                    for (int z = ChunkPos.getZ(anchor) - radius; z <= ChunkPos.getZ(anchor) + radius; z++) {
                        expanded.add(ChunkPos.asLong(x, z));
                    }
                }
            }
            final var external = EmitterRegistry.occupiedChunksNear(LVL, anchors, radius, true);
            final var nativeKeys = EmitterRegistry.occupiedChunksNear(LVL, anchors, radius, false);
            assertEquals(new java.util.ArrayList<>(EmitterRegistry.snapshotExternalInChunks(LVL, expanded, Integer.MAX_VALUE)),
                    new java.util.ArrayList<>(EmitterRegistry.snapshotExternalInChunks(LVL, external, Integer.MAX_VALUE)));
            assertEquals(new java.util.ArrayList<>(EmitterRegistry.snapshotNativeInChunks(LVL, expanded)),
                    new java.util.ArrayList<>(EmitterRegistry.snapshotNativeInChunks(LVL, nativeKeys)));
        }
        assertTrue(EmitterRegistry.occupiedChunksNear(LVL, Set.of(ChunkPos.asLong(10000, 10000)), 32, false).isEmpty());
    }

    @Test
    void directCandidatesRetainOldOrderLimitsAndDefensiveLifecycle() {
        final java.util.LinkedHashSet<Long> keys = new java.util.LinkedHashSet<>();
        for (int x : new int[]{-32, 32, 0, -32}) {
            keys.add(ChunkPos.asLong(x >> 4, 0));
            for (int y = 0; y < 8; y++) EmitterRegistry.registerExternal(LVL, new BlockPos(x, y, 0));
        }
        keys.add(ChunkPos.asLong(999, 999));
        for (int limit : new int[]{-1, 0, 1, 7, 8, 9, 23, 24, 25, 256}) {
            assertEquals(new java.util.ArrayList<>(EmitterRegistry.snapshotExternalInChunks(LVL, keys, limit)),
                    EmitterRegistry.snapshotExternalListInChunks(LVL, keys, limit));
        }
        final var snapshot = EmitterRegistry.snapshotExternalListInChunks(LVL, keys, 256);
        EmitterRegistry.dropExternalChunk(LVL, new ChunkPos(-2, 0));
        assertEquals(24, snapshot.size());
        snapshot.clear();
        assertEquals(16, EmitterRegistry.externalSize(LVL));
        assertEquals(16, EmitterRegistry.snapshotExternalListInChunks(LVL, keys, 256).size());
        assertTrue(EmitterRegistry.snapshotExternalListInChunks(LVL, Set.of(), 256).isEmpty());
    }

    @Test
    void emptyLevelReportsSizeZero() {
        assertEquals(0, EmitterRegistry.size(LVL));
        assertTrue(EmitterRegistry.snapshot(LVL).isEmpty());
    }

    @Test
    void registerIncrementsSize() {
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.register(LVL, B);
        assertEquals(2, EmitterRegistry.size(LVL));
    }

    @Test
    void registerIsIdempotentForSamePos() {
        // The underlying HashSet de-duplicates — double-registering the same
        // pos shouldn't inflate the count (chunk reloads, etc.).
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.register(LVL, A);
        assertEquals(1, EmitterRegistry.size(LVL));
    }

    @Test
    void unregisterRemovesPosAndShrinksSize() {
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.register(LVL, B);
        EmitterRegistry.unregister(LVL, A);
        assertEquals(1, EmitterRegistry.size(LVL));
        final Set<BlockPos> snap = EmitterRegistry.snapshot(LVL);
        assertEquals(Set.of(B), snap);
    }

    @Test
    void unregisteringLastPosDropsTheLevelBucket() {
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.unregister(LVL, A);
        // Per impl: when the inner set empties, the level entry is removed.
        // size() returns 0 either way, but the snapshot confirms no stale set.
        assertEquals(0, EmitterRegistry.size(LVL));
        assertTrue(EmitterRegistry.snapshot(LVL).isEmpty());
    }

    @Test
    void forEachVisitsEveryRegisteredPosExactlyOnce() {
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.register(LVL, B);
        EmitterRegistry.register(LVL, C);

        final Set<BlockPos> visited = new HashSet<>();
        final AtomicInteger calls = new AtomicInteger();
        EmitterRegistry.forEach(LVL, (lvl, pos) -> {
            visited.add(pos);
            calls.incrementAndGet();
        });

        assertEquals(3, calls.get());
        assertEquals(Set.of(A, B, C), visited);
    }

    @Test
    void forEachOnEmptyLevelDoesNothing() {
        final AtomicInteger calls = new AtomicInteger();
        EmitterRegistry.forEach(LVL, (lvl, pos) -> calls.incrementAndGet());
        assertEquals(0, calls.get());
    }

    @Test
    void snapshotIsDefensiveCopy() {
        // Mutating the snapshot must not mutate the registry — otherwise
        // callers could accidentally corrupt the bucket.
        EmitterRegistry.register(LVL, A);
        final Set<BlockPos> snap = EmitterRegistry.snapshot(LVL);
        snap.add(B);
        snap.remove(A);
        assertEquals(1, EmitterRegistry.size(LVL));
        assertEquals(Set.of(A), EmitterRegistry.snapshot(LVL));
    }

    @Test
    void unregisterAcceptsMissingPosWithoutThrowing() {
        // Idempotent unregister: called twice or for a never-registered pos
        // should be a no-op, not a crash. Important: BE.setRemoved may fire
        // for BEs that never finished onLoad.
        EmitterRegistry.unregister(LVL, A); // never registered → safe
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.unregister(LVL, A);
        EmitterRegistry.unregister(LVL, A); // already gone → safe
        assertEquals(0, EmitterRegistry.size(LVL));
    }

    @Test
    void externalChunkReplacementIsAtomicAndPreservesNativeEmitters() {
        final ChunkPos chunk = new ChunkPos(A);
        EmitterRegistry.register(LVL, A);
        EmitterRegistry.replaceExternalChunk(LVL, chunk, Set.of(A, new BlockPos(2, 3, 4)));
        assertEquals(2, EmitterRegistry.externalSize(LVL));

        final BlockPos replacement = new BlockPos(8, 9, 10);
        EmitterRegistry.replaceExternalChunk(LVL, chunk, Set.of(replacement));
        assertEquals(Set.of(replacement), EmitterRegistry.snapshotExternal(LVL));
        assertTrue(EmitterRegistry.snapshot(LVL).contains(A),
                "Replacing an external scan must not erase a native BE registration");
    }

    @Test
    void unloadingChunkDropsOnlyItsExternalBucket() {
        final BlockPos sameChunk = new BlockPos(2, 3, 4);
        final BlockPos otherChunk = new BlockPos(40, 5, 40);
        EmitterRegistry.replaceExternalChunk(LVL, new ChunkPos(A), Set.of(A, sameChunk));
        EmitterRegistry.replaceExternalChunk(LVL, new ChunkPos(otherChunk), Set.of(otherChunk));

        EmitterRegistry.dropExternalChunk(LVL, new ChunkPos(A));

        assertEquals(Set.of(otherChunk), EmitterRegistry.snapshotExternal(LVL));
    }

    @Test
    void spatialExternalQueryExcludesUnrelatedChunksAndHonorsLimit() {
        final BlockPos nearbyA = new BlockPos(1, 2, 1);
        final BlockPos nearbyB = new BlockPos(17, 2, 1);
        final BlockPos far = new BlockPos(1600, 2, 1600);
        EmitterRegistry.registerExternal(LVL, nearbyA);
        EmitterRegistry.registerExternal(LVL, nearbyB);
        EmitterRegistry.registerExternal(LVL, far);

        final Set<BlockPos> near = EmitterRegistry.snapshotExternalNear(LVL, BlockPos.ZERO, 32, 8);
        assertEquals(Set.of(nearbyA, nearbyB), near);
        assertEquals(1, EmitterRegistry.snapshotExternalNear(LVL, BlockPos.ZERO, 32, 1).size());
    }
    @Test
    void categoryCountsTrackDuplicateRegistrationReplacementAndUnload() {
        final java.util.Random random = new java.util.Random(451);
        for (int step = 0; step < 1000; step++) {
            final BlockPos pos = new BlockPos(random.nextInt(80) - 40, 5, random.nextInt(80) - 40);
            switch (random.nextInt(6)) {
                case 0 -> { EmitterRegistry.register(LVL, pos); EmitterRegistry.register(LVL, pos); }
                case 1 -> EmitterRegistry.unregister(LVL, pos);
                case 2 -> { EmitterRegistry.registerExternal(LVL, pos); EmitterRegistry.registerExternal(LVL, pos); }
                case 3 -> EmitterRegistry.unregisterExternal(LVL, pos);
                case 4 -> EmitterRegistry.replaceExternalChunk(LVL, new ChunkPos(pos),
                        Set.of(pos, pos.above(), pos.offset(48, 0, 0)));
                case 5 -> EmitterRegistry.dropExternalChunk(LVL, new ChunkPos(pos));
            }
            assertEquals(!EmitterRegistry.snapshotNative(LVL).isEmpty(), EmitterRegistry.hasNative(LVL));
            assertEquals(!EmitterRegistry.snapshotExternal(LVL).isEmpty(), EmitterRegistry.hasExternal(LVL));
            assertEquals(EmitterRegistry.snapshotExternal(LVL).size(), EmitterRegistry.externalSize(LVL));
            final Set<Long> nativeKeys = new HashSet<>(), externalKeys = new HashSet<>();
            EmitterRegistry.snapshotNative(LVL).forEach(p -> nativeKeys.add(ChunkPos.asLong(p)));
            EmitterRegistry.snapshotExternal(LVL).forEach(p -> externalKeys.add(ChunkPos.asLong(p)));
            assertEquals(nativeKeys, EmitterRegistry.occupiedChunksNear(LVL, Set.of(0L), 4, false));
            assertEquals(externalKeys, EmitterRegistry.occupiedChunksNear(LVL, Set.of(0L), 4, true));
        }
        clearBucket();
        assertEquals(false, EmitterRegistry.hasNative(LVL));
        assertEquals(false, EmitterRegistry.hasExternal(LVL));
    }
}
