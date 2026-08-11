package com.stonytark.magnetization.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resource matrix gate for the 1.4 noble-gas feature set. */
class NobleGasResourceCompletenessTest {
    private static final List<String> GASES = List.of("helium", "neon", "argon", "krypton", "xenon", "radon");
    private static final List<String> ALL_GASES = List.of(
            "hydrogen", "tritium", "helium_3", "helium", "neon", "argon", "krypton", "xenon", "radon");
    private static final List<String> MACHINES = List.of("gas_exciter", "air_separator", "ion_thruster");

    @Test
    void gasesHaveAssetsTranslationsAndInteroperabilityTags() {
        final JsonObject lang = json("assets/magnetization/lang/en_us.json");
        final Set<String> propellants = tagValues("data/magnetization/tags/fluid/ion_thruster_propellants.json");
        for (final String gas : GASES) {
            resource("assets/magnetization/blockstates/" + gas + ".json");
            resource("assets/magnetization/models/block/" + gas + ".json");
            resource("assets/magnetization/models/item/" + gas + "_bucket.json");
            resource("assets/magnetization/textures/item/" + gas + "_fill.png");
            assertTrue(lang.has("fluid_type.magnetization." + gas), () -> "Missing fluid translation for " + gas);
            assertTrue(lang.has("item.magnetization." + gas + "_bucket"), () -> "Missing bucket translation for " + gas);
            assertTrue(tagValues("data/c/tags/fluid/" + gas + ".json").contains("magnetization:" + gas));
            assertTrue(tagValues("data/c/tags/item/buckets/" + gas + ".json")
                    .contains("magnetization:" + gas + "_bucket"));
            assertTrue(propellants.contains("magnetization:" + gas));
            assertTrue(propellants.contains("magnetization:flowing_" + gas));
        }
        assertEquals(GASES.size() * 2, propellants.size(),
                "Built-in propellant tag changed without updating the compatibility contract");
    }

    @Test
    void allGasesHaveCommonGaseousAndIsotopeTags() {
        final Set<String> gaseous = tagValues("data/c/tags/fluid/gaseous.json");
        for (final String gas : ALL_GASES) {
            assertTrue(gaseous.contains("magnetization:" + gas),
                    () -> "Missing source gas from c:gaseous: " + gas);
            assertTrue(gaseous.contains("magnetization:flowing_" + gas),
                    () -> "Missing flowing gas from c:gaseous: " + gas);
        }
        for (final String gas : List.of("tritium", "helium_3")) {
            assertTrue(tagValues("data/c/tags/fluid/" + gas + ".json")
                            .contains("magnetization:" + gas),
                    () -> "Missing common fluid tag for " + gas);
            assertTrue(tagValues("data/c/tags/item/buckets/" + gas + ".json")
                            .contains("magnetization:" + gas + "_bucket"),
                    () -> "Missing common bucket tag for " + gas);
        }
    }

    @Test
    void machinesAndUpgradeHaveCompleteReleaseResources() {
        final JsonObject lang = json("assets/magnetization/lang/en_us.json");
        for (final String machine : MACHINES) {
            resource("assets/magnetization/blockstates/" + machine + ".json");
            resource("assets/magnetization/models/block/" + machine + ".json");
            resource("assets/magnetization/models/item/" + machine + ".json");
            resource("data/magnetization/loot_table/blocks/" + machine + ".json");
            resource("data/magnetization/recipe/" + machine + ".json");
            assertTrue(lang.has("block.magnetization." + machine), () -> "Missing block translation for " + machine);
        }
        resource("assets/magnetization/models/item/isotope_separation_module.json");
        resource("assets/magnetization/textures/item/isotope_separation_module.png");
        resource("data/magnetization/recipe/isotope_separation_module.json");
        resource("assets/magnetization/models/item/air_filter.json");
        resource("data/magnetization/recipe/air_filter.json");
        assertTrue(lang.has("item.magnetization.isotope_separation_module"));
        assertTrue(lang.has("item.magnetization.air_filter"));
        assertTrue(lang.has("tooltip.magnetization.air_filter.use"));
        final JsonObject isotopeRecipe = json("data/magnetization/recipe/isotope_separation_module.json");
        final String isotopeIngredients = isotopeRecipe.getAsJsonArray("ingredients").toString();
        assertEquals(2, isotopeIngredients.split("magnetization:air_filter", -1).length - 1,
                "Isotope module must consume two Air Filters");
        assertFalse(isotopeIngredients.contains("magnetization:tokamak_coil"),
                "Tokamak coils must not be required by the isotope module");
        assertPaperFilterRecipe(json("data/magnetization/recipe/air_filter.json"));
        assertPlasticFilterRecipe("data/magnetization/recipe/air_filter_from_immersiveengineering_plastic.json",
                "immersiveengineering", "immersive_engineering", "c:plates/plastic");
        assertPlasticFilterRecipe("data/magnetization/recipe/air_filter_from_tfmg_plastic.json",
                "tfmg", "tfmg_processing", "c:ingots/plastic");
        for (final String key : List.of(
                "tooltip.magnetization.air_separator.rpm",
                "tooltip.magnetization.air_separator.storage",
                "tooltip.magnetization.air_separator.isotope_progress",
                "tooltip.magnetization.air_separator.no_module",
                "tooltip.magnetization.air_separator.status_disallowed",
                "tooltip.magnetization.air_separator.status_needs_speed",
                "tooltip.magnetization.air_separator.status_running",
                "tooltip.magnetization.air_separator.status_outputs_full",
                "gui.magnetization.air_separator.speed",
                "gui.magnetization.air_separator.tank",
                "gui.magnetization.air_separator.rate",
                "gui.magnetization.air_separator.output_face",
                "gui.magnetization.air_separator.port_tooltip",
                "gui.magnetization.air_separator.upgrade_slot",
                "gui.magnetization.air_separator.output_slot",
                "gui.magnetization.air_separator.face_short.up",
                "gui.magnetization.air_separator.face_short.down",
                "gui.magnetization.air_separator.face_short.north",
                "gui.magnetization.air_separator.face_short.south",
                "gui.magnetization.air_separator.face_short.east",
                "gui.magnetization.air_separator.face_short.west")) {
            assertTrue(lang.has(key), () -> "Missing Air Separator GUI/HUD translation " + key);
        }
    }

    private static void assertPaperFilterRecipe(final JsonObject recipe) {
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals(4, recipe.getAsJsonArray("ingredients").size());
        recipe.getAsJsonArray("ingredients").forEach(ingredient ->
                assertEquals("minecraft:paper", ingredient.getAsJsonObject().get("item").getAsString()));
        assertEquals("magnetization:air_filter", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    private static void assertPlasticFilterRecipe(final String path, final String mod, final String feature,
                                                   final String tag) {
        final JsonObject recipe = json(path);
        final String conditions = recipe.getAsJsonArray("neoforge:conditions").toString();
        assertTrue(conditions.contains("\"modid\":\"" + mod + "\""),
                () -> "Missing mod condition in " + path);
        assertTrue(conditions.contains("\"feature\":\"" + feature + "\""),
                () -> "Missing config condition in " + path);
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals(4, recipe.getAsJsonArray("ingredients").size());
        recipe.getAsJsonArray("ingredients").forEach(ingredient ->
                assertEquals(tag, ingredient.getAsJsonObject().get("tag").getAsString()));
        assertEquals("magnetization:air_filter", recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void gasWorldgenAndManualEntriesArePresent() {
        for (final String gas : List.of("helium", "radon")) {
            resource("data/magnetization/worldgen/configured_feature/" + gas + "_pocket.json");
            resource("data/magnetization/worldgen/placed_feature/" + gas + "_pocket.json");
            resource("data/magnetization/neoforge/biome_modifier/add_" + gas + "_pockets.json");
        }
        resource("assets/magnetization/textures/mob_effect/radon_exposure.png");
        resource("assets/magnetization/patchouli_books/field_manual/en_us/entries/fluids/noble_gases.json");
        resource("assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/gas_exciter.json");
        resource("assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/air_separator.json");
        resource("assets/magnetization/patchouli_books/field_manual/en_us/entries/ships/ion_thruster.json");
    }

    private static Set<String> tagValues(final String path) {
        final Set<String> values = new HashSet<>();
        for (final var value : json(path).getAsJsonArray("values")) {
            values.add(value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString());
        }
        return Set.copyOf(values);
    }

    private static JsonObject json(final String path) {
        try (var reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (final java.io.IOException e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    private static InputStream resource(final String path) {
        final InputStream stream = NobleGasResourceCompletenessTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, () -> "Missing resource " + path);
        return stream;
    }
}
