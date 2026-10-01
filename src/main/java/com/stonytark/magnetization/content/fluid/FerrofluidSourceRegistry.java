package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-level set of PLAIN ferrofluid <em>source</em> positions. Mirrors
 * {@link MagnetizedFerrofluidRegistry} (which tracks magnetized sources + their
 * pole) so {@link FerrofluidCreepHandler} can iterate the small set of fluid
 * sources and test each against active fields — instead of cube-scanning a
 * magnet's (possibly huge, up to 128-block) field volume for fluid.
 *
 * <p>{@link FerrofluidBlock} adds/removes entries on place/remove; the creep
 * handler prunes any entry that's no longer a source (a drained/flowed cell).
 */
public final class FerrofluidSourceRegistry {

    private static final WeakHashMap<Level, Sources> BY_LEVEL = new WeakHashMap<>();
    private static final class Sources {
        final Set<BlockPos> positions = new HashSet<>();
        FluidTargetRegions regions;
    }

    private FerrofluidSourceRegistry() {}

    public static void add(final Level level, final BlockPos pos) {
        final Sources sources = BY_LEVEL.computeIfAbsent(level, l -> new Sources());
        if (sources.positions.add(pos.immutable())) sources.regions = null;
    }

    public static void remove(final Level level, final BlockPos pos) {
        final Sources sources = BY_LEVEL.get(level);
        if (sources != null && sources.positions.remove(pos)) sources.regions = null;
    }

    /** Snapshot of the plain-source positions in this level (safe to mutate during iteration). */
    public static Set<BlockPos> snapshot(final Level level) {
        final Sources sources = BY_LEVEL.get(level);
        return sources == null ? Set.of() : new HashSet<>(sources.positions);
    }
    public static FluidTargetRegions targetRegions(final Level level) {
        final Sources sources = BY_LEVEL.get(level);
        if (sources == null) return FluidTargetRegions.EMPTY;
        if (sources.regions == null) {
            // Match the former per-tick defensive HashSet iteration exactly,
            // including its capacity after removals and the target cursor order.
            sources.regions = FluidTargetRegions.of(new HashSet<>(sources.positions));
        }
        return sources.regions;
    }
}
