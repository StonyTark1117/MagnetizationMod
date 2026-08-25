package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.compat.coastersmagnetized.MagCoastersMagnetizedCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves the addon's powered models for field-powered anchors. */
@Pseudo
@Mixin(targets = "net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors", remap = false)
public abstract class CoastersMagnetizedRenderPowerMixin {
    @Inject(method = "isPoweredForRender", at = @At("RETURN"), cancellable = true, remap = false)
    private static void magnetization$includeFieldPower(final BlockGetter level, final BlockPos pos,
                                                         final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && MagCoastersMagnetizedCompat.clientFieldPowersAnchor(pos)) {
            cir.setReturnValue(true);
        }
    }
}
