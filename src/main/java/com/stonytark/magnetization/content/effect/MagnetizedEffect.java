package com.stonytark.magnetization.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Status effect: while applied, the entity's magnetic susceptibility is
 * boosted, so emitters yank it around much more aggressively. The actual
 * multiplier is applied by {@link com.stonytark.magnetization.physics.FieldApplicator}
 * when it computes per-entity force.
 */
public class MagnetizedEffect extends MobEffect {

    /** Susceptibility multiplier per amplifier level: amp 0 → 2x, amp 1 → 3x, amp 2 → 4x. */
    public static double multiplierFor(final int amplifier) {
        return 2.0d + amplifier;
    }

    public MagnetizedEffect() {
        super(MobEffectCategory.NEUTRAL, 0xC83838);
    }
}
