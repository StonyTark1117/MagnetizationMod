package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.api.EquippedArmor;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
        for (final ItemStack armor : EquippedArmor.all(target)) {
            final MagneticPolarity p = armor.get(MagDataComponents.ARMOR_POLARITY.get());
            if (p != null) targetNet += p.sign();
        }
        final int amp;
        if (targetNet != 0 && Integer.signum(targetNet) != weaponPol.sign()) {
            amp = MagnetizedEffect.PIN_AMPLIFIER; // opposite-pole grab — pin
        } else {
            amp = 0; // same-pole repel or unmagnetized target — light tag only
        }
        target.addEffect(new MobEffectInstance(MagEffects.MAGNETIZED,
                com.stonytark.magnetization.config.MagConfig.magnetizedEffectDurationTicks(), amp,
                false, true));

        // Sword signature ability: yank ANY target wearing metal armor (regardless of polarity).
        // The thinking is "iron grabbing iron" — the field handle the sword has on the target's
        // ferrous gear pulls them in, opposite poles or not. Strength is boosted on opposite-pole
        // pin, but a same-pole or unpolarized metal-clad target still gets dragged toward the
        // attacker. Untagged-armor targets (no METAL_ARMOR pieces) get no kinetic pull.
        if (!swordYankEnabled()) return;
        if (!weapon.is(ItemTags.SWORDS)) return;
        if (!wearsMetalArmor(target)) return;
        final Vec3 pull = attacker.position().subtract(target.position());
        final double dist = pull.length();
        if (dist < 0.1) return;
        // Stronger pull on opposite-pole grabs (pin amp) — the same field that pins also
        // accelerates the lunge.
        final double strength = amp == MagnetizedEffect.PIN_AMPLIFIER
                ? com.stonytark.magnetization.config.MagConfig.weaponYankOpposite()
                : com.stonytark.magnetization.config.MagConfig.weaponYankSame();
        final Vec3 nudge = pull.scale(strength / dist);
        target.setDeltaMovement(target.getDeltaMovement().add(nudge.x, 0.1d, nudge.z));
        target.hurtMarked = true;
    }

    private static boolean wearsMetalArmor(final LivingEntity entity) {
        for (final ItemStack armor : EquippedArmor.all(entity)) {
            if (armor.is(MagTags.METAL_ARMOR)) return true;
        }
        return false;
    }

    private static boolean swordYankEnabled() {
        try { return MagConfig.TOOL_SWORD_YANK_ENABLED.get(); } catch (Throwable t) { return true; }
    }
}
