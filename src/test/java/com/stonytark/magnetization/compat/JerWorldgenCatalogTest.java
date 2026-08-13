package com.stonytark.magnetization.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stonytark.magnetization.compat.jer.JerWorldgenCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps JER's complete chart plan synchronized with shipping worldgen and loot data. */
class JerWorldgenCatalogTest {
    private static final Path MAIN = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/generated/resources");

    @Test
    void oreCatalogInventoriesEveryConfiguredAndPlacedOreFeature() throws IOException {
        final Set<String> configuredFeatures;
        try (var files = Files.list(MAIN.resolve("data/magnetization/worldgen/configured_feature"))) {
            configuredFeatures = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("ore_") && name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .collect(Collectors.toUnmodifiableSet());
        }
        assertEquals(configuredFeatures, JerWorldgenCatalog.oreFamilies().stream()
                .map(JerWorldgenCatalog.OreFamily::configuredFeature)
                .collect(Collectors.toUnmodifiableSet()),
                "JER ore-family inventory drifted from configured features");

        final Set<String> placedFeatures;
        try (var files = Files.list(MAIN.resolve("data/magnetization/worldgen/placed_feature"))) {
            placedFeatures = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("ore_") && name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - 5))
                    .collect(Collectors.toUnmodifiableSet());
        }
        assertEquals(placedFeatures, JerWorldgenCatalog.oreFamilies().stream()
                .flatMap(family -> family.placements().stream())
                .map(JerWorldgenCatalog.PlacementBand::placedFeature)
                .collect(Collectors.toUnmodifiableSet()),
                "JER distribution-band inventory drifted from placed ore features");

        for (final JerWorldgenCatalog.OreFamily family : JerWorldgenCatalog.oreFamilies()) {
            assertOreFamily(family);
        }
    }

    @Test
    void naturalResourceCatalogMatchesFeatureRatesRangesAndLoot() throws IOException {
        assertEquals(Set.of("helium_3_geode", "helium_3_geode_end", "magnetic_gravel_disk",
                        "anomaly_lodestone_cluster", "meteorite_core", "petrified_forest_trees",
                        "helium_pocket", "radon_pocket"),
                JerWorldgenCatalog.naturalResources().stream()
                        .map(JerWorldgenCatalog.Chart::source).collect(Collectors.toUnmodifiableSet()),
                "JER must inventory every non-ore natural Magnetization resource");

        for (final JerWorldgenCatalog.Chart chart : JerWorldgenCatalog.naturalResources()) {
            assertFalse(chart.bands().isEmpty(), () -> chart.source() + " has no JER distribution");
            final JerWorldgenCatalog.ChartBand band = chart.bands().getFirst();
            if (chart.source().equals("helium_pocket") || chart.source().equals("radon_pocket")) {
                assertGasPocketDefaults(chart, band);
                continue;
            }
            final JsonObject placed = json("data/magnetization/worldgen/placed_feature/"
                    + chart.source() + ".json");
            final JsonObject height = placementOrNull(placed, "minecraft:height_range");
            if (height != null) {
                final JsonObject range = height.getAsJsonObject("height");
                assertEquals(band.minY(), absolute(range, "min_inclusive"));
                assertEquals(band.maxY(), absolute(range, "max_inclusive"));
            }
            final JsonObject rarity = placementOrNull(placed, "minecraft:rarity_filter");
            final JsonObject count = placementOrNull(placed, "minecraft:count");
            final float expectedFrequency = rarity != null ? 1f / rarity.get("chance").getAsInt()
                    : count != null ? count.get("count").getAsFloat() : 1f;
            assertEquals(expectedFrequency, band.frequency(), 0.000001f,
                    chart.source() + " JER frequency drifted from its placed feature");
            assertNaturalLoot(chart);
        }
    }

    @Test
    void runtimeChartPlanContainsEveryVariantAndValidDistribution() {
        final var charts = JerWorldgenCatalog.charts();
        assertEquals(28, charts.size(), "Expected 20 ore-variant charts plus 8 natural-resource charts");
        final Set<String> displays = charts.stream().map(JerWorldgenCatalog.Chart::display)
                .collect(Collectors.toUnmodifiableSet());
        for (final JerWorldgenCatalog.OreFamily family : JerWorldgenCatalog.oreFamilies()) {
            family.stoneBlock().ifPresent(block -> assertTrue(displays.contains(block),
                    () -> "JER chart plan omits " + block));
            family.deepslateBlock().ifPresent(block -> assertTrue(displays.contains(block),
                    () -> "JER chart plan omits " + block));
        }
        for (final JerWorldgenCatalog.Chart chart : charts) {
            assertFalse(chart.drops().isEmpty(), () -> chart.source() + " has no displayed drops");
            assertFalse(chart.bands().isEmpty(), () -> chart.source() + " has no distribution bands");
            for (final JerWorldgenCatalog.ChartBand band : chart.bands()) {
                assertTrue(band.minY() <= band.maxY() && band.frequency() > 0,
                        () -> "Invalid JER distribution band for " + chart.source());
            }
        }
    }

    private static void assertOreFamily(final JerWorldgenCatalog.OreFamily family) throws IOException {
        final Set<String> expectedBlocks = new HashSet<>();
        family.stoneBlock().ifPresent(path -> expectedBlocks.add("magnetization:" + path));
        family.deepslateBlock().ifPresent(path -> expectedBlocks.add("magnetization:" + path));
        final JsonObject configured = json("data/magnetization/worldgen/configured_feature/"
                + family.configuredFeature() + ".json");
        final Set<String> actualBlocks = configured.getAsJsonObject("config").getAsJsonArray("targets")
                .asList().stream().map(target -> target.getAsJsonObject()
                        .getAsJsonObject("state").get("Name").getAsString())
                .collect(Collectors.toUnmodifiableSet());
        assertEquals(expectedBlocks, actualBlocks,
                family.configuredFeature() + " JER variants drifted from configured worldgen");

        for (final JerWorldgenCatalog.PlacementBand band : family.placements()) {
            final JsonObject placed = json("data/magnetization/worldgen/placed_feature/"
                    + band.placedFeature() + ".json");
            assertEquals("magnetization:" + family.configuredFeature(), placed.get("feature").getAsString());
            final JsonObject count = placement(placed, "minecraft:count");
            assertEquals(band.attemptsPerChunk(), count.get("count").getAsInt());
            final JsonObject rarity = placementOrNull(placed, "minecraft:rarity_filter");
            assertEquals(band.rarityDivisor(), rarity == null ? 1 : rarity.get("chance").getAsInt());
            final JsonObject height = placement(placed, "minecraft:height_range").getAsJsonObject("height");
            assertEquals(band.minY(), absolute(height, "min_inclusive"));
            assertEquals(band.maxY(), absolute(height, "max_inclusive"));
        }
        for (final String block : expectedBlocks) {
            final Set<String> loot = lootItems(block.substring("magnetization:".length()));
            assertTrue(loot.contains("magnetization:" + family.drop()),
                    () -> block + " loot no longer contains magnetization:" + family.drop());
        }
    }

    private static void assertNaturalLoot(final JerWorldgenCatalog.Chart chart) throws IOException {
        final String block = switch (chart.source()) {
            case "helium_3_geode", "helium_3_geode_end" -> "helium_3_geode";
            case "magnetic_gravel_disk" -> "magnetic_gravel";
            case "anomaly_lodestone_cluster" -> "lodestone_core";
            case "meteorite_core" -> "meteorite_core";
            case "petrified_forest_trees" -> "petrified_wood";
            default -> throw new AssertionError("Unhandled natural resource " + chart.source());
        };
        final Set<String> loot = lootItems(block);
        for (final JerWorldgenCatalog.Drop drop : chart.drops()) {
            assertTrue(loot.contains("magnetization:" + drop.item()),
                    () -> chart.source() + " loot no longer contains magnetization:" + drop.item());
        }
    }

    private static void assertGasPocketDefaults(final JerWorldgenCatalog.Chart chart,
                                                final JerWorldgenCatalog.ChartBand band) throws IOException {
        final String prefix = chart.source().startsWith("helium") ? "helium" : "radon";
        final String source = Files.readString(Path.of(
                "src/main/java/com/stonytark/magnetization/config/MagConfig.java"));
        assertEquals(configDefault(source, prefix + "PocketMinY"), band.minY());
        assertEquals(configDefault(source, prefix + "PocketMaxY"), band.maxY());
        assertEquals(1f / configDefault(source, prefix + "PocketRarity"), band.frequency(), 0.000001f);
        assertTrue(Files.isRegularFile(MAIN.resolve("data/magnetization/worldgen/configured_feature/"
                        + chart.source() + ".json")),
                () -> "Missing gas-pocket configured feature " + chart.source());
    }

    private static int configDefault(final String source, final String key) {
        final var matcher = Pattern.compile("defineInRange\\(\\\"" + Pattern.quote(key)
                + "\\\",\\s*(-?\\d+)").matcher(source);
        assertTrue(matcher.find(), () -> "Missing config default for " + key);
        return Integer.parseInt(matcher.group(1));
    }

    private static int absolute(final JsonObject range, final String endpoint) {
        return range.getAsJsonObject(endpoint).get("absolute").getAsInt();
    }

    private static JsonObject placement(final JsonObject placed, final String type) {
        final JsonObject value = placementOrNull(placed, type);
        if (value == null) throw new AssertionError("Missing placement modifier " + type);
        return value;
    }

    private static JsonObject placementOrNull(final JsonObject placed, final String type) {
        return placed.getAsJsonArray("placement").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(element -> type.equals(element.get("type").getAsString())).findFirst().orElse(null);
    }

    private static Set<String> lootItems(final String block) throws IOException {
        final Set<String> names = new HashSet<>();
        collectItemNames(json("data/magnetization/loot_table/blocks/" + block + ".json"), names);
        return names;
    }

    private static void collectItemNames(final JsonElement element, final Set<String> names) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectItemNames(child, names));
            return;
        }
        if (!element.isJsonObject()) return;
        final JsonObject object = element.getAsJsonObject();
        if (object.has("type") && "minecraft:item".equals(object.get("type").getAsString())
                && object.has("name")) names.add(object.get("name").getAsString());
        object.entrySet().forEach(entry -> collectItemNames(entry.getValue(), names));
    }

    private static JsonObject json(final String relative) throws IOException {
        final Path main = MAIN.resolve(relative);
        final Path generated = GENERATED.resolve(relative);
        final Path path = Files.isRegularFile(main) ? main : generated;
        assertTrue(Files.isRegularFile(path), () -> "Missing resource " + relative);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
