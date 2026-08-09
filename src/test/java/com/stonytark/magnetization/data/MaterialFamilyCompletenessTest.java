package com.stonytark.magnetization.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the complete resource and integration matrix for every applicable material family. */
class MaterialFamilyCompletenessTest {

    private static final List<String> SOLID_EQUIPMENT_MATERIALS = List.of(
            "magnetite", "maghemite", "ferromagnetic", "gallium",
            "lithium", "pyrrhotite", "hematite", "titanomagnetite");
    private static final List<String> TOOLS = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe");
    private static final List<String> ARMOR = List.of(
            "helmet", "chestplate", "leggings", "boots", "horse_armor");
    private static final Map<String, String> TOOL_TAGS = Map.of(
            "sword", "swords", "pickaxe", "pickaxes", "axe", "axes", "shovel", "shovels", "hoe", "hoes");
    private static final Map<String, String> ARMOR_TAGS = Map.of(
            "helmet", "head_armor", "chestplate", "chest_armor",
            "leggings", "leg_armor", "boots", "foot_armor");
    private static final List<String> EQUIPMENT = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe",
            "helmet", "chestplate", "leggings", "boots", "horse_armor");
    private static final List<StorageFamily> STORAGE_FAMILIES = List.of(
            new StorageFamily("magnetite_block", "magnetite_block", "magnetite_ingot_from_block", "magnetite"),
            new StorageFamily("raw_magnetite_block", "raw_magnetite_block", "raw_magnetite_from_block", "raw_magnetite"),
            new StorageFamily("maghemite_block", "maghemite_block", "maghemite_ingot_from_block", "maghemite"),
            new StorageFamily("raw_maghemite_block", "raw_maghemite_block", "raw_maghemite_from_block", "raw_maghemite"),
            new StorageFamily("ferromagnetic_block", "ferromagnetic_block", "ferromagnetic_ingot_from_block", "magnetic_alloy"),
            new StorageFamily("pyrrhotite_block", "pyrrhotite_block", "pyrrhotite_ingot_from_block", "pyrrhotite"),
            new StorageFamily("raw_pyrrhotite_block", "raw_pyrrhotite_block", "raw_pyrrhotite_from_block", "raw_pyrrhotite"),
            new StorageFamily("hematite_block", "hematite_block", "hematite_ingot_from_block", "hematite"),
            new StorageFamily("raw_hematite_block", "raw_hematite_block", "raw_hematite_from_block", "raw_hematite"),
            new StorageFamily("titanomagnetite_block", "titanomagnetite_block", "titanomagnetite_ingot_from_block", "titanomagnetite"),
            new StorageFamily("raw_titanomagnetite_block", "raw_titanomagnetite_block", "raw_titanomagnetite_from_block", "raw_titanomagnetite"),
            new StorageFamily("lithium_block", "lithium_block", "lithium_from_block", "lithium"),
            new StorageFamily("raw_lithium_block", "raw_lithium_block", "raw_lithium_from_block", "raw_lithium"),
            new StorageFamily("solid_gallium", "solid_gallium", "gallium_ingot_from_block", "gallium"),
            new StorageFamily("raw_gallium_block", "raw_gallium_block", "raw_gallium_from_block", "raw_gallium"),
            new StorageFamily("solid_helium_3", "solid_helium_3", "helium_3_crystal_from_block", "helium_3"));

    @Test
    void everyEquipmentFamilyHasModelsTexturesRecipesAndTranslations() {
        final JsonObject translations = json("assets/magnetization/lang/en_us.json");
        final Set<String> metalArmor = tagValues("data/magnetization/tags/item/metal_armor.json");
        final Set<String> metalTools = tagValues("data/magnetization/tags/item/metal_tools.json");
        for (final String material : SOLID_EQUIPMENT_MATERIALS) {
            for (final String part : EQUIPMENT) {
                final String id = material + "_" + part;
                resource("assets/magnetization/models/item/" + id + ".json");
                resource("assets/magnetization/textures/item/" + id + ".png");
                resource("data/magnetization/recipe/" + id + ".json");
                assertTrue(translations.has("item.magnetization." + id),
                        () -> "Missing translation for " + id);
            }
            for (final String part : TOOLS) {
                final String id = "magnetization:" + material + "_" + part;
                assertTrue(metalTools.contains(id), () -> id + " must participate in metal-tool mechanics");
                assertTrue(tagValues("data/minecraft/tags/item/" + TOOL_TAGS.get(part) + ".json").contains(id),
                        () -> id + " must participate in vanilla tool-category and enchantment mechanics");
            }
            for (final String part : ARMOR) {
                final String id = "magnetization:" + material + "_" + part;
                assertTrue(metalArmor.contains(id), () -> id + " must participate in metal-armor mechanics");
                if (!part.equals("horse_armor")) {
                    assertTrue(tagValues("data/minecraft/tags/item/" + ARMOR_TAGS.get(part) + ".json").contains(id),
                            () -> id + " must participate in vanilla armor and enchantment mechanics");
                }
            }
            resource("assets/magnetization/textures/models/armor/" + material + "_layer_1.png");
            resource("assets/magnetization/textures/models/armor/" + material + "_layer_2.png");
            resource("assets/magnetization/textures/entity/horse/armor/horse_armor_" + material + ".png");
        }
    }

    @Test
    void specializedMrFluidEquipmentFamilyIsCompleteWithoutSolidStorage() {
        final JsonObject translations = json("assets/magnetization/lang/en_us.json");
        for (final String part : TOOLS) {
            final String id = "mr_fluid_" + part;
            resource("assets/magnetization/models/item/" + id + ".json");
            resource("assets/magnetization/textures/item/" + id + ".png");
            resource("data/magnetization/recipe/" + id + ".json");
            assertTrue(translations.has("item.magnetization." + id), () -> "Missing translation for " + id);
            assertTrue(tagValues("data/minecraft/tags/item/" + TOOL_TAGS.get(part) + ".json")
                    .contains("magnetization:" + id));
        }
        for (final String part : List.of("helmet", "chestplate", "leggings", "boots")) {
            final String id = "mr_liquid_" + part;
            resource("assets/magnetization/models/item/" + id + ".json");
            resource("assets/magnetization/textures/item/" + id + ".png");
            resource("data/magnetization/recipe/" + id + ".json");
            assertTrue(translations.has("item.magnetization." + id), () -> "Missing translation for " + id);
            assertTrue(tagValues("data/minecraft/tags/item/" + ARMOR_TAGS.get(part) + ".json")
                    .contains("magnetization:" + id));
        }
        resource("assets/magnetization/models/item/mr_fluid_horse_armor.json");
        resource("assets/magnetization/textures/item/mr_fluid_horse_armor.png");
        resource("assets/magnetization/textures/models/armor/mr_liquid_layer_1.png");
        resource("assets/magnetization/textures/models/armor/mr_liquid_layer_2.png");
        resource("assets/magnetization/textures/entity/horse/armor/horse_armor_mr_liquid.png");
        resource("data/magnetization/recipe/mr_fluid_horse_armor.json");
        assertTrue(translations.has("item.magnetization.mr_fluid_horse_armor"));
    }

    @Test
    void standaloneWearablesParticipateInVanillaEnchantmentTags() {
        assertTrue(tagValues("data/minecraft/tags/item/foot_armor.json")
                .contains("magnetization:magnetoresistive_boots"));
        for (final String id : List.of("magnetization:magnetic_elytra", "magnetization:alfven_backpack")) {
            assertTrue(tagValues("data/minecraft/tags/item/enchantable/durability.json").contains(id));
            assertTrue(tagValues("data/minecraft/tags/item/enchantable/equippable.json").contains(id));
            assertTrue(tagValues("data/minecraft/tags/item/enchantable/vanishing.json").contains(id));
        }
    }

    @Test
    void everyStorageBlockHasCompleteAssetsTagsLootAndLosslessRoundTripRecipes() {
        final JsonObject translations = json("assets/magnetization/lang/en_us.json");
        final Set<String> aggregateBlocks = tagValues("data/c/tags/block/storage_blocks.json");
        final Set<String> aggregateItems = tagValues("data/c/tags/item/storage_blocks.json");

        for (final StorageFamily family : STORAGE_FAMILIES) {
            resource("assets/magnetization/blockstates/" + family.block() + ".json");
            resource("assets/magnetization/models/block/" + family.block() + ".json");
            resource("assets/magnetization/models/item/" + family.block() + ".json");
            resource("assets/magnetization/textures/block/" + family.block() + ".png");
            resource("data/magnetization/loot_table/blocks/" + family.block() + ".json");
            final JsonObject compact = json("data/magnetization/recipe/" + family.compactRecipe() + ".json");
            final String pattern = compact.getAsJsonArray("pattern").asList().stream()
                    .map(element -> element.getAsString()).reduce("", String::concat);
            assertEquals(9, pattern.length(), family.compactRecipe() + " must fill a 3x3 grid");
            assertEquals(1, pattern.chars().distinct().count(), family.compactRecipe() + " must use one material");
            assertEquals("magnetization:" + family.block(), compact.getAsJsonObject("result").get("id").getAsString());
            assertEquals(1, compact.getAsJsonObject("result").get("count").getAsInt());

            final JsonObject unpack = json("data/magnetization/recipe/" + family.unpackRecipe() + ".json");
            assertEquals(1, unpack.getAsJsonArray("ingredients").size(), family.unpackRecipe() + " must consume one block");
            assertEquals(9, unpack.getAsJsonObject("result").get("count").getAsInt(),
                    family.unpackRecipe() + " must return all nine items");
            resource("data/magnetization/recipe/" + family.unpackRecipe() + ".json");
            final String id = "magnetization:" + family.block();
            assertTrue(tagValues("data/c/tags/block/storage_blocks/" + family.commonTag() + ".json").contains(id));
            assertTrue(tagValues("data/c/tags/item/storage_blocks/" + family.commonTag() + ".json").contains(id));
            assertTrue(aggregateBlocks.contains(id), () -> id + " missing from c:block/storage_blocks");
            assertTrue(aggregateItems.contains(id), () -> id + " missing from c:item/storage_blocks");
            assertTrue(translations.has("block.magnetization." + family.block()),
                    () -> "Missing translation for " + family.block());
        }
    }

    @Test
    void commonMaterialTagsAndAuditedFamilyMatricesStayInLockstep() {
        final Set<String> expectedRefined = Set.of(
                "magnetization:magnetite_ingot", "magnetization:maghemite_ingot",
                "magnetization:ferromagnetic_ingot", "magnetization:gallium_ingot",
                "magnetization:lithium", "magnetization:pyrrhotite_ingot",
                "magnetization:hematite_ingot", "magnetization:titanomagnetite_ingot");
        final Set<String> expectedRaw = Set.of(
                "magnetization:raw_magnetite", "magnetization:raw_maghemite",
                "magnetization:raw_gallium", "magnetization:raw_lithium",
                "magnetization:raw_pyrrhotite", "magnetization:raw_hematite",
                "magnetization:raw_titanomagnetite");
        final Set<String> expectedStorage = STORAGE_FAMILIES.stream()
                .map(family -> "magnetization:" + family.block()).collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(expectedRefined, magnetizationValues("data/c/tags/item/ingots.json"),
                "Every refined material exposed through c:ingots must have an audited equipment/storage policy");
        assertEquals(expectedRaw, magnetizationValues("data/c/tags/item/raw_materials.json"),
                "Every raw material must have an audited raw-storage policy");
        assertEquals(expectedStorage, magnetizationValues("data/c/tags/item/storage_blocks.json"),
                "Every storage block must be represented in the audited round-trip matrix");
        assertEquals(expectedStorage, magnetizationValues("data/c/tags/block/storage_blocks.json"),
                "Block and item storage aggregates must agree");
    }

    private static Set<String> magnetizationValues(final String path) {
        return tagValues(path).stream().filter(value -> value.startsWith("magnetization:"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> tagValues(final String path) {
        final java.util.HashSet<String> values = new java.util.HashSet<>();
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

    private static java.io.InputStream resource(final String path) {
        final java.io.InputStream stream = MaterialFamilyCompletenessTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, () -> "Missing resource " + path);
        return stream;
    }

    private record StorageFamily(String block, String compactRecipe, String unpackRecipe, String commonTag) {}
}
