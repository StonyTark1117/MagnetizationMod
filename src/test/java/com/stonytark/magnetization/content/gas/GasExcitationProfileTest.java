package com.stonytark.magnetization.content.gas;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasExcitationProfileTest {
    private static final Path PROFILES = Path.of(
            "src/main/resources/data/magnetization/magnetization/gas_excitation_profiles");

    private static final Map<String, Expected> EXPECTED = Map.ofEntries(
            expected("hydrogen", "rise", 0x30D0F2F5, 0xFFFF6FAE),
            expected("neon", "rise", 0x309DEDE9, 0xFFFF3218),
            expected("carbon_dioxide", "sink", 0x30525252, 0xFF8F7CFF),
            expected("ethylene", "neutral", 0x30BCADCC, 0xFF65D9FF),
            expected("propylene", "sink", 0x30C0D1B4, 0xFF78C8FF),
            expected("propane", "sink", 0x3088BF80, 0xFF6EB5FF),
            expected("butane", "sink", 0x30AD77D4, 0xFF5FA4FF),
            expected("lpg", "sink", 0x30F5E687, 0xFF66B8FF),
            expected("furnace_gas", "rise", 0x305C5555, 0xFFA7D7FF),
            expected("air", "neutral", 0x30DFE6E5, 0xFFA97BFF),
            expected("hot_air", "rise", 0x30E8E1D5, 0xFFC083FF));

    @Test
    void codecAcceptsThePublicSchemaAndRejectsInvalidProfiles() {
        final var valid = JsonParser.parseString("""
                {"fluids":["example:gas","example:flowing_gas"],"buoyancy":"rise",
                 "dormant_argb":"#30112233","excited_argb":"FF445566"}
                """);
        final GasExcitationProfile profile = GasExcitationProfile.CODEC.parse(JsonOps.INSTANCE, valid)
                .result().orElseThrow();
        assertEquals(List.of(id("example:gas"), id("example:flowing_gas")), profile.fluids());
        assertEquals(GasExcitationProfile.Buoyancy.RISE, profile.buoyancy());
        assertEquals(0x30112233, profile.dormantArgb());
        assertEquals(0xFF445566, profile.excitedArgb());

        for (final String invalid : List.of(
                "{\"fluids\":[],\"buoyancy\":\"rise\",\"dormant_argb\":\"30112233\",\"excited_argb\":\"FF445566\"}",
                "{\"fluids\":[\"example:gas\"],\"buoyancy\":\"float\",\"dormant_argb\":\"30112233\",\"excited_argb\":\"FF445566\"}",
                "{\"fluids\":[\"example:gas\"],\"buoyancy\":\"rise\",\"dormant_argb\":\"112233\",\"excited_argb\":\"FF445566\"}")) {
            assertTrue(GasExcitationProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid))
                    .result().isEmpty(), () -> "Invalid profile decoded: " + invalid);
        }
    }

    @Test
    void duplicateActiveFluidClaimsAreRejected() {
        final GasExcitationProfile first = profile("example:shared", GasExcitationProfile.Buoyancy.RISE);
        final GasExcitationProfile second = profile("example:shared", GasExcitationProfile.Buoyancy.SINK);
        final Map<ResourceLocation, GasExcitationProfile> profiles = new LinkedHashMap<>();
        profiles.put(id("example:first"), first);
        profiles.put(id("example:second"), second);

        final IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GasExcitationProfiles.claimOwners(profiles));
        assertTrue(error.getMessage().contains("example:shared")
                && error.getMessage().contains("example:first")
                && error.getMessage().contains("example:second"));
    }

    @Test
    void allTfmgProfilesHaveExactFluidsConditionsBuoyancyAndColors() throws IOException {
        try (var files = Files.list(PROFILES)) {
            assertEquals(EXPECTED.size(), files.filter(path -> path.toString().endsWith(".json")).count(),
                    "Unexpected built-in gas-profile resource count");
        }

        final Map<ResourceLocation, GasExcitationProfile> decoded = new LinkedHashMap<>();
        for (final var entry : EXPECTED.entrySet()) {
            final String gas = entry.getKey();
            final Expected expected = entry.getValue();
            final Path path = PROFILES.resolve("tfmg_" + gas + ".json");
            final JsonObject json;
            try (var reader = Files.newBufferedReader(path)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            final var conditions = json.getAsJsonArray("neoforge:conditions");
            assertEquals(2, conditions.size(), () -> path + " must have exactly two conditions");
            assertEquals("neoforge:mod_loaded",
                    conditions.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("tfmg", conditions.get(0).getAsJsonObject().get("modid").getAsString());
            assertEquals("magnetization:compat_config",
                    conditions.get(1).getAsJsonObject().get("type").getAsString());
            assertEquals("tfmg_gas_excitation",
                    conditions.get(1).getAsJsonObject().get("feature").getAsString());

            final GasExcitationProfile profile = GasExcitationProfile.CODEC.parse(JsonOps.INSTANCE, json)
                    .result().orElseThrow();
            assertEquals(List.of(id("tfmg:" + gas), id("tfmg:flowing_" + gas)), profile.fluids());
            assertEquals(GasExcitationProfile.Buoyancy.valueOf(expected.buoyancy().toUpperCase()),
                    profile.buoyancy());
            assertEquals(expected.dormantArgb(), profile.dormantArgb());
            assertEquals(expected.excitedArgb(), profile.excitedArgb());
            decoded.put(id("magnetization:tfmg_" + gas), profile);
        }
        assertEquals(22, GasExcitationProfiles.claimOwners(decoded).size());
    }

    private static GasExcitationProfile profile(final String fluid,
                                                final GasExcitationProfile.Buoyancy buoyancy) {
        return new GasExcitationProfile(List.of(id(fluid)), buoyancy, 0x30112233, 0xFF445566);
    }

    private static Map.Entry<String, Expected> expected(final String gas, final String buoyancy,
                                                        final int dormant, final int excited) {
        return Map.entry(gas, new Expected(buoyancy, dormant, excited));
    }

    private static ResourceLocation id(final String value) {
        return ResourceLocation.parse(value);
    }

    private record Expected(String buoyancy, int dormantArgb, int excitedArgb) {}
}
