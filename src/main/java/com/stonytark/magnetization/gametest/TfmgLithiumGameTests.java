package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime contracts for bidirectional lithium interoperability with Create: TFMG. */
@GameTestHolder("magnetization_tfmg")
@PrefixGameTestTemplate(false)
public final class TfmgLithiumGameTests {
    private static final TagKey<Item> LITHIUM_INGOTS = itemTag("ingots/lithium");
    private static final TagKey<Item> RAW_LITHIUM = itemTag("raw_materials/lithium");
    private static final TagKey<Item> LITHIUM_ORES = itemTag("ores/lithium");
    private static final TagKey<Item> STONE_ORES = itemTag("ores_in_ground/stone");
    private static final TagKey<Item> DEEPSLATE_ORES = itemTag("ores_in_ground/deepslate");
    private static final TagKey<Block> LITHIUM_ORE_BLOCKS = blockTag("ores/lithium");

    private TfmgLithiumGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lithiumTagsAreBidirectionallyCompatible(final GameTestHelper helper) {
        final Item tfmgIngot = item("tfmg", "lithium_ingot");
        final Item tfmgRaw = item("tfmg", "raw_lithium");

        helper.assertTrue(new ItemStack(MagItems.LITHIUM.get()).is(LITHIUM_INGOTS),
                "Magnetization lithium is missing from c:ingots/lithium");
        helper.assertTrue(new ItemStack(tfmgIngot).is(LITHIUM_INGOTS),
                "TFMG lithium is missing from c:ingots/lithium");
        helper.assertTrue(new ItemStack(MagItems.RAW_LITHIUM.get()).is(RAW_LITHIUM),
                "Magnetization raw lithium is missing from c:raw_materials/lithium");
        helper.assertTrue(new ItemStack(tfmgRaw).is(RAW_LITHIUM),
                "TFMG raw lithium is missing from c:raw_materials/lithium");

        helper.assertTrue(new ItemStack(MagBlocks.LITHIUM_ORE.get()).is(LITHIUM_ORES)
                        && new ItemStack(MagBlocks.DEEPSLATE_LITHIUM_ORE.get()).is(LITHIUM_ORES),
                "Magnetization lithium ores are missing from c:ores/lithium");
        helper.assertTrue(MagBlocks.LITHIUM_ORE.get().builtInRegistryHolder().is(LITHIUM_ORE_BLOCKS)
                        && MagBlocks.DEEPSLATE_LITHIUM_ORE.get().builtInRegistryHolder().is(LITHIUM_ORE_BLOCKS),
                "Magnetization lithium blocks are missing from the block c:ores/lithium tag");
        helper.assertTrue(new ItemStack(MagBlocks.LITHIUM_ORE.get()).is(STONE_ORES)
                        && new ItemStack(MagBlocks.DEEPSLATE_LITHIUM_ORE.get()).is(DEEPSLATE_ORES),
                "Magnetization lithium ores are missing their c:ores_in_ground classifications");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lithiumRecipesWorkAcrossBothMods(final GameTestHelper helper) {
        final RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        final ItemStack tfmgIngot = new ItemStack(item("tfmg", "lithium_ingot"));
        final ItemStack tfmgRaw = new ItemStack(item("tfmg", "raw_lithium"));
        final ItemStack magnetizationIngot = new ItemStack(MagItems.LITHIUM.get());

        assertIngredientAccepts(helper, recipes, "magnetization", "liquid_lithium_from_lithium", tfmgIngot);
        assertIngredientAccepts(helper, recipes, "magnetization", "tritium_cell", tfmgIngot);
        assertIngredientAccepts(helper, recipes, "magnetization", "lithium_from_smelting", tfmgRaw);
        assertIngredientAccepts(helper, recipes, "magnetization", "lithium_from_blasting", tfmgRaw);
        assertIngredientAccepts(helper, recipes, "tfmg", "crafting/materials/lithium_charge", magnetizationIngot);

        assertCrushingRecipe(helper, recipes, "tfmg_crushing_lithium_ore",
                new ItemStack(MagBlocks.LITHIUM_ORE.get()));
        assertCrushingRecipe(helper, recipes, "tfmg_crushing_deepslate_lithium_ore",
                new ItemStack(MagBlocks.DEEPSLATE_LITHIUM_ORE.get()));
        assertCrushingRecipe(helper, recipes, "tfmg_crushing_raw_lithium",
                new ItemStack(MagItems.RAW_LITHIUM.get()));
        helper.succeed();
    }

    private static void assertIngredientAccepts(
            final GameTestHelper helper,
            final RecipeManager recipes,
            final String namespace,
            final String path,
            final ItemStack stack
    ) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        final RecipeHolder<?> recipe = recipes.byKey(id).orElse(null);
        helper.assertTrue(recipe != null, "Missing compatibility recipe " + id);
        helper.assertTrue(recipe.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack)),
                id + " does not accept " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static void assertCrushingRecipe(
            final GameTestHelper helper,
            final RecipeManager recipes,
            final String path,
            final ItemStack input
    ) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
        final RecipeHolder<?> recipe = recipes.byKey(id).orElse(null);
        helper.assertTrue(recipe != null && recipe.value() instanceof ProcessingRecipe<?, ?>,
                "Missing TFMG-gated Create crushing recipe " + id);
        helper.assertTrue(recipe.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(input)),
                id + " does not accept " + BuiltInRegistries.ITEM.getKey(input.getItem()));
    }

    private static TagKey<Item> itemTag(final String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Block> blockTag(final String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static Item item(final String namespace, final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalStateException("Missing required compatibility-test item " + id);
        }
        return BuiltInRegistries.ITEM.get(id);
    }
}
