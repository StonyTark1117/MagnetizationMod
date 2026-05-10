package com.stonytark.magnetization.compat.rei;

import com.stonytark.magnetization.api.MagTags;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * REI integration mirroring {@code MagJeiPlugin}: surfaces an info page for the
 * {@code #magnetization:ferromagnetic} tag. Discovery via the
 * {@link REIPluginClient @REIPluginClient} annotation; if REI isn't installed
 * this class is never loaded and the missing imports don't surface.
 */
@REIPluginClient
public class MagReiPlugin implements REIClientPlugin {

    @Override
    public void registerDisplays(final DisplayRegistry registry) {
        // Build EntryStacks from items currently in the ferromagnetic tag.
        final List<EntryStack<ItemStack>> entries = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(MagTags.FERROMAGNETIC_ITEMS).ifPresent(set ->
                set.forEach(holder -> entries.add(EntryStacks.of(new ItemStack(holder.value())))));
        if (entries.isEmpty()) return;

        final EntryIngredient ingredient = EntryIngredient.of(entries);
        final Component name = Component.translatable("rei.magnetization.ferromagnetic.name");
        final Component info = Component.translatable("rei.magnetization.ferromagnetic.info")
                .withStyle(ChatFormatting.GRAY);

        registry.add(DefaultInformationDisplay.createFromEntries(ingredient, name).line(info));
    }
}
