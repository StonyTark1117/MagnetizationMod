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
                        // ── Ores (all metals, stone + deepslate) ──
                        accept(output, MagItems.MAGNETITE_ORE);
                        accept(output, MagItems.DEEPSLATE_MAGNETITE_ORE);
                        accept(output, MagItems.MAGHEMITE_ORE);
                        accept(output, MagItems.DEEPSLATE_MAGHEMITE_ORE);
                        accept(output, MagItems.PYRRHOTITE_ORE);
                        accept(output, MagItems.DEEPSLATE_PYRRHOTITE_ORE);
                        accept(output, MagItems.HEMATITE_ORE);
                        accept(output, MagItems.DEEPSLATE_HEMATITE_ORE);
                        accept(output, MagItems.TITANOMAGNETITE_ORE);
                        accept(output, MagItems.DEEPSLATE_TITANOMAGNETITE_ORE);
                        // ── Raw drops ──
                        accept(output, MagItems.RAW_MAGNETITE);
                        accept(output, MagItems.RAW_MAGHEMITE);
                        accept(output, MagItems.RAW_PYRRHOTITE);
                        accept(output, MagItems.RAW_HEMATITE);
                        accept(output, MagItems.RAW_TITANOMAGNETITE);
                        // ── Ingots ──
                        accept(output, MagItems.MAGNETITE_INGOT);
                        accept(output, MagItems.FERROMAGNETIC_INGOT);
                        accept(output, MagItems.MAGHEMITE_INGOT);
                        accept(output, MagItems.PYRRHOTITE_INGOT);
                        accept(output, MagItems.HEMATITE_INGOT);
                        accept(output, MagItems.TITANOMAGNETITE_INGOT);
                        // ── Storage blocks (refined + raw) ──
                        accept(output, MagItems.MAGNETITE_BLOCK);
                        accept(output, MagItems.RAW_MAGNETITE_BLOCK);
                        accept(output, MagItems.MAGHEMITE_BLOCK);
                        accept(output, MagItems.RAW_MAGHEMITE_BLOCK);
                        accept(output, MagItems.PYRRHOTITE_BLOCK);
                        accept(output, MagItems.RAW_PYRRHOTITE_BLOCK);
                        accept(output, MagItems.HEMATITE_BLOCK);
                        accept(output, MagItems.RAW_HEMATITE_BLOCK);
                        accept(output, MagItems.TITANOMAGNETITE_BLOCK);
                        accept(output, MagItems.RAW_TITANOMAGNETITE_BLOCK);

                        // ── Worldgen stone, gravel & decoration ──
                        accept(output, MagItems.ANOMALY_STONE);
                        accept(output, MagItems.COBBLED_ANOMALY_STONE);
                        accept(output, MagItems.ANOMALY_STONE_STAIRS);
                        accept(output, MagItems.ANOMALY_STONE_SLAB);
                        accept(output, MagItems.COBBLED_ANOMALY_STONE_STAIRS);
                        accept(output, MagItems.COBBLED_ANOMALY_STONE_SLAB);
                        accept(output, MagItems.COBBLED_ANOMALY_STONE_WALL);
                        accept(output, MagItems.MAGNETIC_GRAVEL);
                        accept(output, MagItems.PETRIFIED_WOOD);

                        // ── Tools (by metal) ──
                        accept(output, MagItems.MAGNETITE_SWORD);
                        accept(output, MagItems.MAGNETITE_PICKAXE);
                        accept(output, MagItems.MAGNETITE_AXE);
                        accept(output, MagItems.MAGNETITE_SHOVEL);
                        accept(output, MagItems.MAGNETITE_HOE);
                        accept(output, MagItems.FERROMAGNETIC_SWORD);
                        accept(output, MagItems.FERROMAGNETIC_PICKAXE);
                        accept(output, MagItems.FERROMAGNETIC_AXE);
                        accept(output, MagItems.FERROMAGNETIC_SHOVEL);
                        accept(output, MagItems.FERROMAGNETIC_HOE);
                        accept(output, MagItems.MAGHEMITE_SWORD);
                        accept(output, MagItems.MAGHEMITE_PICKAXE);
                        accept(output, MagItems.MAGHEMITE_AXE);
                        accept(output, MagItems.MAGHEMITE_SHOVEL);
                        accept(output, MagItems.MAGHEMITE_HOE);

                        // ── Armor & horse armor (by metal) ──
                        accept(output, MagItems.MAGNETITE_HELMET);
                        accept(output, MagItems.MAGNETITE_CHESTPLATE);
                        accept(output, MagItems.MAGNETITE_LEGGINGS);
                        accept(output, MagItems.MAGNETITE_BOOTS);
                        accept(output, MagItems.MAGNETITE_HORSE_ARMOR);
                        accept(output, MagItems.FERROMAGNETIC_HELMET);
                        accept(output, MagItems.FERROMAGNETIC_CHESTPLATE);
                        accept(output, MagItems.FERROMAGNETIC_LEGGINGS);
                        accept(output, MagItems.FERROMAGNETIC_BOOTS);
                        accept(output, MagItems.FERROMAGNETIC_HORSE_ARMOR);
                        accept(output, MagItems.MAGHEMITE_HELMET);
                        accept(output, MagItems.MAGHEMITE_CHESTPLATE);
                        accept(output, MagItems.MAGHEMITE_LEGGINGS);
                        accept(output, MagItems.MAGHEMITE_BOOTS);
                        accept(output, MagItems.MAGHEMITE_HORSE_ARMOR);

                        // ── Specialty wearables ──
                        accept(output, MagItems.MAGNETIC_ELYTRA);
                        accept(output, MagItems.ALFVEN_BACKPACK);
                        accept(output, MagItems.MR_LIQUID_HELMET);
                        accept(output, MagItems.MR_LIQUID_CHESTPLATE);
                        accept(output, MagItems.MR_LIQUID_LEGGINGS);
                        accept(output, MagItems.MR_LIQUID_BOOTS);
                        accept(output, MagItems.MAGNETORESISTIVE_BOOTS);

                        // ── Crafting components ──
                        accept(output, MagItems.IMPRINT_MODULE);
                        accept(output, MagItems.MAGNETIC_PLATE);
                        accept(output, MagItems.PYROLYTIC_CARBON);
                        accept(output, MagItems.DIAMAGNETIC_BLOCK);
                        accept(output, MagItems.HEMATITE_LENS);
                        accept(output, MagItems.VECTOR_CORE);
                        accept(output, MagItems.DEUTERIUM_CELL);
                        accept(output, MagItems.PYRRHOTITE_CATALYST);
                        accept(output, MagItems.ENHANCED_PYRRHOTITE_CATALYST);
                        accept(output, MagItems.COSMIC_PYRRHOTITE_CATALYST);
                        accept(output, MagItems.METEORITE_CORE);
                        accept(output, MagItems.METEORITE_SAPLING);
                        accept(output, MagItems.METEORITE_FRAGMENT);

                        // ── Fluids ──
                        accept(output, MagItems.FERROFLUID_BUCKET);
                        accept(output, MagItems.MR_FLUID_BUCKET);
                        accept(output, MagItems.DEUTERIUM_OXIDE_BUCKET);
                        accept(output, MagItems.MR_FLUID_GOLEM_SPAWN_EGG);

                        // ── Compasses & handheld tools ──
                        accept(output, MagItems.FIELD_COMPASS);
                        accept(output, MagItems.ORE_COMPASS);
                        accept(output, MagItems.COSMIC_COMPASS);
                        accept(output, MagItems.MAGNETIC_GRAPPLE);
                        accept(output, MagItems.REPULSOR_GUN);

                        // ── Emitters ──
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

                        // ── Machines & tech ──
                        accept(output, MagItems.INDUCTION_PAD);
                        accept(output, MagItems.KINETIC_COIL);
                        accept(output, MagItems.EMP_CHARGE);
                        accept(output, MagItems.GYROSTABILIZER);
                        accept(output, MagItems.MAGNETOSTRICTIVE_SENSOR);
                        accept(output, MagItems.BARKHAUSEN_GENERATOR);
                        accept(output, MagItems.STRUCTURAL_INDUCER);
                        accept(output, MagItems.HOMOPOLAR_MOTOR);
                        accept(output, MagItems.TOKAMAK_CONTROLLER);
                        accept(output, MagItems.TOKAMAK_COIL);
                        accept(output, MagItems.G_FORCE_CUSHION);

                        // ── Propulsion ──
                        accept(output, MagItems.SOLAR_SAIL);
                        accept(output, MagItems.MHD_JET);
                        accept(output, MagItems.MICRO_THRUSTER);

                        // ── Magnetic-metal anvils ──
                        accept(output, MagItems.MAGNETITE_ANVIL);
                        accept(output, MagItems.MAGHEMITE_ANVIL);
                        accept(output, MagItems.HEMATITE_ANVIL);
                        accept(output, MagItems.TITANOMAGNETITE_ANVIL);

                        // ── Decorative ──
                        accept(output, MagItems.MAGNETIC_ITEM_FRAME);
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
