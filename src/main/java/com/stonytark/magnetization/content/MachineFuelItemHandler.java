package com.stonytark.magnetization.content;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Exposes a machine's fuel/input {@link Container} to hoppers, Create funnels/
 * belts/arms, and any other automation as an {@link IItemHandler} — so every
 * item-burning machine can be fed without a manual right-click.
 *
 * <p><b>Insert</b> is gated by the container's own {@code canPlaceItem} filter, so
 * only the fuel each machine accepts goes in (cells for the tokamak, potency
 * magnets for the motor/MHD jet, and fuel or coolant buckets for the fusion machines).
 *
 * <p><b>Extract</b> follows a single "spent-only" rule: an item may leave the slot
 * <i>only if the slot would reject it on insert</i>. For fuel slots the held item
 * is always valid fuel, so nothing can be pulled — no hopper fuel-theft, and no
 * refreshing a magnet's burn timer by yanking and re-seating it. For the bucket
 * machines the slot holds a plain emptied bucket after draining (which the fluid-
 * bucket filter rejects), so a hopper <i>can</i> pull the empty bucket, enabling
 * continuous bucket recirculation. One rule, correct for both.
 */
public final class MachineFuelItemHandler implements IItemHandler {

    private final Container container;

    public MachineFuelItemHandler(final Container container) {
        this.container = container;
    }

    @Override
    public int getSlots() {
        return container.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return container.getItem(slot);
    }

    @Override
    public int getSlotLimit(final int slot) {
        return container.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return container.canPlaceItem(slot, stack);
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!container.canPlaceItem(slot, stack)) return stack;

        // A filled bucket stacks to 1; cells/magnets to 64 — honour whichever is smaller.
        final int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        final ItemStack existing = container.getItem(slot);

        final int accepted;
        if (existing.isEmpty()) {
            accepted = Math.min(limit, stack.getCount());
            if (!simulate) {
                container.setItem(slot, stack.copyWithCount(accepted));
                container.setChanged();
            }
        } else {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) return stack;
            final int space = limit - existing.getCount();
            if (space <= 0) return stack;
            accepted = Math.min(space, stack.getCount());
            if (!simulate) {
                existing.grow(accepted);
                container.setChanged();
            }
        }
        return accepted >= stack.getCount() ? ItemStack.EMPTY
                : stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        final ItemStack existing = container.getItem(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        // Spent-only: never surrender anything the slot still counts as fuel.
        if (container.canPlaceItem(slot, existing)) return ItemStack.EMPTY;

        final int taken = Math.min(amount, existing.getCount());
        if (simulate) return existing.copyWithCount(taken);
        final ItemStack out = container.removeItem(slot, taken);
        container.setChanged();
        return out;
    }
}
