package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.electromagnet.ElectromagnetBlockEntity;
import com.stonytark.magnetization.content.electromagnet.KineticElectromagnetBlockEntity;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity;
import com.stonytark.magnetization.content.temporary.TemporaryMagnetBlockEntity;
import com.stonytark.magnetization.content.repulsor.RepulsorCoilBlockEntity;
import com.stonytark.magnetization.content.tractor.TractorBeamBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Magnetization.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectromagnetBlockEntity>> ELECTROMAGNET =
            REGISTER.register("electromagnet", () -> BlockEntityType.Builder
                    .of(ElectromagnetBlockEntity::new, MagBlocks.ELECTROMAGNET.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticElectromagnetBlockEntity>> KINETIC_ELECTROMAGNET =
            REGISTER.register("kinetic_electromagnet", () -> BlockEntityType.Builder
                    .<KineticElectromagnetBlockEntity>of(
                            (pos, state) -> new KineticElectromagnetBlockEntity(
                                    MagBlockEntities.KINETIC_ELECTROMAGNET.get(), pos, state),
                            MagBlocks.KINETIC_ELECTROMAGNET.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticAnchorBlockEntity>> MAGNETIC_ANCHOR =
            REGISTER.register("magnetic_anchor", () -> BlockEntityType.Builder
                    .of(MagneticAnchorBlockEntity::new, MagBlocks.MAGNETIC_ANCHOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepulsorCoilBlockEntity>> REPULSOR_COIL =
            REGISTER.register("repulsor_coil", () -> BlockEntityType.Builder
                    .of(RepulsorCoilBlockEntity::new, MagBlocks.REPULSOR_COIL.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TractorBeamBlockEntity>> TRACTOR_BEAM =
            REGISTER.register("tractor_beam", () -> BlockEntityType.Builder
                    .of(TractorBeamBlockEntity::new, MagBlocks.TRACTOR_BEAM.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticExcavatorBlockEntity>> MAGNETIC_EXCAVATOR =
            REGISTER.register("magnetic_excavator", () -> BlockEntityType.Builder
                    .of(MagneticExcavatorBlockEntity::new, MagBlocks.MAGNETIC_EXCAVATOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticSwitchBlockEntity>> MAGNETIC_SWITCH =
            REGISTER.register("magnetic_switch", () -> BlockEntityType.Builder
                    .of(MagneticSwitchBlockEntity::new, MagBlocks.MAGNETIC_SWITCH.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PermanentMagnetBlockEntity>> PERMANENT_MAGNET =
            REGISTER.register("permanent_magnet", () -> BlockEntityType.Builder
                    .of(PermanentMagnetBlockEntity::new, MagBlocks.PERMANENT_MAGNET.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TemporaryMagnetBlockEntity>> TEMPORARY_MAGNET =
            REGISTER.register("temporary_magnet", () -> BlockEntityType.Builder
                    .of(TemporaryMagnetBlockEntity::new, MagBlocks.TEMPORARY_MAGNET.get())
                    .build(null));

    // Iron-oxide-family BEs.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity>> PYRRHOTITE =
            REGISTER.register("pyrrhotite", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity::new, MagBlocks.PYRRHOTITE_BLOCK.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.titanomagnetite.TitanomagnetiteBlockEntity>> TITANOMAGNETITE =
            REGISTER.register("titanomagnetite", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.titanomagnetite.TitanomagnetiteBlockEntity::new, MagBlocks.TITANOMAGNETITE_BLOCK.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity>> METEORITE_CORE =
            REGISTER.register("meteorite_core", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity::new, MagBlocks.METEORITE_CORE.get())
                    .build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity>> METEORITE_SAPLING =
            REGISTER.register("meteorite_sapling", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity::new, MagBlocks.METEORITE_SAPLING.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.gyro.GyrostabilizerBlockEntity>> GYROSTABILIZER =
            REGISTER.register("gyrostabilizer", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.gyro.GyrostabilizerBlockEntity::new, MagBlocks.GYROSTABILIZER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity>> BARKHAUSEN =
            REGISTER.register("barkhausen_generator", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity::new, MagBlocks.BARKHAUSEN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity>> MAGNETOSTRICTIVE_SENSOR =
            REGISTER.register("magnetostrictive_sensor", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity::new, MagBlocks.MAGNETOSTRICTIVE_SENSOR.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.induction.KineticCoilBlockEntity>> KINETIC_COIL =
            REGISTER.register("kinetic_coil", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.induction.KineticCoilBlockEntity::new, MagBlocks.KINETIC_COIL.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.induction.InductionPadBlockEntity>> INDUCTION_PAD =
            REGISTER.register("induction_pad", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.induction.InductionPadBlockEntity::new, MagBlocks.INDUCTION_PAD.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.itemframe.MagneticItemFrameBlockEntity>> MAGNETIC_ITEM_FRAME =
            REGISTER.register("magnetic_item_frame", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.itemframe.MagneticItemFrameBlockEntity::new, MagBlocks.MAGNETIC_ITEM_FRAME.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity>> TOKAMAK_CONTROLLER =
            REGISTER.register("tokamak_controller", () -> BlockEntityType.Builder
                    .of(com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity::new, MagBlocks.TOKAMAK_CONTROLLER.get())
                    .build(null));

    private MagBlockEntities() {}
}
