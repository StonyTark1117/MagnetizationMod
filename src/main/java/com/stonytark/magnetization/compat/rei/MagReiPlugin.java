package com.stonytark.magnetization.compat.rei;

import com.stonytark.magnetization.compat.FerromagneticInfoHelper;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;
import net.minecraft.ChatFormatting;
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
        registerInfoPage(registry, FerromagneticInfoHelper.stacks(),
                "rei.magnetization.ferromagnetic.name",
                "rei.magnetization.ferromagnetic.info");
        registerInfoPage(registry, FerromagneticInfoHelper.blockStacks(),
                "rei.magnetization.ferromagnetic_blocks.name",
                "rei.magnetization.ferromagnetic_blocks.info");
    }

    private static void registerInfoPage(final DisplayRegistry registry, final List<ItemStack> stacks,
                                          final String nameKey, final String infoKey) {
        if (stacks.isEmpty()) return;
        final List<EntryStack<ItemStack>> entries = new ArrayList<>();
        for (final ItemStack stack : stacks) entries.add(EntryStacks.of(stack));
        final EntryIngredient ingredient = EntryIngredient.of(entries);
        final Component name = Component.translatable(nameKey);
        final Component info = Component.translatable(infoKey).withStyle(ChatFormatting.GRAY);
        registry.add(DefaultInformationDisplay.createFromEntries(ingredient, name).line(info));
    }
}
