package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.item.FieldCompassItem;
import com.stonytark.magnetization.content.item.MagneticGrappleItem;
import net.minecraft.world.item.AnimalArmorItem;
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
    public static final DeferredItem<BlockItem> MAGNETIC_EXCAVATOR = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_EXCAVATOR);
    public static final DeferredItem<BlockItem> LODESTONE_CORE   = REGISTER.registerSimpleBlockItem(MagBlocks.LODESTONE_CORE);
    public static final DeferredItem<BlockItem> MAGNETIC_ITEM_FRAME = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_ITEM_FRAME);
    public static final DeferredItem<BlockItem> INDUCTION_PAD       = REGISTER.registerSimpleBlockItem(MagBlocks.INDUCTION_PAD);
    public static final DeferredItem<BlockItem> KINETIC_COIL        = REGISTER.registerSimpleBlockItem(MagBlocks.KINETIC_COIL);
    public static final DeferredItem<BlockItem> EMP_CHARGE          = REGISTER.registerSimpleBlockItem(MagBlocks.EMP_CHARGE);
    public static final DeferredItem<BlockItem> MAGNETOSTRICTIVE_SENSOR = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETOSTRICTIVE_SENSOR);
    public static final DeferredItem<BlockItem> BARKHAUSEN_GENERATOR = REGISTER.registerSimpleBlockItem(MagBlocks.BARKHAUSEN);
    public static final DeferredItem<BlockItem> MAGNETIC_SWITCH  = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_SWITCH);
    public static final DeferredItem<BlockItem> PERMANENT_MAGNET = REGISTER.registerSimpleBlockItem(MagBlocks.PERMANENT_MAGNET);
    public static final DeferredItem<BlockItem> TEMPORARY_MAGNET = REGISTER.registerSimpleBlockItem(MagBlocks.TEMPORARY_MAGNET);
    public static final DeferredItem<BlockItem> POLARITY_INVERTER = REGISTER.registerSimpleBlockItem(MagBlocks.POLARITY_INVERTER);
    public static final DeferredItem<BlockItem> MAGNETITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_MAGNETITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_MAGNETITE_ORE);
    public static final DeferredItem<BlockItem> MAGNETITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETITE_BLOCK);
    public static final DeferredItem<BlockItem> RAW_MAGNETITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_MAGNETITE_BLOCK);
    public static final DeferredItem<BlockItem> ANOMALY_STONE = REGISTER.registerSimpleBlockItem(MagBlocks.ANOMALY_STONE);
    public static final DeferredItem<BlockItem> MAGNETIC_GRAVEL = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_GRAVEL);
    public static final DeferredItem<BlockItem> COBBLED_ANOMALY_STONE = REGISTER.registerSimpleBlockItem(MagBlocks.COBBLED_ANOMALY_STONE);
    public static final DeferredItem<BlockItem> ANOMALY_STONE_STAIRS = REGISTER.registerSimpleBlockItem(MagBlocks.ANOMALY_STONE_STAIRS);
    public static final DeferredItem<BlockItem> ANOMALY_STONE_SLAB = REGISTER.registerSimpleBlockItem(MagBlocks.ANOMALY_STONE_SLAB);
    public static final DeferredItem<BlockItem> COBBLED_ANOMALY_STONE_STAIRS = REGISTER.registerSimpleBlockItem(MagBlocks.COBBLED_ANOMALY_STONE_STAIRS);
    public static final DeferredItem<BlockItem> COBBLED_ANOMALY_STONE_SLAB = REGISTER.registerSimpleBlockItem(MagBlocks.COBBLED_ANOMALY_STONE_SLAB);
    public static final DeferredItem<BlockItem> COBBLED_ANOMALY_STONE_WALL = REGISTER.registerSimpleBlockItem(MagBlocks.COBBLED_ANOMALY_STONE_WALL);

    /** Raw drop from magnetite ore. Smelt or blast to a magnetite ingot. */
    public static final DeferredItem<Item> RAW_MAGNETITE =
            REGISTER.registerSimpleItem("raw_magnetite", new Item.Properties());

    /** Smelted magnetite. In #magnetization:ferromagnetic so emitters pull it. */
    public static final DeferredItem<Item> MAGNETITE_INGOT =
            REGISTER.registerSimpleItem("magnetite_ingot", new Item.Properties());

    // ------------------------------------------------------------------
    // Iron-oxide family BlockItems + raw/ingot pairs (mechanics deferred).
    // ------------------------------------------------------------------
    public static final DeferredItem<BlockItem> MAGHEMITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.MAGHEMITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_MAGHEMITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_MAGHEMITE_ORE);
    public static final DeferredItem<BlockItem> MAGHEMITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.MAGHEMITE_BLOCK);
    public static final DeferredItem<BlockItem> RAW_MAGHEMITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_MAGHEMITE_BLOCK);
    public static final DeferredItem<Item> RAW_MAGHEMITE = REGISTER.registerSimpleItem("raw_maghemite", new Item.Properties());
    public static final DeferredItem<Item> MAGHEMITE_INGOT = REGISTER.registerSimpleItem("maghemite_ingot", new Item.Properties());

    // Maghemite equipment — lower tier than magnetite, mirrors stone/iron-tier
    // stats. Reflavoured as "what you get from oxidised magnetite" — a usable
    // early-game gear set for players whose magnetite has rusted past its
    // prime, closing the oxidation-decay gameplay loop.
    public static final DeferredItem<SwordItem> MAGHEMITE_SWORD =
            REGISTER.registerItem("maghemite_sword",
                    p -> new SwordItem(MagTiers.MAGHEMITE, p),
                    new Item.Properties().attributes(SwordItem.createAttributes(MagTiers.MAGHEMITE, 3, -2.4f)));
    public static final DeferredItem<PickaxeItem> MAGHEMITE_PICKAXE =
            REGISTER.registerItem("maghemite_pickaxe",
                    p -> new PickaxeItem(MagTiers.MAGHEMITE, p),
                    new Item.Properties().attributes(PickaxeItem.createAttributes(MagTiers.MAGHEMITE, 1, -2.8f)));
    public static final DeferredItem<AxeItem> MAGHEMITE_AXE =
            REGISTER.registerItem("maghemite_axe",
                    p -> new AxeItem(MagTiers.MAGHEMITE, p),
                    new Item.Properties().attributes(AxeItem.createAttributes(MagTiers.MAGHEMITE, 6, -3.1f)));
    public static final DeferredItem<ShovelItem> MAGHEMITE_SHOVEL =
            REGISTER.registerItem("maghemite_shovel",
                    p -> new ShovelItem(MagTiers.MAGHEMITE, p),
                    new Item.Properties().attributes(ShovelItem.createAttributes(MagTiers.MAGHEMITE, 1.5f, -3.0f)));
    public static final DeferredItem<HoeItem> MAGHEMITE_HOE =
            REGISTER.registerItem("maghemite_hoe",
                    p -> new HoeItem(MagTiers.MAGHEMITE, p),
                    new Item.Properties().attributes(HoeItem.createAttributes(MagTiers.MAGHEMITE, -2, -1.0f)));
    public static final DeferredItem<ArmorItem> MAGHEMITE_HELMET =
            REGISTER.registerItem("maghemite_helmet",
                    p -> new ArmorItem(MagArmorMaterials.maghemite(), ArmorItem.Type.HELMET, p),
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(10)));
    public static final DeferredItem<ArmorItem> MAGHEMITE_CHESTPLATE =
            REGISTER.registerItem("maghemite_chestplate",
                    p -> new ArmorItem(MagArmorMaterials.maghemite(), ArmorItem.Type.CHESTPLATE, p),
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(10)));
    public static final DeferredItem<ArmorItem> MAGHEMITE_LEGGINGS =
            REGISTER.registerItem("maghemite_leggings",
                    p -> new ArmorItem(MagArmorMaterials.maghemite(), ArmorItem.Type.LEGGINGS, p),
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(10)));
    public static final DeferredItem<ArmorItem> MAGHEMITE_BOOTS =
            REGISTER.registerItem("maghemite_boots",
                    p -> new ArmorItem(MagArmorMaterials.maghemite(), ArmorItem.Type.BOOTS, p),
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(10)));
    public static final DeferredItem<AnimalArmorItem> MAGHEMITE_HORSE_ARMOR =
            REGISTER.registerItem("maghemite_horse_armor",
                    p -> new AnimalArmorItem(MagArmorMaterials.maghemite(),
                            AnimalArmorItem.BodyType.EQUESTRIAN, false, p),
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<BlockItem> PYRRHOTITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.PYRRHOTITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_PYRRHOTITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_PYRRHOTITE_ORE);
    public static final DeferredItem<BlockItem> PYRRHOTITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.PYRRHOTITE_BLOCK);
    public static final DeferredItem<BlockItem> RAW_PYRRHOTITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_PYRRHOTITE_BLOCK);
    public static final DeferredItem<Item> RAW_PYRRHOTITE = REGISTER.registerSimpleItem("raw_pyrrhotite", new Item.Properties());
    public static final DeferredItem<Item> PYRRHOTITE_INGOT = REGISTER.registerSimpleItem("pyrrhotite_ingot", new Item.Properties());

    public static final DeferredItem<BlockItem> HEMATITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.HEMATITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_HEMATITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_HEMATITE_ORE);
    public static final DeferredItem<BlockItem> HEMATITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.HEMATITE_BLOCK);
    public static final DeferredItem<BlockItem> RAW_HEMATITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_HEMATITE_BLOCK);
    public static final DeferredItem<Item> RAW_HEMATITE = REGISTER.registerSimpleItem("raw_hematite", new Item.Properties());
    public static final DeferredItem<Item> HEMATITE_INGOT = REGISTER.registerSimpleItem("hematite_ingot", new Item.Properties());

    public static final DeferredItem<BlockItem> TITANOMAGNETITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.TITANOMAGNETITE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_TITANOMAGNETITE_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE);
    public static final DeferredItem<BlockItem> TITANOMAGNETITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.TITANOMAGNETITE_BLOCK);
    public static final DeferredItem<BlockItem> RAW_TITANOMAGNETITE_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_TITANOMAGNETITE_BLOCK);
    public static final DeferredItem<Item> RAW_TITANOMAGNETITE = REGISTER.registerSimpleItem("raw_titanomagnetite", new Item.Properties());
    public static final DeferredItem<Item> TITANOMAGNETITE_INGOT = REGISTER.registerSimpleItem("titanomagnetite_ingot", new Item.Properties());

    public static final DeferredItem<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlockItem> PYRRHOTITE_CATALYST =
            REGISTER.registerItem("pyrrhotite_catalyst",
                    p -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlockItem(
                            MagBlocks.PYRRHOTITE_CATALYST.get(), p));
    public static final DeferredItem<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlockItem> ENHANCED_PYRRHOTITE_CATALYST =
            REGISTER.registerItem("enhanced_pyrrhotite_catalyst",
                    p -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlockItem(
                            MagBlocks.ENHANCED_PYRRHOTITE_CATALYST.get(), p));
    public static final DeferredItem<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlockItem> COSMIC_PYRRHOTITE_CATALYST =
            REGISTER.registerItem("cosmic_pyrrhotite_catalyst",
                    p -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlockItem(
                            MagBlocks.COSMIC_PYRRHOTITE_CATALYST.get(), p));
    public static final DeferredItem<BlockItem> METEORITE_CORE = REGISTER.registerSimpleBlockItem(MagBlocks.METEORITE_CORE);
    public static final DeferredItem<BlockItem> METEORITE_SAPLING = REGISTER.registerSimpleBlockItem(MagBlocks.METEORITE_SAPLING);

    /** Bonus drop from breaking a meteorite core (+1–3 per break alongside
     *  the core block itself). High-tier reagent for advanced recipes —
     *  meant to feel like a rare cosmic-origin material the player can stash
     *  for future use. Currently slots in as a substitute for ferromagnetic
     *  ingot in a few crafts; future items (cosmic-themed gear, anomaly-
     *  resistant emitters) will lean on it more heavily. */
    public static final DeferredItem<Item> METEORITE_FRAGMENT =
            REGISTER.registerSimpleItem("meteorite_fragment", new Item.Properties());

    /** Imprint Module — portable field-config memory. Captures from an emitter
     *  via shift-right-click, projects via right-click. Reusable indefinitely.
     *  Crafted from titanomagnetite_ingot + ender_pearl. */
    public static final DeferredItem<com.stonytark.magnetization.content.item.ImprintModuleItem> IMPRINT_MODULE =
            REGISTER.registerItem("imprint_module",
                    com.stonytark.magnetization.content.item.ImprintModuleItem::new,
                    new Item.Properties());

    /** Hematite Lens — polarity-lock tool. Right-click an emitter to force its
     *  polarity to the lens's current value, overriding any Polarity Inverter.
     *  Crafted from 1 hematite_ingot + 1 glass_pane. */
    public static final DeferredItem<com.stonytark.magnetization.content.item.HematiteLensItem> HEMATITE_LENS =
            REGISTER.registerItem("hematite_lens",
                    com.stonytark.magnetization.content.item.HematiteLensItem::new,
                    new Item.Properties());

    /** Magnetic elytra — vanilla elytra reforged with ferromagnetic plate.
     *  Gliding wearer's field-susceptibility is multiplied so passing
     *  emitters tug them harder; skilled players can rail-ride between
     *  emitter chains. Same chest slot as vanilla elytra. */
    public static final DeferredItem<com.stonytark.magnetization.content.item.MagneticElytraItem> MAGNETIC_ELYTRA =
            REGISTER.registerItem("magnetic_elytra",
                    com.stonytark.magnetization.content.item.MagneticElytraItem::new,
                    new Item.Properties().durability(432).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    // Crafting components.
    public static final DeferredItem<Item> FERROMAGNETIC_INGOT =
            REGISTER.registerSimpleItem("ferromagnetic_ingot", new Item.Properties());

    public static final DeferredItem<Item> MAGNETIC_PLATE =
            REGISTER.registerSimpleItem("magnetic_plate", new Item.Properties());

    /** Rare drop from chopping logs with a magnetized axe. Now a real placeable block —
     *  the BlockItem references {@link MagBlocks#PETRIFIED_WOOD}. Magnetized axes
     *  preferentially pull both the drop and the block (the axe rip yanks placed
     *  petrified-wood blocks from the world). Tagged ferromagnetic so generic emitters
     *  weakly pull dropped pieces, but intentionally NOT in ferromagnetic_blocks. */
    public static final DeferredItem<BlockItem> PETRIFIED_WOOD =
            REGISTER.registerSimpleBlockItem(MagBlocks.PETRIFIED_WOOD);

    public static final DeferredItem<FieldCompassItem> FIELD_COMPASS =
            REGISTER.registerItem("field_compass", FieldCompassItem::new, new Item.Properties().stacksTo(1));

    /** Ore Dowsing Compass — points at the nearest metallic ore vein; anvil-tune
     *  it onto a specific ore. Scrambles in the Anomaly biome. */
    public static final DeferredItem<com.stonytark.magnetization.content.item.OreCompassItem> ORE_COMPASS =
            REGISTER.registerItem("ore_compass",
                    com.stonytark.magnetization.content.item.OreCompassItem::new,
                    new Item.Properties().stacksTo(1));

    /** Long-range compass tracking the nearest active meteorite_core (range
     *  512 blocks). Not scrambled by the anomaly biome. */
    public static final DeferredItem<com.stonytark.magnetization.content.item.CosmicCompassItem> COSMIC_COMPASS =
            REGISTER.registerItem("cosmic_compass",
                    com.stonytark.magnetization.content.item.CosmicCompassItem::new,
                    new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    public static final DeferredItem<MagneticGrappleItem> MAGNETIC_GRAPPLE =
            REGISTER.registerItem("magnetic_grapple", MagneticGrappleItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<com.stonytark.magnetization.content.item.RepulsorGunItem> REPULSOR_GUN =
            REGISTER.registerItem("repulsor_gun",
                    com.stonytark.magnetization.content.item.RepulsorGunItem::new,
                    new Item.Properties().stacksTo(1));

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

    // ---- Horse body armor ----
    // Single-stack, no durability (matches vanilla horse-armor convention).
    // The shared MagArmorMaterials.MAGNETITE / FERROMAGNETIC already define
    // ArmorItem.Type.BODY values; AnimalArmorItem renders via the BodyType
    // EQUESTRIAN locator which resolves to
    // textures/entity/horse/armor/horse_armor_<material_path>.png.
    public static final DeferredItem<AnimalArmorItem> MAGNETITE_HORSE_ARMOR =
            REGISTER.registerItem("magnetite_horse_armor",
                    p -> new AnimalArmorItem(MagArmorMaterials.magnetite(),
                            AnimalArmorItem.BodyType.EQUESTRIAN, false, p),
                    new Item.Properties().stacksTo(1));

    public static final DeferredItem<AnimalArmorItem> FERROMAGNETIC_HORSE_ARMOR =
            REGISTER.registerItem("ferromagnetic_horse_armor",
                    p -> new AnimalArmorItem(MagArmorMaterials.ferromagnetic(),
                            AnimalArmorItem.BodyType.EQUESTRIAN, false, p),
                    new Item.Properties().stacksTo(1));

    private MagItems() {}
}
