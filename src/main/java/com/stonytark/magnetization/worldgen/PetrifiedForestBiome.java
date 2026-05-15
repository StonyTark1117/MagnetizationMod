package com.stonytark.magnetization.worldgen;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Lookup helpers for the {@code magnetization:petrified_forest} biome — the
 * lightning-blasted, log-petrified inland forest where LIRM thrives.
 *
 * <p>Always gated on {@link MagConfig#PETRIFIED_FOREST_ENABLED}; if the flag is
 * off the biome JSON still loads but is effectively dormant (no runtime
 * effects like the frequent lightning).
 */
public final class PetrifiedForestBiome {

    public static final ResourceKey<Biome> KEY =
            ResourceKey.create(Registries.BIOME, Magnetization.id("petrified_forest"));

    private PetrifiedForestBiome() {}

    public static boolean enabled() {
        try {
            return MagConfig.PETRIFIED_FOREST_ENABLED.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True if the position is inside the petrified forest AND the feature is enabled. */
    public static boolean isAt(final Level level, final BlockPos pos) {
        if (!enabled()) return false;
        return level.getBiome(pos).is(KEY);
    }
}
