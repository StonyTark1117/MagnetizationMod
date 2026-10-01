package com.stonytark.magnetization.compat;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagFluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Consumer;

/** Keep TFMG's 144 mB metal casts; our bucket-sized casts retain their 1000 mB cost. */
public final class TfmgCastingFluidTank extends SmartFluidTank {
    private final int nativeCapacity;

    public TfmgCastingFluidTank(int nativeCapacity, Consumer<FluidStack> onChanged) {
        super(nativeCapacity, onChanged);
        this.nativeCapacity = nativeCapacity;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        int originalCapacity = getCapacity();
        if (isEmpty()) {
            boolean bucketCast = MagConfig.tfmgProcessingRecipesEnabled()
                    && (resource.is(MagFluids.GALLIUM.get()) || resource.is(MagFluids.LIQUID_LITHIUM.get()));
            setCapacity(bucketCast ? 1000 : nativeCapacity);
        }
        try { return super.fill(resource, action); }
        finally { if (action.simulate()) setCapacity(originalCapacity); }
    }

    @Override
    public void setFluid(FluidStack stack) {
        // Saved tanks must recover their capacity before the native full-tank test.
        setCapacity(stack.is(MagFluids.GALLIUM.get()) || stack.is(MagFluids.LIQUID_LITHIUM.get()) ? 1000 : nativeCapacity);
        super.setFluid(stack);
    }
}
