package com.stonytark.magnetization.compat.jer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registry-independent plan for every Magnetization resource shown by JER.
 *
 * <p>The plan mirrors configured features, placed features, custom feature
 * defaults, and block loot. Keeping it free of Minecraft and JER classes lets
 * unit tests compare the exact chart inventory and distribution inputs with
 * the shipping data before the runtime adapter resolves items.</p>
 */
public final class JerWorldgenCatalog {
    private static final float JER_ORE_CHANCE = 0.008f;
    private static final int VANILLA_IRON_ATTEMPTS = 90;
    private static final float SECONDARY_ROCK_MULTIPLIER = 0.05f;

    private static final List<OreFamily> ORE_FAMILIES = List.of(
            ore("ore_magnetite", "magnetite_ore", "deepslate_magnetite_ore", "raw_magnetite",
                    band("ore_magnetite", -48, 80, 16, 1, RockLayer.ANY),
                    band("ore_magnetite_peaks", 80, 200, 24, 1, RockLayer.STONE)),
            ore("ore_maghemite", "maghemite_ore", "deepslate_maghemite_ore", "raw_maghemite",
                    band("ore_maghemite", 40, 120, 14, 1, RockLayer.ANY),
                    band("ore_maghemite_deep_rare", -16, -1, 1, 4, RockLayer.DEEPSLATE)),
            ore("ore_pyrrhotite", "pyrrhotite_ore", "deepslate_pyrrhotite_ore", "raw_pyrrhotite",
                    band("ore_pyrrhotite", -32, 48, 12, 1, RockLayer.ANY)),
            ore("ore_hematite", "hematite_ore", "deepslate_hematite_ore", "raw_hematite",
                    band("ore_hematite", -40, 96, 16, 1, RockLayer.ANY)),
            ore("ore_titanomagnetite", "titanomagnetite_ore", "deepslate_titanomagnetite_ore",
                    "raw_titanomagnetite",
                    band("ore_titanomagnetite", -64, -8, 8, 1, RockLayer.ANY),
                    band("ore_titanomagnetite_shallow_rare", 0, 32, 1, 6, RockLayer.STONE)),
            ore("ore_lithium", "lithium_ore", "deepslate_lithium_ore", "raw_lithium",
                    band("ore_lithium", -32, 72, 7, 1, RockLayer.ANY)),
            ore("ore_bastnasite", "bastnasite_ore", "deepslate_bastnasite_ore",
                    "bastnasite_concentrate",
                    band("ore_bastnasite", -64, -32, 2, 1, RockLayer.ANY)),
            ore("ore_monazite", "monazite_ore", "deepslate_monazite_ore", "monazite_concentrate",
                    band("ore_monazite", -64, -40, 1, 1, RockLayer.ANY)),
            ore("ore_cobaltite", "cobaltite_ore", "deepslate_cobaltite_ore", "cobaltite_concentrate",
                    band("ore_cobaltite", -48, 8, 2, 1, RockLayer.ANY)),
            ore("ore_borax", "borax_ore", "deepslate_borax_ore", "boron_dust",
                    band("ore_borax", -32, 32, 3, 1, RockLayer.ANY))
    );

    private static final List<Chart> NATURAL_RESOURCES = List.of(
            chart("helium_3_geode", "helium_3_geode", DistributionKind.SQUARE, Dimension.OVERWORLD,
                    List.of(drop("helium_3_crystal", 1, 1, 1f)),
                    chartBand("helium_3_geode", -58, 16, 1f / 53f)),
            chart("helium_3_geode_end", "helium_3_geode", DistributionKind.SQUARE, Dimension.END,
                    List.of(drop("helium_3_crystal", 1, 1, 1f)),
                    chartBand("helium_3_geode_end", 0, 96, 1f / 40f)),
            chart("magnetic_gravel_disk", "magnetic_gravel", DistributionKind.UNDERWATER,
                    Dimension.OVERWORLD,
                    List.of(drop("raw_magnetite", 1, 1, 0.05f),
                            drop("raw_maghemite", 1, 1, 0.03f),
                            drop("magnetic_gravel", 1, 1, 0.92f)),
                    chartBand("magnetic_gravel_disk", 0, 0, 1f / 24f)),
            chart("anomaly_lodestone_cluster", "lodestone_core", DistributionKind.SURFACE,
                    Dimension.OVERWORLD, List.of(drop("lodestone_core", 1, 1, 1f)),
                    chartBand("anomaly_lodestone_cluster", 0, 0, 1f / 6f)),
            chart("meteorite_core", "meteorite_core", DistributionKind.SURFACE, Dimension.OVERWORLD,
                    List.of(drop("meteorite_core", 1, 1, 1f), drop("meteorite_fragment", 1, 3, 1f)),
                    chartBand("meteorite_core", 0, 0, 1f / 800f)),
            chart("petrified_forest_trees", "petrified_wood", DistributionKind.SURFACE,
                    Dimension.OVERWORLD, List.of(drop("petrified_wood", 1, 1, 1f)),
                    chartBand("petrified_forest_trees", 0, 0, 8f)),
            chart("helium_pocket", "helium_bucket", DistributionKind.SQUARE, Dimension.OVERWORLD,
                    List.of(drop("helium_bucket", 1, 1, 1f)),
                    chartBand("helium_pocket", -48, 16, 1f / 32f)),
            chart("radon_pocket", "radon_bucket", DistributionKind.SQUARE, Dimension.OVERWORLD,
                    List.of(drop("radon_bucket", 1, 1, 1f)),
                    chartBand("radon_pocket", -60, -16, 1f / 48f))
    );

    private JerWorldgenCatalog() {}

    public static List<OreFamily> oreFamilies() {
        return ORE_FAMILIES;
    }

    public static List<Chart> charts() {
        final List<Chart> charts = new ArrayList<>();
        for (final OreFamily family : ORE_FAMILIES) {
            family.stoneBlock().ifPresent(block -> charts.add(oreChart(family, block, RockLayer.STONE)));
            family.deepslateBlock().ifPresent(block -> charts.add(oreChart(family, block, RockLayer.DEEPSLATE)));
        }
        charts.addAll(NATURAL_RESOURCES);
        return List.copyOf(charts);
    }

    public static List<Chart> naturalResources() {
        return NATURAL_RESOURCES;
    }

    private static Chart oreChart(final OreFamily family, final String block, final RockLayer layer) {
        final List<ChartBand> bands = new ArrayList<>();
        for (final PlacementBand source : family.placements()) {
            if (source.layer() == layer) {
                bands.add(chartBand(source.placedFeature(), source.minY(), source.maxY(), oreChance(source)));
            } else if (source.layer() == RockLayer.ANY) {
                final int min = layer == RockLayer.STONE ? Math.max(0, source.minY()) : source.minY();
                final int max = layer == RockLayer.STONE ? source.maxY() : Math.min(-1, source.maxY());
                if (min <= max) bands.add(chartBand(source.placedFeature(), min, max, oreChance(source)));
            }
        }
        // Noise can leave a few stone cells below Y=0 (or deepslate above it).
        // If a configured feature targets that rock but no main band crosses the
        // nominal layer boundary, retain a small chart entry instead of hiding a
        // registered and genuinely generated variant from JER.
        if (bands.isEmpty()) {
            family.placements().stream().filter(source -> source.layer() == RockLayer.ANY).findFirst()
                    .ifPresent(source -> bands.add(chartBand(source.placedFeature(), source.minY(), source.maxY(),
                            oreChance(source) * SECONDARY_ROCK_MULTIPLIER)));
        }
        return new Chart(family.configuredFeature(), block,
                List.of(drop(family.drop(), 1, 1, 1f)), DistributionKind.SQUARE,
                Dimension.OVERWORLD, List.copyOf(bands));
    }

    private static float oreChance(final PlacementBand source) {
        return source.attemptsPerChunk() / (float) source.rarityDivisor()
                / VANILLA_IRON_ATTEMPTS * JER_ORE_CHANCE;
    }

    private static OreFamily ore(final String configuredFeature, final String stoneBlock,
                                  final String deepslateBlock, final String drop,
                                  final PlacementBand... placements) {
        return new OreFamily(configuredFeature, Optional.of(stoneBlock), Optional.of(deepslateBlock),
                drop, List.of(placements));
    }

    private static PlacementBand band(final String feature, final int minY, final int maxY,
                                      final int attempts, final int rarity, final RockLayer layer) {
        return new PlacementBand(feature, minY, maxY, attempts, rarity, layer);
    }

    private static Chart chart(final String source, final String display, final DistributionKind kind,
                               final Dimension dimension, final List<Drop> drops, final ChartBand... bands) {
        return new Chart(source, display, drops, kind, dimension, List.of(bands));
    }

    private static ChartBand chartBand(final String feature, final int minY, final int maxY,
                                       final float frequency) {
        return new ChartBand(feature, minY, maxY, frequency);
    }

    private static Drop drop(final String item, final int min, final int max, final float chance) {
        return new Drop(item, min, max, chance);
    }

    public enum RockLayer { ANY, STONE, DEEPSLATE }
    public enum DistributionKind { SQUARE, SURFACE, UNDERWATER }
    public enum Dimension { OVERWORLD, END }

    public record PlacementBand(String placedFeature, int minY, int maxY, int attemptsPerChunk,
                                int rarityDivisor, RockLayer layer) {}
    public record OreFamily(String configuredFeature, Optional<String> stoneBlock,
                            Optional<String> deepslateBlock, String drop,
                            List<PlacementBand> placements) {}
    public record ChartBand(String placedFeature, int minY, int maxY, float frequency) {}
    public record Drop(String item, int min, int max, float chance) {}
    public record Chart(String source, String display, List<Drop> drops, DistributionKind distribution,
                        Dimension dimension, List<ChartBand> bands) {}
}
