package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.ExternalFieldCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
                "alexscaves_permanent_magnet_from_neodymium",
                "alexscaves_azure_magnet_from_permanent_magnet",
                "alexscaves_scarlet_magnet_from_permanent_magnet",
                "alexscaves_levitation_rail_from_permanent_magnets",
                "alexscaves_ferrofluid_from_ferrouslime",
                "createaddition_electric_motor_from_permanent_magnet",
                "createaddition_alternator_from_permanent_magnet"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
            helper.assertTrue(recipes.byKey(id).isEmpty(),
                    "Optional compatibility recipe loaded without its mod: " + id);
        }
        helper.succeed();
    }
}
