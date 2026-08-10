package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.compat.tfmg.TfmgPolarizerCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.EmitterRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional adapter merged only when TFMG's Polarizer block entity exists. */
@Mixin(targets = "com.drmangotea.tfmg.content.electricity.utilities.polarizer.PolarizerBlockEntity",
        remap = false)
public abstract class TfmgPolarizerBlockEntityMixin implements MagneticFieldSource {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void magnetization$registerPoweredPolarizer(final CallbackInfo ci) {
        if (!MagConfig.tfmgPolarizerFieldEnabled()) return;
        final BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null) EmitterRegistry.registerExternal(self.getLevel(), self.getBlockPos());
    }

    @Override
    public @Nullable MagneticField currentField() {
        return TfmgPolarizerCompat.currentField((BlockEntity) (Object) this);
    }
}
