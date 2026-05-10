package com.stonytark.magnetization.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Per-level set of currently-loaded emitter block positions. BEs register
 * themselves in {@code onLoad} and unregister in {@code setRemoved}; the client
 * scanner and any future tooling iterate this set instead of walking every BE
 * in chunks within view distance.
 *
 * <p>Indexed weakly by {@link Level} so unloaded levels don't pin the entries.
 * Server and client maintain independent maps because {@link Level} instances
 * are side-specific.
 */
public final class EmitterRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> ACTIVE_BY_LEVEL = new WeakHashMap<>();

    private EmitterRegistry() {}

    public static synchronized void register(final Level level, final BlockPos pos) {
        ACTIVE_BY_LEVEL.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void unregister(final Level level, final BlockPos pos) {
        final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) ACTIVE_BY_LEVEL.remove(level);
    }

    /**
     * Iterate every registered emitter pos for the given level. The callback
     * receives the level and pos. Iteration is over a snapshot to avoid
     * concurrent-modification trouble if a BE registers/unregisters mid-iteration.
     */
    public static void forEach(final Level level, final BiConsumer<Level, BlockPos> callback) {
        final Set<BlockPos> snapshot;
        synchronized (EmitterRegistry.class) {
            final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
            if (set == null || set.isEmpty()) return;
            snapshot = new HashSet<>(set);
        }
        for (BlockPos pos : snapshot) callback.accept(level, pos);
    }

    public static int size(final Level level) {
        synchronized (EmitterRegistry.class) {
            final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? 0 : set.size();
        }
    }

    /** Read-only snapshot of all positions for testing / debugging. */
    public static Set<BlockPos> snapshot(final Level level) {
        synchronized (EmitterRegistry.class) {
            final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? Collections.emptySet() : new HashSet<>(set);
        }
    }
}
