package com.stonytark.magnetization.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the resource matrix for each material family added for 1.3.0. */
class MaterialFamilyCompletenessTest {

    private static final List<String> MATERIALS = List.of(
            "lithium", "pyrrhotite", "hematite", "titanomagnetite");
    private static final List<String> EQUIPMENT = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe",
            "helmet", "chestplate", "leggings", "boots", "horse_armor");

    @Test
    void everyEquipmentFamilyHasModelsTexturesRecipesAndTranslations() {
        final JsonObject translations = json("assets/magnetization/lang/en_us.json");
        final String metalArmor = json("data/magnetization/tags/item/metal_armor.json")
                .getAsJsonArray("values").toString();
        for (final String material : MATERIALS) {
            for (final String part : EQUIPMENT) {
                final String id = material + "_" + part;
                resource("assets/magnetization/models/item/" + id + ".json");
                resource("assets/magnetization/textures/item/" + id + ".png");
                resource("data/magnetization/recipe/" + id + ".json");
                assertTrue(translations.has("item.magnetization." + id),
                        () -> "Missing translation for " + id);
                if (part.equals("helmet") || part.equals("chestplate") || part.equals("leggings")
                        || part.equals("boots") || part.equals("horse_armor")) {
                    assertTrue(metalArmor.contains("magnetization:" + id),
                            () -> id + " must participate in metal-armor mechanics");
                }
            }
            resource("assets/magnetization/textures/models/armor/" + material + "_layer_1.png");
            resource("assets/magnetization/textures/models/armor/" + material + "_layer_2.png");
            resource("assets/magnetization/textures/entity/horse/armor/horse_armor_" + material + ".png");
        }
        assertTrue(metalArmor.contains("magnetization:gallium_horse_armor"),
                "Existing gallium gear must participate in metal-armor mechanics too");
    }

    @Test
    void everyNewStorageBlockHasCompleteAssetsLootAndRoundTripRecipes() {
        final JsonObject translations = json("assets/magnetization/lang/en_us.json");
        final List<StorageFamily> blocks = List.of(
                new StorageFamily("ferromagnetic_block", "ferromagnetic_ingot_from_block", "magnetic_alloy"),
                new StorageFamily("lithium_block", "lithium_from_block", "lithium"),
                new StorageFamily("raw_lithium_block", "raw_lithium_from_block", "raw_lithium"),
                new StorageFamily("raw_gallium_block", "raw_gallium_from_block", "raw_gallium"));

        for (final StorageFamily family : blocks) {
            resource("assets/magnetization/blockstates/" + family.block() + ".json");
            resource("assets/magnetization/models/block/" + family.block() + ".json");
            resource("assets/magnetization/models/item/" + family.block() + ".json");
            resource("assets/magnetization/textures/block/" + family.block() + ".png");
            resource("data/magnetization/loot_table/blocks/" + family.block() + ".json");
            resource("data/magnetization/recipe/" + family.block() + ".json");
            resource("data/magnetization/recipe/" + family.unpackRecipe() + ".json");
            resource("data/c/tags/block/storage_blocks/" + family.commonTag() + ".json");
            resource("data/c/tags/item/storage_blocks/" + family.commonTag() + ".json");
            assertTrue(translations.has("block.magnetization." + family.block()),
                    () -> "Missing translation for " + family.block());
        }
    }

    private static JsonObject json(final String path) {
        try (var reader = new InputStreamReader(resource(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (final java.io.IOException e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    private static java.io.InputStream resource(final String path) {
        final java.io.InputStream stream = MaterialFamilyCompletenessTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, () -> "Missing resource " + path);
        return stream;
    }

    private record StorageFamily(String block, String unpackRecipe, String commonTag) {}
}
