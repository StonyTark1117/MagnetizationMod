package com.stonytark.magnetization.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.stonytark.magnetization.content.gyro.GyrostabilizerBlockEntity;
import com.stonytark.magnetization.content.induction.InductionPadBlockEntity;
import com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteHeatResolver;
import com.stonytark.magnetization.registry.MagBlocks;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import static com.stonytark.magnetization.gametest.LifecyclePresentationAudit.check;

/** Stage assertions read the played schematic; server GameTests cover the real mechanics. */
final class ThermalPowerPonderAudit {
    private ThermalPowerPonderAudit() {}
    static void verify(PonderScene scene, String id, int stage) {
        var world = scene.getWorld();
        switch (id) {
            case "pyrrhotite_heat" -> {
                var target = ThermalPowerPonderScenes.HEAT_TARGET;
                check(world.getBlockState(target).is(MagBlocks.PYRRHOTITE_BLOCK.get()), "Pyrrhotite missing");
                HeatLevel expected = switch (stage) {
                    case 0, 7, 9 -> HeatLevel.NONE;
                    case 1 -> HeatLevel.SMOULDERING;
                    case 2 -> HeatLevel.KINDLED;
                    default -> HeatLevel.SEETHING;
                };
                check(PyrrhotiteHeatResolver.resolve(world, target) == expected, "Actual heat resolver disagrees with tutorial stage " + stage);
                int cells = switch (expected) { case NONE -> 0; case SMOULDERING, FADING -> 1; case KINDLED -> 3; case SEETHING -> 4; };
                for (int x = 1; x <= 4; x++) check(world.getBlockState(new BlockPos(x, 1, 4)).is(x <= cells ? Blocks.BLUE_STAINED_GLASS : Blocks.AIR), "Heat strength gauge disagrees with resolver");
                if (stage >= 4 && stage <= 6) {
                    var catalyst = world.getBlockState(target.east(3 + (stage - 4) * 2)).getBlock();
                    check(catalyst == (stage == 4 ? MagBlocks.PYRRHOTITE_CATALYST.get() : stage == 5
                            ? MagBlocks.ENHANCED_PYRRHOTITE_CATALYST.get() : MagBlocks.COSMIC_PYRRHOTITE_CATALYST.get()), "Catalyst tier/range mismatch");
                }
            }
            case "gyrostabilizer" -> {
                var p = ThermalPowerPonderScenes.GYRO;
                var gyro = (GyrostabilizerBlockEntity) world.getBlockEntity(p);
                boolean active = stage == 1 || stage == 2;
                check(gyro != null && gyro.isStabilizing() == active
                        && world.getBlockState(p).getValue(BlockStateProperties.POWERED) == active, "Gyro displayed power/status stage " + stage);
                check(gyro.energyBuffer().getEnergyStored() == (stage == 2 ? 1000 : 0), "Gyro FE example mismatch");
                check(world.getBlockState(p.east()).is(stage == 1 ? Blocks.REDSTONE_BLOCK : stage == 2 ? Blocks.GOLD_BLOCK : Blocks.AIR), "Gyro power marker mismatch");
                for (int x = 1; x <= 3; x++) check(world.getBlockState(new BlockPos(x, 1, 2)).is(Blocks.IRON_BLOCK), "Gyro hull missing");
                var hulls = new java.util.ArrayList<net.createmod.ponder.api.element.WorldSectionElement>();
                scene.forEach(net.createmod.ponder.api.element.WorldSectionElement.class, element -> {
                    if (!element.isEmpty() && element.getAnimatedRotation().lengthSqr() > 0.0001) hulls.add(element);
                });
                check(hulls.size() == 1, "Gyro needs one independent moving hull");
                var hull = hulls.getFirst();
                check(hull.getAnimatedRotation().y > 0, "Ship never rotated");
                if (active) check(Math.abs(hull.getAnimatedRotation().y - 35) < 0.01
                        && hull.getAnimatedOffset().x > 0, "Powered hull must hold its angle while translated");
                if (stage == 3) check(hull.getAnimatedRotation().y > 35, "Unpowered hull did not resume rotating");
            }
            case "induction_pad" -> {
                var p = ThermalPowerPonderScenes.PAD;
                var pad = (InductionPadBlockEntity) world.getBlockEntity(p);
                check(pad != null && pad.energyBuffer().getEnergyStored() == (stage == 0 ? 0 : stage == 1 ? 16000 : 8000), "Induction illustrated buffer stage " + stage);
                check(world.getBlockState(p.west()).is(stage == 0 || stage == 4 ? Blocks.AIR : Blocks.GOLD_BLOCK), "Induction supply marker mismatch");
                for (int x = 2; x <= 4; x++) check(world.getBlockState(new BlockPos(x, 1, 4)).is(stage >= 2 ? Blocks.LIME_STAINED_GLASS : Blocks.AIR), "Equipment charge gauge mismatch");
                var stands = new java.util.ArrayList<ArmorStand>(); scene.forEachWorldEntity(ArmorStand.class, stands::add);
                check(stands.size() == 1 && Math.abs(stands.getFirst().getX() - (stage == 4 ? 6.5 : 2.5)) < 0.01, "Carrier did not leave default charging area");
            }
            case "magnetic_shaft" -> {
                for (int x : new int[]{1, 3}) {
                    var state = world.getBlockState(new BlockPos(x, 1, 2));
                    check(state.is(MagBlocks.MAGNETIC_SHAFT.get()) && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.X, "Wireless shafts not aligned");
                }
                var motor = world.getBlockState(new BlockPos(0, 1, 2));
                check(stage < 2 ? motor.is(AllBlocks.CREATIVE_MOTOR.get()) && motor.getValue(BlockStateProperties.FACING) == Direction.EAST : motor.isAir(), "Mechanical source removal stage " + stage);
                if (stage >= 1) {
                    check(world.getBlockState(new BlockPos(2, 1, 3)).is(MagBlocks.SAMARIUM_COBALT_MAGNETIC_SHAFT.get()), "SmCo shaft missing");
                    check(world.getBlockState(new BlockPos(3, 1, 3)).is(MagBlocks.NEODYMIUM_MAGNETIC_SHAFT.get()), "Neodymium shaft missing");
                }
            }
            default -> throw new IllegalArgumentException("Unknown thermal/power scene " + id);
        }
    }
}
