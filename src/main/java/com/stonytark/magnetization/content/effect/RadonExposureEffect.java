package com.stonytark.magnetization.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Visible marker for accumulated ionizing-radiation exposure. */
public final class RadonExposureEffect extends MobEffect {
    public RadonExposureEffect() { super(MobEffectCategory.HARMFUL, 0x6657FF); }
}
