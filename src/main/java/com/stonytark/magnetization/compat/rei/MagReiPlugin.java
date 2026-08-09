package com.stonytark.magnetization.compat.rei;

import com.stonytark.magnetization.compat.RecipeViewerInfo;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** REI adapter for the shared Magnetization information-page catalog. */
@REIPluginClient
public class MagReiPlugin implements REIClientPlugin {

    @Override
    public void registerDisplays(final DisplayRegistry registry) {
        for (final RecipeViewerInfo.Topic topic : RecipeViewerInfo.topics()) {
            registerInfoPage(registry, topic);
        }
    }

    private static void registerInfoPage(final DisplayRegistry registry,
                                         final RecipeViewerInfo.Topic topic) {
        final List<ItemStack> stacks = topic.resolveStacks();
        if (stacks.isEmpty()) return;
        final List<EntryStack<ItemStack>> entries = new ArrayList<>();
        for (final ItemStack stack : stacks) entries.add(EntryStacks.of(stack));
        registry.add(DefaultInformationDisplay
                .createFromEntries(EntryIngredient.of(entries), topic.title())
                .lines(topic.descriptions()));
    }
}
