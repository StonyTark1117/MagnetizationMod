package com.stonytark.magnetization.client;

import com.stonytark.magnetization.compat.copycats.MagCopycatsCompat;
import com.stonytark.magnetization.content.jet.FusionThrusterPanel;
import com.stonytark.magnetization.content.railgun.RailgunHandler;
import com.stonytark.magnetization.content.tokamak.TokamakRingPreview;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;

import static com.stonytark.magnetization.gametest.LifecyclePresentationAudit.check;

/** Assertions against the actual played scene world, including shipping build rules. */
final class PonderSceneAudit {
    private PonderSceneAudit() {}

    static void verify(final PonderScene scene, final String id, final boolean last) {
        final var world = scene.getWorld();
        switch (id) {
            case "docking_signals" -> DockControlPonderAudit.verify(scene, id, last ? 6 : 0);
            case "railgun_remote" -> DockControlPonderAudit.verify(scene, id, last ? 5 : 0);
            case "imprint_module", "tractor_beam" -> DockControlPonderAudit.verify(scene, id, last ? 3 : 0);
            case "pyrrhotite_heat" -> ThermalPowerPonderAudit.verify(scene, id, last ? 9 : 0);
            case "gyrostabilizer", "magnetic_shaft" -> ThermalPowerPonderAudit.verify(scene, id, last ? 3 : 0);
            case "induction_pad" -> ThermalPowerPonderAudit.verify(scene, id, last ? 4 : 0);
            case "magnetic_basics" -> MagneticWorkflowSceneAudit.verify(scene, id, last ? 6 : 0);
            case "magnetic_excavator" -> MagneticWorkflowSceneAudit.verify(scene, id, last ? 5 : 0);
            case "repulsor_transport" -> MagneticWorkflowSceneAudit.verify(scene, id, last ? 3 : 0);
            case "mr_fluid_bridge", "magnetizing_equipment" -> verifyInstruction(scene, id, last ? 3 : 0);
            case "field_strength_control" -> verifyInstruction(scene, id, last ? 6 : 0);
            case "tokamak_ring" -> {
                final var result = TokamakRingPreview.previewExact(world, new BlockPos(2, 1, 2), 5, 7);
                check(result.valid() && result.coreCount() == 9 && result.coilCount() == 16, "Ponder Tokamak cannot form: " + result + " bounds=" + world.getBounds());
                if (last) {
                    final var be = (com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity) world.getBlockEntity(new BlockPos(2, 1, 2));
                    check(be != null && !be.fuelContainer().getItem(0).isEmpty() && be.coolantStored() > 0, "Ponder Tokamak fuel/coolant example missing");
                    check(world.getBlockState(new BlockPos(5, 1, 2)).is(Blocks.GOLD_BLOCK), "Ponder Tokamak FE-output diagram missing");
                }
            }
            case "fusion_panel" -> {
                final var result = FusionThrusterPanel.validate(world, new BlockPos(2, 2, 2), Direction.NORTH, 7);
                check(result.valid() && result.interiorCount() == (last ? 3 : 1), "Ponder Fusion panel cannot form at shown stage");
            }
            case "railgun_pair" -> {
                final var first = new BlockPos(1, 1, 1); final var second = new BlockPos(1, 1, 3);
                check(world.getBlockState(first).getValue(DirectionalBlock.FACING) == Direction.EAST
                        && world.getBlockState(second).getValue(DirectionalBlock.FACING) == Direction.EAST, "Ponder rails face differently");
                check(RailgunHandler.parallelBreeches(first, second, Direction.EAST, 6), "Ponder rails do not align for pairing");
                for (final var emitter : java.util.List.of(first, second)) {
                    check(RailgunHandler.walkRail(world, emitter, Direction.EAST, p -> p.getX() <= 6) == 4,
                            "Ponder rail materials/length are invalid");
                }
            }
            case "copycat_magnetism" -> {
                if (last) check(MagCopycatsCompat.materialsOf(world.getBlockEntity(new BlockPos(2, 1, 2))).stream()
                        .anyMatch(state -> state.is(Blocks.IRON_BLOCK)), "Ponder Copycat never stored the taught Iron material");
            }
            case "air_separator" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.gas.AirSeparatorBlockEntity) world.getBlockEntity(new BlockPos(2, 2, 2));
                    check(be != null && !be.upgradeContainer().getItem(0).isEmpty(), "Ponder isotope module was not installed");
                    final Direction[] faces = {Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST, Direction.NORTH};
                    for (int gas = 0; gas < faces.length; gas++) check(be.gasForFace(faces[gas]) == gas, "Ponder gas legend mismatches actual port assignment");
                }
            }
            case "homopolar_motor" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity) world.getBlockEntity(new BlockPos(2, 1, 2));
                    check(be != null && !be.getMagnet().isEmpty(), "Ponder motor magnet missing");
                    check(be.getBlockState().getValue(DirectionalBlock.FACING).getAxis() == Direction.Axis.X, "Ponder motor shaft axis is wrong");
                }
            }
            case "mhd_jet" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.jet.MhdJetBlockEntity) world.getBlockEntity(new BlockPos(2, 1, 2));
                    check(be != null && !be.getMagnet().isEmpty() && be.guiStat1() > 0 && be.guiEnergyStored() > 0,
                            "Ponder MHD fuel/magnet/FE inputs missing");
                }
            }
            case "micro_thruster" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity) world.getBlockEntity(new BlockPos(2, 1, 2));
                    check(be != null && be.guiStat1() > 0 && be.guiEnergyStored() > 0, "Ponder Micro Thruster fluid/FE missing");
                }
            }
            case "electrolyzer" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity) world.getBlockEntity(new BlockPos(2, 1, 2));
                    check(be != null && be.waterAmount() > 0 && be.guiEnergyStored() > 0, "Ponder Electrolyzer water/FE missing");
                }
            }
            case "gas_exciter" -> {
                for (int x=2; x<=4; x++) check(world.getBlockState(new BlockPos(x,1,2)).is(last ? Blocks.PINK_STAINED_GLASS : Blocks.PURPLE_STAINED_GLASS), "Ponder excitation diagram has wrong stage");
            }
            case "gas_vent" -> {
                if (last) {
                    final var vent = (com.stonytark.magnetization.content.gas.GasVentBlockEntity) world.getBlockEntity(new BlockPos(2,1,2));
                    check(vent != null && vent.outputDirection() == Direction.EAST && vent.attachedExciterPos().equals(new BlockPos(1,1,2)), "Ponder vent outlet/rear Exciter mismatch");
                    check(world.getBlockState(new BlockPos(3,1,2)).is(Blocks.PINK_STAINED_GLASS)
                            && world.getBlockState(new BlockPos(2,1,3)).is(com.simibubi.create.AllBlocks.FLUID_PIPE.get()), "Ponder cloud/input diagrams missing");
                }
            }
            case "ion_thruster" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.jet.IonThrusterBlockEntity) world.getBlockEntity(new BlockPos(2,1,3));
                    check(be != null && be.guiStat1() > 0 && be.guiEnergyStored() > 0 && be.getBlockState().getValue(DirectionalBlock.FACING) == Direction.SOUTH, "Ponder Ion Thruster inputs/exhaust missing");
                    check(be.fluidHandler().getFluidInTank(0).getFluid() == com.stonytark.magnetization.registry.MagFluids.XENON.get(), "Ponder propellant is not shown Xenon");
                }
            }
            case "rare_earth_magnets" -> {
                if (last) check(world.getBlockState(new BlockPos(1,1,3)).is(com.stonytark.magnetization.registry.MagBlocks.SAMARIUM_COBALT_MAGNET.get())
                        && world.getBlockState(new BlockPos(3,1,3)).is(com.stonytark.magnetization.registry.MagBlocks.NEODYMIUM_MAGNET.get())
                        && world.getBlockState(new BlockPos(0,2,3)).is(com.simibubi.create.AllBlocks.MECHANICAL_PRESS.get()), "Ponder magnet branch/processing examples missing");
            }
            case "steam_rails_magnetism" -> {
                if (last) {
                    for (int x=0; x<=4; x++) check(world.getBlockState(new BlockPos(x,0,1)).getValue(com.simibubi.create.content.trains.track.TrackBlock.SHAPE)
                            == com.simibubi.create.content.trains.track.TrackShape.XO, "Ponder train diagram moves across, rather than along, rail axis");
                    check(world.getBlockState(new BlockPos(1,1,1)).is(Blocks.IRON_BLOCK) && world.getBlockState(new BlockPos(3,1,1)).is(Blocks.IRON_BLOCK)
                            && world.getBlockState(new BlockPos(2,1,1)).is(Blocks.CHAIN), "Ponder linked two-car model missing");
                }
            }
            case "solar_sail" -> {
                if (last) {
                    final var be = (com.stonytark.magnetization.content.sail.SolarSailBlockEntity) world.getBlockEntity(new BlockPos(2,1,2));
                    check(be != null && be.isNightDisabled() && world.getBlockState(new BlockPos(1,1,2)).is(Blocks.BLACK_STAINED_GLASS), "Ponder night cutoff/example missing");
                }
            }
            case "kinetic_coil" -> {
                if (last) check(world.getBlockState(new BlockPos(2,2,2)).is(Blocks.IRON_BLOCK)
                        && world.getBlockState(new BlockPos(3,1,2)).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT), "Ponder induction/pulse examples missing");
            }
            case "structural_inducer" -> {
                if (last) {
                    final var pos = new BlockPos(2,1,2);
                    final var facing = world.getBlockState(pos).getValue(DirectionalBlock.FACING);
                    check(world.getBlockState(pos.relative(facing.getOpposite(),2)).is(Blocks.IRON_BLOCK), "Ponder inducer structure is outside capture cone");
                    check(world.getBlockState(pos.west()).is(Blocks.GOLD_BLOCK), "Ponder inducer FE diagram missing");
                }
            }
            case "dipole_electromagnet" -> {
                if (last) check(world.getBlockState(new BlockPos(2,1,2)).getValue(DirectionalBlock.FACING) == Direction.WEST
                        && world.getBlockState(new BlockPos(1,1,2)).is(Blocks.BLUE_STAINED_GLASS)
                        && world.getBlockState(new BlockPos(3,1,2)).is(Blocks.RED_STAINED_GLASS), "Ponder dipole axis/pole diagram mismatch");
            }
            default -> throw new IllegalArgumentException("No native scene-state audit for " + id);
        }
    }
    /** Check every taught transition when its actual native instruction renders. */
    static void verifyInstruction(final PonderScene scene, final String id, final int stage) {
        final var world = scene.getWorld();
        final var center = MaterialControlPonderScenes.CENTER;
        switch (id) {
            case "docking_signals", "railgun_remote", "imprint_module", "tractor_beam" -> DockControlPonderAudit.verify(scene, id, stage);
            case "pyrrhotite_heat", "gyrostabilizer", "induction_pad", "magnetic_shaft" -> ThermalPowerPonderAudit.verify(scene, id, stage);
            case "magnetic_basics", "magnetic_excavator", "repulsor_transport" -> MagneticWorkflowSceneAudit.verify(scene, id, stage);
            case "mr_fluid_bridge" -> {
                boolean hard = stage == 1 || stage == 2;
                for (int x = 1; x <= 3; x++) {
                    var state = world.getBlockState(new BlockPos(x, stage == 3 && x > 1 ? 1 : 2, 2));
                    check(state.is(hard ? com.stonytark.magnetization.registry.MagBlocks.HARDENED_MR_FLUID.get()
                            : com.stonytark.magnetization.registry.MagBlocks.MR_FLUID_BLOCK.get()), "MR bridge phase " + stage);
                    check((hard ? state.getValue(com.stonytark.magnetization.content.fluid.HardenedMrFluidBlock.SOURCE)
                            : state.getFluidState().isSource()) == (x == 1), "MR source/flow distinction lost");
                }
                check(world.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.REDSTONE_BLOCK) == (stage == 1), "MR redstone phase mismatch");
                check(world.getBlockState(new BlockPos(2, 2, 4)).is(com.stonytark.magnetization.registry.MagBlocks.PERMANENT_MAGNET.get()) == (stage == 2), "MR magnetic phase mismatch");
                if (stage == 3) check(!world.getBlockState(new BlockPos(2, 1, 2)).getFluidState().isEmpty(), "MR restored flow missing");
            }
            case "field_strength_control" -> {
                var base = com.stonytark.magnetization.api.MagneticStrength.MEDIUM;
                var damped = com.stonytark.magnetization.content.hematite.HematiteBlock.dampenedStrength(world, center, base);
                var actual = com.stonytark.magnetization.content.HalbachArray.boostedStrength(world, center,
                        com.stonytark.magnetization.api.MagneticPolarity.NORTH, damped);
                int[] expectedTiers = {2, 3, 3, 4, 2, 1, 0};
                check(actual.ordinal() == expectedTiers[stage], "Field-strength scene teaches wrong tier at stage " + stage + ": " + actual);
            }
            case "magnetizing_equipment" -> {
                var stands = new java.util.ArrayList<net.minecraft.world.entity.decoration.ArmorStand>();
                scene.forEachWorldEntity(net.minecraft.world.entity.decoration.ArmorStand.class, stands::add);
                check(stands.size() == 1, "Equipment wearer missing");
                var helmet = stands.getFirst().getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                var component = com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get();
                var expected = stage == 1 ? com.stonytark.magnetization.api.MagneticPolarity.NORTH
                        : stage == 2 ? com.stonytark.magnetization.api.MagneticPolarity.SOUTH : null;
                check(helmet.is(net.minecraft.world.item.Items.IRON_HELMET) && helmet.get(component) == expected,
                        "Equipment tutorial did not apply native N/S/Clear action " + stage);
                if (stage > 0) check(stage == 2 ? stands.getFirst().getX() < 3 : stands.getFirst().getX() > 3,
                        "Equipment response arrow/movement has wrong sign");
            }
            default -> { } // Existing scenes retain their first/final state assertions.
        }
    }

}
