package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Small, material-specific benefits for the rare-earth equipment sets.
 * Samarium–cobalt's defining gameplay property is thermal stability; the
 * neodymium set is the stronger general-purpose endgame set. Its full-set
 * Magnetic Anchoring bonus is consumed by the field applicator rather than the
 * damage event below.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class RareEarthEquipmentEffects {
    public static final float FULL_NEODYMIUM_FIELD_MULTIPLIER = 0.25f;

    private RareEarthEquipmentEffects() {}

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event) {
        final LivingEntity entity = event.getEntity();
        int samariumCobalt = 0;
        for (final ItemStack stack : entity.getArmorSlots()) {
            if (isSamariumCobaltArmor(stack)) {
                samariumCobalt++;
            }
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            // SmCo remains magnetic at temperatures where NdFeB would lose
            // performance; make that distinction visible without granting
            // unconditional fire immunity.
            event.setAmount(event.getAmount() * fireDamageMultiplier(samariumCobalt));
        }
    }

    /** Remaining fire damage for the number of worn SmCo pieces. Kept as a
     *  small pure function so the intended 20/40/60/80% ladder is regression-testable. */
    public static float fireDamageMultiplier(final int samariumCobaltPieces) {
        return 1.0f - Math.min(0.80f, Math.max(0, samariumCobaltPieces) * 0.20f);
    }

    /** A complete NdFeB set remains ferromagnetic but resists 75% of field-driven
     *  entity motion, making the endgame armor usable around high-tier emitters. */
    public static double fieldSusceptibilityMultiplier(final LivingEntity entity) {
        return hasFullNeodymiumSet(entity) ? FULL_NEODYMIUM_FIELD_MULTIPLIER : 1.0d;
    }

    public static boolean hasFullNeodymiumSet(final LivingEntity entity) {
        return entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                        .is(MagItems.NEODYMIUM_HELMET.get())
                && entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                        .is(MagItems.NEODYMIUM_CHESTPLATE.get())
                && entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS)
                        .is(MagItems.NEODYMIUM_LEGGINGS.get())
                && entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
                        .is(MagItems.NEODYMIUM_BOOTS.get());
    }

    private static boolean isSamariumCobaltArmor(final ItemStack stack) {
        return stack.is(MagItems.SAMARIUM_COBALT_HELMET.get())
                || stack.is(MagItems.SAMARIUM_COBALT_CHESTPLATE.get())
                || stack.is(MagItems.SAMARIUM_COBALT_LEGGINGS.get())
                || stack.is(MagItems.SAMARIUM_COBALT_BOOTS.get());
    }
}
