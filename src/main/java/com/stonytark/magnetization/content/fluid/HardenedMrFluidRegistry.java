package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Per-level set of Hardened MR Fluid block positions, so the handler can revert
 *  them to MR fluid once they leave a magnetic field. */
public final class HardenedMrFluidRegistry {
    private static final WeakHashMap<Level, Set<BlockPos>> BY_LEVEL = new WeakHashMap<>();
    private HardenedMrFluidRegistry() {}
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
