package com.stonytark.magnetization.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the cross-mod registration paths which main-menu client smokes cannot exercise. */
class HudSurfaceCompletenessTest {
    private static final Path JAVA = Path.of("src/main/java/com/stonytark/magnetization");

    @Test void gasExciterUsesSharedFourSurfaceMachineContract() throws Exception {
        for (final String path : new String[] {
                "content/gas/GasExciterBlockEntity.java",
                "content/gyro/GyrostabilizerBlockEntity.java",
                "content/induction/InductionPadBlockEntity.java",
                "content/sensor/MagnetostrictiveSensorBlockEntity.java",
                "content/sensor/BarkhausenBlockEntity.java"
        }) {
            final String source = Files.readString(JAVA.resolve(path));
            assertTrue(source.contains("com.stonytark.magnetization.menu.MachineHudData"), path);
            assertTrue(source.contains("hudLines()"), path);
        }
    }

    @Test void everyGolemViewerHasAnExplicitRegistration() throws Exception {
        final String jade = Files.readString(JAVA.resolve("compat/jade/MagJadePlugin.java"));
        assertTrue(jade.contains("MagneticGolemProvider.INSTANCE"));
        assertTrue(jade.contains("GalliumGolemProvider.INSTANCE"));
        assertTrue(jade.contains("MrFluidGolemProvider.INSTANCE"));

        final String wthit = Files.readString(JAVA.resolve("compat/wthit/MagWthitClientPlugin.java"));
        assertTrue(wthit.contains("MagneticGolemBodyProvider.INSTANCE"));
        assertTrue(wthit.contains("GalliumGolemBodyProvider.INSTANCE"));
        assertTrue(wthit.contains("MrFluidGolemBodyProvider.INSTANCE"));

        final String top = Files.readString(JAVA.resolve("compat/top/MagTopRegistration.java"));
        assertTrue(top.contains("registerEntityProvider(CustomGolemProbeProvider.INSTANCE)"));

        final String create = Files.readString(JAVA.resolve("client/GolemGoggleHud.java"));
        assertTrue(create.contains("GogglesItem.isWearingGoggles"));
        assertTrue(create.contains("CustomGolemHud.lines"));
    }

    @Test void everyJadeProviderHasAConfigTranslation() throws Exception {
        final String lang = Files.readString(Path.of(
                "src/main/resources/assets/magnetization/lang/en_us.json"));
        for (final String id : new String[] {
                "field_info", "machine_info", "golem_info", "gallium_golem_info",
                "mr_fluid_golem_info"
        }) {
            assertTrue(lang.contains("\"config.jade.plugin_magnetization." + id + "\""), id);
        }
    }

    @Test void jadeAndSableHaveNonDuplicatedOverlayOwnership() throws Exception {
        final String plugin = Files.readString(JAVA.resolve("compat/jade/MagJadePlugin.java"));
        // Sable's Jade bridge projects targeting into sublevels. Magnetization
        // owns the two content providers and registers each exactly once against
        // ordinary blocks; a second SubLevel-specific provider would duplicate
        // the same field/machine lines when looking inside a craft.
        assertEquals(1, occurrences(plugin,
                "registerBlockComponent(EmitterFieldProvider.INSTANCE"));
        assertEquals(1, occurrences(plugin,
                "registerBlockComponent(MachineInfoProvider.INSTANCE"));
        assertFalse(plugin.contains("ServerSubLevel"));
        assertFalse(plugin.contains("ClientSubLevel"));

        final String emitter = Files.readString(JAVA.resolve(
                "compat/jade/EmitterFieldProvider.java"));
        final String machine = Files.readString(JAVA.resolve(
                "compat/jade/MachineInfoProvider.java"));
        assertTrue(emitter.contains("instanceof MagneticFieldSource"));
        assertTrue(machine.contains("instanceof MachineHudData"));
    }

    @Test void solidCustomGolemsCannotFallBackToVanillaModels() throws Exception {
        final String oxideRenderer = Files.readString(JAVA.resolve("client/IronOxideGolemRenderer.java"));
        final String galliumRenderer = Files.readString(JAVA.resolve("client/GalliumGolemRenderer.java"));
        final String registrations = Files.readString(JAVA.resolve("client/MagClientRegistration.java"));
        assertTrue(oxideRenderer.contains("new IronOxideGolemModel(profile)"));
        assertTrue(galliumRenderer.contains("new GalliumGolemModel()"));
        for (final String profile : new String[] {
                "MAGNETITE", "PYRRHOTITE", "HEMATITE", "TITANOMAGNETITE"
        }) {
            assertTrue(registrations.contains("IronOxideGolemModel.Profile." + profile), profile);
        }
    }

    private static int occurrences(final String source, final String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
