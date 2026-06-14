package com.stonytark.magnetization.content.mrarmor;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Drives MR Liquid Armor's on-impact hardening: when the wearer takes a kinetic
 * hit (physical melee/projectile, fall, or blast), each equipped MR piece snaps
 * rigid and shaves 22.5% off that hit — a full set caps at 90% mitigation. Other
 * damage types (fire, magic, drowning, …) pass through unchanged.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MrArmorHandler {

    private static final float PER_PIECE = 0.225f;
    private static final float MAX_MITIGATION = 0.9f;
    private static final long HARDEN_TICKS = 30L; // ~1.5s of rigid-plate look after a hit

    private MrArmorHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event) {
        if (!isKinetic(event.getSource())) return;
        final LivingEntity living = event.getEntity();
        int pieces = 0;
        for (final ItemStack armor : living.getArmorSlots()) {
            if (armor.getItem() instanceof MrLiquidArmorItem) pieces++;
        }
        if (pieces == 0) return;
        final float mitigation = Math.min(MAX_MITIGATION, PER_PIECE * pieces);
        event.setAmount(event.getAmount() * (1.0f - mitigation));
        // Visibly harden every worn MR piece for ~1.5s (drives the client model swap).
        final long until = living.level().getGameTime() + HARDEN_TICKS;
        for (final ItemStack armor : living.getArmorSlots()) {
            if (armor.getItem() instanceof MrLiquidArmorItem) {
                armor.set(com.stonytark.magnetization.registry.MagDataComponents.HARDENED_UNTIL.get(), until);
            }
        }
    }

    /** Physical / fall / blast — the impacts the fluid can stiffen against. */
    private static boolean isKinetic(final DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_PROJECTILE)
                || (source.getDirectEntity() instanceof LivingEntity
                    && !source.is(DamageTypeTags.BYPASSES_ARMOR));
    }
}
