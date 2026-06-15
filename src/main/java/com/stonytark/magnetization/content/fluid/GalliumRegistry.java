package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-level set of gallium <em>source</em> positions, so {@link GalliumLorentzHandler}
 * can iterate the small set of placed gallium cells (and test each for a redstone
 * current inside a magnetic field) instead of scanning the world. {@link GalliumBlock}
 * adds/removes entries on place/remove; the handler prunes any stale entry.
 */
public final class GalliumRegistry {

    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();

    private GalliumRegistry() {}

    public static void add(final Level level, final BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(final Level level, final BlockPos pos) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        if (set != null) set.remove(pos);
    }

    public static Set<BlockPos> snapshot(final Level level) {
        final Set<BlockPos> set = BY_LEVEL.get(level);
        return set == null ? Set.of() : new HashSet<>(set);
    }
}
