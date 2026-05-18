package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;

/**
 * Magnetic elytra. Functions identically to a vanilla elytra (same chest-slot
 * equip, same gliding hook), but the wearer becomes dramatically more
 * susceptible to passing magnetic fields while gliding — the
 * {@code MagneticElytraGlideBonus} hook in {@code FieldApplicator} multiplies
 * the entity's pull-by-field susceptibility by {@link #GLIDE_SUSCEPTIBILITY_BONUS}
 * whenever {@link LivingEntity#isFallFlying()} is true and a magnetic elytra
 * is in the chest slot.
 *
 * <p>Gameplay outcome: a player wearing the magnetic elytra can surf along
 * a chain of emitters as if riding magnetic rails — a NORTH-polarity
 * emitter pulls them in for an attractive boost, a SOUTH one pushes them
 * away for a deflection. Skilled players can chain emitters to launch
 * themselves across the map without needing rockets.
 *
 * <p>Repair: ferromagnetic ingots (same as the other ferromagnetic gear).
 * Durability matches vanilla elytra (432 uses).
 */
public final class MagneticElytraItem extends ElytraItem {

    /** Multiplier applied to {@code FieldApplicator.susceptibilityOf} when the
     *  wearer is actively gliding with this item in the chest slot. Tuned so
     *  a magnetic elytra over a single magnetized chestplate (~2.0 base
     *  susceptibility once stamped) produces a noticeable in-flight tug from
     *  WEAK emitters at point-blank, but doesn't dominate at long range. */
    public static final double GLIDE_SUSCEPTIBILITY_BONUS = 4.0d;

    public MagneticElytraItem(final Properties props) {
        super(props);
    }

    @Override
    public boolean isValidRepairItem(final ItemStack stack, final ItemStack repair) {
        return repair.is(MagItems.FERROMAGNETIC_INGOT.get())
                || super.isValidRepairItem(stack, repair);
    }
}
