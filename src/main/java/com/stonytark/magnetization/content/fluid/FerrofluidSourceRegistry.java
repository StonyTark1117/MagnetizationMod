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

    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();

    private FerrofluidSourceRegistry() {}

    public static void add(final Level level, final BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(final Level level, final BlockPos pos) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        if (set != null) set.remove(pos);
    }

    /** Snapshot of the plain-source positions in this level (safe to mutate during iteration). */
    public static Set<BlockPos> snapshot(final Level level) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        return set == null ? Set.of() : new HashSet<>(set);
    }
}
