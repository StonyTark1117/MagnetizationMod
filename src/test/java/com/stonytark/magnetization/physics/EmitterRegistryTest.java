package com.stonytark.magnetization.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
}
