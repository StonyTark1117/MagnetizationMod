package com.stonytark.magnetization.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Status effect: while applied, the entity's magnetic susceptibility is
 * boosted, so emitters yank it around much more aggressively. The actual
 * multiplier is applied by {@link com.stonytark.magnetization.physics.FieldApplicator}
 * when it computes per-entity force.
 *
 * <p>At amplifier &gt;= {@link #PIN_AMPLIFIER} the effect additionally pins the
 * entity in place — horizontal velocity is damped each tick so the magnetic
 * field overwhelms whatever the entity is trying to do, simulating a strong
 * iron-on-iron grab.
 */
public class MagnetizedEffect extends MobEffect {

    /** Amplifier (0-indexed; level III in /effect terms) at which the pin
     *  behavior kicks in. Below this threshold the effect is a pure
     *  susceptibility multiplier; at or above it, horizontal motion is
     *  also damped. */
    public static final int PIN_AMPLIFIER = 2;

    /** Susceptibility multiplier per amplifier level: amp 0 → 2x, amp 1 → 3x, amp 2 → 4x. */
    public static double multiplierFor(final int amplifier) {
        return 2.0d + amplifier;
    }

    public MagnetizedEffect() {
        super(MobEffectCategory.NEUTRAL, 0xC83838);
    }

    @Override
    public boolean applyEffectTick(final LivingEntity entity, final int amplifier) {
        if (amplifier >= PIN_AMPLIFIER) {
            // 60% horizontal damp every tick — leaves vertical free so falling /
            // jumping under the field still works, just feels "stuck to a magnet".
            final Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(v.x * 0.4d, v.y, v.z * 0.4d);
            entity.hurtMarked = true;
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(final int duration, final int amplifier) {
        return amplifier >= PIN_AMPLIFIER;
    }
}
