package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.compat.tfmg.TfmgPolarizerCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.content.jet.MhdJetBlockEntity;
import com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity;
import com.stonytark.magnetization.data.CompatConfigCondition;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.conditions.ICondition;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Full magnetic, recipe, fluid, and active-field contract against TFMG 1.2.0. */
@GameTestHolder("magnetization_tfmg")
@PrefixGameTestTemplate(false)
public final class TfmgMagnetismGameTests {
    private static final TagKey<Item> MAGNETIC_PLATES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/magnetic_alloy"));

    private TfmgMagnetismGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfmgMaterialsAndElectricalMachinesHaveMagneticRoles(final GameTestHelper helper) {
        for (final String path : new String[]{
                "magnet", "magnetic_alloy_sheet", "electromagnetic_coil", "large_coil"}) {
            helper.assertTrue(new ItemStack(item(path)).is(MagTags.FERROMAGNETIC_ITEMS),
                    "TFMG " + path + " is not a ferromagnetic item");
        }

        for (final String path : new String[]{
                "laminated_magnetic_alloy_block", "large_coil", "transformer", "large_transformer",
                "generator", "electric_motor", "rotor", "stator", "electric_pump", "converter", "polarizer"}) {
            final Block block = block(path);
            helper.assertTrue(block.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                    "TFMG " + path + " is not a ferromagnetic block");
            helper.assertTrue(block.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                    "TFMG " + path + " is not an eddy-current conductor");
        }
        final Block accumulator = block("accumulator");
        helper.assertTrue(accumulator.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "TFMG accumulator is not an eddy-current conductor");
        helper.assertTrue(!accumulator.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "TFMG accumulator should be conductive without being classified as ferromagnetic");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfmgMagnetsDriveMagnetizationMachines(final GameTestHelper helper) {
        final boolean enabled = MagConfig.EXTERNAL_MACHINE_MAGNETS_ENABLED.get();
        final int potency = MagConfig.EXTERNAL_MACHINE_MAGNET_POTENCY.get();
        try {
            MagConfig.EXTERNAL_MACHINE_MAGNETS_ENABLED.set(true);
            MagConfig.EXTERNAL_MACHINE_MAGNET_POTENCY.set(16);
            for (final String path : new String[]{"magnet", "magnetic_alloy_ingot", "magnetic_alloy_sheet"}) {
                final ItemStack stack = new ItemStack(item(path));
                helper.assertTrue(stack.is(MagTags.MACHINE_MAGNETS),
                        "TFMG " + path + " is missing from the machine-magnet tag");
                helper.assertTrue(MagneticMaterials.potency(stack) == 16,
                        "TFMG " + path + " did not receive configured external potency");
            }

            final BlockPos motorPos = new BlockPos(1, 1, 1);
            final BlockPos jetPos = new BlockPos(3, 1, 1);
            helper.setBlock(motorPos, MagBlocks.HOMOPOLAR_MOTOR.get());
            helper.setBlock(jetPos, MagBlocks.MHD_JET.get());
            final HomopolarMotorBlockEntity motor = (HomopolarMotorBlockEntity) helper.getBlockEntity(motorPos);
            final MhdJetBlockEntity jet = (MhdJetBlockEntity) helper.getBlockEntity(jetPos);
            final ItemStack magnet = new ItemStack(item("magnet"));
            helper.assertTrue(motor.magnetContainer().canPlaceItem(0, magnet),
                    "Homopolar Motor rejected TFMG's Magnet");
            helper.assertTrue(jet.magnetContainer().canPlaceItem(0, magnet),
                    "MHD Jet rejected TFMG's Magnet");

            MagConfig.EXTERNAL_MACHINE_MAGNETS_ENABLED.set(false);
            helper.assertTrue(MagneticMaterials.potency(magnet) == 0,
                    "External-machine-magnet config did not disable TFMG fuel");
            helper.succeed();
        } finally {
            MagConfig.EXTERNAL_MACHINE_MAGNETS_ENABLED.set(enabled);
            MagConfig.EXTERNAL_MACHINE_MAGNET_POTENCY.set(potency);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void magneticPlatesAndSupplementalRecipesAreBidirectional(final GameTestHelper helper) {
        final ItemStack ourPlate = new ItemStack(MagItems.MAGNETIC_PLATE.get());
        final ItemStack tfmgSheet = new ItemStack(item("magnetic_alloy_sheet"));
        helper.assertTrue(ourPlate.is(MAGNETIC_PLATES) && tfmgSheet.is(MAGNETIC_PLATES),
                "Magnetization plate and TFMG sheet do not share c:plates/magnetic_alloy");

        final RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        assertIngredientAccepts(helper, recipes, "electromagnet", tfmgSheet);
        assertIngredientAccepts(helper, recipes, "lodestone_core", tfmgSheet);
        assertIngredientAccepts(helper, recipes, "tfmg_transformer_from_magnetic_plate", ourPlate);
        assertIngredientAccepts(helper, recipes, "tfmg_laminated_magnetic_alloy_block_from_plates", ourPlate);
        assertIngredientAccepts(helper, recipes, "tfmg_press_magnetic_alloy_sheet",
                new ItemStack(MagItems.FERROMAGNETIC_INGOT.get()));
        assertIngredientAccepts(helper, recipes, "tfmg_polarize_magnet",
                new ItemStack(MagItems.FERROMAGNETIC_INGOT.get()));

        final ItemStack permanentMagnet = new ItemStack(MagBlocks.PERMANENT_MAGNET.get());
        for (final String path : new String[]{
                "tfmg_voltmeter_from_permanent_magnet", "tfmg_electric_pump_from_permanent_magnets",
                "tfmg_stator_from_permanent_magnet"}) {
            assertIngredientAccepts(helper, recipes, path, permanentMagnet);
        }
        assertSequenceUses(helper, recipes, "tfmg_generator_from_permanent_magnet", permanentMagnet);
        assertSequenceUses(helper, recipes, "tfmg_motor_from_permanent_magnet", permanentMagnet);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void tfmgIndustrialRecipesAndMoltenSteelMhdLoad(final GameTestHelper helper) {
        final RecipeManager recipes = helper.getLevel().getServer().getRecipeManager();
        assertProcessingRecipe(helper, recipes, "tfmg_ferrofluid_from_lubrication_oil");
        assertRecipePresent(helper, recipes, "tfmg_cast_solid_gallium");
        assertRecipePresent(helper, recipes, "tfmg_cast_lithium");
        assertIngredientAccepts(helper, recipes, "tfmg_industrial_blasting_raw_magnetite",
                new ItemStack(MagItems.RAW_MAGNETITE.get()));
        assertIngredientAccepts(helper, recipes, "tfmg_industrial_blasting_raw_hematite",
                new ItemStack(MagItems.RAW_HEMATITE.get()));

        final double taggedFluidConductivity = MagConfig.MHD_CONDUCTIVITY_TAGGED_FLUID.get();
        try {
            final Fluid moltenSteel = fluid("molten_steel");
            helper.assertTrue(moltenSteel.builtInRegistryHolder().is(MagTags.MHD_WORKING_FLUIDS),
                    "TFMG molten steel is missing from the MHD working-fluid tag");
            MagConfig.MHD_CONDUCTIVITY_TAGGED_FLUID.set(0.8d);
            helper.assertTrue(MhdJetBlockEntity.conductivityMult(moltenSteel) > 0.0d
                            && MhdJetBlockEntity.conductivityMult(moltenSteel)
                            < MagConfig.mhdConductivityLiquidLithium(),
                    "TFMG molten steel should have a positive multiplier below liquid lithium");
            MagConfig.MHD_CONDUCTIVITY_TAGGED_FLUID.set(0.0d);
            helper.assertTrue(MhdJetBlockEntity.conductivityMult(moltenSteel) == 0.0d,
                    "Zero tagged-fluid conductivity did not disable external MHD fluids");
            MagConfig.MHD_CONDUCTIVITY_TAGGED_FLUID.set(0.8d);

            final BlockPos jetPos = new BlockPos(1, 1, 1);
            helper.setBlock(jetPos, MagBlocks.MHD_JET.get());
            final MhdJetBlockEntity jet = (MhdJetBlockEntity) helper.getBlockEntity(jetPos);
            final FluidActionResult transfer = FluidUtil.tryEmptyContainer(
                    new ItemStack(item("molten_steel_bucket")), jet.fluidHandler(), 1000, null, true);
            helper.assertTrue(transfer.isSuccess() && transfer.getResult().is(Items.BUCKET),
                    "Generic fluid-container handling did not drain TFMG's Molten Steel Bucket");
            helper.assertTrue(jet.fluidHandler().getFluidInTank(0).is(moltenSteel)
                            && jet.fluidHandler().getFluidInTank(0).getAmount() == 1000,
                    "MHD Jet did not retain one bucket of TFMG molten steel");
            helper.succeed();
        } finally {
            MagConfig.MHD_CONDUCTIVITY_TAGGED_FLUID.set(taggedFluidConductivity);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfmgRecipeConditionsFollowServerConfig(final GameTestHelper helper) {
        final boolean processing = MagConfig.TFMG_PROCESSING_RECIPES_ENABLED.get();
        final boolean steelmaking = MagConfig.TFMG_STEELMAKING_RECIPES_ENABLED.get();
        try {
            final CompatConfigCondition processingCondition = new CompatConfigCondition(
                    CompatConfigCondition.Feature.TFMG_PROCESSING);
            final CompatConfigCondition steelmakingCondition = new CompatConfigCondition(
                    CompatConfigCondition.Feature.TFMG_STEELMAKING);
            MagConfig.TFMG_PROCESSING_RECIPES_ENABLED.set(true);
            MagConfig.TFMG_STEELMAKING_RECIPES_ENABLED.set(true);
            helper.assertTrue(processingCondition.test(ICondition.IContext.EMPTY)
                            && steelmakingCondition.test(ICondition.IContext.EMPTY),
                    "Enabled TFMG recipe configs did not admit compatibility recipes");
            MagConfig.TFMG_PROCESSING_RECIPES_ENABLED.set(false);
            MagConfig.TFMG_STEELMAKING_RECIPES_ENABLED.set(false);
            helper.assertTrue(!processingCondition.test(ICondition.IContext.EMPTY)
                            && !steelmakingCondition.test(ICondition.IContext.EMPTY),
                    "Disabled TFMG recipe configs did not reject compatibility recipes");
            helper.succeed();
        } finally {
            MagConfig.TFMG_PROCESSING_RECIPES_ENABLED.set(processing);
            MagConfig.TFMG_STEELMAKING_RECIPES_ENABLED.set(steelmaking);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "tfmgPolarizerField")
    public static void poweredTfmgPolarizerHasConfigGatedVoltageField(final GameTestHelper helper) {
        final boolean enabled = MagConfig.TFMG_POLARIZER_FIELD_ENABLED.get();
        final int extremeVoltage = MagConfig.TFMG_POLARIZER_VOLTAGE_FOR_EXTREME.get();
        final double multiplier = MagConfig.TFMG_POLARIZER_FORCE_MULTIPLIER.get();
        MagConfig.TFMG_POLARIZER_FIELD_ENABLED.set(true);
        MagConfig.TFMG_POLARIZER_VOLTAGE_FOR_EXTREME.set(500);
        MagConfig.TFMG_POLARIZER_FORCE_MULTIPLIER.set(1.0d);

        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block("polarizer"));
        helper.runAfterDelay(4, () -> {
            try {
                final BlockEntity blockEntity = helper.getBlockEntity(pos);
                helper.assertTrue(blockEntity instanceof MagneticFieldSource,
                        "TFMG Polarizer did not receive the optional MagneticFieldSource adapter");
                setVoltage(blockEntity, 500);
                helper.assertTrue(TfmgPolarizerCompat.voltage(blockEntity) == 500,
                        "TFMG Polarizer adapter could not read live network voltage");
                final MagneticField field = ((MagneticFieldSource) blockEntity).currentField();
                helper.assertTrue(field != null
                                && field.strength() == com.stonytark.magnetization.api.MagneticStrength.EXTREME
                                && Math.abs(field.force()
                                - com.stonytark.magnetization.api.MagneticStrength.EXTREME.force()) < 0.001d,
                        "500 V TFMG Polarizer did not map to the configured EXTREME field");
                helper.assertTrue(EmitterRegistry.snapshot(helper.getLevel()).contains(helper.absolutePos(pos)),
                        "TFMG Polarizer did not register as an active field source");

                MagConfig.TFMG_POLARIZER_FIELD_ENABLED.set(false);
                helper.assertTrue(((MagneticFieldSource) blockEntity).currentField() == null,
                        "Disabling TFMG Polarizer fields did not silence the adapter");
                helper.setBlock(pos, Blocks.AIR);
                helper.assertTrue(!EmitterRegistry.snapshot(helper.getLevel()).contains(helper.absolutePos(pos)),
                        "Removed TFMG Polarizer remained in the emitter registry");
                helper.succeed();
            } finally {
                MagConfig.TFMG_POLARIZER_FIELD_ENABLED.set(enabled);
                MagConfig.TFMG_POLARIZER_VOLTAGE_FOR_EXTREME.set(extremeVoltage);
                MagConfig.TFMG_POLARIZER_FORCE_MULTIPLIER.set(multiplier);
            }
        });
    }

    private static void setVoltage(final BlockEntity blockEntity, final int voltage) {
        try {
            final Method getData = blockEntity.getClass().getMethod("getData");
            final Object data = getData.invoke(blockEntity);
            final Field field = data.getClass().getField("voltage");
            field.setInt(data, voltage);
        } catch (final ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not set TFMG test voltage", ex);
        }
    }

    private static void assertIngredientAccepts(final GameTestHelper helper, final RecipeManager recipes,
                                                final String path, final ItemStack stack) {
        final RecipeHolder<?> recipe = recipe(helper, recipes, path);
        helper.assertTrue(recipe.value().getIngredients().stream().anyMatch(i -> i.test(stack)),
                "magnetization:" + path + " does not accept "
                        + BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static void assertSequenceUses(final GameTestHelper helper, final RecipeManager recipes,
                                           final String path, final ItemStack stack) {
        final RecipeHolder<?> holder = recipe(helper, recipes, path);
        helper.assertTrue(holder.value() instanceof SequencedAssemblyRecipe,
                "magnetization:" + path + " is not a sequenced assembly recipe");
        final SequencedAssemblyRecipe recipe = (SequencedAssemblyRecipe) holder.value();
        helper.assertTrue(recipe.getSequence().stream()
                        .flatMap(step -> step.getRecipe().getIngredients().stream())
                        .anyMatch(ingredient -> ingredient.test(stack)),
                "magnetization:" + path + " does not deploy a Permanent Magnet");
    }

    private static void assertProcessingRecipe(final GameTestHelper helper, final RecipeManager recipes,
                                               final String path) {
        final RecipeHolder<?> recipe = recipe(helper, recipes, path);
        helper.assertTrue(recipe.value() instanceof ProcessingRecipe<?, ?>,
                "magnetization:" + path + " is not a Create processing recipe");
    }

    private static void assertRecipePresent(final GameTestHelper helper, final RecipeManager recipes,
                                            final String path) {
        recipe(helper, recipes, path);
    }

    private static RecipeHolder<?> recipe(final GameTestHelper helper, final RecipeManager recipes,
                                          final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
        final RecipeHolder<?> recipe = recipes.byKey(id).orElse(null);
        helper.assertTrue(recipe != null, "Missing TFMG compatibility recipe " + id);
        return recipe;
    }

    private static Item item(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tfmg", path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Missing TFMG item " + id);
        return BuiltInRegistries.ITEM.get(id);
    }

    private static Block block(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tfmg", path);
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Missing TFMG block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static Fluid fluid(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tfmg", path);
        if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalStateException("Missing TFMG fluid " + id);
        return BuiltInRegistries.FLUID.get(id);
    }
}
