package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.compat.TfmgCastingFluidTank;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native TFMG casting waits for a full tank; the optional recipes need one bucket. */
@Mixin(targets = "com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin.CastingBasinBlockEntity", remap = false)
public abstract class TfmgCastingBasinMixin {
    @Shadow public FluidTank tank;
    @Shadow public IFluidHandler fluidCapability;

    @Invoker("onFluidChanged")
    public abstract void magnetization$fluidChanged(FluidStack stack);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void magnetization$bucketCastingCapacity(CallbackInfo ci) {
        tank = new TfmgCastingFluidTank(tank.getCapacity(), this::magnetization$fluidChanged);
        fluidCapability = tank;
    }
}
