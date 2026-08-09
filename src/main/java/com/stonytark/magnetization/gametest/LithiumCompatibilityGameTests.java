package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Lithium contracts that must remain valid in the normal TFMG-absent profile. */
@GameTestHolder("magnetization")
@PrefixGameTestTemplate(false)
public final class LithiumCompatibilityGameTests {
    private static final TagKey<Item> LITHIUM_INGOTS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/lithium"));
    private static final TagKey<Item> RAW_LITHIUM = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "raw_materials/lithium"));

    private LithiumCompatibilityGameTests() {}

    @GameTest(template = "empty", batch = "lithiumCompatibility", timeoutTicks = 40)
    public static void lithiumRecipesRemainSelfContainedWithoutTfmg(final GameTestHelper helper) {
        final RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        final ItemStack lithium = new ItemStack(MagItems.LITHIUM.get());
        final ItemStack rawLithium = new ItemStack(MagItems.RAW_LITHIUM.get());

        helper.assertTrue(lithium.is(LITHIUM_INGOTS),
                "Magnetization lithium must populate c:ingots/lithium without TFMG");
        helper.assertTrue(rawLithium.is(RAW_LITHIUM),
                "Magnetization raw lithium must populate c:raw_materials/lithium without TFMG");
        assertIngredientAccepts(helper, recipes, "liquid_lithium_from_lithium", lithium);
        assertIngredientAccepts(helper, recipes, "tritium_cell", lithium);
        assertIngredientAccepts(helper, recipes, "lithium_from_smelting", rawLithium);
        assertIngredientAccepts(helper, recipes, "lithium_from_blasting", rawLithium);

        if (!ModList.get().isLoaded("tfmg")) {
            helper.assertTrue(recipes.byKey(magnetization("tfmg_crushing_lithium_ore")).isEmpty()
                            && recipes.byKey(magnetization("tfmg_crushing_deepslate_lithium_ore")).isEmpty()
                            && recipes.byKey(magnetization("tfmg_crushing_raw_lithium")).isEmpty(),
                    "TFMG-only crushing recipes must not load when TFMG is absent");
        }
        helper.succeed();
    }

    private static void assertIngredientAccepts(
            final GameTestHelper helper,
            final RecipeManager recipes,
            final String path,
            final ItemStack stack
    ) {
        final ResourceLocation id = magnetization(path);
        final RecipeHolder<?> recipe = recipes.byKey(id).orElse(null);
        helper.assertTrue(recipe != null, "Missing lithium recipe " + id);
        helper.assertTrue(recipe.value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(stack)),
                id + " does not accept Magnetization's own lithium tag member");
    }

    private static ResourceLocation magnetization(final String path) {
        return ResourceLocation.fromNamespaceAndPath("magnetization", path);
    }
}
