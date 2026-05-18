package com.stonytark.magnetization.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * One-stop iteration over every equipped armor slot on a {@link LivingEntity},
 * including the {@code BODY} slot that {@link Mob#getArmorAndBodyArmorSlots()}
 * exposes for horses, llamas, wolves, etc.
 *
 * <p>Default {@link LivingEntity#getArmorSlots()} only returns the four
 * humanoid slots (helmet/chestplate/leggings/boots) — the body-armor slot is
 * a separate field on {@code Mob}. Code that wants to apply magnetism effects
 * to every piece of armor an entity is wearing must use this helper to avoid
 * silently skipping horse-armor stamps, drops, repulsion, etc.
 *
 * <p>Players, item-frames, armor-stands, and other non-{@code Mob}
 * {@link LivingEntity}s fall through to {@link LivingEntity#getArmorSlots()}
 * unchanged.
 */
public final class EquippedArmor {

    private EquippedArmor() {}

    /** Every equipped armor stack, including body armor on mobs. */
    public static Iterable<ItemStack> all(final LivingEntity entity) {
        if (entity instanceof Mob mob) return mob.getArmorAndBodyArmorSlots();
        return entity.getArmorSlots();
    }
}
