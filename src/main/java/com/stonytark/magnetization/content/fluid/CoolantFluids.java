package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.Optional;

/** Shared coolant identity for fusion machines.
 *
 * <p>Vanilla water, deuterium oxide, and liquid gallium are built in. Addons can opt dedicated coolants into the
 * common {@code c:cooling_fluid} and {@code c:buckets/cooling_fluid} tags; TFMG
 * already publishes its Cooling Fluid to both. TFMG-owned entries additionally
 * honor Magnetization's live TFMG-coolant compatibility toggle. {@link #quality} drives
 * a configurable curve: better coolant is consumed more slowly and scales the
 * machine's configured cooling bonuses farther above dry performance.</p>
 */
public final class CoolantFluids {

    private CoolantFluids() {}

    public static boolean isCoolant(final Fluid fluid) {
        return quality(fluid) > 0.0d;
    }

    public static double quality(final Fluid fluid) {
        final ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id != null && id.getNamespace().equals("tfmg") && !MagConfig.tfmgCoolingFluidEnabled()) return 0.0d;
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER
                || fluid.builtInRegistryHolder().is(net.minecraft.tags.FluidTags.WATER)) {
            return Math.max(0.1d, MagConfig.coolantQualityWater());
        }
        if (fluid == MagFluids.DEUTERIUM_OXIDE.get() || fluid == MagFluids.DEUTERIUM_OXIDE_FLOWING.get()) {
            return Math.max(0.1d, MagConfig.coolantQualityDeuteriumOxide());
        }
        if (fluid == MagFluids.GALLIUM.get() || fluid == MagFluids.GALLIUM_FLOWING.get()) {
            return Math.max(0.1d, MagConfig.coolantQualityGallium());
        }
        return fluid.builtInRegistryHolder().is(MagTags.COOLING_FLUIDS)
                ? Math.max(0.1d, MagConfig.coolantQualityTagged()) : 0.0d;
    }

    public static double maximumConfiguredQuality() {
        return Math.max(Math.max(MagConfig.coolantQualityWater(), MagConfig.coolantQualityDeuteriumOxide()),
                Math.max(MagConfig.coolantQualityGallium(), MagConfig.coolantQualityTagged()));
    }

    public static int consumptionForQuality(final int baseline, final double quality) {
        if (baseline <= 0) return 0;
        if (quality <= 0.0d) return baseline;
        return Math.max(1, (int) Math.ceil(baseline / quality));
    }

    public static boolean isCoolantBucket(final ItemStack stack) {
        return coolantFromBucket(stack).isPresent();
    }

    /** A full bucket-sized coolant input. Smaller containers are intentionally
     * rejected because these machine slots always return one empty bucket. */
    public static Optional<FluidStack> coolantFromBucket(final ItemStack stack) {
        if (!stack.is(Items.WATER_BUCKET)
                && !stack.is(MagItems.DEUTERIUM_OXIDE_BUCKET.get())
                && !stack.is(MagItems.GALLIUM_BUCKET.get())
                && !stack.is(MagTags.COOLING_FLUID_BUCKETS)) {
            return Optional.empty();
        }
        final Optional<FluidStack> contained = FluidUtil.getFluidContained(stack);
        if (contained.isEmpty() || contained.get().getAmount() < 1_000
                || !isCoolant(contained.get().getFluid())) return Optional.empty();
        return Optional.of(new FluidStack(contained.get().getFluid(), 1_000));
    }
}
