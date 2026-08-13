package com.stonytark.magnetization.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents the multi-stage rare-earth progression from regressing to partial or placeholder content. */
class RareEarthResourceCompletenessTest {
    private static final Path MAIN = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/generated/resources");

    @Test
    void oresAndIntermediatesHaveDedicatedAssetsAndTranslations() throws IOException {
        final JsonObject lang = json("assets/magnetization/lang/en_us.json");
        for (final String ore : List.of("bastnasite", "monazite", "cobaltite", "borax")) {
            for (final String prefix : List.of("", "deepslate_")) {
                final String id = prefix + ore + "_ore";
                resource("assets/magnetization/blockstates/" + id + ".json");
                final String model = resourceText("assets/magnetization/models/block/" + id + ".json");
                resource("assets/magnetization/models/item/" + id + ".json");
                resource("assets/magnetization/textures/block/" + id + ".png");
                resource("data/magnetization/loot_table/blocks/" + id + ".json");
                assertFalse(model.contains("minecraft:block/iron_ore")
                                || model.contains("minecraft:block/deepslate_iron_ore"),
                        () -> id + " still uses a vanilla placeholder texture");
                assertTrue(lang.has("block.magnetization." + id), () -> "Missing translation for " + id);
            }
        }

        for (final String id : List.of(
                "bastnasite_concentrate", "monazite_concentrate", "cobaltite_concentrate",
                "neodymium_oxide", "dysprosium_oxide", "samarium_oxide", "boron_dust",
                "neodymium_powder", "dysprosium_powder", "samarium_powder", "cobalt_powder",
                "neodymium_ingot", "samarium_ingot", "cobalt_ingot",
                "samarium_cobalt_alloy", "neodymium_alloy",
                "samarium_cobalt_plate", "neodymium_alloy_plate",
                "samarium_cobalt_magnet_blank", "neodymium_magnet_blank",
                "sintered_samarium_cobalt", "sintered_neodymium")) {
            final String model = resourceText("assets/magnetization/models/item/" + id + ".json");
            resource("assets/magnetization/textures/item/" + id + ".png");
            assertFalse(model.contains("minecraft:item/iron_"), () -> id + " still uses a vanilla placeholder model");
            assertTrue(lang.has("item.magnetization." + id), () -> "Missing translation for " + id);
        }
    }

    @Test
    void processingChainsReachPowdersIngotsAlloysPlatesAndSinteredMagnets() throws IOException {
        for (final String recipe : List.of(
                "crushing_bastnasite", "crushing_deepslate_bastnasite", "washing_bastnasite",
                "crushing_monazite", "crushing_deepslate_monazite", "washing_monazite",
                "crushing_cobaltite", "crushing_deepslate_cobaltite", "washing_cobaltite",
                "reduce_neodymium_oxide", "reduce_dysprosium_oxide", "reduce_samarium_oxide",
                "compact_neodymium_powder", "compact_samarium_powder", "compact_cobalt_powder",
                "samarium_cobalt_alloy", "neodymium_alloy",
                "samarium_cobalt_plate", "neodymium_alloy_plate",
                "samarium_cobalt_magnet_blank", "neodymium_magnet_blank",
                "sinter_samarium_cobalt", "sinter_neodymium",
                "samarium_cobalt_magnet", "neodymium_magnet")) {
            resource("data/magnetization/recipe/" + recipe + ".json");
        }

        assertFirstResult("reduce_samarium_oxide", "magnetization:samarium_powder");
        assertFirstResult("reduce_neodymium_oxide", "magnetization:neodymium_powder");
        for (final String recipe : List.of("reduce_samarium_oxide", "reduce_dysprosium_oxide",
                "reduce_neodymium_oxide")) {
            final JsonObject hydrogen = json("data/magnetization/recipe/" + recipe + ".json")
                    .getAsJsonArray("ingredients").get(1).getAsJsonObject();
            assertEquals("neoforge:single", hydrogen.get("type").getAsString());
            assertEquals("magnetization:hydrogen", hydrogen.get("fluid").getAsString());
            assertEquals(250, hydrogen.get("amount").getAsInt());
        }
        assertFirstResult("compact_samarium_powder", "magnetization:samarium_ingot");
        assertFirstResult("compact_neodymium_powder", "magnetization:neodymium_ingot");
        assertFirstResult("samarium_cobalt_plate", "magnetization:samarium_cobalt_plate");
        assertFirstResult("neodymium_alloy_plate", "magnetization:neodymium_alloy_plate");

        assertFinishedMagnetRecipe("samarium_cobalt_magnet", "S", "magnetization:sintered_samarium_cobalt",
                "c:plates/samarium_cobalt", "magnetization:samarium_cobalt_magnet");
        assertFinishedMagnetRecipe("neodymium_magnet", "N", "magnetization:sintered_neodymium",
                "c:plates/neodymium_alloy", "magnetization:neodymium_magnet");
    }

    @Test
    void magnetBlocksUseDedicatedModelsAndConfigGatedWorldgen() throws IOException {
        for (final String material : List.of("samarium_cobalt", "neodymium")) {
            final String id = material + "_magnet";
            resource("assets/magnetization/blockstates/" + id + ".json");
            resource("assets/magnetization/models/item/" + id + ".json");
            resource("assets/magnetization/models/block/" + id + "_north.json");
            resource("assets/magnetization/models/block/" + id + "_south.json");
            for (final String suffix : List.of("bottom", "side", "top_north", "top_south")) {
                resource("assets/magnetization/textures/block/" + id + "_" + suffix + ".png");
            }
            resource("data/magnetization/loot_table/blocks/" + id + ".json");
            final String model = resourceText("assets/magnetization/models/block/" + id + "_north.json");
            assertFalse(model.contains("diamond_block") || model.contains("netherite_block"),
                    () -> id + " still uses a vanilla placeholder block texture");
        }

        for (final String ore : List.of("bastnasite", "monazite", "cobaltite", "borax")) {
            final JsonObject modifier = json("data/magnetization/neoforge/biome_modifier/add_ore_" + ore + ".json");
            assertEquals("magnetization:config_gated_add_features", modifier.get("type").getAsString());
            assertEquals("rare_earth_ores", modifier.get("flag").getAsString());

            final String configured = json("data/magnetization/worldgen/configured_feature/ore_" + ore + ".json")
                    .getAsJsonObject("config").getAsJsonArray("targets").toString();
            assertTrue(configured.contains("minecraft:stone_ore_replaceables")
                            && configured.contains("magnetization:" + ore + "_ore"),
                    () -> "Stone " + ore + " ore is registered but absent from its configured feature");
            assertTrue(configured.contains("minecraft:deepslate_ore_replaceables")
                            && configured.contains("magnetization:deepslate_" + ore + "_ore"),
                    () -> "Deepslate " + ore + " ore is registered but absent from its configured feature");
        }
    }

    @Test
    void optionalIndustrialRecipesRemainConditionedAndProduceNativeParts() throws IOException {
        assertTfmgRecipe("tfmg_polarize_samarium_cobalt_magnet",
                "magnetization:sintered_samarium_cobalt", "magnetization:samarium_cobalt_magnet");
        assertTfmgRecipe("tfmg_polarize_neodymium_magnet",
                "magnetization:sintered_neodymium", "magnetization:neodymium_magnet");

        assertIePlateRecipe("immersiveengineering_metal_press_samarium_cobalt_plate",
                "c:ingots/samarium_cobalt", "magnetization:samarium_cobalt_plate");
        assertIePlateRecipe("immersiveengineering_metal_press_neodymium_alloy_plate",
                "c:ingots/neodymium_alloy", "magnetization:neodymium_alloy_plate");
    }

    private static void assertFirstResult(final String recipe, final String expected) throws IOException {
        assertEquals(expected, json("data/magnetization/recipe/" + recipe + ".json")
                .getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString());
    }

    private static void assertFinishedMagnetRecipe(final String recipe, final String materialKey,
                                                   final String material, final String plateTag,
                                                   final String result) throws IOException {
        final JsonObject json = json("data/magnetization/recipe/" + recipe + ".json");
        assertEquals(material, json.getAsJsonObject("key").getAsJsonObject(materialKey).get("item").getAsString());
        assertEquals(plateTag, json.getAsJsonObject("key").getAsJsonObject("P").get("tag").getAsString());
        assertEquals(result, json.getAsJsonObject("result").get("id").getAsString());
        assertEquals(6, json.getAsJsonArray("pattern").asList().stream()
                .map(JsonElement::getAsString).mapToLong(row -> row.chars().filter(c -> c == materialKey.charAt(0)).count()).sum());
    }

    private static void assertTfmgRecipe(final String recipe, final String input, final String output)
            throws IOException {
        final JsonObject json = json("data/magnetization/recipe/" + recipe + ".json");
        final String conditions = json.getAsJsonArray("neoforge:conditions").toString();
        assertTrue(conditions.contains("neoforge:mod_loaded") && conditions.contains("tfmg"));
        assertTrue(conditions.contains("magnetization:compat_config") && conditions.contains("tfmg_processing"));
        assertEquals(input, json.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
        assertEquals(output, json.getAsJsonArray("results").get(0).getAsJsonObject().get("id").getAsString());
    }

    private static void assertIePlateRecipe(final String recipe, final String inputTag, final String output)
            throws IOException {
        final JsonObject json = json("data/magnetization/recipe/" + recipe + ".json");
        final String conditions = json.getAsJsonArray("neoforge:conditions").toString();
        assertTrue(conditions.contains("neoforge:mod_loaded") && conditions.contains("immersiveengineering"));
        assertTrue(conditions.contains("magnetization:compat_config") && conditions.contains("immersive_engineering"));
        assertEquals(inputTag, json.getAsJsonObject("input").get("tag").getAsString());
        assertEquals(output, json.getAsJsonObject("result").get("id").getAsString());
    }

    private static JsonObject json(final String relative) throws IOException {
        return JsonParser.parseString(resourceText(relative)).getAsJsonObject();
    }

    private static String resourceText(final String relative) throws IOException {
        return Files.readString(resource(relative));
    }

    private static Path resource(final String relative) {
        final Path main = MAIN.resolve(relative);
        final Path generated = GENERATED.resolve(relative);
        assertTrue(Files.isRegularFile(main) || Files.isRegularFile(generated), () -> "Missing resource " + relative);
        return Files.isRegularFile(main) ? main : generated;
    }
}
