package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helper for JEI and REI plugins that need to enumerate every item /
 * block in the magnetization tag families. Both plugins render an info page
 * off this list; the only difference is the wrapper type their respective
 * APIs require, so we hand them raw {@link ItemStack}s and let them adapt.
 */
public final class FerromagneticInfoHelper {

    private FerromagneticInfoHelper() {}

    /** Build a fresh list of single-stack ItemStacks for every item currently
     *  resolved into the ferromagnetic tag. Returns an empty list if the tag
     *  isn't loaded yet — callers should treat that as "no info page to show". */
    public static List<ItemStack> stacks() {
        final List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(MagTags.FERROMAGNETIC_ITEMS).ifPresent(set ->
                set.forEach(holder -> out.add(new ItemStack(holder.value()))));
        return out;
    }

    /** Build a fresh list of single-stack ItemStacks for every block currently
     *  resolved into the {@code #magnetization:ferromagnetic_blocks} tag —
     *  i.e. the blocks the Magnetic Excavator will rip out of the ground.
     *  Resolves each block's item form (asItem); blocks without an item form
     *  (e.g. flowing fluids) are skipped. */
    public static List<ItemStack> blockStacks() {
        final List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTag(MagTags.FERROMAGNETIC_BLOCKS).ifPresent(set ->
                set.forEach(holder -> {
                    final var item = holder.value().asItem();
                    if (item != net.minecraft.world.item.Items.AIR) out.add(new ItemStack(item));
                }));
        return out;
    }
}
