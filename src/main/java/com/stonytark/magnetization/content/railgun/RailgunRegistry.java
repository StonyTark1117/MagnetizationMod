package com.stonytark.magnetization.content.railgun;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-level set of currently-loaded Railgun emitter positions. Emitter BEs
 * register in {@code onLoad} and unregister in {@code setRemoved}; the
 * {@link RailgunHandler} iterates a snapshot each tick to pair rails and drive
 * arcs without walking every BE in view distance. Mirrors
 * {@link com.stonytark.magnetization.physics.EmitterRegistry}.
 */
public final class RailgunRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> ACTIVE_BY_LEVEL = new WeakHashMap<>();

    private RailgunRegistry() {}

    public static synchronized void register(final Level level, final BlockPos pos) {
        ACTIVE_BY_LEVEL.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static synchronized void unregister(final Level level, final BlockPos pos) {
        final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) ACTIVE_BY_LEVEL.remove(level);
    }

    public static int size(final Level level) {
        synchronized (RailgunRegistry.class) {
            final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? 0 : set.size();
        }
    }

    /** Read-only snapshot of all emitter positions for the level. */
    public static Set<BlockPos> snapshot(final Level level) {
        synchronized (RailgunRegistry.class) {
            final Set<BlockPos> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? Collections.emptySet() : new HashSet<>(set);
        }
    }
}
