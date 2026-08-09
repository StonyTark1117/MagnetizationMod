package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.PickaxeItem;
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

    private MaterialFamilyGameTests() {}

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void storageBlocksPlaceAndRoundTripRecipesLoad(final GameTestHelper helper) {
        final List<Block> blocks = List.of(
                MagBlocks.FERROMAGNETIC_BLOCK.get(), MagBlocks.LITHIUM_BLOCK.get(),
                MagBlocks.RAW_LITHIUM_BLOCK.get(), MagBlocks.RAW_GALLIUM_BLOCK.get());
        for (int i = 0; i < blocks.size(); i++) {
            final BlockPos pos = new BlockPos(i, 1, 1);
            helper.setBlock(pos, blocks.get(i));
            helper.assertBlockPresent(blocks.get(i), pos);
        }

        final var recipes = helper.getLevel().getServer().getRecipeManager();
        for (final String id : List.of(
                "ferromagnetic_block", "ferromagnetic_ingot_from_block",
                "lithium_block", "lithium_from_block",
                "raw_lithium_block", "raw_lithium_from_block",
                "raw_gallium_block", "raw_gallium_from_block")) {
            final ResourceLocation key = Magnetization.id(id);
            helper.assertTrue(recipes.byKey(key).isPresent(), "Missing storage recipe " + key);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void equipmentFamiliesRegisterWithExpectedItemTypes(final GameTestHelper helper) {
        helper.assertTrue(MagItems.LITHIUM_SWORD.get() instanceof SwordItem, "Lithium sword not registered");
        helper.assertTrue(MagItems.PYRRHOTITE_PICKAXE.get() instanceof PickaxeItem, "Pyrrhotite pickaxe not registered");
        helper.assertTrue(MagItems.HEMATITE_CHESTPLATE.get() instanceof ArmorItem, "Hematite chestplate not registered");
        helper.assertTrue(MagItems.TITANOMAGNETITE_BOOTS.get() instanceof ArmorItem, "Titanomagnetite boots not registered");

        final var recipes = helper.getLevel().getServer().getRecipeManager();
        for (final String material : List.of("lithium", "pyrrhotite", "hematite", "titanomagnetite")) {
            for (final String part : List.of("sword", "pickaxe", "axe", "shovel", "hoe",
                    "helmet", "chestplate", "leggings", "boots", "horse_armor")) {
                final ResourceLocation key = Magnetization.id(material + "_" + part);
                helper.assertTrue(recipes.byKey(key).isPresent(), "Missing equipment recipe " + key);
            }
        }
        helper.succeed();
    }
}
