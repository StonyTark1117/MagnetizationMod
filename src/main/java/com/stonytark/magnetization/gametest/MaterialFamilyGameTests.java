package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Runtime registration and recipe coverage for complete material families. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialFamilyGameTests {

    private static final String EMPTY_TEMPLATE = "empty";
    private static final List<String> SOLID_MATERIALS = List.of(
            "magnetite", "maghemite", "ferromagnetic", "gallium",
            "lithium", "pyrrhotite", "hematite", "titanomagnetite");
    private static final List<String> STORAGE_BLOCKS = List.of(
            "magnetite_block", "raw_magnetite_block", "maghemite_block", "raw_maghemite_block",
            "ferromagnetic_block", "pyrrhotite_block", "raw_pyrrhotite_block",
            "hematite_block", "raw_hematite_block", "titanomagnetite_block",
            "raw_titanomagnetite_block", "lithium_block", "raw_lithium_block",
            "solid_gallium", "raw_gallium_block", "solid_helium_3");
    private static final List<String> STORAGE_RECIPES = List.of(
            "magnetite_block", "magnetite_ingot_from_block",
            "raw_magnetite_block", "raw_magnetite_from_block",
            "maghemite_block", "maghemite_ingot_from_block",
            "raw_maghemite_block", "raw_maghemite_from_block",
            "ferromagnetic_block", "ferromagnetic_ingot_from_block",
            "pyrrhotite_block", "pyrrhotite_ingot_from_block",
            "raw_pyrrhotite_block", "raw_pyrrhotite_from_block",
            "hematite_block", "hematite_ingot_from_block",
            "raw_hematite_block", "raw_hematite_from_block",
            "titanomagnetite_block", "titanomagnetite_ingot_from_block",
            "raw_titanomagnetite_block", "raw_titanomagnetite_from_block",
            "lithium_block", "lithium_from_block",
            "raw_lithium_block", "raw_lithium_from_block",
            "solid_gallium", "gallium_ingot_from_block",
            "raw_gallium_block", "raw_gallium_from_block",
            "solid_helium_3", "helium_3_crystal_from_block");

    private MaterialFamilyGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void storageBlocksPlaceAndRoundTripRecipesLoad(final GameTestHelper helper) {
        for (int i = 0; i < STORAGE_BLOCKS.size(); i++) {
            final String id = STORAGE_BLOCKS.get(i);
            final Block block = BuiltInRegistries.BLOCK.get(Magnetization.id(id));
            helper.assertTrue(block != null, "Storage block not registered: " + id);
            final BlockPos pos = new BlockPos(i % 4, 1, i / 4);
            helper.setBlock(pos, block);
            helper.assertBlockPresent(block, pos);
        }

        final var recipes = helper.getLevel().getServer().getRecipeManager();
        for (final String id : STORAGE_RECIPES) {
            final ResourceLocation key = Magnetization.id(id);
            helper.assertTrue(recipes.byKey(key).isPresent(), "Missing storage recipe " + key);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void equipmentFamiliesRegisterWithExpectedItemTypes(final GameTestHelper helper) {
        final var recipes = helper.getLevel().getServer().getRecipeManager();
        for (final String material : SOLID_MATERIALS) {
            for (final String part : List.of("sword", "pickaxe", "axe", "shovel", "hoe",
                    "helmet", "chestplate", "leggings", "boots", "horse_armor")) {
                final String id = material + "_" + part;
                final ResourceLocation key = Magnetization.id(id);
                final Item item = BuiltInRegistries.ITEM.get(key);
                helper.assertTrue(item != null && hasExpectedType(item, part),
                        id + " has the wrong item type: " + item);
                helper.assertTrue(recipes.byKey(key).isPresent(), "Missing equipment recipe " + key);
                final ItemStack stack = new ItemStack(item);
                if (List.of("sword", "pickaxe", "axe", "shovel", "hoe").contains(part)) {
                    helper.assertTrue(stack.is(MagTags.METAL_TOOLS), id + " is missing from metal_tools");
                    helper.assertTrue(isVanillaToolCategory(stack, part), id + " is missing its vanilla tool-category tag");
                    assertVanillaToolEnchantability(helper, stack, id, part);
                } else {
                    helper.assertTrue(stack.is(MagTags.METAL_ARMOR), id + " is missing from metal_armor");
                    if (!part.equals("horse_armor")) {
                        helper.assertTrue(isVanillaArmorCategory(stack, part), id + " is missing its vanilla armor-slot tag");
                        helper.assertTrue(stack.is(ItemTags.ARMOR_ENCHANTABLE), id + " is not armor-enchantable");
                        helper.assertTrue(stack.is(ItemTags.DURABILITY_ENCHANTABLE), id + " cannot receive durability enchantments");
                    }
                }
            }
        }

        // MR equipment is a deliberately fluid, field-hardening special family:
        // complete tools/armor/barding, but intentionally excluded from the metal
        // tags so fields harden it rather than pulling the wearer.
        for (final String part : List.of("sword", "pickaxe", "axe", "shovel", "hoe")) {
            assertSpecialEquipment(helper, recipes, "mr_fluid_" + part, part, MagTags.METAL_TOOLS);
        }
        for (final String part : List.of("helmet", "chestplate", "leggings", "boots")) {
            assertSpecialEquipment(helper, recipes, "mr_liquid_" + part, part, MagTags.METAL_ARMOR);
        }
        assertSpecialEquipment(helper, recipes, "mr_fluid_horse_armor", "horse_armor", MagTags.METAL_ARMOR);

        final ItemStack dampeningBoots = stack("magnetoresistive_boots");
        helper.assertTrue(dampeningBoots.is(ItemTags.FOOT_ARMOR), "Magnetoresistive Boots are missing from foot_armor");
        helper.assertTrue(dampeningBoots.is(ItemTags.DURABILITY_ENCHANTABLE),
                "Magnetoresistive Boots cannot receive durability enchantments");
        for (final String id : List.of("magnetic_elytra", "alfven_backpack")) {
            final ItemStack wearable = stack(id);
            helper.assertTrue(wearable.is(ItemTags.DURABILITY_ENCHANTABLE), id + " cannot receive durability enchantments");
            helper.assertTrue(wearable.is(ItemTags.EQUIPPABLE_ENCHANTABLE), id + " is not equippable-enchantable");
            helper.assertTrue(wearable.is(ItemTags.VANISHING_ENCHANTABLE), id + " cannot receive Curse of Vanishing");
        }
        helper.succeed();
    }

    private static void assertSpecialEquipment(final GameTestHelper helper,
                                               final net.minecraft.world.item.crafting.RecipeManager recipes,
                                               final String id, final String part,
                                               final net.minecraft.tags.TagKey<Item> excludedTag) {
        final ResourceLocation key = Magnetization.id(id);
        final Item item = BuiltInRegistries.ITEM.get(key);
        helper.assertTrue(item != null && hasExpectedType(item, part), id + " has the wrong item type: " + item);
        helper.assertTrue(recipes.byKey(key).isPresent(), "Missing equipment recipe " + key);
        final ItemStack stack = new ItemStack(item);
        helper.assertTrue(!stack.is(excludedTag), id + " must remain outside " + excludedTag.location());
        if (List.of("sword", "pickaxe", "axe", "shovel", "hoe").contains(part)) {
            helper.assertTrue(isVanillaToolCategory(stack, part), id + " is missing its vanilla tool-category tag");
            assertVanillaToolEnchantability(helper, stack, id, part);
        } else if (!part.equals("horse_armor")) {
            helper.assertTrue(isVanillaArmorCategory(stack, part), id + " is missing its vanilla armor-slot tag");
            helper.assertTrue(stack.is(ItemTags.ARMOR_ENCHANTABLE), id + " is not armor-enchantable");
            helper.assertTrue(stack.is(ItemTags.DURABILITY_ENCHANTABLE), id + " cannot receive durability enchantments");
        }
    }

    private static void assertVanillaToolEnchantability(final GameTestHelper helper, final ItemStack stack,
                                                         final String id, final String part) {
        helper.assertTrue(stack.is(ItemTags.DURABILITY_ENCHANTABLE), id + " cannot receive durability enchantments");
        if (part.equals("sword")) {
            helper.assertTrue(stack.is(ItemTags.SWORD_ENCHANTABLE), id + " is not sword-enchantable");
        } else {
            helper.assertTrue(stack.is(ItemTags.MINING_ENCHANTABLE), id + " is not mining-enchantable");
        }
    }

    private static ItemStack stack(final String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Magnetization.id(id)));
    }

    private static boolean hasExpectedType(final Item item, final String part) {
        return switch (part) {
            case "sword" -> item instanceof SwordItem;
            case "pickaxe" -> item instanceof PickaxeItem;
            case "axe" -> item instanceof AxeItem;
            case "shovel" -> item instanceof ShovelItem;
            case "hoe" -> item instanceof HoeItem;
            case "helmet", "chestplate", "leggings", "boots" -> item instanceof ArmorItem;
            case "horse_armor" -> item instanceof AnimalArmorItem;
            default -> false;
        };
    }

    private static boolean isVanillaToolCategory(final ItemStack stack, final String part) {
        return switch (part) {
            case "sword" -> stack.is(ItemTags.SWORDS);
            case "pickaxe" -> stack.is(ItemTags.PICKAXES);
            case "axe" -> stack.is(ItemTags.AXES);
            case "shovel" -> stack.is(ItemTags.SHOVELS);
            case "hoe" -> stack.is(ItemTags.HOES);
            default -> false;
        };
    }

    private static boolean isVanillaArmorCategory(final ItemStack stack, final String part) {
        return switch (part) {
            case "helmet" -> stack.is(ItemTags.HEAD_ARMOR);
            case "chestplate" -> stack.is(ItemTags.CHEST_ARMOR);
            case "leggings" -> stack.is(ItemTags.LEG_ARMOR);
            case "boots" -> stack.is(ItemTags.FOOT_ARMOR);
            default -> false;
        };
    }
}
