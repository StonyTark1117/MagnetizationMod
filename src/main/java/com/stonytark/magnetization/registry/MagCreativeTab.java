package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagCreativeTab {

    public static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Magnetization.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            REGISTER.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.magnetization"))
                    .icon(() -> new ItemStack(MagItems.LODESTONE_CORE.get()))
                    .displayItems((params, output) -> {
                        output.accept(MagItems.MAGNETITE_ORE.get());
                        output.accept(MagItems.DEEPSLATE_MAGNETITE_ORE.get());
                        output.accept(MagItems.RAW_MAGNETITE.get());
                        output.accept(MagItems.RAW_MAGNETITE_BLOCK.get());
                        output.accept(MagItems.MAGNETITE_INGOT.get());
                        output.accept(MagItems.MAGNETITE_BLOCK.get());
                        output.accept(MagItems.MAGNETITE_SWORD.get());
                        output.accept(MagItems.MAGNETITE_PICKAXE.get());
                        output.accept(MagItems.MAGNETITE_AXE.get());
                        output.accept(MagItems.MAGNETITE_SHOVEL.get());
                        output.accept(MagItems.MAGNETITE_HOE.get());
                        output.accept(MagItems.MAGNETITE_HELMET.get());
                        output.accept(MagItems.MAGNETITE_CHESTPLATE.get());
                        output.accept(MagItems.MAGNETITE_LEGGINGS.get());
                        output.accept(MagItems.MAGNETITE_BOOTS.get());
                        output.accept(MagItems.FERROMAGNETIC_INGOT.get());
                        output.accept(MagItems.MAGNETIC_PLATE.get());
                        output.accept(MagItems.FIELD_COMPASS.get());
                        output.accept(MagItems.MAGNETIC_GRAPPLE.get());
                        output.accept(MagItems.LODESTONE_CORE.get());
                        output.accept(MagItems.ELECTROMAGNET.get());
                        output.accept(MagItems.KINETIC_ELECTROMAGNET.get());
                        output.accept(MagItems.MAGNETIC_ANCHOR.get());
                        output.accept(MagItems.REPULSOR_COIL.get());
                        output.accept(MagItems.TRACTOR_BEAM.get());
                        output.accept(MagItems.MAGNETIC_SWITCH.get());
                        output.accept(MagItems.PERMANENT_MAGNET.get());
                        output.accept(MagItems.POLARITY_INVERTER.get());
                    })
                    .build());

    private MagCreativeTab() {}
}
