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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Resource matrix gate for the 1.4 noble-gas feature set. */
class NobleGasResourceCompletenessTest {
    private static final List<String> GASES = List.of("helium", "neon", "argon", "krypton", "xenon", "radon");
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
        assertTrue(lang.has("item.magnetization.isotope_separation_module"));
    }

    @Test
    void gasWorldgenAndManualEntriesArePresent() {
        for (final String gas : List.of("helium", "radon")) {
            resource("data/magnetization/worldgen/configured_feature/" + gas + "_pocket.json");
            resource("data/magnetization/worldgen/placed_feature/" + gas + "_pocket.json");
            resource("data/magnetization/neoforge/biome_modifier/add_" + gas + "_pockets.json");
        }
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
