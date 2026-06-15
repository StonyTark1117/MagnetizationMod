package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-level set of ferrofluid cells that {@link FerrofluidCreepHandler} <em>added</em>
 * while reaching toward a magnet (as opposed to the fluid the player poured). Lets
 * the handler recede the creep path — removing these cells once no magnet drives
 * them — without touching the player's original fluid.
 *
 * <p>In-memory only (transient): a world reload simply leaves any creep cells as
 * ordinary fluid, which is harmless.
 */
public final class FerrofluidCreepRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();

    private FerrofluidCreepRegistry() {}

    public static void add(final Level level, final BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(final Level level, final BlockPos pos) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        if (set != null) set.remove(pos);
    }

    public static boolean contains(final Level level, final BlockPos pos) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        return set != null && set.contains(pos);
    }

    /** Snapshot of the creep cells in this level (safe to iterate + mutate during). */
    public static Set<BlockPos> snapshot(final Level level) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        return set == null ? Set.of() : new HashSet<>(set);
    }
}
