package com.stonytark.magnetization.compat.jei;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI integration: surfaces an info page for the {@code #magnetization:ferromagnetic}
 * tag. JEI auto-discovers classes annotated {@link JeiPlugin}; if JEI isn't
 * installed, this class is never loaded so the missing imports don't surface.
 */
@JeiPlugin
public class MagJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return Magnetization.id("jei");
    }

    @Override
    public void registerRecipes(final IRecipeRegistration registration) {
        // Build the list of items currently in the ferromagnetic tag.
        final List<ItemStack> ferromagnetic = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(MagTags.FERROMAGNETIC_ITEMS).ifPresent(set ->
                set.forEach(holder -> ferromagnetic.add(new ItemStack(holder.value()))));
        if (ferromagnetic.isEmpty()) return;

        registration.addIngredientInfo(
                ferromagnetic,
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.magnetization.ferromagnetic.info")
                        .withStyle(ChatFormatting.GRAY));
    }
}
