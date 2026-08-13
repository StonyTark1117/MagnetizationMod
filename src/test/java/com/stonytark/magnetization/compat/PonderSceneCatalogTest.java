package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
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

        final Set<String> targets = new HashSet<>();
        for (final PonderSceneCatalog.Scene scene : scenes) {
            assertFalse(scene.title().isBlank(), () -> scene.id() + " has no title");
            assertFalse(scene.targets().isEmpty(), () -> scene.id() + " has no targets");
            if (scene.kind() == PonderSceneCatalog.Kind.GENERIC) {
                assertFalse(scene.message().isBlank(), () -> scene.id() + " has no explanatory text");
            }
            for (final String target : scene.targets()) {
                assertTrue(targets.add(target), () -> target + " has more than one Ponder scene definition");
                if (target.startsWith("magnetization:")) assertLocalBlockstate(target);
            }
        }
    }

    @Test
    void releaseCriticalMachinesAndMaterialsRemainCovered() {
        final Set<String> targets = PonderSceneCatalog.coreScenes().stream()
                .flatMap(scene -> scene.targets().stream())
                .collect(Collectors.toSet());
        assertTrue(targets.containsAll(Set.of(
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

    private static void assertLocalBlockstate(final String target) {
        final String path = target.substring("magnetization:".length());
        final String relative = "assets/magnetization/blockstates/" + path + ".json";
        assertTrue(Files.isRegularFile(MAIN.resolve(relative)) || Files.isRegularFile(GENERATED.resolve(relative)),
                () -> "Ponder target has no shipping blockstate: " + target);
    }
}
