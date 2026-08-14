package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
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
 * <p>Vanilla water is always valid. Addons can opt dedicated coolants into the
 * common {@code c:cooling_fluid} and {@code c:buckets/cooling_fluid} tags; TFMG
 * already publishes its Cooling Fluid to both. TFMG-owned entries additionally
 * honor Magnetization's live TFMG compatibility toggle.</p>
 */
public final class CoolantFluids {

    private CoolantFluids() {}

    public static boolean isCoolant(final Fluid fluid) {
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER
                || fluid.builtInRegistryHolder().is(net.minecraft.tags.FluidTags.WATER)) return true;
        final ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id != null && id.getNamespace().equals("tfmg") && !MagConfig.tfmgCompatEnabled()) return false;
        return fluid.builtInRegistryHolder().is(MagTags.COOLING_FLUIDS);
    }

    public static boolean isCoolantBucket(final ItemStack stack) {
        return coolantFromBucket(stack).isPresent();
    }

    /** A full bucket-sized coolant input. Smaller containers are intentionally
     * rejected because these machine slots always return one empty bucket. */
    public static Optional<FluidStack> coolantFromBucket(final ItemStack stack) {
        if (!stack.is(Items.WATER_BUCKET) && !stack.is(MagTags.COOLING_FLUID_BUCKETS)) {
            return Optional.empty();
        }
        final Optional<FluidStack> contained = FluidUtil.getFluidContained(stack);
        if (contained.isEmpty() || contained.get().getAmount() < 1_000
                || !isCoolant(contained.get().getFluid())) return Optional.empty();
        return Optional.of(new FluidStack(contained.get().getFluid(), 1_000));
    }
}
