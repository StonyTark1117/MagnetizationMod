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

    private static DeferredItem<net.minecraft.world.item.BucketItem> gasBucket(
            final String id, final java.util.function.Supplier<? extends net.minecraft.world.level.material.Fluid> fluid) {
        return REGISTER.registerItem(id, p -> new net.minecraft.world.item.BucketItem(fluid.get(), p),
                new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    }

    // Block items — wired to MagBlocks entries.
    public static final DeferredItem<BlockItem> ELECTROMAGNET    = REGISTER.registerSimpleBlockItem(MagBlocks.ELECTROMAGNET);
    public static final DeferredItem<BlockItem> DIPOLE_ELECTROMAGNET = REGISTER.registerSimpleBlockItem(MagBlocks.DIPOLE_ELECTROMAGNET);
    public static final DeferredItem<BlockItem> KINETIC_ELECTROMAGNET = REGISTER.registerSimpleBlockItem(MagBlocks.KINETIC_ELECTROMAGNET);
    public static final DeferredItem<BlockItem> MAGNETIC_ANCHOR  = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_ANCHOR);
    public static final DeferredItem<BlockItem> REPULSOR_COIL    = REGISTER.registerSimpleBlockItem(MagBlocks.REPULSOR_COIL);
    public static final DeferredItem<BlockItem> TRACTOR_BEAM     = REGISTER.registerSimpleBlockItem(MagBlocks.TRACTOR_BEAM);
    public static final DeferredItem<BlockItem> MAGNETIC_EXCAVATOR = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_EXCAVATOR);
    public static final DeferredItem<BlockItem> LODESTONE_CORE   = REGISTER.registerSimpleBlockItem(MagBlocks.LODESTONE_CORE);
    public static final DeferredItem<BlockItem> MAGNETIC_ITEM_FRAME = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETIC_ITEM_FRAME);
    public static final DeferredItem<BlockItem> INDUCTION_PAD       = REGISTER.registerSimpleBlockItem(MagBlocks.INDUCTION_PAD);
    public static final DeferredItem<BlockItem> DIAMAGNETIC_BLOCK    = REGISTER.registerSimpleBlockItem(MagBlocks.DIAMAGNETIC_BLOCK);
    public static final DeferredItem<BlockItem> KINETIC_COIL        = REGISTER.registerSimpleBlockItem(MagBlocks.KINETIC_COIL);
    public static final DeferredItem<BlockItem> EMP_CHARGE          = REGISTER.registerSimpleBlockItem(MagBlocks.EMP_CHARGE);
    public static final DeferredItem<BlockItem> GYROSTABILIZER      = REGISTER.registerSimpleBlockItem(MagBlocks.GYROSTABILIZER);
    public static final DeferredItem<BlockItem> MAGNETITE_ANVIL       = REGISTER.registerSimpleBlockItem(MagBlocks.MAGNETITE_ANVIL);
    public static final DeferredItem<BlockItem> MAGHEMITE_ANVIL       = REGISTER.registerSimpleBlockItem(MagBlocks.MAGHEMITE_ANVIL);
    public static final DeferredItem<BlockItem> HEMATITE_ANVIL        = REGISTER.registerSimpleBlockItem(MagBlocks.HEMATITE_ANVIL);
    public static final DeferredItem<BlockItem> TITANOMAGNETITE_ANVIL = REGISTER.registerSimpleBlockItem(MagBlocks.TITANOMAGNETITE_ANVIL);
    public static final DeferredItem<BlockItem> G_FORCE_CUSHION      = REGISTER.registerSimpleBlockItem(MagBlocks.G_FORCE_CUSHION);
    public static final DeferredItem<BlockItem> SOLAR_SAIL           = REGISTER.registerSimpleBlockItem(MagBlocks.SOLAR_SAIL);
    public static final DeferredItem<BlockItem> MICRO_THRUSTER       = REGISTER.registerSimpleBlockItem(MagBlocks.MICRO_THRUSTER);
    public static final DeferredItem<BlockItem> ION_THRUSTER         = REGISTER.registerSimpleBlockItem(MagBlocks.ION_THRUSTER);
    public static final DeferredItem<BlockItem> MHD_JET              = REGISTER.registerSimpleBlockItem(MagBlocks.MHD_JET);
    public static final DeferredItem<BlockItem> FUSION_THRUSTER      = REGISTER.registerSimpleBlockItem(MagBlocks.FUSION_THRUSTER);
    public static final DeferredItem<BlockItem> RAILGUN_EMITTER      = REGISTER.registerSimpleBlockItem(MagBlocks.RAILGUN_EMITTER);
    /** Railgun remote trigger — pair in a rail's GUI slot, fire a held arc in-hand. */
    public static final DeferredItem<com.stonytark.magnetization.content.railgun.RailgunRemoteItem> RAILGUN_REMOTE =
            REGISTER.registerItem("railgun_remote",
                    com.stonytark.magnetization.content.railgun.RailgunRemoteItem::new,
                    new Item.Properties().stacksTo(1));
    public static final DeferredItem<BlockItem> ELECTROLYZER         = REGISTER.registerSimpleBlockItem(MagBlocks.ELECTROLYZER);
    public static final DeferredItem<BlockItem> GAS_EXCITER          = REGISTER.registerSimpleBlockItem(MagBlocks.GAS_EXCITER);
    public static final DeferredItem<BlockItem> AIR_SEPARATOR        = REGISTER.registerSimpleBlockItem(MagBlocks.AIR_SEPARATOR);
    public static final DeferredItem<BlockItem> HOMOPOLAR_MOTOR      = REGISTER.registerSimpleBlockItem(MagBlocks.HOMOPOLAR_MOTOR);
    public static final DeferredItem<BlockItem> STRUCTURAL_INDUCER   = REGISTER.registerSimpleBlockItem(MagBlocks.STRUCTURAL_INDUCER);
    public static final DeferredItem<BlockItem> TOKAMAK_COIL         = REGISTER.registerSimpleBlockItem(MagBlocks.TOKAMAK_COIL);
    public static final DeferredItem<BlockItem> TOKAMAK_CONTROLLER   = REGISTER.registerSimpleBlockItem(MagBlocks.TOKAMAK_CONTROLLER);
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
    public static final DeferredItem<BlockItem> FERROMAGNETIC_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.FERROMAGNETIC_BLOCK);
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
    /** Pyrolytic carbon — a strongly diamagnetic wafer; drop it over a magnet and it floats. */
    public static final DeferredItem<Item> PYROLYTIC_CARBON =
            REGISTER.registerSimpleItem("pyrolytic_carbon", new Item.Properties());
    /** Magnetoresistive Dampening Boots — auto-arrest falls (durability cost); inert to fields unless magnetized. */
    public static final DeferredItem<com.stonytark.magnetization.content.dampener.MagnetoresistiveBootsItem> MAGNETORESISTIVE_BOOTS =
            REGISTER.registerItem("magnetoresistive_boots",
                    com.stonytark.magnetization.content.dampener.MagnetoresistiveBootsItem::new,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(12)));
    /** Deuterium Cell — D-T fusion fuel for the tokamak; right-click the controller to load it. */
    public static final DeferredItem<Item> DEUTERIUM_CELL =
            REGISTER.registerSimpleItem("deuterium_cell", new Item.Properties());
    /** Vector Core — titanomagnetite chip; right-click a Repulsor to make it thrust ships along its facing. */
    public static final DeferredItem<Item> VECTOR_CORE =
            REGISTER.registerSimpleItem("vector_core", new Item.Properties());
    /** Bucket of ferrofluid. Magnetizable in the electromagnet GUI (carries a polarity). */
    public static final DeferredItem<com.stonytark.magnetization.content.fluid.FerrofluidBucketItem> FERROFLUID_BUCKET =
            REGISTER.registerItem("ferrofluid_bucket",
                    p -> new com.stonytark.magnetization.content.fluid.FerrofluidBucketItem(MagFluids.FERROFLUID.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Bucket of magnetorheological fluid. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> MR_FLUID_BUCKET =
            REGISTER.registerItem("mr_fluid_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.MR_FLUID.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Bucket of deuterium oxide (heavy water) — fuel for the Deuterium Fuel Cell. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> DEUTERIUM_OXIDE_BUCKET =
            REGISTER.registerItem("deuterium_oxide_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.DEUTERIUM_OXIDE.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Bucket of liquid gallium (Lorentz current metal). */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> GALLIUM_BUCKET =
            REGISTER.registerItem("gallium_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.GALLIUM.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Bucket of mixed gallium (gallium + magnetite/iron; ferrofluid-like). */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> MIXED_GALLIUM_BUCKET =
            REGISTER.registerItem("mixed_gallium_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.MIXED_GALLIUM.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));

    // ---------------- Fusion-fuel isotope chain (1.3) ----------------
    /** Bucket of hydrogen — cheap starter propellant + parent of deuterium. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> HYDROGEN_BUCKET =
            REGISTER.registerItem("hydrogen_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.HYDROGEN.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Bucket of tritium — D-T fusion fuel, bred from lithium. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> TRITIUM_BUCKET =
            REGISTER.registerItem("tritium_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.TRITIUM.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Bucket of helium-3 — premium aneutronic fusion fuel. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> HELIUM_3_BUCKET =
            REGISTER.registerItem("helium_3_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.HELIUM_3.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    public static final DeferredItem<net.minecraft.world.item.BucketItem> HELIUM_BUCKET = gasBucket("helium_bucket", MagFluids.HELIUM);
    public static final DeferredItem<net.minecraft.world.item.BucketItem> NEON_BUCKET = gasBucket("neon_bucket", MagFluids.NEON);
    public static final DeferredItem<net.minecraft.world.item.BucketItem> ARGON_BUCKET = gasBucket("argon_bucket", MagFluids.ARGON);
    public static final DeferredItem<net.minecraft.world.item.BucketItem> KRYPTON_BUCKET = gasBucket("krypton_bucket", MagFluids.KRYPTON);
    public static final DeferredItem<net.minecraft.world.item.BucketItem> XENON_BUCKET = gasBucket("xenon_bucket", MagFluids.XENON);
    public static final DeferredItem<net.minecraft.world.item.BucketItem> RADON_BUCKET = gasBucket("radon_bucket", MagFluids.RADON);
    /** Bucket of liquid lithium — conductive working fluid for the MHD jet. */
    public static final DeferredItem<net.minecraft.world.item.BucketItem> LIQUID_LITHIUM_BUCKET =
            REGISTER.registerItem("liquid_lithium_bucket",
                    p -> new net.minecraft.world.item.BucketItem(MagFluids.LIQUID_LITHIUM.get(), p),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1));
    /** Tritium Cell — D-T fusion fuel for the tokamak (higher raw output than deuterium). */
    public static final DeferredItem<Item> TRITIUM_CELL =
            REGISTER.registerSimpleItem("tritium_cell", new Item.Properties());
    /** Helium-3 Cell — premium aneutronic tokamak fuel (cleanest, most total energy). */
    public static final DeferredItem<Item> HELIUM_3_CELL =
            REGISTER.registerSimpleItem("helium_3_cell", new Item.Properties());
    /** Lithium — tritium-breeding feedstock; melts to liquid lithium. */
    public static final DeferredItem<Item> LITHIUM =
            REGISTER.registerSimpleItem("lithium", new Item.Properties());
    /** Raw lithium — mined from lithium ore, smelts to the Lithium item. */
    public static final DeferredItem<Item> RAW_LITHIUM =
            REGISTER.registerSimpleItem("raw_lithium", new Item.Properties());
    /** Helium-3 crystal — dropped by helium-3 geodes; fills the He-3 bucket / crafts the cell. */
    public static final DeferredItem<Item> HELIUM_3_CRYSTAL =
            REGISTER.registerSimpleItem("helium_3_crystal", new Item.Properties());
    /** Replaceable intake filter for the Air Separator's isotope upgrade. */
    public static final DeferredItem<Item> AIR_FILTER =
            REGISTER.registerSimpleItem("air_filter", new Item.Properties());
    public static final DeferredItem<Item> ISOTOPE_SEPARATION_MODULE =
            REGISTER.registerSimpleItem("isotope_separation_module", new Item.Properties().stacksTo(1));
    /** Raw gallium — rare byproduct of mining zinc/aluminium-bearing ores; smelts to an ingot. */
    public static final DeferredItem<Item> RAW_GALLIUM =
            REGISTER.registerSimpleItem("raw_gallium", new Item.Properties());
    /** Gallium ingot — soft, low-melting silvery metal; gear material (gold-like but worse). */
    public static final DeferredItem<Item> GALLIUM_INGOT =
            REGISTER.registerSimpleItem("gallium_ingot", new Item.Properties());
    /** Solid gallium block item (the frozen form / storage block). */
    public static final DeferredItem<BlockItem> SOLID_GALLIUM =
            REGISTER.registerSimpleBlockItem(MagBlocks.SOLID_GALLIUM);

    // Gallium gear — gold-like but worse (very soft): low durability, fast mining,
    // no attack bonus (see MagTiers.GALLIUM / MagArmorMaterials.GALLIUM).
    public static final DeferredItem<SwordItem> GALLIUM_SWORD =
            REGISTER.registerItem("gallium_sword", p -> new SwordItem(MagTiers.GALLIUM, p),
                    new Item.Properties().attributes(SwordItem.createAttributes(MagTiers.GALLIUM, 3, -2.4f)));
    public static final DeferredItem<PickaxeItem> GALLIUM_PICKAXE =
            REGISTER.registerItem("gallium_pickaxe", p -> new PickaxeItem(MagTiers.GALLIUM, p),
                    new Item.Properties().attributes(PickaxeItem.createAttributes(MagTiers.GALLIUM, 1, -2.8f)));
    public static final DeferredItem<AxeItem> GALLIUM_AXE =
            REGISTER.registerItem("gallium_axe", p -> new AxeItem(MagTiers.GALLIUM, p),
                    new Item.Properties().attributes(AxeItem.createAttributes(MagTiers.GALLIUM, 6, -3.1f)));
    public static final DeferredItem<ShovelItem> GALLIUM_SHOVEL =
            REGISTER.registerItem("gallium_shovel", p -> new ShovelItem(MagTiers.GALLIUM, p),
                    new Item.Properties().attributes(ShovelItem.createAttributes(MagTiers.GALLIUM, 1.5f, -3.0f)));
    public static final DeferredItem<HoeItem> GALLIUM_HOE =
            REGISTER.registerItem("gallium_hoe", p -> new HoeItem(MagTiers.GALLIUM, p),
                    new Item.Properties().attributes(HoeItem.createAttributes(MagTiers.GALLIUM, 0, -3.0f)));
    public static final DeferredItem<ArmorItem> GALLIUM_HELMET =
            REGISTER.registerItem("gallium_helmet", p -> new ArmorItem(MagArmorMaterials.gallium(), ArmorItem.Type.HELMET, p),
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(5)));
    public static final DeferredItem<ArmorItem> GALLIUM_CHESTPLATE =
            REGISTER.registerItem("gallium_chestplate", p -> new ArmorItem(MagArmorMaterials.gallium(), ArmorItem.Type.CHESTPLATE, p),
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(5)));
    public static final DeferredItem<ArmorItem> GALLIUM_LEGGINGS =
            REGISTER.registerItem("gallium_leggings", p -> new ArmorItem(MagArmorMaterials.gallium(), ArmorItem.Type.LEGGINGS, p),
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(5)));
    public static final DeferredItem<ArmorItem> GALLIUM_BOOTS =
            REGISTER.registerItem("gallium_boots", p -> new ArmorItem(MagArmorMaterials.gallium(), ArmorItem.Type.BOOTS, p),
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(5)));
    public static final DeferredItem<AnimalArmorItem> GALLIUM_HORSE_ARMOR =
            REGISTER.registerItem("gallium_horse_armor",
                    p -> new AnimalArmorItem(MagArmorMaterials.gallium(), AnimalArmorItem.BodyType.EQUESTRIAN, false, p),
                    new Item.Properties().stacksTo(1));
    /** Spawn egg for the MR Fluid Golem (crafted, not multiblock — it's fluid-based). */
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> MR_FLUID_GOLEM_SPAWN_EGG =
            REGISTER.registerItem("mr_fluid_golem_spawn_egg",
                    p -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                            com.stonytark.magnetization.registry.MagEntities.MR_FLUID_GOLEM, 0x4A4E54, 0x9AA0A8, p),
                    new Item.Properties());

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
    // Magnetorheological-fluid tools — iron-equivalent, barely wear (the fluid
    // hardens on use). Icons ripple when idle and snap to the rigid plate while
    // actively swung/mined (magnetization:hardened item property). See MrFluidTools.
    public static final DeferredItem<com.stonytark.magnetization.content.mrtools.MrFluidTools.Sword> MR_FLUID_SWORD =
            REGISTER.registerItem("mr_fluid_sword",
                    com.stonytark.magnetization.content.mrtools.MrFluidTools.Sword::new,
                    new Item.Properties().attributes(SwordItem.createAttributes(MagTiers.MR_FLUID, 3, -2.4f)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrtools.MrFluidTools.Pickaxe> MR_FLUID_PICKAXE =
            REGISTER.registerItem("mr_fluid_pickaxe",
                    com.stonytark.magnetization.content.mrtools.MrFluidTools.Pickaxe::new,
                    new Item.Properties().attributes(PickaxeItem.createAttributes(MagTiers.MR_FLUID, 1, -2.8f)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrtools.MrFluidTools.Axe> MR_FLUID_AXE =
            REGISTER.registerItem("mr_fluid_axe",
                    com.stonytark.magnetization.content.mrtools.MrFluidTools.Axe::new,
                    new Item.Properties().attributes(AxeItem.createAttributes(MagTiers.MR_FLUID, 6, -3.1f)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrtools.MrFluidTools.Shovel> MR_FLUID_SHOVEL =
            REGISTER.registerItem("mr_fluid_shovel",
                    com.stonytark.magnetization.content.mrtools.MrFluidTools.Shovel::new,
                    new Item.Properties().attributes(ShovelItem.createAttributes(MagTiers.MR_FLUID, 1.5f, -3.0f)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrtools.MrFluidTools.Hoe> MR_FLUID_HOE =
            REGISTER.registerItem("mr_fluid_hoe",
                    com.stonytark.magnetization.content.mrtools.MrFluidTools.Hoe::new,
                    new Item.Properties().attributes(HoeItem.createAttributes(MagTiers.MR_FLUID, -2, -1.0f)));

    public static final DeferredItem<com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem> MR_LIQUID_HELMET =
            REGISTER.registerItem("mr_liquid_helmet",
                    p -> new com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem(ArmorItem.Type.HELMET, p),
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(8)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem> MR_LIQUID_CHESTPLATE =
            REGISTER.registerItem("mr_liquid_chestplate",
                    p -> new com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem(ArmorItem.Type.CHESTPLATE, p),
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(8)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem> MR_LIQUID_LEGGINGS =
            REGISTER.registerItem("mr_liquid_leggings",
                    p -> new com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem(ArmorItem.Type.LEGGINGS, p),
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(8)));
    public static final DeferredItem<com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem> MR_LIQUID_BOOTS =
            REGISTER.registerItem("mr_liquid_boots",
                    p -> new com.stonytark.magnetization.content.mrarmor.MrLiquidArmorItem(ArmorItem.Type.BOOTS, p),
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(8)));
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
    public static final DeferredItem<com.stonytark.magnetization.content.mrarmor.MrFluidHorseArmorItem> MR_FLUID_HORSE_ARMOR =
            REGISTER.registerItem("mr_fluid_horse_armor",
                    p -> new com.stonytark.magnetization.content.mrarmor.MrFluidHorseArmorItem(
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
    public static final DeferredItem<BlockItem> LITHIUM_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.LITHIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_LITHIUM_ORE = REGISTER.registerSimpleBlockItem(MagBlocks.DEEPSLATE_LITHIUM_ORE);
    public static final DeferredItem<BlockItem> LITHIUM_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.LITHIUM_BLOCK);
    public static final DeferredItem<BlockItem> RAW_LITHIUM_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_LITHIUM_BLOCK);
    public static final DeferredItem<BlockItem> HELIUM_3_GEODE = REGISTER.registerSimpleBlockItem(MagBlocks.HELIUM_3_GEODE);
    public static final DeferredItem<BlockItem> SOLID_HELIUM_3 = REGISTER.registerSimpleBlockItem(MagBlocks.SOLID_HELIUM_3);
    public static final DeferredItem<Item> RAW_TITANOMAGNETITE = REGISTER.registerSimpleItem("raw_titanomagnetite", new Item.Properties());
    public static final DeferredItem<Item> TITANOMAGNETITE_INGOT = REGISTER.registerSimpleItem("titanomagnetite_ingot", new Item.Properties());
    public static final DeferredItem<BlockItem> RAW_GALLIUM_BLOCK = REGISTER.registerSimpleBlockItem(MagBlocks.RAW_GALLIUM_BLOCK);

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

    /** Alfvén Ribbon Backpack — chest-slot glider with a passive day/altitude/End boost. */
    public static final DeferredItem<com.stonytark.magnetization.content.sail.AlfvenBackpackItem> ALFVEN_BACKPACK =
            REGISTER.registerItem("alfven_backpack",
                    com.stonytark.magnetization.content.sail.AlfvenBackpackItem::new,
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

    // ---- Additional complete metal families ----
    // Lithium is fast and enchantable but deliberately fragile. Pyrrhotite and
    // hematite fill the iron progression; scarce titanomagnetite is diamond-tier.
    public static final DeferredItem<SwordItem> LITHIUM_SWORD = sword("lithium_sword", MagTiers.LITHIUM, 3, -2.4f);
    public static final DeferredItem<PickaxeItem> LITHIUM_PICKAXE = pickaxe("lithium_pickaxe", MagTiers.LITHIUM, 1, -2.8f);
    public static final DeferredItem<AxeItem> LITHIUM_AXE = axe("lithium_axe", MagTiers.LITHIUM, 6, -3.1f);
    public static final DeferredItem<ShovelItem> LITHIUM_SHOVEL = shovel("lithium_shovel", MagTiers.LITHIUM, 1.5f, -3.0f);
    public static final DeferredItem<HoeItem> LITHIUM_HOE = hoe("lithium_hoe", MagTiers.LITHIUM, 0, -3.0f);
    public static final DeferredItem<ArmorItem> LITHIUM_HELMET = armor("lithium_helmet", MagArmorMaterials.lithium(), ArmorItem.Type.HELMET, 4);
    public static final DeferredItem<ArmorItem> LITHIUM_CHESTPLATE = armor("lithium_chestplate", MagArmorMaterials.lithium(), ArmorItem.Type.CHESTPLATE, 4);
    public static final DeferredItem<ArmorItem> LITHIUM_LEGGINGS = armor("lithium_leggings", MagArmorMaterials.lithium(), ArmorItem.Type.LEGGINGS, 4);
    public static final DeferredItem<ArmorItem> LITHIUM_BOOTS = armor("lithium_boots", MagArmorMaterials.lithium(), ArmorItem.Type.BOOTS, 4);
    public static final DeferredItem<AnimalArmorItem> LITHIUM_HORSE_ARMOR = horseArmor("lithium_horse_armor", MagArmorMaterials.lithium());

    public static final DeferredItem<SwordItem> PYRRHOTITE_SWORD = sword("pyrrhotite_sword", MagTiers.PYRRHOTITE, 3, -2.4f);
    public static final DeferredItem<PickaxeItem> PYRRHOTITE_PICKAXE = pickaxe("pyrrhotite_pickaxe", MagTiers.PYRRHOTITE, 1, -2.8f);
    public static final DeferredItem<AxeItem> PYRRHOTITE_AXE = axe("pyrrhotite_axe", MagTiers.PYRRHOTITE, 6, -3.1f);
    public static final DeferredItem<ShovelItem> PYRRHOTITE_SHOVEL = shovel("pyrrhotite_shovel", MagTiers.PYRRHOTITE, 1.5f, -3.0f);
    public static final DeferredItem<HoeItem> PYRRHOTITE_HOE = hoe("pyrrhotite_hoe", MagTiers.PYRRHOTITE, -2, -1.0f);
    public static final DeferredItem<ArmorItem> PYRRHOTITE_HELMET = armor("pyrrhotite_helmet", MagArmorMaterials.pyrrhotite(), ArmorItem.Type.HELMET, 14);
    public static final DeferredItem<ArmorItem> PYRRHOTITE_CHESTPLATE = armor("pyrrhotite_chestplate", MagArmorMaterials.pyrrhotite(), ArmorItem.Type.CHESTPLATE, 14);
    public static final DeferredItem<ArmorItem> PYRRHOTITE_LEGGINGS = armor("pyrrhotite_leggings", MagArmorMaterials.pyrrhotite(), ArmorItem.Type.LEGGINGS, 14);
    public static final DeferredItem<ArmorItem> PYRRHOTITE_BOOTS = armor("pyrrhotite_boots", MagArmorMaterials.pyrrhotite(), ArmorItem.Type.BOOTS, 14);
    public static final DeferredItem<AnimalArmorItem> PYRRHOTITE_HORSE_ARMOR = horseArmor("pyrrhotite_horse_armor", MagArmorMaterials.pyrrhotite());

    public static final DeferredItem<SwordItem> HEMATITE_SWORD = sword("hematite_sword", MagTiers.HEMATITE, 3, -2.4f);
    public static final DeferredItem<PickaxeItem> HEMATITE_PICKAXE = pickaxe("hematite_pickaxe", MagTiers.HEMATITE, 1, -2.8f);
    public static final DeferredItem<AxeItem> HEMATITE_AXE = axe("hematite_axe", MagTiers.HEMATITE, 6, -3.1f);
    public static final DeferredItem<ShovelItem> HEMATITE_SHOVEL = shovel("hematite_shovel", MagTiers.HEMATITE, 1.5f, -3.0f);
    public static final DeferredItem<HoeItem> HEMATITE_HOE = hoe("hematite_hoe", MagTiers.HEMATITE, -2, -1.0f);
    public static final DeferredItem<ArmorItem> HEMATITE_HELMET = armor("hematite_helmet", MagArmorMaterials.hematite(), ArmorItem.Type.HELMET, 18);
    public static final DeferredItem<ArmorItem> HEMATITE_CHESTPLATE = armor("hematite_chestplate", MagArmorMaterials.hematite(), ArmorItem.Type.CHESTPLATE, 18);
    public static final DeferredItem<ArmorItem> HEMATITE_LEGGINGS = armor("hematite_leggings", MagArmorMaterials.hematite(), ArmorItem.Type.LEGGINGS, 18);
    public static final DeferredItem<ArmorItem> HEMATITE_BOOTS = armor("hematite_boots", MagArmorMaterials.hematite(), ArmorItem.Type.BOOTS, 18);
    public static final DeferredItem<AnimalArmorItem> HEMATITE_HORSE_ARMOR = horseArmor("hematite_horse_armor", MagArmorMaterials.hematite());

    public static final DeferredItem<SwordItem> TITANOMAGNETITE_SWORD = sword("titanomagnetite_sword", MagTiers.TITANOMAGNETITE, 3, -2.4f);
    public static final DeferredItem<PickaxeItem> TITANOMAGNETITE_PICKAXE = pickaxe("titanomagnetite_pickaxe", MagTiers.TITANOMAGNETITE, 1, -2.8f);
    public static final DeferredItem<AxeItem> TITANOMAGNETITE_AXE = axe("titanomagnetite_axe", MagTiers.TITANOMAGNETITE, 5, -3.0f);
    public static final DeferredItem<ShovelItem> TITANOMAGNETITE_SHOVEL = shovel("titanomagnetite_shovel", MagTiers.TITANOMAGNETITE, 1.5f, -3.0f);
    public static final DeferredItem<HoeItem> TITANOMAGNETITE_HOE = hoe("titanomagnetite_hoe", MagTiers.TITANOMAGNETITE, -3, 0.0f);
    public static final DeferredItem<ArmorItem> TITANOMAGNETITE_HELMET = armor("titanomagnetite_helmet", MagArmorMaterials.titanomagnetite(), ArmorItem.Type.HELMET, 30);
    public static final DeferredItem<ArmorItem> TITANOMAGNETITE_CHESTPLATE = armor("titanomagnetite_chestplate", MagArmorMaterials.titanomagnetite(), ArmorItem.Type.CHESTPLATE, 30);
    public static final DeferredItem<ArmorItem> TITANOMAGNETITE_LEGGINGS = armor("titanomagnetite_leggings", MagArmorMaterials.titanomagnetite(), ArmorItem.Type.LEGGINGS, 30);
    public static final DeferredItem<ArmorItem> TITANOMAGNETITE_BOOTS = armor("titanomagnetite_boots", MagArmorMaterials.titanomagnetite(), ArmorItem.Type.BOOTS, 30);
    public static final DeferredItem<AnimalArmorItem> TITANOMAGNETITE_HORSE_ARMOR = horseArmor("titanomagnetite_horse_armor", MagArmorMaterials.titanomagnetite());

    private static DeferredItem<SwordItem> sword(final String name, final net.minecraft.world.item.Tier tier,
                                                  final float damage, final float speed) {
        return REGISTER.registerItem(name, p -> new SwordItem(tier, p),
                new Item.Properties().attributes(SwordItem.createAttributes(tier, damage, speed)));
    }

    private static DeferredItem<PickaxeItem> pickaxe(final String name, final net.minecraft.world.item.Tier tier,
                                                      final float damage, final float speed) {
        return REGISTER.registerItem(name, p -> new PickaxeItem(tier, p),
                new Item.Properties().attributes(PickaxeItem.createAttributes(tier, damage, speed)));
    }

    private static DeferredItem<AxeItem> axe(final String name, final net.minecraft.world.item.Tier tier,
                                              final float damage, final float speed) {
        return REGISTER.registerItem(name, p -> new AxeItem(tier, p),
                new Item.Properties().attributes(AxeItem.createAttributes(tier, damage, speed)));
    }

    private static DeferredItem<ShovelItem> shovel(final String name, final net.minecraft.world.item.Tier tier,
                                                    final float damage, final float speed) {
        return REGISTER.registerItem(name, p -> new ShovelItem(tier, p),
                new Item.Properties().attributes(ShovelItem.createAttributes(tier, damage, speed)));
    }

    private static DeferredItem<HoeItem> hoe(final String name, final net.minecraft.world.item.Tier tier,
                                              final float damage, final float speed) {
        return REGISTER.registerItem(name, p -> new HoeItem(tier, p),
                new Item.Properties().attributes(HoeItem.createAttributes(tier, damage, speed)));
    }

    private static DeferredItem<ArmorItem> armor(final String name,
                                                  final net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material,
                                                  final ArmorItem.Type type, final int durabilityFactor) {
        return REGISTER.registerItem(name, p -> new ArmorItem(material, type, p),
                new Item.Properties().durability(type.getDurability(durabilityFactor)));
    }

    private static DeferredItem<AnimalArmorItem> horseArmor(final String name,
                                                             final net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material) {
        return REGISTER.registerItem(name,
                p -> new AnimalArmorItem(material, AnimalArmorItem.BodyType.EQUESTRIAN, false, p),
                new Item.Properties().stacksTo(1));
    }

    private MagItems() {}
}
