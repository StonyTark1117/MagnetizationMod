package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.compat.coastersmagnetized.MagCoastersMagnetizedCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds Magnetization fields to the addon's existing redstone-or-power predicate. */
@Pseudo
@Mixin(targets = "net.antopfr.coastersmagnetized.magnet.MagnetBoost", remap = false)
public abstract class CoastersMagnetizedPowerMixin {
    @Redirect(method = "signedFactor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"),
            remap = false)
    private static boolean magnetization$fieldOrRedstone(final ServerLevel level, final BlockPos pos) {
        return level.hasNeighborSignal(pos) || MagCoastersMagnetizedCompat.fieldPowersAnchor(level, pos);
    }
}
