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

    private static final WeakHashMap<Level, java.util.Map<BlockPos, RailgunEmitterBlockEntity>> ACTIVE_BY_LEVEL = new WeakHashMap<>();

    private RailgunRegistry() {}

    public static synchronized void register(final Level level, final BlockPos pos) {
        // Kept for source compatibility with older integrations; normal emitters
        // use the BE overload so Sable plot emitters can be resolved even when a
        // vanilla level lookup does not see their plot-local block position.
        ACTIVE_BY_LEVEL.computeIfAbsent(level, l -> new java.util.HashMap<>())
                .put(pos.immutable(), null);
    }

    public static synchronized void register(final Level level, final RailgunEmitterBlockEntity emitter) {
        ACTIVE_BY_LEVEL.computeIfAbsent(level, l -> new java.util.HashMap<>())
                .put(emitter.getBlockPos().immutable(), emitter);
    }

    public static synchronized void unregister(final Level level, final BlockPos pos) {
        final java.util.Map<BlockPos, RailgunEmitterBlockEntity> set = ACTIVE_BY_LEVEL.get(level);
        if (set == null) return;
        set.remove(pos);
        if (set.isEmpty()) ACTIVE_BY_LEVEL.remove(level);
    }

    public static int size(final Level level) {
        synchronized (RailgunRegistry.class) {
            final java.util.Map<BlockPos, RailgunEmitterBlockEntity> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? 0 : set.size();
        }
    }

    /** Read-only snapshot of all emitter positions for the level. */
    public static Set<BlockPos> snapshot(final Level level) {
        synchronized (RailgunRegistry.class) {
            final java.util.Map<BlockPos, RailgunEmitterBlockEntity> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? Collections.emptySet() : new HashSet<>(set.keySet());
        }
    }

    /** Resolve an emitter from the registration snapshot, including Sable plots. */
    public static RailgunEmitterBlockEntity find(final Level level, final BlockPos pos) {
        synchronized (RailgunRegistry.class) {
            final java.util.Map<BlockPos, RailgunEmitterBlockEntity> set = ACTIVE_BY_LEVEL.get(level);
            return set == null ? null : set.get(pos);
        }
    }
}
