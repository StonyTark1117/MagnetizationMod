package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.compat.immersiveaeronautics.MagImmersiveAeronauticsCompat;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional completion hook for Immersive Aeronautics' internal Sable bridge. */
@Pseudo
@Mixin(targets = "qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge", remap = false)
public abstract class ImmersiveAeronauticsShipTransferMixin {

    @Inject(method = "moveThroughPortal", at = @At("RETURN"), remap = false)
    private static void magnetization$afterShipTransfer(
            final ServerSubLevel originalSub,
            final ServerLevel source,
            final ServerLevel destination,
            final Vec3 destinationPosition,
            final Quaterniond destinationRotation,
            final CallbackInfoReturnable<Object> cir) {
        final Object moved = cir.getReturnValue();
        if (moved != null) {
            MagImmersiveAeronauticsCompat.onShipMoved(source, destination, moved);
        }
    }
}
