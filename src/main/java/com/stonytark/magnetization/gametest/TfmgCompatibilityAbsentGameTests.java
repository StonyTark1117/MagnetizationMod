package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.content.jet.MhdJetBlockEntity;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** TFMG compatibility resources must remain soft when TFMG is absent. */
@GameTestHolder("magnetization")
@PrefixGameTestTemplate(false)
public final class TfmgCompatibilityAbsentGameTests {
    private TfmgCompatibilityAbsentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfmgCompatibilityRemainsOptional(final GameTestHelper helper) {
        helper.assertTrue(MagneticMaterials.potency(new ItemStack(MagItems.FERROMAGNETIC_INGOT.get())) > 0,
                "Tag-backed foreign magnets broke native machine magnets");
        helper.assertTrue(MagFluids.GALLIUM.get().builtInRegistryHolder().is(MagTags.MHD_WORKING_FLUIDS)
                        && MhdJetBlockEntity.conductivityMult(MagFluids.GALLIUM.get()) > 0.0d,
                "Tagged MHD working fluids broke native gallium");
        if (!ModList.get().isLoaded("tfmg")) {
            for (final String path : new String[]{
                    "tfmg_ferrofluid_from_lubrication_oil", "tfmg_cast_solid_gallium", "tfmg_cast_lithium",
                    "tfmg_press_magnetic_alloy_sheet", "tfmg_polarize_magnet",
                    "tfmg_polarize_samarium_cobalt_magnet", "tfmg_polarize_neodymium_magnet",
                    "air_filter_from_tfmg_plastic",
                    "tfmg_transformer_from_magnetic_plate", "tfmg_laminated_magnetic_alloy_block_from_plates",
                    "tfmg_voltmeter_from_permanent_magnet", "tfmg_electric_pump_from_permanent_magnets",
                    "tfmg_stator_from_permanent_magnet", "tfmg_generator_from_permanent_magnet",
                    "tfmg_motor_from_permanent_magnet", "tfmg_industrial_blasting_raw_magnetite",
                    "tfmg_industrial_blasting_raw_hematite"}) {
                helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(
                                ResourceLocation.fromNamespaceAndPath("magnetization", path)).isEmpty(),
                        "TFMG-only recipe loaded without TFMG: " + path);
            }
        }
        helper.succeed();
    }
}
