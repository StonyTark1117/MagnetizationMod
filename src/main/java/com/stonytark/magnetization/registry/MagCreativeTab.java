package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagCreativeTab {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Magnetization.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            REGISTER.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.magnetization"))
                    .icon(() -> new ItemStack(MagItems.LODESTONE_CORE.get()))
                    .displayItems((params, output) -> {
                        accept(output, MagItems.MAGNETITE_ORE);
                        accept(output, MagItems.DEEPSLATE_MAGNETITE_ORE);
                        accept(output, MagItems.RAW_MAGNETITE);
                        accept(output, MagItems.RAW_MAGNETITE_BLOCK);
                        accept(output, MagItems.MAGNETITE_INGOT);
                        accept(output, MagItems.MAGNETITE_BLOCK);
                        accept(output, MagItems.MAGNETITE_SWORD);
                        accept(output, MagItems.MAGNETITE_PICKAXE);
                        accept(output, MagItems.MAGNETITE_AXE);
                        accept(output, MagItems.MAGNETITE_SHOVEL);
                        accept(output, MagItems.MAGNETITE_HOE);
                        accept(output, MagItems.MAGNETITE_HELMET);
                        accept(output, MagItems.MAGNETITE_CHESTPLATE);
                        accept(output, MagItems.MAGNETITE_LEGGINGS);
                        accept(output, MagItems.MAGNETITE_BOOTS);
                        accept(output, MagItems.FERROMAGNETIC_INGOT);
                        accept(output, MagItems.FERROMAGNETIC_SWORD);
                        accept(output, MagItems.FERROMAGNETIC_PICKAXE);
                        accept(output, MagItems.FERROMAGNETIC_AXE);
                        accept(output, MagItems.FERROMAGNETIC_SHOVEL);
                        accept(output, MagItems.FERROMAGNETIC_HOE);
                        accept(output, MagItems.FERROMAGNETIC_HELMET);
                        accept(output, MagItems.FERROMAGNETIC_CHESTPLATE);
                        accept(output, MagItems.FERROMAGNETIC_LEGGINGS);
                        accept(output, MagItems.FERROMAGNETIC_BOOTS);
                        accept(output, MagItems.MAGNETIC_PLATE);
                        accept(output, MagItems.PETRIFIED_WOOD);
                        accept(output, MagItems.FIELD_COMPASS);
                        accept(output, MagItems.MAGNETIC_GRAPPLE);
                        accept(output, MagItems.LODESTONE_CORE);
                        accept(output, MagItems.ELECTROMAGNET);
                        accept(output, MagItems.KINETIC_ELECTROMAGNET);
                        accept(output, MagItems.MAGNETIC_ANCHOR);
                        accept(output, MagItems.REPULSOR_COIL);
                        accept(output, MagItems.TRACTOR_BEAM);
                        accept(output, MagItems.MAGNETIC_EXCAVATOR);
                        accept(output, MagItems.MAGNETIC_SWITCH);
                        accept(output, MagItems.PERMANENT_MAGNET);
                        accept(output, MagItems.TEMPORARY_MAGNET);
                        accept(output, MagItems.POLARITY_INVERTER);
                    })
                    .build());

    /** Add the item to the tab unless its registry path is in the config disabled-list. */
    private static void accept(final CreativeModeTab.Output output, final DeferredItem<? extends Item> entry) {
        final Item item = entry.get();
        final ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        if (MagConfig.isItemDisabled(rl.getPath()) || MagConfig.isBlockDisabled(rl.getPath())) return;
        output.accept(item);
    }

    private MagCreativeTab() {}
}
