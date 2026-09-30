package com.stonytark.magnetization.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Cosmetic bolts must not alter equipment or the world through LIRM. */
@Mixin(LightningBolt.class)
public interface LightningBoltAccessor {
    @Accessor("visualOnly")
    boolean magnetization$isVisualOnly();
}
