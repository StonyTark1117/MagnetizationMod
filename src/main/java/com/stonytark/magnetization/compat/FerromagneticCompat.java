package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Applies compatibility master switches to otherwise static material tags. */
public final class FerromagneticCompat {
    private FerromagneticCompat() {}

    public static boolean isFerromagnetic(final ItemStack stack) {
        return is(stack, MagTags.FERROMAGNETIC_ITEMS);
    }

    public static boolean isFerromagnetic(final BlockState state) {
        return is(state, MagTags.FERROMAGNETIC_BLOCKS);
    }

    public static boolean is(final ItemStack stack, final TagKey<Item> tag) {
        return !stack.isEmpty() && stack.is(tag)
                && integrationEnabled(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean is(final BlockState state, final TagKey<Block> tag) {
        return state.is(tag) && integrationEnabled(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public static boolean integrationEnabled(final ItemStack stack) {
        return !stack.isEmpty() && integrationEnabled(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean integrationEnabled(final BlockState state) {
        return integrationEnabled(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /**
     * Tags remain populated for datapack interoperability, but disabled compat
     * namespaces must not acquire Magnetization behavior from those tags.
     */
    public static boolean integrationEnabled(final ResourceLocation id) {
        if (id == null) return true;
        return switch (id.getNamespace()) {
            case "tfmg" -> MagConfig.tfmgCompatEnabled();
            case "railways" -> MagConfig.steamRailsCompatEnabled();
            default -> true;
        };
    }
}
