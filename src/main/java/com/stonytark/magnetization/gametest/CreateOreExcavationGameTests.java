package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.data.CompatConfigCondition;
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
                "ore_vein_type/bastnasite", "drilling/bastnasite",
                "ore_vein_type/monazite", "drilling/monazite",
                "ore_vein_type/cobaltite", "drilling/cobaltite",
                "ore_vein_type/borax", "drilling/borax",
                "ore_vein_type/helium_3", "extracting/helium_3"}) {
            if (recipes.byKey(ResourceLocation.fromNamespaceAndPath("magnetization", recipe)).isEmpty()) {
                missing.add(recipe);
            }
        }
        helper.assertTrue(missing.isEmpty(), "Missing Create Ore Excavation compatibility recipes: " + missing);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void rareEarthVeinsHaveIndependentConfigGates(final GameTestHelper helper) {
        final var gates = java.util.Map.of(
                CompatConfigCondition.Feature.ORE_EXCAVATION_BASTNASITE,
                MagConfig.CREATE_ORE_EXCAVATION_BASTNASITE_VEIN_ENABLED,
                CompatConfigCondition.Feature.ORE_EXCAVATION_MONAZITE,
                MagConfig.CREATE_ORE_EXCAVATION_MONAZITE_VEIN_ENABLED,
                CompatConfigCondition.Feature.ORE_EXCAVATION_COBALTITE,
                MagConfig.CREATE_ORE_EXCAVATION_COBALTITE_VEIN_ENABLED,
                CompatConfigCondition.Feature.ORE_EXCAVATION_BORAX,
                MagConfig.CREATE_ORE_EXCAVATION_BORAX_VEIN_ENABLED);
        final boolean master = MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.get();
        final var originals = new java.util.LinkedHashMap<
                net.neoforged.neoforge.common.ModConfigSpec.BooleanValue, Boolean>();
        gates.values().forEach(value -> originals.put(value, value.get()));
        try {
            MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(true);
            for (final var entry : gates.entrySet()) {
                entry.getValue().set(false);
                helper.assertTrue(!new CompatConfigCondition(entry.getKey())
                                .test(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY),
                        entry.getKey().serializedName() + " ignored its independent config switch");
                entry.getValue().set(true);
            }
            helper.succeed();
        } finally {
            MagConfig.ORE_EXCAVATION_COMPAT_ENABLED.set(master);
            originals.forEach((value, original) -> value.set(original));
        }
    }
}
