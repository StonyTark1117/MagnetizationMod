package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.item.FieldCompassItem;
import com.stonytark.magnetization.content.item.MagneticGrappleItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagItems {

    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(Magnetization.MOD_ID);

    // Block items — wired to MagBlocks entries.
    public static final DeferredItem<BlockItem> ELECTROMAGNET    = REGISTER.registerSimpleBlockItem(MagBlocks.ELECTROMAGNET);
    public static final DeferredItem<BlockItem> KINETIC_ELECTROMAGNET = REGISTER.registerSimpleBlockItem(MagBlocks.KINETIC_ELECTROMAGNET);
    public static final DeferredItem<BlockItem> MAGNETIC_ANCHOR  = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_ANCHOR);
    public static final DeferredItem<BlockItem> REPULSOR_COIL    = REGISTER.registerSimpleBlockItem(MagBlocks.REPULSOR_COIL);
    public static final DeferredItem<BlockItem> TRACTOR_BEAM     = REGISTER.registerSimpleBlockItem(MagBlocks.TRACTOR_BEAM);
    public static final DeferredItem<BlockItem> LODESTONE_CORE   = REGISTER.registerSimpleBlockItem(MagBlocks.LODESTONE_CORE);
    public static final DeferredItem<BlockItem> MAGNETIC_SWITCH  = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_SWITCH);
    public static final DeferredItem<BlockItem> PERMANENT_MAGNET = REGISTER.registerSimpleBlockItem(MagBlocks.PERMANENT_MAGNET);
    public static final DeferredItem<BlockItem> POLARITY_INVERTER = REGISTER.registerSimpleBlockItem(MagBlocks.POLARITY_INVERTER);
    public static final DeferredItem<BlockItem> MAGNETITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_MAGNETITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_MAGNETITE_ORE);
    public static final DeferredItem<BlockItem> MAGNETITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETITE_BLOCK);
    public static final DeferredItem<BlockItem> RAW_MAGNETITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_MAGNETITE_BLOCK);

    /** Raw drop from magnetite ore. Smelt or blast to a magnetite ingot. */
    public static final DeferredItem<Item> RAW_MAGNETITE =
            REGISTER.registerSimpleItem("raw_magnetite", new Item.Properties());

    /** Smelted magnetite. In #magnetization:ferromagnetic so emitters pull it. */
    public static final DeferredItem<Item> MAGNETITE_INGOT =
            REGISTER.registerSimpleItem("magnetite_ingot", new Item.Properties());

    // Crafting components.
    public static final DeferredItem<Item> FERROMAGNETIC_INGOT =
            REGISTER.registerSimpleItem("ferromagnetic_ingot", new Item.Properties());

    public static final DeferredItem<Item> MAGNETIC_PLATE =
            REGISTER.registerSimpleItem("magnetic_plate", new Item.Properties());

    public static final DeferredItem<FieldCompassItem> FIELD_COMPASS =
            REGISTER.registerItem("field_compass", FieldCompassItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<MagneticGrappleItem> MAGNETIC_GRAPPLE =
            REGISTER.registerItem("magnetic_grapple", MagneticGrappleItem::new, new Item.Properties().stacksTo(1));

    // ---- Magnetite gear ----
    // 1.21.1 tool constructors take only Tier + Properties; attack damage and
    // speed are baked into Properties via the per-class createAttributes static.
    public static final DeferredItem<SwordItem> MAGNETITE_SWORD =
            REGISTER.registerItem("magnetite_sword",
                    p -> new SwordItem(MagTiers.MAGNETITE, p),
                    new Item.Properties().attributes(SwordItem.createAttributes(MagTiers.MAGNETITE, 3, -2.4f)));
    public static final DeferredItem<PickaxeItem> MAGNETITE_PICKAXE =
            REGISTER.registerItem("magnetite_pickaxe",
                    p -> new PickaxeItem(MagTiers.MAGNETITE, p),
                    new Item.Properties().attributes(PickaxeItem.createAttributes(MagTiers.MAGNETITE, 1, -2.8f)));
    public static final DeferredItem<AxeItem> MAGNETITE_AXE =
            REGISTER.registerItem("magnetite_axe",
                    p -> new AxeItem(MagTiers.MAGNETITE, p),
                    new Item.Properties().attributes(AxeItem.createAttributes(MagTiers.MAGNETITE, 6, -3.1f)));
    public static final DeferredItem<ShovelItem> MAGNETITE_SHOVEL =
            REGISTER.registerItem("magnetite_shovel",
                    p -> new ShovelItem(MagTiers.MAGNETITE, p),
                    new Item.Properties().attributes(ShovelItem.createAttributes(MagTiers.MAGNETITE, 1.5f, -3.0f)));
    public static final DeferredItem<HoeItem> MAGNETITE_HOE =
            REGISTER.registerItem("magnetite_hoe",
                    p -> new HoeItem(MagTiers.MAGNETITE, p),
                    new Item.Properties().attributes(HoeItem.createAttributes(MagTiers.MAGNETITE, -2, -1.0f)));

    // Armor pieces — automatically magnetic via the #magnetization:metal_armor tag,
    // so wearing them lets emitters yank the player around.
    public static final DeferredItem<ArmorItem> MAGNETITE_HELMET =
            REGISTER.registerItem("magnetite_helmet",
                    p -> new ArmorItem(MagArmorMaterials.magnetite(), ArmorItem.Type.HELMET, p),
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15)));
    public static final DeferredItem<ArmorItem> MAGNETITE_CHESTPLATE =
            REGISTER.registerItem("magnetite_chestplate",
                    p -> new ArmorItem(MagArmorMaterials.magnetite(), ArmorItem.Type.CHESTPLATE, p),
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15)));
    public static final DeferredItem<ArmorItem> MAGNETITE_LEGGINGS =
            REGISTER.registerItem("magnetite_leggings",
                    p -> new ArmorItem(MagArmorMaterials.magnetite(), ArmorItem.Type.LEGGINGS, p),
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15)));
    public static final DeferredItem<ArmorItem> MAGNETITE_BOOTS =
            REGISTER.registerItem("magnetite_boots",
                    p -> new ArmorItem(MagArmorMaterials.magnetite(), ArmorItem.Type.BOOTS, p),
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15)));

    // ---- Ferromagnetic gear ----
    // Crafted from ferromagnetic_ingot (iron + magnetite alloy). Slightly stronger
    // than magnetite, weaker than netherite — slotted as a diamond-tier alternative
    // for ferromagnetic-themed builds.
    public static final DeferredItem<SwordItem> FERROMAGNETIC_SWORD =
            REGISTER.registerItem("ferromagnetic_sword",
                    p -> new SwordItem(MagTiers.FERROMAGNETIC, p),
                    new Item.Properties().attributes(SwordItem.createAttributes(MagTiers.FERROMAGNETIC, 3, -2.4f)));
    public static final DeferredItem<PickaxeItem> FERROMAGNETIC_PICKAXE =
            REGISTER.registerItem("ferromagnetic_pickaxe",
                    p -> new PickaxeItem(MagTiers.FERROMAGNETIC, p),
                    new Item.Properties().attributes(PickaxeItem.createAttributes(MagTiers.FERROMAGNETIC, 1, -2.8f)));
    public static final DeferredItem<AxeItem> FERROMAGNETIC_AXE =
            REGISTER.registerItem("ferromagnetic_axe",
                    p -> new AxeItem(MagTiers.FERROMAGNETIC, p),
                    new Item.Properties().attributes(AxeItem.createAttributes(MagTiers.FERROMAGNETIC, 5, -3.0f)));
    public static final DeferredItem<ShovelItem> FERROMAGNETIC_SHOVEL =
            REGISTER.registerItem("ferromagnetic_shovel",
                    p -> new ShovelItem(MagTiers.FERROMAGNETIC, p),
                    new Item.Properties().attributes(ShovelItem.createAttributes(MagTiers.FERROMAGNETIC, 1.5f, -3.0f)));
    public static final DeferredItem<HoeItem> FERROMAGNETIC_HOE =
            REGISTER.registerItem("ferromagnetic_hoe",
                    p -> new HoeItem(MagTiers.FERROMAGNETIC, p),
                    new Item.Properties().attributes(HoeItem.createAttributes(MagTiers.FERROMAGNETIC, -3, 0.0f)));

    public static final DeferredItem<ArmorItem> FERROMAGNETIC_HELMET =
            REGISTER.registerItem("ferromagnetic_helmet",
                    p -> new ArmorItem(MagArmorMaterials.ferromagnetic(), ArmorItem.Type.HELMET, p),
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(33)));
    public static final DeferredItem<ArmorItem> FERROMAGNETIC_CHESTPLATE =
            REGISTER.registerItem("ferromagnetic_chestplate",
                    p -> new ArmorItem(MagArmorMaterials.ferromagnetic(), ArmorItem.Type.CHESTPLATE, p),
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(33)));
    public static final DeferredItem<ArmorItem> FERROMAGNETIC_LEGGINGS =
            REGISTER.registerItem("ferromagnetic_leggings",
                    p -> new ArmorItem(MagArmorMaterials.ferromagnetic(), ArmorItem.Type.LEGGINGS, p),
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(33)));
    public static final DeferredItem<ArmorItem> FERROMAGNETIC_BOOTS =
            REGISTER.registerItem("ferromagnetic_boots",
                    p -> new ArmorItem(MagArmorMaterials.ferromagnetic(), ArmorItem.Type.BOOTS, p),
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(33)));

    private MagItems() {}
}
