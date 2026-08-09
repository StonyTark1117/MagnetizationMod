package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.physics.EmitterRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes optional TFMG field adapters from the emitter index on block or chunk removal. */
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.SmartBlockEntity", remap = false)
public abstract class TfmgEmitterLifecycleMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"), require = 0)
    private void magnetization$unregisterOptionalFieldSource(final CallbackInfo ci) {
        if (!((Object) this instanceof MagneticFieldSource)) return;
        final BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null) EmitterRegistry.unregister(self.getLevel(), self.getBlockPos());
    }
}
