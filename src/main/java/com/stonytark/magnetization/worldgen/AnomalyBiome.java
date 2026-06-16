package com.stonytark.magnetization.worldgen;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Lookup helpers for the {@code magnetization:anomaly} biome — the rare patch
 * where compasses spin and emitters get a strength bonus.
 *
 * <p>Always gated on {@link MagConfig#ANOMALY_BIOME_ENABLED}; if the flag is off
 * the biome JSON still loads but is effectively dormant (no runtime effects).
 */
public final class AnomalyBiome {

    /** Strength multiplier applied to emitter output when the emitter sits inside the anomaly. */

    public static final ResourceKey<Biome> KEY =
            ResourceKey.create(Registries.BIOME, Magnetization.id("anomaly"));

    private AnomalyBiome() {}

    public static boolean enabled() {
        try {
            return MagConfig.ANOMALY_BIOME_ENABLED.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True if the position is inside the anomaly biome AND the feature is enabled. */
    public static boolean isAt(final Level level, final BlockPos pos) {
        if (!enabled()) return false;
        return level.getBiome(pos).is(KEY);
    }

    /** Hot-path variant for callers that have already gated on {@link #enabled()}.
     *  Skips the redundant config getter so loops over many positions / ships /
     *  entities don't pay it per iteration. */
    public static boolean isAtAssumeEnabled(final Level level, final BlockPos pos) {
        return level.getBiome(pos).is(KEY);
    }
}
