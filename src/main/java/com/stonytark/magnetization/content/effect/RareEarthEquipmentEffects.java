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
 * neodymium set is a stronger general-purpose endgame set and also tolerates
 * electrical/arc damage better than the lower magnetic tiers.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class RareEarthEquipmentEffects {
    private RareEarthEquipmentEffects() {}

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event) {
        final LivingEntity entity = event.getEntity();
        int samariumCobalt = 0;
        int neodymium = 0;
        for (final ItemStack stack : entity.getArmorSlots()) {
            if (stack.is(MagItems.SAMARIUM_COBALT_HELMET.get().getDefaultInstance().getItem())
                    || stack.is(MagItems.SAMARIUM_COBALT_CHESTPLATE.get().getDefaultInstance().getItem())
                    || stack.is(MagItems.SAMARIUM_COBALT_LEGGINGS.get().getDefaultInstance().getItem())
                    || stack.is(MagItems.SAMARIUM_COBALT_BOOTS.get().getDefaultInstance().getItem())) {
                samariumCobalt++;
            }
            if (stack.is(MagItems.NEODYMIUM_HELMET.get().getDefaultInstance().getItem())
                    || stack.is(MagItems.NEODYMIUM_CHESTPLATE.get().getDefaultInstance().getItem())
                    || stack.is(MagItems.NEODYMIUM_LEGGINGS.get().getDefaultInstance().getItem())
                    || stack.is(MagItems.NEODYMIUM_BOOTS.get().getDefaultInstance().getItem())) {
                neodymium++;
            }
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
            // SmCo remains magnetic at temperatures where NdFeB would lose
            // performance; make that distinction visible without granting
            // unconditional fire immunity.
            final float reduction = Math.min(0.80f, samariumCobalt * 0.20f)
                    + Math.min(0.20f, neodymium * 0.05f);
            if (reduction > 0.0f) event.setAmount(event.getAmount() * (1.0f - reduction));
        }
        if (event.getSource().is(DamageTypeTags.IS_LIGHTNING)) {
            final float reduction = Math.min(0.60f, neodymium * 0.15f);
            if (reduction > 0.0f) event.setAmount(event.getAmount() * (1.0f - reduction));
        }
    }
}
