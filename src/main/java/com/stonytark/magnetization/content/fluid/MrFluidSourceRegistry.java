package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Per-level set of MR-fluid SOURCE positions, so {@link MrFluidHardenHandler}
 *  can find MR fluid cheaply (vs scanning a magnet's field volume). */
public final class MrFluidSourceRegistry {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private MrFluidSourceRegistry() {}
    public static void add(final Level level, final BlockPos pos) {
        BY_LEVEL.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }
    public static void remove(final Level level, final BlockPos pos) {
        final Set<BlockPos> s = BY_LEVEL.get(level);
        if (s != null) s.remove(pos);
    }
    public static Set<BlockPos> snapshot(final Level level) {
        final Set<BlockPos> s = BY_LEVEL.get(level);
        return s == null ? Set.of() : new HashSet<>(s);
    }
}
