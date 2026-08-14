package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.fluid.CoolantFluids;
import com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity;
import com.stonytark.magnetization.content.gas.GasExcitationProfiles;
import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import com.stonytark.magnetization.content.gas.GasVentBlockEntity;
import com.stonytark.magnetization.content.gas.ProxyGasCloudBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runtime contracts against the published Create: The Factory Must Grow build. */
@GameTestHolder("magnetization_tfmg")
@PrefixGameTestTemplate(false)
public final class TfmgHydrogenGameTests {
    private static final TagKey<Fluid> TFMG_GASES = TagKey.create(
            net.minecraft.core.registries.Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("tfmg", "gas"));
    private static final TagKey<Fluid> TFMG_FLAMMABLE = TagKey.create(
            net.minecraft.core.registries.Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("tfmg", "flammable"));
    private static final TagKey<Fluid> COMMON_GASEOUS = TagKey.create(
            net.minecraft.core.registries.Registries.FLUID,
            ResourceLocation.fromNamespaceAndPath("c", "gaseous"));

    private TfmgHydrogenGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "tfmgCooling")
    public static void tfmgCoolingFluidWorksWithoutMakingFuelsCoolant(final GameTestHelper helper) {
        final boolean originalMaster = MagConfig.TFMG_COMPAT_ENABLED.get();
        final boolean originalCooling = MagConfig.TFMG_COOLING_FLUID_ENABLED.get();
        final Fluid cooling = fluid("tfmg", "cooling_fluid");
        final Fluid flowingCooling = fluid("tfmg", "flowing_cooling_fluid");
        final ItemStack coolingBucket = new ItemStack(item("tfmg", "cooling_fluid_bucket"));
        try {
            MagConfig.TFMG_COMPAT_ENABLED.set(true);
            MagConfig.TFMG_COOLING_FLUID_ENABLED.set(true);
            helper.assertTrue(cooling.builtInRegistryHolder().is(MagTags.COOLING_FLUIDS)
                            && flowingCooling.builtInRegistryHolder().is(MagTags.COOLING_FLUIDS),
                    "TFMG Cooling Fluid variants are missing from c:cooling_fluid");
            helper.assertTrue(coolingBucket.is(MagTags.COOLING_FLUID_BUCKETS)
                            && CoolantFluids.isCoolantBucket(coolingBucket),
                    "TFMG Cooling Fluid Bucket is missing from the shared coolant input contract");

            final BlockPos fusionPos = new BlockPos(1, 1, 1);
            helper.setBlock(fusionPos, MagBlocks.FUSION_THRUSTER.get());
            final FusionThrusterBlockEntity fusion =
                    (FusionThrusterBlockEntity) helper.getBlockEntity(fusionPos);
            helper.assertTrue(fusion.fluidHandler().fill(new FluidStack(cooling, 1_000),
                            IFluidHandler.FluidAction.EXECUTE) == 1_000
                            && fusion.fluidHandler().getFluidInTank(0).isEmpty()
                            && fusion.coolantStored() == 1_000,
                    "Fusion Thruster did not route piped TFMG Cooling Fluid to its coolant tank");

            final BlockPos tokamakPos = new BlockPos(3, 1, 1);
            helper.setBlock(tokamakPos, MagBlocks.TOKAMAK_CONTROLLER.get());
            final com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity tokamak =
                    (com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity)
                            helper.getBlockEntity(tokamakPos);
            helper.assertTrue(tokamak.fuelContainer().canPlaceItem(0, coolingBucket)
                            && tokamak.fillCoolantBucket(coolingBucket)
                            && tokamak.coolantStored() == 1_000,
                    "Tokamak did not accept a TFMG Cooling Fluid Bucket");

            helper.assertTrue(CoolantFluids.quality(MagFluids.GALLIUM.get())
                            > CoolantFluids.quality(MagFluids.DEUTERIUM_OXIDE.get())
                            && CoolantFluids.quality(MagFluids.DEUTERIUM_OXIDE.get())
                            > CoolantFluids.quality(net.minecraft.world.level.material.Fluids.WATER)
                            && !CoolantFluids.isCoolant(MagFluids.HYDROGEN.get()),
                    "Gallium, heavy water, and water should follow the intended curve without making fuel gas coolant");

            MagConfig.TFMG_COOLING_FLUID_ENABLED.set(false);
            final BlockPos disabledFusionPos = new BlockPos(5, 1, 1);
            helper.setBlock(disabledFusionPos, MagBlocks.FUSION_THRUSTER.get());
            final FusionThrusterBlockEntity disabledFusion =
                    (FusionThrusterBlockEntity) helper.getBlockEntity(disabledFusionPos);
            helper.assertTrue(!MagConfig.tfmgCoolingFluidEnabled()
                            && CoolantFluids.quality(cooling) == 0.0d
                            && !CoolantFluids.isCoolantBucket(coolingBucket)
                            && disabledFusion.fluidHandler().fill(new FluidStack(cooling, 1_000),
                                    IFluidHandler.FluidAction.EXECUTE) == 0
                            && CoolantFluids.isCoolant(net.minecraft.world.level.material.Fluids.WATER)
                            && CoolantFluids.isCoolant(MagFluids.GALLIUM.get()),
                    "Dedicated TFMG cooling toggle did not reject TFMG input while preserving built-in coolants");
        } finally {
            MagConfig.TFMG_COOLING_FLUID_ENABLED.set(originalCooling);
            MagConfig.TFMG_COMPAT_ENABLED.set(originalMaster);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void hydrogenTagsAreBidirectionallyCompatible(final GameTestHelper helper) {
        final Fluid tfmgHydrogen = fluid("tfmg", "hydrogen");
        final Fluid tfmgFlowingHydrogen = fluid("tfmg", "flowing_hydrogen");
        final Item tfmgHydrogenBucket = item("tfmg", "hydrogen_bucket");

        helper.assertTrue(tfmgHydrogen.builtInRegistryHolder().is(MagTags.HYDROGEN_FLUIDS),
                "TFMG hydrogen is missing from the shared c:hydrogen fluid tag");
        helper.assertTrue(tfmgFlowingHydrogen.builtInRegistryHolder().is(MagTags.HYDROGEN_FLUIDS),
                "TFMG flowing hydrogen is missing from the shared c:hydrogen fluid tag");
        helper.assertTrue(MagFluids.HYDROGEN.get().builtInRegistryHolder().is(MagTags.HYDROGEN_FLUIDS),
                "Magnetization hydrogen is missing from the shared c:hydrogen fluid tag");
        helper.assertTrue(new ItemStack(tfmgHydrogenBucket).is(MagTags.HYDROGEN_BUCKETS),
                "TFMG Hydrogen Tank is missing from c:buckets/hydrogen");
        helper.assertTrue(new ItemStack(MagItems.HYDROGEN_BUCKET.get()).is(MagTags.HYDROGEN_BUCKETS),
                "Magnetization Hydrogen Bucket is missing from c:buckets/hydrogen");

        for (final Fluid gas : new Fluid[]{
                MagFluids.HYDROGEN.get(), MagFluids.HYDROGEN_FLOWING.get(),
                MagFluids.TRITIUM.get(), MagFluids.TRITIUM_FLOWING.get(),
                MagFluids.HELIUM_3.get(), MagFluids.HELIUM_3_FLOWING.get(),
                MagFluids.HELIUM.get(), MagFluids.HELIUM_FLOWING.get(),
                MagFluids.NEON.get(), MagFluids.NEON_FLOWING.get(),
                MagFluids.ARGON.get(), MagFluids.ARGON_FLOWING.get(),
                MagFluids.KRYPTON.get(), MagFluids.KRYPTON_FLOWING.get(),
                MagFluids.XENON.get(), MagFluids.XENON_FLOWING.get(),
                MagFluids.RADON.get(), MagFluids.RADON_FLOWING.get()}) {
            helper.assertTrue(gas.builtInRegistryHolder().is(TFMG_GASES),
                    BuiltInRegistries.FLUID.getKey(gas) + " is missing from tfmg:gas");
            helper.assertTrue(gas.builtInRegistryHolder().is(COMMON_GASEOUS),
                    BuiltInRegistries.FLUID.getKey(gas) + " is missing from c:gaseous");
        }
        helper.assertTrue(MagFluids.HYDROGEN.get().builtInRegistryHolder().is(TFMG_FLAMMABLE)
                        && MagFluids.TRITIUM.get().builtInRegistryHolder().is(TFMG_FLAMMABLE),
                "Combustible Magnetization gases should share TFMG's flammable classification");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void tfmgHydrogenWorksInMagnetizationAndViceVersa(final GameTestHelper helper) {
        final Fluid tfmgHydrogen = fluid("tfmg", "hydrogen");
        final ItemStack tfmgHydrogenBucket = new ItemStack(item("tfmg", "hydrogen_bucket"));
        final BlockPos thrusterPos = new BlockPos(1, 1, 1);
        helper.setBlock(thrusterPos, MagBlocks.FUSION_THRUSTER.get());
        final FusionThrusterBlockEntity thruster =
                (FusionThrusterBlockEntity) helper.getBlockEntity(thrusterPos);

        helper.assertTrue(thruster.fluidHandler().isFluidValid(0, new FluidStack(tfmgHydrogen, 1000)),
                "Fusion Thruster should accept piped TFMG hydrogen");
        helper.assertTrue(thruster.bucketContainer().canPlaceItem(0, tfmgHydrogenBucket),
                "Fusion Thruster should accept a TFMG Hydrogen Tank in its input slot");
        helper.assertTrue(thruster.fillFromBucket(tfmgHydrogenBucket),
                "Fusion Thruster should drain a TFMG Hydrogen Tank");
        helper.assertTrue(thruster.fluidHandler().getFluidInTank(0).is(MagTags.HYDROGEN_FLUIDS)
                        && thruster.fluidHandler().getFluidInTank(0).getAmount() == 1000,
                "Fusion Thruster should retain 1000 mB of compatible hydrogen");

        final var recipes = helper.getLevel().getServer().getRecipeManager();
        final var deuterium = recipes.byKey(ResourceLocation.fromNamespaceAndPath(
                "magnetization", "deuterium_cell_from_hydrogen"));
        helper.assertTrue(deuterium.isPresent()
                        && deuterium.get().value().getIngredients().stream()
                        .anyMatch(ingredient -> ingredient.test(tfmgHydrogenBucket)),
                "TFMG Hydrogen Tank should satisfy Magnetization's deuterium recipe");

        final var tfmgFilling = recipes.byKey(ResourceLocation.fromNamespaceAndPath(
                "magnetization", "tfmg_hydrogen_tank_from_compatible_hydrogen"));
        helper.assertTrue(tfmgFilling.isPresent()
                        && tfmgFilling.get().value() instanceof ProcessingRecipe<?, ?> processing
                        && processing.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.BUCKET)))
                        && processing.getFluidIngredients().stream()
                        .anyMatch(i -> i.test(new FluidStack(MagFluids.HYDROGEN.get(), 1000))),
                "Magnetization hydrogen should satisfy the TFMG Hydrogen Tank filling recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void everyTfmgVirtualGasHasAnExcitationProfile(final GameTestHelper helper) {
        for (final String name : new String[]{"lpg", "butane", "propane", "hydrogen", "furnace_gas",
                "ethylene", "propylene", "neon", "carbon_dioxide", "air", "hot_air"}) {
            helper.assertTrue(GasExcitationProfiles.supports(fluid("tfmg", name)),
                    "Missing Gas Vent profile for tfmg:" + name);
            helper.assertTrue(GasExcitationProfiles.supports(fluid("tfmg", "flowing_" + name)),
                    "Missing Gas Vent profile for tfmg:flowing_" + name);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyTfmgVirtualGasPassesThroughTheVentWithoutIdentityLoss(
            final GameTestHelper helper) {
        final BlockPos ventPos = new BlockPos(1, 1, 1);
        final BlockPos cloudPos = new BlockPos(2, 1, 1);
        helper.setBlock(ventPos, MagBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                        net.minecraft.core.Direction.EAST));
        final GasVentBlockEntity vent = (GasVentBlockEntity) helper.getBlockEntity(ventPos);
        final var level = helper.getLevel();
        final BlockPos absoluteVent = helper.absolutePos(ventPos);

        for (final String name : new String[]{"lpg", "butane", "propane", "hydrogen", "furnace_gas",
                "ethylene", "propylene", "neon", "carbon_dioxide", "air", "hot_air"}) {
            for (final String path : new String[]{name, "flowing_" + name}) {
                final Fluid original = fluid("tfmg", path);
                helper.assertTrue(vent.fluidHandler().fill(new FluidStack(original, 1000),
                                IFluidHandler.FluidAction.EXECUTE) == 1000,
                        "Gas Vent rejected profiled fluid tfmg:" + path);
                GasVentBlockEntity.serverTick(level, absoluteVent, level.getBlockState(absoluteVent), vent);
                helper.assertTrue(helper.getBlockEntity(cloudPos) instanceof ProxyGasCloudBlockEntity cloud
                                && cloud.isSource() && cloud.fluid() == original,
                        "Gas Vent changed or lost tfmg:" + path + " identity");
                final ProxyGasCloudBlockEntity cloud = (ProxyGasCloudBlockEntity) helper.getBlockEntity(cloudPos);
                final FluidStack recovered = cloud.fluidHandler().drain(1000,
                        IFluidHandler.FluidAction.EXECUTE);
                helper.assertTrue(recovered.getFluid() == original && recovered.getAmount() == 1000,
                        "Gas source recovery changed tfmg:" + path + " identity or amount");
                helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.AIR, cloudPos);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void gasVentPreservesTfmgIdentityAndExcitesThroughRearMachine(final GameTestHelper helper) {
        final BlockPos exciterPos = new BlockPos(0, 1, 1);
        final BlockPos ventPos = new BlockPos(1, 1, 1);
        final BlockPos cloudPos = new BlockPos(2, 1, 1);
        helper.setBlock(exciterPos, MagBlocks.GAS_EXCITER.get());
        helper.setBlock(ventPos, MagBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                        net.minecraft.core.Direction.EAST));
        final GasVentBlockEntity vent = (GasVentBlockEntity) helper.getBlockEntity(ventPos);
        final Fluid tfmgHydrogen = fluid("tfmg", "hydrogen");
        helper.assertTrue(vent.fluidHandler().fill(new FluidStack(tfmgHydrogen, 1000),
                IFluidHandler.FluidAction.EXECUTE) == 1000, "Gas Vent rejected profiled TFMG hydrogen");

        final var level = helper.getLevel();
        final BlockPos absoluteVent = helper.absolutePos(ventPos);
        helper.assertTrue(vent.outputPos().equals(helper.absolutePos(cloudPos)),
                "Gas Vent output did not face the expected test cell: " + vent.outputPos());
        helper.assertTrue(level.getBlockState(vent.outputPos()).isAir()
                        || level.getBlockState(vent.outputPos()).canBeReplaced(),
                "Gas Vent output test cell was not replaceable: " + level.getBlockState(vent.outputPos()));
        GasVentBlockEntity.serverTick(level, absoluteVent, level.getBlockState(absoluteVent), vent);
        helper.assertTrue(level.getBlockState(helper.absolutePos(cloudPos)).is(MagBlocks.PROXY_GAS_CLOUD.get()),
                "Gas Vent did not place a proxy source block despite a full valid tank");
        final ProxyGasCloudBlockEntity cloud = (ProxyGasCloudBlockEntity) helper.getBlockEntity(cloudPos);
        helper.assertTrue(cloud.isSource() && cloud.fluid() == tfmgHydrogen,
                "Gas Vent did not preserve the TFMG fluid identity in its source cloud");

        final GasExciterBlockEntity exciter = (GasExciterBlockEntity) helper.getBlockEntity(exciterPos);
        exciter.energyBuffer().receiveEnergy(100, false);
        final BlockPos absoluteExciter = helper.absolutePos(exciterPos);
        GasExciterBlockEntity.serverTick(level, absoluteExciter, level.getBlockState(absoluteExciter), exciter);
        helper.assertTrue(cloud.isExcited() && exciter.hudGas() == tfmgHydrogen,
                "Rear-mounted Gas Exciter did not energize or identify the vented TFMG cloud");

        ProxyGasCloudBlockEntity.serverTick(level, helper.absolutePos(cloudPos),
                level.getBlockState(helper.absolutePos(cloudPos)), cloud);
        helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 2, 1)) instanceof ProxyGasCloudBlockEntity,
                "TFMG hydrogen profile did not rise away from the source");
        final FluidStack recovered = cloud.fluidHandler().drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(recovered.getFluid() == tfmgHydrogen && recovered.getAmount() == 1000,
                "Recovered cloud changed TFMG identity or amount");
        helper.succeed();
    }

    private static Fluid fluid(final String namespace, final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.FLUID.containsKey(id)) {
            throw new IllegalStateException("Missing required compatibility-test fluid " + id);
        }
        return BuiltInRegistries.FLUID.get(id);
    }

    private static Item item(final String namespace, final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalStateException("Missing required compatibility-test item " + id);
        }
        return BuiltInRegistries.ITEM.get(id);
    }
}
