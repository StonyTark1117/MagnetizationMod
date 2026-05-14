package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlock;
import com.stonytark.magnetization.content.electromagnet.ElectromagnetBlock;
import com.stonytark.magnetization.content.electromagnet.KineticElectromagnetBlock;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlock;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.content.repulsor.RepulsorCoilBlock;
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

    private MagBlocks() {}
}
