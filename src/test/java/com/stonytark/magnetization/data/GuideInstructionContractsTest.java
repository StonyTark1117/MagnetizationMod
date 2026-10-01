package com.stonytark.magnetization.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stonytark.magnetization.config.MagConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipping data against which the guide's corrected instructions
 * were reviewed. A deliberate recipe change requires another guide review.
 * These checks do not establish prose accuracy or native machine operation.
 */
class GuideInstructionContractsTest {
    private static final Path MAIN = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path ENTRIES = MAIN.resolve(
            "assets/magnetization/patchouli_books/field_manual/en_us/entries");

    @Test
    void localizedBookStringsSupportPatchoulisNoArgumentFormatting() throws IOException {
        final JsonObject language = parse(MAIN.resolve("assets/magnetization/lang/en_us.json"));
        int reviewed = 0;
        for (final var entry : language.entrySet()) {
            if (!entry.getKey().startsWith("book.magnetization.")) continue;
            // Patchouli's i18n BookTextRenderer calls I18n.get(key) with no arguments.
            // I18n uses String.format; a literal percentage therefore needs %%.
            assertDoesNotThrow(() -> String.format(Locale.ROOT, entry.getValue().getAsString()), entry.getKey());
            reviewed++;
        }
        assertTrue(reviewed > 0, "No localized book strings were inspected");
    }

    @Test
    void rawPercentageFailsButEscapedPercentageKeepsItsVisibleValue() {
        assertThrows(java.util.IllegalFormatException.class,
                () -> String.format(Locale.ROOT, "Magnetic Gravel (5%)"));
        assertEquals("Magnetic Gravel (5%)", String.format(Locale.ROOT, "Magnetic Gravel (5%%)"));
    }

    @Test
    void reviewedProgressionRecipesKeepTheirIngredientsLayoutAndYield() throws IOException {
        assertRecipe("ferromagnetic_ingot", 8, List.of("III", "ILI", "III"),
                Map.of("I", "minecraft:iron_ingot", "L", "minecraft:lodestone"));
        assertRecipe("magnetic_plate", 3, List.of("FFF"),
                Map.of("F", "#c:ingots/magnetic_alloy"));
        assertRecipe("lodestone_core", 1, List.of("PLP", "LFL", "PLP"),
                Map.of("P", "#c:plates/magnetic_alloy", "L", "minecraft:lodestone",
                        "F", "#c:ingots/magnetic_alloy"));
        assertRecipe("meteorite_sapling", 1, List.of(" F ", "RFR", " R "),
                Map.of("F", "magnetization:meteorite_fragment", "R", "magnetization:raw_magnetite"));
        assertRecipe("magnetic_elytra", 1, List.of("FPF", "FEF", " F "),
                Map.of("F", "#c:ingots/magnetic_alloy", "P", "#c:plates/magnetic_alloy",
                        "E", "minecraft:elytra"));
        assertRecipe("ferromagnetic_horse_armor", 1, List.of("M M", "MMM", "M M"),
                Map.of("M", "#c:ingots/magnetic_alloy"));
        assertRecipe("enhanced_pyrrhotite_catalyst", 1, List.of(" P ", "PCP", " P "),
                Map.of("P", "magnetization:pyrrhotite_ingot", "C", "magnetization:pyrrhotite_catalyst"));
        assertRecipe("cosmic_pyrrhotite_catalyst", 1, List.of(" F ", "PCP", " F "),
                Map.of("F", "magnetization:meteorite_fragment", "P", "magnetization:pyrrhotite_ingot",
                        "C", "magnetization:enhanced_pyrrhotite_catalyst"));
    }

    @Test
    void reviewedCommonIngredientsActuallyIncludeOurGuideMaterials() throws IOException {
        assertTagContains("ingots/magnetic_alloy", "magnetization:ferromagnetic_ingot");
        assertTagContains("plates/magnetic_alloy", "magnetization:magnetic_plate");
    }

    @Test
    void reviewedGuideDefaultsKeepMagnetConsumptionOptional() {
        assertFalse(MagConfig.MAGNET_SLOT_CONSUMES_FUEL.getDefault(),
                "Ponder teaches permanent magnet use unless consumption is explicitly enabled");
        assertEquals(8.0, MagConfig.SENSOR_RANGE.getDefault());
        assertEquals(12000, MagConfig.TEMPORARY_MAGNET_LIFETIME.getDefault());
        assertEquals(512, MagConfig.COSMIC_COMPASS_RANGE.getDefault());
        assertEquals(36000, MagConfig.METEORITE_SAPLING_GROW_TICKS.getDefault());
        assertEquals(1.5, MagConfig.ANOMALY_STRENGTH_BONUS.getDefault());
    }

    @Test
    void bothRecipeSlotsAndRecipeArraysResolveForEveryBookPage() throws IOException {
        int allReferences = 0;
        try (var paths = Files.walk(ENTRIES)) {
            for (final Path entry : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                final JsonObject definition = parse(entry);
                for (final JsonElement element : definition.getAsJsonArray("pages")) {
                    final JsonObject page = element.getAsJsonObject();
                    for (final String id : recipeReferences(page)) {
                        allReferences++;
                        assertReferenceResolves(id, entry.toString());
                    }
                }
            }
        }
        assertTrue(allReferences > 0, "No authored recipes were inspected");
    }

    @Test
    void referenceCollectorIncludesSecondaryAndArrayRecipes() {
        final JsonObject page = JsonParser.parseString("""
                {"recipe":"magnetization:first", "recipe2":"magnetization:missing_second",
                 "recipes":["magnetization:third", "magnetization:fourth"]}
                """).getAsJsonObject();
        assertEquals(List.of("magnetization:first", "magnetization:missing_second",
                "magnetization:third", "magnetization:fourth"), recipeReferences(page));
    }

    @Test
    void missingSecondaryRecipeFailsEvenWhenPrimaryResolves() {
        final JsonObject page = JsonParser.parseString("""
                {"recipe":"magnetization:magnetic_plate", "recipe2":"magnetization:missing_second"}
                """).getAsJsonObject();
        assertReferenceResolves(page.get("recipe").getAsString(), "regression fixture");
        final AssertionError failure = assertThrows(AssertionError.class, () -> {
            for (final String id : recipeReferences(page)) assertReferenceResolves(id, "regression fixture");
        });
        assertTrue(failure.getMessage().contains("magnetization:missing_second"));
    }

    private static void assertReferenceResolves(final String id, final String context) {
        if (!id.startsWith("magnetization:")) return;
        final String relative = "data/magnetization/recipe/"
                + id.substring("magnetization:".length()) + ".json";
        assertTrue(Files.isRegularFile(MAIN.resolve(relative))
                        || Files.isRegularFile(GENERATED.resolve(relative)),
                () -> context + " references missing recipe " + id);
    }

    private static List<String> recipeReferences(final JsonObject page) {
        final List<String> result = new ArrayList<>();
        for (final String key : List.of("recipe", "recipe2")) {
            if (page.has(key)) result.add(page.get(key).getAsString());
        }
        if (page.has("recipes")) {
            for (final JsonElement recipe : page.getAsJsonArray("recipes")) result.add(recipe.getAsString());
        }
        return result;
    }

    private static void assertRecipe(final String name, final int count,
                                     final List<String> pattern, final Map<String, String> ingredients)
            throws IOException {
        final String relative = "data/magnetization/recipe/" + name + ".json";
        final Path main = MAIN.resolve(relative);
        final JsonObject recipe = parse(Files.isRegularFile(main) ? main : GENERATED.resolve(relative));
        final String context = name + " changed; review the Field Manual instructions against this recipe";
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString(), context);
        final List<String> actualPattern = new ArrayList<>();
        recipe.getAsJsonArray("pattern").forEach(row -> actualPattern.add(row.getAsString()));
        assertEquals(pattern, actualPattern, context);
        final JsonObject keys = recipe.getAsJsonObject("key");
        assertEquals(ingredients.keySet(), keys.keySet(), context);
        for (final var ingredient : ingredients.entrySet()) {
            final JsonObject expected = new JsonObject();
            final String value = ingredient.getValue();
            expected.addProperty(value.startsWith("#") ? "tag" : "item",
                    value.startsWith("#") ? value.substring(1) : value);
            assertEquals(expected, keys.get(ingredient.getKey()), context);
        }
        final JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("magnetization:" + name, result.get("id").getAsString(), context);
        assertEquals(count, result.has("count") ? result.get("count").getAsInt() : 1, context);
    }

    private static void assertTagContains(final String tag, final String item) throws IOException {
        final JsonObject data = parse(MAIN.resolve("data/c/tags/item/" + tag + ".json"));
        final List<String> values = new ArrayList<>();
        for (final JsonElement value : data.getAsJsonArray("values")) {
            values.add(value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString());
        }
        assertTrue(values.contains(item), () -> tag + " no longer accepts " + item + "; review guide progression");
    }

    private static JsonObject parse(final Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
