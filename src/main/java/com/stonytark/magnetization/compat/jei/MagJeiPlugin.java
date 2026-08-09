package com.stonytark.magnetization.compat.jei;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.compat.FerromagneticInfoHelper;
import com.stonytark.magnetization.registry.MagItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
        final List<ItemStack> ferromagnetic = FerromagneticInfoHelper.stacks();
        if (!ferromagnetic.isEmpty()) {
            registration.addIngredientInfo(
                    ferromagnetic,
                    VanillaTypes.ITEM_STACK,
                    Component.translatable("jei.magnetization.ferromagnetic.info")
                            .withStyle(ChatFormatting.GRAY));
        }

        final List<ItemStack> ferroBlocks = FerromagneticInfoHelper.blockStacks();
        if (!ferroBlocks.isEmpty()) {
            registration.addIngredientInfo(
                    ferroBlocks,
                    VanillaTypes.ITEM_STACK,
                    Component.translatable("jei.magnetization.ferromagnetic_blocks.info")
                            .withStyle(ChatFormatting.GRAY));
        }

        addInfo(registration, MagItems.HELIUM_3_GAS.get(), "jei.magnetization.helium_3_gas.info");
        addInfo(registration, MagItems.HYDROGEN_BUCKET.get(), "jei.magnetization.hydrogen_bucket.info");
        addInfo(registration, MagItems.GALLIUM_BUCKET.get(), "jei.magnetization.gallium_bucket.info");
        addInfo(registration, MagItems.RAW_GALLIUM.get(), "jei.magnetization.raw_gallium.info");
        addInfo(registration, MagItems.RAW_MAGNETITE.get(), "jei.magnetization.raw_magnetite.info");
        addInfo(registration, MagItems.RAW_MAGHEMITE.get(), "jei.magnetization.raw_maghemite.info");
        addInfo(registration, MagItems.RAW_PYRRHOTITE.get(), "jei.magnetization.raw_pyrrhotite.info");
        addInfo(registration, MagItems.RAW_HEMATITE.get(), "jei.magnetization.raw_hematite.info");
        addInfo(registration, MagItems.RAW_TITANOMAGNETITE.get(), "jei.magnetization.raw_titanomagnetite.info");
        addInfo(registration, MagItems.RAW_LITHIUM.get(), "jei.magnetization.raw_lithium.info");
    }

    private static void addInfo(final IRecipeRegistration registration, final Item item, final String key) {
        registration.addIngredientInfo(
                List.of(new ItemStack(item)),
                VanillaTypes.ITEM_STACK,
                Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
