package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.data.CompatConfigCondition;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registry, recipe, material, fluid, and dimension contracts against the
 * published Create: Cosmonautics runtime. Production integration stays
 * registry/tag based so Magnetization remains loadable without the addon. */
@GameTestHolder("magnetization_cosmonautics")
@PrefixGameTestTemplate(false)
public final class CosmonauticsGameTests {
    private static final TagKey<Fluid> ROCKET_FUEL = TagKey.create(
            Registries.FLUID, ResourceLocation.fromNamespaceAndPath("rocketnautics", "rocket_fuel"));

    private CosmonauticsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void publishedRuntimeContractIsPresent(final GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("rocketnautics"),
                "Cosmonautics must load under its runtime mod id rocketnautics");
        for (final String path : new String[]{"magnetic_stabilizer", "rocket_thruster",
                "booster_thruster", "rcs_thruster", "vector_thruster"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("rocketnautics", path);
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id),
                    "Published Cosmonautics block contract is missing " + id);
        }
        for (final String path : new String[]{"deep_space", "moon"}) {
            final ResourceLocation definition = ResourceLocation.fromNamespaceAndPath(
                    "rocketnautics", "dimension/" + path + ".json");
            helper.assertTrue(helper.getLevel().getServer().getResourceManager().getResource(definition).isPresent(),
                    "Published Cosmonautics dimension definition is missing: " + definition);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void magneticStabilizerAndHydrogenInteroperate(final GameTestHelper helper) {
        final ResourceLocation stabilizerId = ResourceLocation.fromNamespaceAndPath(
                "rocketnautics", "magnetic_stabilizer");
        final var stabilizer = BuiltInRegistries.BLOCK.get(stabilizerId);
        helper.assertTrue(stabilizer.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Cosmonautics Magnetic Stabilizer is not a ferromagnetic ship component");
        helper.assertTrue(new ItemStack(stabilizer).is(MagTags.FERROMAGNETIC_ITEMS),
                "Cosmonautics Magnetic Stabilizer item is not ferromagnetic");
        helper.assertTrue(MagFluids.HYDROGEN.get().builtInRegistryHolder().is(ROCKET_FUEL)
                        && MagFluids.HYDROGEN_FLOWING.get().builtInRegistryHolder().is(ROCKET_FUEL),
                "Magnetization source and flowing Hydrogen are not Cosmonautics rocket fuel");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void samariumCobaltSpacecraftRecipesLoad(final GameTestHelper helper) {
        final var recipes = helper.getLevel().getServer().getRecipeManager();
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("cosmonautics_magnetic_stabilizer_from_samarium_cobalt", "magnetic_stabilizer");
        expected.put("cosmonautics_rcs_thruster_from_samarium_cobalt", "rcs_thruster");
        expected.put("cosmonautics_vector_thruster_from_samarium_cobalt", "vector_thruster");
        expected.put("cosmonautics_rocket_thruster_from_samarium_cobalt", "rocket_thruster");
        expected.put("cosmonautics_booster_thruster_from_samarium_cobalt", "booster_thruster");
        final ItemStack smCoMagnet = new ItemStack(MagItems.SAMARIUM_COBALT_MAGNET.get());
        for (final var entry : expected.entrySet()) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", entry.getKey());
            final var holder = recipes.byKey(id);
            helper.assertTrue(holder.isPresent(), "Missing Cosmonautics compatibility recipe " + id);
            final var recipe = holder.orElseThrow().value();
            helper.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(smCoMagnet)),
                    "Cosmonautics compatibility recipe does not consume a Samarium-Cobalt Magnet: " + id);
            final ResourceLocation result = BuiltInRegistries.ITEM.getKey(
                    recipe.getResultItem(helper.getLevel().registryAccess()).getItem());
            helper.assertTrue(result.equals(ResourceLocation.fromNamespaceAndPath("rocketnautics", entry.getValue())),
                    "Cosmonautics compatibility recipe has the wrong output: " + id + " -> " + result);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void recipeMasterCascadesToCosmonauticsCondition(final GameTestHelper helper) {
        final boolean compat = MagConfig.COSMONAUTICS_COMPAT_ENABLED.get();
        final boolean recipes = MagConfig.COSMONAUTICS_RECIPES_ENABLED.get();
        try {
            MagConfig.COSMONAUTICS_COMPAT_ENABLED.set(false);
            MagConfig.COSMONAUTICS_RECIPES_ENABLED.set(true);
            helper.assertTrue(!MagConfig.cosmonauticsRecipesEnabled()
                            && !new CompatConfigCondition(CompatConfigCondition.Feature.COSMONAUTICS)
                            .test(ICondition.IContext.EMPTY),
                    "Cosmonautics recipe condition ignored the compatibility master switch");
            helper.succeed();
        } finally {
            MagConfig.COSMONAUTICS_COMPAT_ENABLED.set(compat);
            MagConfig.COSMONAUTICS_RECIPES_ENABLED.set(recipes);
        }
    }
}
