package com.stonytark.magnetization.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ensures optional addon values can be removed independently through config-backed tag conditions. */
final class OptionalCompatibilityTagConditionTest {

    private static final Path TAGS = Path.of("src/main/resources/data/magnetization/tags");

    @Test
    void ironworksMasterConditionCoversAllDirectAndCommonTagAdditions() throws IOException {
        final Map<String, String> expectedByTag = Map.of(
                "item/metal_armor.json", "create_ironworks:copper_armor_helmet",
                "item/ferromagnetic.json", "#c:nuggets/tin",
                "block/ferromagnetic_blocks.json", "create_ironworks:sturdy_sheet_block");

        for (final var entry : expectedByTag.entrySet()) {
            final JsonObject value = value(entry.getKey(), entry.getValue());
            assertEquals(Set.of("ironworks"), conditionFeatures(value), entry.getKey());
        }
        assertEquals(Set.of("ironworks"), conditionFeatures(value(
                "item/ferromagnetic.json", "#c:plates/tin")));
        assertEquals(Set.of("ironworks"), conditionFeatures(value(
                "item/ferromagnetic.json", "#c:nuggets/bronze")));
        assertEquals(Set.of("ironworks"), conditionFeatures(value(
                "item/ferromagnetic.json", "#c:plates/bronze")));
        assertEquals(Set.of("ironworks"), conditionFeatures(value(
                "item/ferromagnetic.json", "#c:nuggets/steel")));
    }

    @Test
    void finHasBothAddonMasterAndFinSpecificConditions() throws IOException {
        assertEquals(Set.of("coasters_additions", "coaster_fin_magnetization"),
                conditionFeatures(value("block/magnetic_emitter.json", "coasterfins:coaster_fin")));
    }

    private static JsonObject value(final String tag, final String id) throws IOException {
        try (var reader = Files.newBufferedReader(TAGS.resolve(tag))) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values").asList().stream()
                    .filter(element -> element.isJsonObject())
                    .map(element -> element.getAsJsonObject())
                    .filter(object -> id.equals(object.get("id").getAsString()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing " + id + " in " + tag));
        }
    }

    private static Set<String> conditionFeatures(final JsonObject value) {
        return value.getAsJsonArray("neoforge:conditions").asList().stream()
                .map(element -> element.getAsJsonObject().get("feature").getAsString())
                .collect(Collectors.toSet());
    }
}
