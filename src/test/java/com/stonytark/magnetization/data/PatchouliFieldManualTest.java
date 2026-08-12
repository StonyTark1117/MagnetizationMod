package com.stonytark.magnetization.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stonytark.magnetization.config.MagConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchouliFieldManualTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path BOOK = RESOURCES.resolve(
            "assets/magnetization/patchouli_books/field_manual/en_us");
    private static final Path BOOK_DEFINITION = RESOURCES.resolve(
            "data/magnetization/patchouli_books/field_manual/book.json");
    private static final Path EN_US = RESOURCES.resolve("assets/magnetization/lang/en_us.json");

    @Test
    void everyBookTranslationAndCategoryResolves() throws IOException {
        final JsonObject translations = parse(EN_US).getAsJsonObject();
        for (final Path definition : definitionFiles()) {
            final Set<String> strings = new HashSet<>();
            collectStrings(parse(definition), strings);
            for (final String value : strings) {
                if (value.startsWith("book.magnetization.")) {
                    assertTrue(translations.has(value),
                            () -> definition + " references missing translation " + value);
                }
            }
        }

        final Set<String> categories = new HashSet<>();
        try (var files = Files.list(BOOK.resolve("categories"))) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> categories.add("magnetization:" + stem(path)));
        }

        for (final Path entry : entryFiles()) {
            final JsonElement json = parse(entry);
            final String category = json.getAsJsonObject().get("category").getAsString();
            assertTrue(categories.contains(category), () -> entry + " references missing category " + category);
        }
    }

    @Test
    void everyLocalItemAndRecipeReferenceResolves() throws IOException {
        for (final Path entry : entryFiles()) {
            final Set<String> itemRefs = new HashSet<>();
            final Set<String> recipeRefs = new HashSet<>();
            collectNamedStrings(parse(entry), "icon", itemRefs);
            collectNamedStrings(parse(entry), "item", itemRefs);
            collectNamedStrings(parse(entry), "recipe", recipeRefs);
            collectArrayStrings(parse(entry), "recipes", recipeRefs);

            for (final String id : itemRefs) {
                if (!id.startsWith("magnetization:")) continue;
                final String path = id.substring("magnetization:".length());
                assertTrue(resourceExists("assets/magnetization/models/item/" + path + ".json")
                                || resourceExists("assets/magnetization/blockstates/" + path + ".json"),
                        () -> entry + " references missing item/block asset " + id);
            }
            for (final String id : recipeRefs) {
                if (!id.startsWith("magnetization:")) continue;
                final String path = id.substring("magnetization:".length());
                assertTrue(resourceExists("data/magnetization/recipe/" + path + ".json"),
                        () -> entry + " references missing recipe " + id);
            }
        }
    }

    @Test
    void releaseCriticalEntriesRemainPresent() {
        final List<String> required = List.of(
                "emitters/dipole_electromagnet.json",
                "ships/fusion_thruster.json",
                "ships/railgun.json",
                "machines/electrolyzer.json",
                "machines/automation.json",
                "fluids/fusion_fuels.json",
                "fluids/lithium.json",
                "advanced/magnet_burning.json",
                "gear/rare_earth_magnets.json",
                "advanced/configuration.json",
                "advanced/compatibility.json");
        for (final String path : required) {
            assertTrue(Files.isRegularFile(BOOK.resolve("entries").resolve(path)),
                    () -> "Field Manual is missing release-critical entry " + path);
        }
    }

    @Test
    void manualRecipesHaveIndependentConfigConditionsAndSafeDefaults() throws IOException {
        assertManualRecipeCondition("field_manual.json", "patchouli_manual_magnetite");
        assertManualRecipeCondition("field_manual_from_iron.json", "patchouli_manual_iron");
        assertManualRecipeCondition("field_manual_from_lodestone.json", "patchouli_manual_lodestone");

        assertTrue(MagConfig.FIELD_MANUAL_MAGNETITE_RECIPE_ENABLED.getDefault());
        assertFalse(MagConfig.FIELD_MANUAL_IRON_RECIPE_ENABLED.getDefault());
        assertTrue(MagConfig.FIELD_MANUAL_LODESTONE_RECIPE_ENABLED.getDefault());

        final JsonObject translations = parse(EN_US).getAsJsonObject();
        final String ironWarning = translations
                .get("magnetization.configuration.compat.fieldManualIronRecipeEnabled.tooltip")
                .getAsString()
                .toLowerCase(java.util.Locale.ROOT);
        assertTrue(ironWarning.contains("conflict") && ironWarning.contains("patchouli"),
                "Iron recipe tooltip must warn about possible Patchouli recipe conflicts");
    }

    private static void assertManualRecipeCondition(final String recipeName, final String expectedFeature)
            throws IOException {
        final Path recipe = RESOURCES.resolve("data/magnetization/recipe").resolve(recipeName);
        final JsonObject json = parse(recipe).getAsJsonObject();
        final var conditions = json.getAsJsonArray("neoforge:conditions");
        assertTrue(conditions.asList().stream().anyMatch(element -> {
            final JsonObject condition = element.getAsJsonObject();
            return "neoforge:mod_loaded".equals(condition.get("type").getAsString())
                    && "patchouli".equals(condition.get("modid").getAsString());
        }), () -> recipe + " must require Patchouli");
        final List<String> configFeatures = conditions.asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(condition -> "magnetization:compat_config".equals(condition.get("type").getAsString()))
                .map(condition -> condition.get("feature").getAsString())
                .toList();
        assertEquals(List.of(expectedFeature), configFeatures,
                () -> recipe + " must use its independent recipe config condition");
    }

    private static List<Path> entryFiles() throws IOException {
        try (var files = Files.walk(BOOK.resolve("entries"))) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static List<Path> definitionFiles() throws IOException {
        try (var files = Files.walk(BOOK)) {
            final List<Path> definitions = new java.util.ArrayList<>(
                    files.filter(path -> path.toString().endsWith(".json")).sorted().toList());
            definitions.add(BOOK_DEFINITION);
            return definitions;
        }
    }

    private static JsonElement parse(final Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static boolean resourceExists(final String relative) {
        return Files.isRegularFile(RESOURCES.resolve(relative))
                || Files.isRegularFile(GENERATED.resolve(relative));
    }

    private static String stem(final Path path) {
        final String name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private static void collectStrings(final JsonElement element, final Set<String> out) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            out.add(element.getAsString());
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectStrings(child, out));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> collectStrings(entry.getValue(), out));
        }
    }

    private static void collectNamedStrings(final JsonElement element, final String name, final Set<String> out) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectNamedStrings(child, name, out));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().equals(name) && entry.getValue().isJsonPrimitive()) {
                    out.add(entry.getValue().getAsString());
                }
                collectNamedStrings(entry.getValue(), name, out);
            });
        }
    }

    private static void collectArrayStrings(final JsonElement element, final String name, final Set<String> out) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectArrayStrings(child, name, out));
        } else if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getKey().equals(name) && entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(value -> out.add(value.getAsString()));
                }
                collectArrayStrings(entry.getValue(), name, out);
            });
        }
    }
}
