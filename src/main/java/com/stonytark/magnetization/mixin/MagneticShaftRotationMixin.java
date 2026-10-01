package com.stonytark.magnetization.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.stonytark.magnetization.content.shaft.MagneticShaftNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RotationPropagator.class, remap = false)
public abstract class MagneticShaftRotationMixin {
    @Inject(method = "getRotationSpeedModifier", at = @At("HEAD"), cancellable = true)
    private static void magneticConnection(KineticBlockEntity from, KineticBlockEntity to, CallbackInfoReturnable<Float> cir) {
        Float ratio = MagneticShaftNetwork.connectionRatio(from, to);
        if (ratio != null) cir.setReturnValue(ratio);
    }
}
