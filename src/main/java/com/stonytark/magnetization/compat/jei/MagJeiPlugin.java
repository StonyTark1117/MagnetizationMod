package com.stonytark.magnetization.compat.jei;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.compat.RecipeViewerInfo;
import com.stonytark.magnetization.config.MagConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** JEI adapter for the shared Magnetization information-page catalog. */
@JeiPlugin
public class MagJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return Magnetization.id("jei");
    }

    @Override
    public void registerRecipes(final IRecipeRegistration registration) {
        if (!MagConfig.jeiCompatEnabled()) return;
        for (final RecipeViewerInfo.Topic topic : RecipeViewerInfo.topics()) {
            final List<ItemStack> stacks = topic.resolveStacks();
            if (stacks.isEmpty()) continue;
            registration.addIngredientInfo(
                    stacks,
                    VanillaTypes.ITEM_STACK,
                    topic.descriptions().toArray(Component[]::new));
        }
    }
}
