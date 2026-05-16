package com.stonytark.magnetization.compat.emi;

import com.stonytark.magnetization.compat.FerromagneticInfoHelper;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * EMI integration mirroring the JEI and REI plugins: surfaces an info recipe
 * for the {@code #magnetization:ferromagnetic} item tag and the
 * {@code #magnetization:ferromagnetic_blocks} block tag. EMI auto-discovers
 * classes annotated {@link EmiEntrypoint}; if EMI isn't installed, this class
 * is never loaded and the missing imports don't surface.
 */
@EmiEntrypoint
public class MagEmiPlugin implements EmiPlugin {

    @Override
    public void register(final EmiRegistry registry) {
        registerInfoStacks(registry, FerromagneticInfoHelper.stacks(),
                "emi.magnetization.ferromagnetic.info");
        registerInfoStacks(registry, FerromagneticInfoHelper.blockStacks(),
                "emi.magnetization.ferromagnetic_blocks.info");
    }

    private static void registerInfoStacks(final EmiRegistry registry, final List<ItemStack> stacks,
                                            final String infoKey) {
        if (stacks.isEmpty()) return;
        final List<EmiIngredient> emiStacks = new ArrayList<>();
        for (final ItemStack stack : stacks) emiStacks.add(EmiStack.of(stack));
        registry.addRecipe(new dev.emi.emi.api.recipe.EmiInfoRecipe(
                emiStacks,
                List.of(Component.translatable(infoKey).withStyle(ChatFormatting.GRAY)),
                null));
    }
}
