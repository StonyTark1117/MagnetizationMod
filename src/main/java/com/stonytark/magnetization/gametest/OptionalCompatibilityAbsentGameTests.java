package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.data.CompatConfigCondition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Proves the primary compatibility package remains inert and loadable when its
 * optional mods are absent from the minimal GameTest profile. */
@GameTestHolder("magnetization_compat_absent")
@PrefixGameTestTemplate(false)
public final class OptionalCompatibilityAbsentGameTests {
    private OptionalCompatibilityAbsentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void optionalAdaptersAndRecipesStayAbsent(final GameTestHelper helper) {
        for (final String mod : new String[]{"create_new_age", "immersiveengineering", "alexscaves",
                "createaddition", "createbigcannons", "createdieselgenerators", "createendertransmission"}) {
            helper.assertTrue(!ModList.get().isLoaded(mod),
                    "Minimal absent-mod profile unexpectedly contains " + mod);
        }
        helper.assertTrue(!ExternalFieldCompat.isKnownEmitter(Blocks.AIR.defaultBlockState()),
                "External emitter adapter matched a vanilla air block");
        final var recipes = helper.getLevel().getServer().getRecipeManager();
        for (final String path : new String[]{
                "create_new_age_basic_motor_from_permanent_magnet",
                "create_new_age_generator_coil_from_permanent_magnets",
                "create_new_age_energising_permanent_magnet",
                "immersiveengineering_mixer_ferrofluid",
                "immersiveengineering_metal_press_magnetic_plate",
                "immersiveengineering_metal_press_samarium_cobalt_plate",
                "immersiveengineering_metal_press_neodymium_alloy_plate",
                "air_filter_from_immersiveengineering_plastic",
                "alexscaves_permanent_magnet_from_neodymium",
                "alexscaves_azure_magnet_from_permanent_magnet",
                "alexscaves_scarlet_magnet_from_permanent_magnet",
                "alexscaves_levitation_rail_from_permanent_magnets",
                "alexscaves_ferrofluid_from_ferrouslime",
                "createaddition_electric_motor_from_permanent_magnet",
                "createaddition_alternator_from_permanent_magnet",
                "tfmg_polarize_samarium_cobalt_magnet",
                "tfmg_polarize_neodymium_magnet"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
            helper.assertTrue(recipes.byKey(id).isEmpty(),
                    "Optional compatibility recipe loaded without its mod: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void everyOptionalPackageMasterDisablesItsEntryPoints(final GameTestHelper helper) {
        final Map<net.neoforged.neoforge.common.ModConfigSpec.BooleanValue, Boolean> originals =
                new LinkedHashMap<>();
        for (final var value : new net.neoforged.neoforge.common.ModConfigSpec.BooleanValue[]{
                MagConfig.AEROPORTALS_COMPAT_ENABLED,
                MagConfig.IMMERSIVE_PORTALS_COMPAT_ENABLED,
                MagConfig.COPYCATS_COMPAT_ENABLED,
                MagConfig.CURIOS_COMPAT_ENABLED,
                MagConfig.TFMG_COMPAT_ENABLED,
                MagConfig.SIMULATED_COASTERS_COMPAT_ENABLED,
                MagConfig.STEAM_N_RAILS_COMPAT_ENABLED,
                MagConfig.PATCHOULI_COMPAT_ENABLED,
                MagConfig.JUST_ENOUGH_RESOURCES_COMPAT_ENABLED,
                MagConfig.JADE_COMPAT_ENABLED,
                MagConfig.WTHIT_COMPAT_ENABLED,
                MagConfig.THE_ONE_PROBE_COMPAT_ENABLED,
                MagConfig.JEI_COMPAT_ENABLED,
                MagConfig.REI_COMPAT_ENABLED,
                MagConfig.EMI_COMPAT_ENABLED}) {
            originals.put(value, value.get());
        }
        try {
            originals.keySet().forEach(value -> value.set(false));
            helper.assertTrue(!MagConfig.aeroPortalsCompatEnabled()
                            && !MagConfig.immersivePortalsCompatEnabled()
                            && !MagConfig.copycatsCompatEnabled()
                            && !MagConfig.curiosCompatEnabled()
                            && !MagConfig.justEnoughResourcesCompatEnabled(),
                    "Core optional-package master switch remained enabled");
            helper.assertTrue(!MagConfig.simulatedCoastersFieldReaction()
                            && !MagConfig.simulatedCoastersStructuralInducer()
                            && !MagConfig.steamRailsFieldReaction(),
                    "Transport compatibility master did not cascade to its behavior toggles");
            helper.assertTrue(!MagConfig.tfmgProcessingRecipesEnabled()
                            && !MagConfig.tfmgSteelmakingRecipesEnabled()
                            && !MagConfig.tfmgPolarizerFieldEnabled(),
                    "TFMG master did not cascade to recipes and Polarizer fields");
            helper.assertTrue(!new CompatConfigCondition(CompatConfigCondition.Feature.TFMG_COMPAT)
                            .test(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY)
                            && !new CompatConfigCondition(CompatConfigCondition.Feature.PATCHOULI)
                            .test(net.neoforged.neoforge.common.conditions.ICondition.IContext.EMPTY),
                    "Recipe master condition remained enabled");
            helper.assertTrue(!MagConfig.jadeCompatEnabled() && !MagConfig.wthitCompatEnabled()
                            && !MagConfig.theOneProbeCompatEnabled() && !MagConfig.jeiCompatEnabled()
                            && !MagConfig.reiCompatEnabled() && !MagConfig.emiCompatEnabled(),
                    "HUD or recipe-viewer master remained enabled");
            helper.succeed();
        } finally {
            originals.forEach((value, original) -> value.set(original));
        }
    }
}
