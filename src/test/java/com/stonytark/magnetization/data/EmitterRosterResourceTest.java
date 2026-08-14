package com.stonytark.magnetization.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps gameplay classification and inventory-based progression on one emitter roster. */
final class EmitterRosterResourceTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Set<String> EMITTERS = Set.of(
            "magnetization:electromagnet",
            "magnetization:dipole_electromagnet",
            "magnetization:kinetic_electromagnet",
            "magnetization:magnetic_anchor",
            "magnetization:repulsor_coil",
            "magnetization:tractor_beam",
            "magnetization:magnetic_excavator",
            "magnetization:permanent_magnet",
            "magnetization:temporary_magnet",
            "magnetization:samarium_cobalt_magnet",
            "magnetization:neodymium_magnet",
            "magnetization:pyrrhotite_block",
            "magnetization:titanomagnetite_block",
            "magnetization:meteorite_core"
    );

    @Test
    void gameplayTagAndEmitterAdvancementsShareTheCompleteRoster() throws IOException {
        final JsonObject blockTag = json("data/magnetization/tags/block/magnetic_emitter.json");
        final Set<String> localTaggedBlocks = new HashSet<>();
        for (final var value : blockTag.getAsJsonArray("values")) {
            if (value.isJsonPrimitive() && value.getAsString().startsWith("magnetization:")) {
                localTaggedBlocks.add(value.getAsString());
            }
        }
        assertEquals(EMITTERS, localTaggedBlocks,
                "The local magnetic_emitter gameplay tag drifted from the field-source roster");

        final JsonObject first = json("data/magnetization/advancement/first_emitter.json");
        final JsonArray anyEmitterIds = first.getAsJsonObject("criteria")
                .getAsJsonObject("any_emitter").getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonArray("items");
        assertEquals(EMITTERS, strings(anyEmitterIds),
                "first_emitter must accept every local emitter and no non-emitter");

        final JsonObject fullKit = json("data/magnetization/advancement/full_kit.json");
        final JsonObject fullCriteria = fullKit.getAsJsonObject("criteria");
        assertEquals(Set.of("all_emitters"), fullCriteria.keySet(),
                "full_kit must use one criterion so collection is checked in one inventory snapshot");
        final JsonArray allPredicates = fullCriteria.getAsJsonObject("all_emitters")
                .getAsJsonObject("conditions").getAsJsonArray("items");
        final Set<String> fullKitIds = new HashSet<>();
        for (final var predicate : allPredicates) {
            fullKitIds.add(predicate.getAsJsonObject().get("items").getAsString());
        }
        assertEquals(EMITTERS, fullKitIds, "full_kit drifted from the emitter roster");
        assertSingleRequirement(fullKit, "all_emitters");
    }

    @Test
    void dualMagnetizedRequiresBothPolaritiesInOneInventorySnapshot() throws IOException {
        final JsonObject dual = json("data/magnetization/advancement/dual_magnetized.json");
        final JsonObject criteria = dual.getAsJsonObject("criteria");
        assertEquals(Set.of("opposite_polarities"), criteria.keySet(),
                "dual_magnetized must not persist NORTH and SOUTH as separate criteria");
        final JsonArray predicates = criteria.getAsJsonObject("opposite_polarities")
                .getAsJsonObject("conditions").getAsJsonArray("items");
        assertEquals(2, predicates.size());
        final Set<String> polarities = new HashSet<>();
        for (final var predicate : predicates) {
            final JsonObject item = predicate.getAsJsonObject();
            assertEquals("#magnetization:metal_armor", item.get("items").getAsString());
            polarities.add(item.getAsJsonObject("components")
                    .get("magnetization:armor_polarity").getAsString());
        }
        assertEquals(Set.of("north", "south"), polarities);
        assertSingleRequirement(dual, "opposite_polarities");
    }

    private static void assertSingleRequirement(final JsonObject advancement, final String criterion) {
        final JsonArray requirements = advancement.getAsJsonArray("requirements");
        assertEquals(1, requirements.size());
        final JsonArray group = requirements.get(0).getAsJsonArray();
        assertEquals(1, group.size());
        assertEquals(criterion, group.get(0).getAsString());
    }

    private static Set<String> strings(final JsonArray values) {
        final Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return result;
    }

    private static JsonObject json(final String relative) throws IOException {
        final Path path = RESOURCES.resolve(relative);
        assertTrue(Files.isRegularFile(path), "Missing resource " + relative);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
