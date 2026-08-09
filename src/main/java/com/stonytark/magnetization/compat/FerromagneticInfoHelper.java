package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared helper for recipe-viewer plugins that need to enumerate every item /
 * block in the Magnetization tag families. The shared information catalog
 * hands the raw {@link ItemStack}s to JEI, REI, or EMI for adaptation.
 */
public final class FerromagneticInfoHelper {

    private FerromagneticInfoHelper() {}

    /** Build a fresh list of single-stack ItemStacks for every item currently
     *  resolved into the ferromagnetic tag. Returns an empty list if the tag
     *  isn't loaded yet — callers should treat that as "no info page to show". */
    public static List<ItemStack> stacks() {
        final List<ItemStack> out = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(MagTags.FERROMAGNETIC_ITEMS).ifPresent(set ->
                set.forEach(holder -> {
                    final ItemStack stack = new ItemStack(holder.value());
                    if (FerromagneticCompat.isFerromagnetic(stack)) out.add(stack);
                }));
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
                    if (item != net.minecraft.world.item.Items.AIR
                            && FerromagneticCompat.isFerromagnetic(holder.value().defaultBlockState())) {
                        out.add(new ItemStack(item));
                    }
                }));
        return out;
    }
}
