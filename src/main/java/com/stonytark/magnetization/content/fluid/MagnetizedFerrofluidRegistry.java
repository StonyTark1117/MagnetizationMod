package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-level set of magnetized-ferrofluid <em>source</em> positions and their
 * polarity. Mirrors {@code EmitterRegistry}, but for a fluid that has no block
 * entity: {@link MagnetizedFerrofluidBlock} adds source positions as they're
 * placed, and {@link MagnetizedFerrofluidFieldHandler} walks the set each tick
 * to emit a weak field from each (pruning any entry that's no longer a
 * magnetized source, so the set self-heals across fluid flow).
 */
public final class MagnetizedFerrofluidRegistry {

    private static final Map<Level, Map<BlockPos, MagneticPolarity>> BY_LEVEL = new WeakHashMap<>();

    public static void add(final Level level, final BlockPos pos, final MagneticPolarity polarity) {
        BY_LEVEL.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).put(pos.immutable(), polarity);
    }

    public static void remove(final Level level, final BlockPos pos) {
        final Map<BlockPos, MagneticPolarity> set = BY_LEVEL.get(level);
        if (set != null) set.remove(pos);
    }

    /** Live view of the positions in this level (empty if none). */
    public static Map<BlockPos, MagneticPolarity> forLevel(final Level level) {
        return BY_LEVEL.getOrDefault(level, Map.of());
    }

    private MagnetizedFerrofluidRegistry() {}
}
