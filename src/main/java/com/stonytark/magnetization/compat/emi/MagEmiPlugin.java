package com.stonytark.magnetization.compat.emi;

import com.stonytark.magnetization.compat.RecipeViewerInfo;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** EMI adapter for the shared Magnetization information-page catalog. */
@EmiEntrypoint
public class MagEmiPlugin implements EmiPlugin {

    @Override
    public void register(final EmiRegistry registry) {
        for (final RecipeViewerInfo.Topic topic : RecipeViewerInfo.topics()) {
            registerInfoPage(registry, topic);
        }
    }

    private static void registerInfoPage(final EmiRegistry registry,
                                         final RecipeViewerInfo.Topic topic) {
        final List<ItemStack> stacks = topic.resolveStacks();
        if (stacks.isEmpty()) return;
        final List<EmiIngredient> ingredients = new ArrayList<>();
        for (final ItemStack stack : stacks) ingredients.add(EmiStack.of(stack));
        // EMI reserves leading-slash paths for synthetic recipes. Without it,
        // the information page works but is reported as a missing data recipe.
        final ResourceLocation syntheticId = ResourceLocation.fromNamespaceAndPath(
                topic.id().getNamespace(), "/" + topic.id().getPath());
        registry.addRecipe(new EmiInfoRecipe(ingredients, topic.descriptions(), syntheticId));
    }
}
