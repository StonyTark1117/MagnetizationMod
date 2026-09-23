package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the optional Ironworks registry IDs against the published 1.21.1 artifact. */
@GameTestHolder("magnetization_ironworks")
@PrefixGameTestTemplate(false)
public final class IronworksGameTests {
    private IronworksGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void metalArmorAndMaterialsAreMagnetized(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("create_ironworks"),
                "Create: Ironworks is not loaded in its isolated compatibility profile");
        for (final String material : new String[]{"copper", "bronze", "brass", "steel", "sturdy"}) {
            for (final String slot : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                final String id = material + "_armor_" + slot;
                final Item item = item(id);
                helper.assertTrue(item != Items.AIR,
                        "Published Ironworks armor item is missing: create_ironworks:" + id);
                helper.assertTrue(new ItemStack(item).is(MagTags.METAL_ARMOR),
                        "Ironworks metal armor is missing from magnetization:metal_armor: " + id);
            }
        }

        for (final String id : new String[]{"tin_nugget", "bronze_nugget", "steel_nugget",
                "tin_sheet", "bronze_sheet"}) {
            final Item item = item(id);
            helper.assertTrue(item != Items.AIR,
                    "Published Ironworks material item is missing: create_ironworks:" + id);
            helper.assertTrue(new ItemStack(item).is(MagTags.FERROMAGNETIC_ITEMS),
                    "Ironworks material is missing from magnetization:ferromagnetic: " + id);
        }

        final var sturdySheet = BuiltInRegistries.BLOCK.get(id("sturdy_sheet_block"));
        helper.assertTrue(sturdySheet != net.minecraft.world.level.block.Blocks.AIR,
                "Published Ironworks sturdy sheet block is missing");
        helper.assertTrue(sturdySheet.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Ironworks sturdy sheet block is missing from ferromagnetic ship materials");
        helper.assertTrue(!new ItemStack(item("rose_quartz_armor_chestplate")).is(MagTags.METAL_ARMOR),
                "Nonmetal Rose Quartz armor was included in Magnetization's metal-armor tag");
        helper.succeed();
    }

    private static Item item(final String path) {
        return BuiltInRegistries.ITEM.get(id(path));
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath("create_ironworks", path);
    }
}
