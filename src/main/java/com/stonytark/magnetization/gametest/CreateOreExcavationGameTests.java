package com.stonytark.magnetization.gametest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

/** Validates that the optional Create Ore Excavation recipes survive data loading. */
@GameTestHolder("magnetization_create_ore_excavation")
@PrefixGameTestTemplate(false)
public final class CreateOreExcavationGameTests {
    private CreateOreExcavationGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void magnetizationVeinsAreRegistered(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("createoreexcavation"),
                "Create Ore Excavation compatibility profile did not load the addon");
        final RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        final var missing = new java.util.ArrayList<String>();
        for (final String recipe : new String[] {
                "ore_vein_type/magnetite", "drilling/magnetite",
                "ore_vein_type/maghemite", "drilling/maghemite",
                "ore_vein_type/pyrrhotite", "drilling/pyrrhotite",
                "ore_vein_type/hematite", "drilling/hematite",
                "ore_vein_type/titanomagnetite", "drilling/titanomagnetite",
                "ore_vein_type/lithium", "drilling/lithium",
                "ore_vein_type/helium_3", "extracting/helium_3"}) {
            if (recipes.byKey(ResourceLocation.fromNamespaceAndPath("magnetization", recipe)).isEmpty()) {
                missing.add(recipe);
            }
        }
        helper.assertTrue(missing.isEmpty(), "Missing Create Ore Excavation compatibility recipes: " + missing);
        helper.succeed();
    }
}
