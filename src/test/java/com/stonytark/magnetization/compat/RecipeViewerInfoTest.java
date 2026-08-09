package com.stonytark.magnetization.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

class RecipeViewerInfoTest {

    private static final Set<String> EXPECTED_TOPICS = Set.of(
            "magnetization:info/ferromagnetic_items",
            "magnetization:info/excavator_targets",
            "magnetization:info/magnetite",
            "magnetization:info/iron_oxide_ores",
            "magnetization:info/lithium",
            "magnetization:info/gallium",
            "magnetization:info/fusion_fuels",
            "magnetization:info/electrolyzer",
            "magnetization:info/dipole_electromagnet",
            "magnetization:info/structural_inducer",
            "magnetization:info/mhd_jet",
            "magnetization:info/fusion_thruster",
            "magnetization:info/tokamak",
            "magnetization:info/railgun");

    private static final List<String> VIEWER_TAG_TRANSLATIONS = List.of(
            "tag.fluid.c.hydrogen",
            "tag.fluid.magnetization.mhd_working_fluids",
            "tag.item.c.buckets.hydrogen",
            "tag.item.c.ingots.hematite",
            "tag.item.c.ingots.lithium",
            "tag.item.c.ingots.maghemite",
            "tag.item.c.ingots.magnetic_alloy",
            "tag.item.c.ingots.magnetite",
            "tag.item.c.ingots.pyrrhotite",
            "tag.item.c.ingots.titanomagnetite",
            "tag.item.c.ores.hematite",
            "tag.item.c.ores.lithium",
            "tag.item.c.ores.maghemite",
            "tag.item.c.ores.magnetite",
            "tag.item.c.ores.pyrrhotite",
            "tag.item.c.ores.titanomagnetite",
            "tag.item.c.plates.magnetic_alloy",
            "tag.item.c.raw_materials.hematite",
            "tag.item.c.raw_materials.lithium",
            "tag.item.c.raw_materials.maghemite",
            "tag.item.c.raw_materials.magnetite",
            "tag.item.c.raw_materials.pyrrhotite",
            "tag.item.c.raw_materials.titanomagnetite",
            "tag.item.c.storage_blocks.hematite",
            "tag.item.c.storage_blocks.maghemite",
            "tag.item.c.storage_blocks.magnetite",
            "tag.item.c.storage_blocks.pyrrhotite",
            "tag.item.c.storage_blocks.raw_hematite",
            "tag.item.c.storage_blocks.raw_maghemite",
            "tag.item.c.storage_blocks.raw_magnetite",
            "tag.item.c.storage_blocks.raw_pyrrhotite",
            "tag.item.c.storage_blocks.raw_titanomagnetite",
            "tag.item.c.storage_blocks.titanomagnetite",
            "tag.item.magnetization.diamagnetic",
            "tag.item.magnetization.electrolyzer_coils",
            "tag.item.magnetization.ferromagnetic",
            "tag.item.magnetization.metal_armor",
            "tag.item.magnetization.metal_tools",
            "tag.item.magnetization.redstone_fuel");

    private static final Path EN_US = Path.of(
            "src/main/resources/assets/magnetization/lang/en_us.json");

    @Test
    void catalogHasStableUniqueTopicsWithUsefulContent() {
        final Set<String> ids = new HashSet<>();
        for (final RecipeViewerInfo.Topic topic : RecipeViewerInfo.topics()) {
            assertTrue(ids.add(topic.id().toString()), () -> "Duplicate recipe-viewer topic " + topic.id());
            assertTrue(topic.id().getPath().startsWith("info/"), () -> "Unscoped info ID " + topic.id());
            assertTrue(topic.titleKey().startsWith("recipe_viewer.magnetization."));
            assertTrue(topic.descriptionKeys().size() >= 3,
                    () -> topic.id() + " regressed to a bare information tab");
            assertEquals(topic.descriptionKeys().size(), new HashSet<>(topic.descriptionKeys()).size(),
                    () -> topic.id() + " repeats a description line");
        }
        assertEquals(EXPECTED_TOPICS, ids, "Unexpected recipe-viewer topic drift");
    }

    @Test
    void everyCatalogTranslationExistsAndIsNonBlank() throws IOException {
        final JsonObject translations;
        try (var reader = Files.newBufferedReader(EN_US)) {
            translations = JsonParser.parseReader(reader).getAsJsonObject();
        }

        for (final RecipeViewerInfo.Topic topic : RecipeViewerInfo.topics()) {
            assertTranslation(translations, topic.titleKey());
            for (final String key : topic.descriptionKeys()) assertTranslation(translations, key);
        }
    }

    @Test
    void viewerFacingTagsHaveReadableNames() throws IOException {
        final JsonObject translations;
        try (var reader = Files.newBufferedReader(EN_US)) {
            translations = JsonParser.parseReader(reader).getAsJsonObject();
        }

        for (final String key : VIEWER_TAG_TRANSLATIONS) assertTranslation(translations, key);
    }

    private static void assertTranslation(final JsonObject translations, final String key) {
        assertTrue(translations.has(key), () -> "Missing recipe-viewer translation " + key);
        assertFalse(translations.get(key).getAsString().isBlank(),
                () -> "Blank recipe-viewer translation " + key);
    }
}
