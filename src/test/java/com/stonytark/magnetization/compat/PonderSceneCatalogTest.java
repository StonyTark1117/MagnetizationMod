package com.stonytark.magnetization.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps the Ponder registry synchronized with shipping blocks and 1.4's guided systems. */
class PonderSceneCatalogTest {

    private static final Path MAIN = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/generated/resources");

    @Test
    void sceneIdsTargetsAndMetadataAreValid() {
        final var scenes = PonderSceneCatalog.allScenes();
        assertEquals(scenes.size(), scenes.stream().map(PonderSceneCatalog.Scene::id).distinct().count(),
                "Ponder scene IDs must be unique");

        for (final PonderSceneCatalog.Scene scene : scenes) {
            final Set<String> targets = new HashSet<>();
            assertFalse(scene.title().isBlank(), () -> scene.id() + " has no title");
            assertFalse(scene.targets().isEmpty(), () -> scene.id() + " has no targets");
            if (scene.kind() == PonderSceneCatalog.Kind.MACHINE) {
                assertEquals(3, scene.texts().size(), () -> scene.id() + " needs setup and two demonstration stages");
            }
            for (final String target : scene.targets()) {
                assertTrue(targets.add(target), () -> target + " is duplicated within a Ponder scene definition");
                if (target.equals("magnetization:mr_fluid_bucket")) {
                    assertTrue(Files.isRegularFile(MAIN.resolve("assets/magnetization/models/item/mr_fluid_bucket.json"))
                            || Files.isRegularFile(GENERATED.resolve("assets/magnetization/models/item/mr_fluid_bucket.json")));
                } else if (target.startsWith("magnetization:")) assertLocalTarget(target);
            }
        }
    }

    @Test
    void everySceneHeaderAndInstructionHasShippingEnglishLocalization() throws Exception {
        final JsonObject lang = JsonParser.parseString(Files.readString(
                MAIN.resolve("assets/magnetization/lang/en_us.json"))).getAsJsonObject();
        final Map<String, String> expected = new LinkedHashMap<>();

        for (final PonderSceneCatalog.Scene scene : PonderSceneCatalog.allScenes()) {
            final String prefix = "magnetization.ponder." + scene.id() + ".";
            expected.put(prefix + "header", scene.title());
            for (int i = 0; i < scene.texts().size(); i++) {
                // Ponder's generated scene text keys are one-based. A zero-based
                // resource silently shifts every instruction and leaves the final
                // overlay displaying its raw translation key in-game.
                expected.put(prefix + "text_" + (i + 1), scene.text(i));
            }
        }

        assertEquals(26, PonderSceneCatalog.allScenes().size(), "Unexpected Ponder scene count");
        assertEquals(86, PonderSceneCatalog.allScenes().stream()
                .mapToInt(scene -> scene.texts().size()).sum(), "Unexpected Ponder instruction count");
        assertEquals(112, expected.size(), "Unexpected Ponder localization count");
        expected.forEach((key, value) -> {
            assertTrue(lang.has(key), () -> "Missing Ponder localization: " + key);
            assertEquals(value, lang.get(key).getAsString(), () -> "Stale Ponder localization: " + key);
        });

        final Set<String> shippingKeys = lang.keySet().stream()
                .filter(key -> key.startsWith("magnetization.ponder."))
                .collect(Collectors.toSet());
        assertEquals(expected.keySet(), shippingKeys,
                "Shipping Ponder keys must exactly match the synchronized scene catalog");
    }

    @Test
    void releaseCriticalMachinesAndMaterialsRemainCovered() {
        final Set<String> targets = PonderSceneCatalog.coreScenes().stream()
                .flatMap(scene -> scene.targets().stream())
                .collect(Collectors.toSet());
        assertTrue(targets.containsAll(Set.of(
                "magnetization:permanent_magnet",
                "magnetization:polarity_inverter",
                "magnetization:magnetic_excavator",
                "magnetization:repulsor_coil",
                "magnetization:vector_core",
                "minecraft:copper_block",
                "magnetization:tokamak_controller",
                "magnetization:tokamak_coil",
                "magnetization:fusion_thruster",
                "magnetization:railgun_emitter",
                "magnetization:electrolyzer",
                "magnetization:gas_exciter",
                "magnetization:gas_vent",
                "magnetization:air_separator",
                "magnetization:ion_thruster",
                "magnetization:samarium_cobalt_magnet",
                "magnetization:neodymium_magnet"
        )), "Ponder omits a release-critical setup or 1.4 progression tier");

        final PonderSceneCatalog.Scene rareEarth = PonderSceneCatalog.coreScenes().stream()
                .filter(scene -> scene.kind() == PonderSceneCatalog.Kind.RARE_EARTH)
                .findFirst().orElseThrow();
        assertEquals(Set.of("magnetization:samarium_cobalt_magnet", "magnetization:neodymium_magnet"),
                Set.copyOf(rareEarth.targets()));
    }

    @Test
    void optionalScenesStayExplicitlyScoped() {
        assertEquals(Set.of("railways:track_coupler", "copycats:copycat_block"),
                PonderSceneCatalog.optionalScenes().stream()
                        .flatMap(scene -> scene.targets().stream())
                        .collect(Collectors.toSet()));
    }

    private static void assertLocalTarget(final String target) {
        final String path = target.substring("magnetization:".length());
        final String block = "assets/magnetization/blockstates/" + path + ".json";
        final String item = "assets/magnetization/models/item/" + path + ".json";
        assertTrue(Files.isRegularFile(MAIN.resolve(block)) || Files.isRegularFile(GENERATED.resolve(block))
                        || Files.isRegularFile(MAIN.resolve(item)) || Files.isRegularFile(GENERATED.resolve(item)),
                () -> "Ponder target has no shipping blockstate or item model: " + target);
    }
}
