package com.stonytark.magnetization.worldgen;

/**
 * Rarity tier for our custom overworld biomes. Translates user-facing rarity
 * into TerraBlender climate-parameter span widths inside each region's
 * {@code addBiomes()}. Narrower spans = the biome only matches a smaller slice
 * of the multi-noise parameter space, so it spawns in fewer chunks.
 *
 * <p>{@link AnomalyRegion} and {@link PetrifiedForestRegion} each read their
 * configured rarity from {@link com.stonytark.magnetization.config.MagConfig}
 * and pick parameter spans accordingly.
 */
public enum BiomeRarity {
    /** Wide spans — biome shows up multiple times per several-thousand-block traversal. */
    COMMON,
    /** Moderately narrow spans — encounter once per ~10k blocks typically. */
    RARE,
    /** Narrow spans — uncommon enough that finding one is a notable event. */
    VERY_RARE,
    /** Single-point spans — biome occupies one tiny slice of the parameter
     *  space; you may go tens of thousands of blocks without encountering it. */
    EXTREMELY_RARE
}
