package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlock;
import com.stonytark.magnetization.content.electromagnet.ElectromagnetBlock;
import com.stonytark.magnetization.content.electromagnet.KineticElectromagnetBlock;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlock;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.content.repulsor.RepulsorCoilBlock;
import com.stonytark.magnetization.content.temporary.TemporaryMagnetBlock;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlock;
import com.stonytark.magnetization.content.tractor.TractorBeamBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagBlocks {

    public static final DeferredRegister.Blocks REGISTER = DeferredRegister.createBlocks(Magnetization.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f, 6.0f)
                .sound(SoundType.NETHERITE_BLOCK)
                .requiresCorrectToolForDrops();
    }

    /**
     * Same as {@link #metal()} but with a small light emission when the {@code POWERED}
     * blockstate is true. Use for emitters that have the property defined.
     */
    private static BlockBehaviour.Properties poweredMetal() {
        return metal().lightLevel(state ->
                state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) ? 6 : 0);
    }

    public static final DeferredBlock<ElectromagnetBlock> ELECTROMAGNET =
            REGISTER.register("electromagnet", () -> new ElectromagnetBlock(poweredMetal()));

    public static final DeferredBlock<KineticElectromagnetBlock> KINETIC_ELECTROMAGNET =
            REGISTER.register("kinetic_electromagnet", () -> new KineticElectromagnetBlock(metal()));

    public static final DeferredBlock<MagneticAnchorBlock> MAGNETIC_ANCHOR =
            REGISTER.register("magnetic_anchor", () -> new MagneticAnchorBlock(poweredMetal()));

    public static final DeferredBlock<RepulsorCoilBlock> REPULSOR_COIL =
            REGISTER.register("repulsor_coil", () -> new RepulsorCoilBlock(poweredMetal()));

    public static final DeferredBlock<TractorBeamBlock> TRACTOR_BEAM =
            REGISTER.register("tractor_beam", () -> new TractorBeamBlock(poweredMetal()));

    public static final DeferredBlock<MagneticExcavatorBlock> MAGNETIC_EXCAVATOR =
            REGISTER.register("magnetic_excavator", () -> new MagneticExcavatorBlock(poweredMetal()));

    public static final DeferredBlock<Block> LODESTONE_CORE =
            REGISTER.register("lodestone_core", () -> new Block(metal()));

    public static final DeferredBlock<MagneticSwitchBlock> MAGNETIC_SWITCH =
            REGISTER.register("magnetic_switch", () -> new MagneticSwitchBlock(metal()));

    public static final DeferredBlock<PermanentMagnetBlock> PERMANENT_MAGNET =
            REGISTER.register("permanent_magnet", () -> new PermanentMagnetBlock(metal()));

    /** Cheap, decaying counterpart to the Permanent Magnet. Crafted from
     *  iron + redstone, emits a WEAK omnidirectional field for ~10 minutes,
     *  then reverts to an iron block (redstone consumed at craft time is
     *  not returned). Players use it for short-lived propulsion-track
     *  segments where they don't want the lodestone-core investment. */
    public static final DeferredBlock<TemporaryMagnetBlock> TEMPORARY_MAGNET =
            REGISTER.register("temporary_magnet", () -> new TemporaryMagnetBlock(metal()));

    public static final DeferredBlock<PolarityInverterBlock> POLARITY_INVERTER =
            REGISTER.register("polarity_inverter", () -> new PolarityInverterBlock(metal()));

    /** Magnetite (Fe3O4) — naturally magnetic iron oxide. The real-world mineral
     *  that magnetic compasses were originally calibrated against; named after
     *  the Greek province of Magnesia. Drops raw_magnetite when mined. */
    public static final DeferredBlock<Block> MAGNETITE_ORE =
            REGISTER.register("magnetite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> DEEPSLATE_MAGNETITE_ORE =
            REGISTER.register("deepslate_magnetite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()));

    /** Storage block from 9 magnetite ingots. Like vanilla iron_block. */
    public static final DeferredBlock<Block> MAGNETITE_BLOCK =
            REGISTER.register("magnetite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    /** Storage block from 9 raw magnetite. Like vanilla raw_iron_block. */
    public static final DeferredBlock<Block> RAW_MAGNETITE_BLOCK =
            REGISTER.register("raw_magnetite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    /** Natural-look terrain block for the anomaly biome — stone-grey with
     *  iron-oxide tinting baked into the texture. Acts as the "bedrock" of
     *  the biome surface so it doesn't read as a storage-block dump. */
    public static final DeferredBlock<Block> ANOMALY_STONE =
            REGISTER.register("anomaly_stone", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    /** Natural-look terrain block for the anomaly biome — dark gravel with
     *  magnetite specks. Falls under gravity (vanilla ColoredFallingBlock —
     *  GravelBlock was merged into it in 1.21) and is in the
     *  ferromagnetic_blocks tag so emitters lightly attract it. Dust colour
     *  is a dark grey to match the visible magnetite-flecked palette. */
    public static final DeferredBlock<net.minecraft.world.level.block.ColoredFallingBlock> MAGNETIC_GRAVEL =
            REGISTER.register("magnetic_gravel", () -> new net.minecraft.world.level.block.ColoredFallingBlock(
                    new net.minecraft.util.ColorRGBA(0xFF3a3a3a),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(0.6f)
                            .sound(SoundType.GRAVEL)));

    /** Cobbled form of {@link #ANOMALY_STONE}, mirroring vanilla cobble. The
     *  surface-repaint handler doesn't place this directly — players get it
     *  by mining anomaly_stone without silk touch, just like cobblestone. */
    public static final DeferredBlock<Block> COBBLED_ANOMALY_STONE =
            REGISTER.register("cobbled_anomaly_stone", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    // Vanilla parity: stone has stairs/slab; cobblestone has stairs/slab/wall.
    // All reuse the parent block's texture, follow vanilla blockstate shape.
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> ANOMALY_STONE_STAIRS =
            REGISTER.register("anomaly_stone_stairs", () -> new net.minecraft.world.level.block.StairBlock(
                    MagBlocks.ANOMALY_STONE.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(MagBlocks.ANOMALY_STONE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> ANOMALY_STONE_SLAB =
            REGISTER.register("anomaly_stone_slab", () -> new net.minecraft.world.level.block.SlabBlock(
                    BlockBehaviour.Properties.ofFullCopy(MagBlocks.ANOMALY_STONE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.StairBlock> COBBLED_ANOMALY_STONE_STAIRS =
            REGISTER.register("cobbled_anomaly_stone_stairs", () -> new net.minecraft.world.level.block.StairBlock(
                    MagBlocks.COBBLED_ANOMALY_STONE.get().defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(MagBlocks.COBBLED_ANOMALY_STONE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> COBBLED_ANOMALY_STONE_SLAB =
            REGISTER.register("cobbled_anomaly_stone_slab", () -> new net.minecraft.world.level.block.SlabBlock(
                    BlockBehaviour.Properties.ofFullCopy(MagBlocks.COBBLED_ANOMALY_STONE.get())));
    public static final DeferredBlock<net.minecraft.world.level.block.WallBlock> COBBLED_ANOMALY_STONE_WALL =
            REGISTER.register("cobbled_anomaly_stone_wall", () -> new net.minecraft.world.level.block.WallBlock(
                    BlockBehaviour.Properties.ofFullCopy(MagBlocks.COBBLED_ANOMALY_STONE.get())));

    // ----------------------------------------------------------------------
    // Iron-oxide family (1.1.2+ phased rollout). Each ore mirrors the
    // magnetite block layout exactly (ore + deepslate_ore + storage_block +
    // raw_storage_block) so the existing JER/loot/recipe/tag pipelines just
    // need a parallel set of registry entries. Mechanics layered later
    // (see backlog tasks #271-#274) — for now these are inert iron-oxide
    // variants that act like flavourful parallel magnetite sources.
    // ----------------------------------------------------------------------

    // --- Maghemite (γ-Fe2O3): weather-oxidised cousin of magnetite. ---
    public static final DeferredBlock<Block> MAGHEMITE_ORE =
            REGISTER.register("maghemite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(3.0f, 3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_MAGHEMITE_ORE =
            REGISTER.register("deepslate_maghemite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> MAGHEMITE_BLOCK =
            REGISTER.register("maghemite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(5.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> RAW_MAGHEMITE_BLOCK =
            REGISTER.register("raw_maghemite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(5.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    // --- Pyrrhotite (Fe7S8): heat-activated iron sulfide. ---
    public static final DeferredBlock<Block> PYRRHOTITE_ORE =
            REGISTER.register("pyrrhotite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(3.0f, 3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_PYRRHOTITE_ORE =
            REGISTER.register("deepslate_pyrrhotite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlock> PYRRHOTITE_BLOCK =
            REGISTER.register("pyrrhotite_block", () -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(5.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> RAW_PYRRHOTITE_BLOCK =
            REGISTER.register("raw_pyrrhotite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(5.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    // --- Hematite (α-Fe2O3): antiferromagnetic; multiblock filter role. ---
    public static final DeferredBlock<Block> HEMATITE_ORE =
            REGISTER.register("hematite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(3.0f, 3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_HEMATITE_ORE =
            REGISTER.register("deepslate_hematite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(4.5f, 3.0f).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<com.stonytark.magnetization.content.hematite.HematiteBlock> HEMATITE_BLOCK =
            REGISTER.register("hematite_block", () -> new com.stonytark.magnetization.content.hematite.HematiteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(5.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> RAW_HEMATITE_BLOCK =
            REGISTER.register("raw_hematite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED).strength(5.0f, 6.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    // --- Titanomagnetite (Fe3O4·Fe2TiO4): deep, scarce, field-recording. ---
    public static final DeferredBlock<Block> TITANOMAGNETITE_ORE =
            REGISTER.register("titanomagnetite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(4.0f, 4.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_TITANOMAGNETITE_ORE =
            REGISTER.register("deepslate_titanomagnetite_ore", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE).strength(5.5f, 4.0f).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<com.stonytark.magnetization.content.titanomagnetite.TitanomagnetiteBlock> TITANOMAGNETITE_BLOCK =
            REGISTER.register("titanomagnetite_block", () -> new com.stonytark.magnetization.content.titanomagnetite.TitanomagnetiteBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(6.0f, 7.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> RAW_TITANOMAGNETITE_BLOCK =
            REGISTER.register("raw_titanomagnetite_block", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(6.0f, 7.0f).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    /** Pyrrhotite Catalyst — passive heat-bridge. Pyrrhotite reactors within
     *  the Catalyst's transmit radius pull heat through it from any heat
     *  source touching the Catalyst. Three tiers: basic 3 / enhanced 5 /
     *  cosmic 7. */
    public static final DeferredBlock<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock> PYRRHOTITE_CATALYST =
            REGISTER.register("pyrrhotite_catalyst", () -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(4.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops(), 3));
    public static final DeferredBlock<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock> ENHANCED_PYRRHOTITE_CATALYST =
            REGISTER.register("enhanced_pyrrhotite_catalyst", () -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(5.0f, 7.0f).sound(SoundType.METAL).requiresCorrectToolForDrops(), 5));
    public static final DeferredBlock<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock> COSMIC_PYRRHOTITE_CATALYST =
            REGISTER.register("cosmic_pyrrhotite_catalyst", () -> new com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteCatalystBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(6.0f, 8.0f).sound(SoundType.METAL).requiresCorrectToolForDrops(), 7));

    /** Meteorite core — rare worldgen feature emitting a decaying field. Phase A
     *  MVP: standalone block with right-click refill. Phase B will surround it
     *  with a crater structure and hook AE2 meteor positions. */
    public static final DeferredBlock<com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlock> METEORITE_CORE =
            REGISTER.register("meteorite_core", () -> new com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(5.0f, 50.0f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    /** Plantable cradle that germinates into a fresh meteorite_core over
     *  ~30 minutes of in-game time. Player-sustainable meteorite supply. */
    public static final DeferredBlock<com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlock> METEORITE_SAPLING =
            REGISTER.register("meteorite_sapling", () -> new com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(0.5f, 0.5f).sound(SoundType.STONE).noOcclusion()));

    /** Petrified wood: stone-flavored ferrous wood. Generates as a rare drop from
     *  log chops with a magnetized axe; placeable, mineable. Intentionally NOT in
     *  {@code #magnetization:ferromagnetic_blocks} — the Magnetic Excavator and
     *  Magnetic Pickaxe rip don't claim it. The Magnetized Axe rip is the only
     *  block-yank that targets it, making the axe its signature tool. */
    public static final DeferredBlock<Block> PETRIFIED_WOOD =
            REGISTER.register("petrified_wood", () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.5f, 4.0f)
                    .sound(SoundType.STONE)));

    /** Barkhausen noise generator — RNG block; magnet adjacent → random redstone pulses. */
    public static final DeferredBlock<com.stonytark.magnetization.content.sensor.BarkhausenBlock> BARKHAUSEN =
            REGISTER.register("barkhausen_generator", () -> new com.stonytark.magnetization.content.sensor.BarkhausenBlock(poweredMetal()));

    /** Magnetostrictive sensor — buried magnetic tripwire; emits redstone on nearby motion. */
    public static final DeferredBlock<com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlock> MAGNETOSTRICTIVE_SENSOR =
            REGISTER.register("magnetostrictive_sensor", () -> new com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlock(poweredMetal()));

    /** Magnetic Gyrostabilizer — on a ship + powered, freezes its rotation (only translates). */
    public static final DeferredBlock<com.stonytark.magnetization.content.gyro.GyrostabilizerBlock> GYROSTABILIZER =
            REGISTER.register("gyrostabilizer", () -> new com.stonytark.magnetization.content.gyro.GyrostabilizerBlock(poweredMetal()));

    /** EMP flux-compression charge — redstone-detonated; blanks emitters + wipes FE in range. */
    public static final DeferredBlock<com.stonytark.magnetization.content.emp.EmpChargeBlock> EMP_CHARGE =
            REGISTER.register("emp_charge", () -> new com.stonytark.magnetization.content.emp.EmpChargeBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5f).sound(SoundType.METAL)));

    /** Kinetic induction coil — a magnet passing through generates FE + a redstone pulse. */
    public static final DeferredBlock<com.stonytark.magnetization.content.induction.KineticCoilBlock> KINETIC_COIL =
            REGISTER.register("kinetic_coil", () -> new com.stonytark.magnetization.content.induction.KineticCoilBlock(poweredMetal()));

    /** Induction charging pad — wirelessly tops up FE items a nearby player carries. */
    public static final DeferredBlock<com.stonytark.magnetization.content.induction.InductionPadBlock> INDUCTION_PAD =
            REGISTER.register("induction_pad", () -> new com.stonytark.magnetization.content.induction.InductionPadBlock(metal()));

    /** Magnetic Item Frame — thin wall plate that displays one item stuck to it. */
    public static final DeferredBlock<com.stonytark.magnetization.content.itemframe.MagneticItemFrameBlock> MAGNETIC_ITEM_FRAME =
            REGISTER.register("magnetic_item_frame", () -> new com.stonytark.magnetization.content.itemframe.MagneticItemFrameBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL).strength(0.4f, 0.4f)
                            .sound(SoundType.NETHERITE_BLOCK).noOcclusion()));

    /** Ferrofluid liquid block (holds the source fluid). */
    public static final DeferredBlock<net.minecraft.world.level.block.LiquidBlock> FERROFLUID_BLOCK =
            REGISTER.register("ferrofluid", () -> new net.minecraft.world.level.block.LiquidBlock(
                    com.stonytark.magnetization.registry.MagFluids.FERROFLUID.get(),
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER)
                            .mapColor(MapColor.COLOR_BLACK)));

    /** MR (magnetorheological) fluid block — solidifies (walkable) when redstone-powered. */
    public static final DeferredBlock<com.stonytark.magnetization.content.fluid.MRFluidBlock> MR_FLUID_BLOCK =
            REGISTER.register("mr_fluid", () -> new com.stonytark.magnetization.content.fluid.MRFluidBlock(
                    com.stonytark.magnetization.registry.MagFluids.MR_FLUID.get(),
                    BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER)
                            .mapColor(MapColor.COLOR_GRAY)));

    private MagBlocks() {}
}
