package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity;
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
