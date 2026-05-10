package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Combat synergy with {@code ARMOR_POLARITY}: hitting any LivingEntity with a
 * magnetized metal-tool weapon stamps a {@link MagnetizedEffect} on the target
 * for a few seconds. If the target is also wearing magnetized armor the effect
 * upgrades from "tagged" to "pinned":
 *
 * <ul>
 *   <li>Opposite poles (sword=SOUTH vs. armor=NORTH, or vice versa) → Magnetized
 *       III (amp 2): the target gets pinned in place AND yanked harder. Iron
 *       grabbing onto iron.</li>
 *   <li>Same poles → Magnetized I (amp 0): the field repels, so we don't pin,
 *       but the target is still tagged for any nearby emitters to grab.</li>
 *   <li>Target unmagnetized → Magnetized I (amp 0): the weapon transfers a
 *       brief susceptibility boost.</li>
 * </ul>
 *
 * <p>Effect is short (3 seconds) so it's a combat tag, not a debuff timer.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagnetizedWeaponHits {

    private static final int EFFECT_DURATION_TICKS = 60; // 3 seconds

    private MagnetizedWeaponHits() {}

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        final ItemStack weapon = attacker.getMainHandItem();
        if (weapon.isEmpty() || !weapon.is(MagTags.METAL_TOOLS)) return;
        final MagneticPolarity weaponPol = weapon.get(MagDataComponents.ARMOR_POLARITY.get());
        if (weaponPol == null || weaponPol == MagneticPolarity.NONE) return;

        final LivingEntity target = event.getEntity();
        // Inspect target's worn armor for a net polarity. If they have one and
        // it's opposite to the weapon's, the field pins; same-pole or no armor
        // gets the lighter tag.
        int targetNet = 0;
        for (final ItemStack armor : target.getArmorSlots()) {
            final MagneticPolarity p = armor.get(MagDataComponents.ARMOR_POLARITY.get());
            if (p != null) targetNet += p.sign();
        }
        final int amp;
        if (targetNet != 0 && Integer.signum(targetNet) != weaponPol.sign()) {
            amp = MagnetizedEffect.PIN_AMPLIFIER; // opposite-pole grab — pin
        } else {
            amp = 0; // same-pole repel or unmagnetized target — light tag only
        }
        target.addEffect(new MobEffectInstance(MagEffects.MAGNETIZED, EFFECT_DURATION_TICKS, amp,
                false, true));
    }
}
