package com.stonytark.magnetization.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.gyro.GyrostabilizerBlockEntity;
import com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity;
import com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteHeatResolver;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

@GameTestHolder("magnetization_regressions")
@PrefixGameTestTemplate(false)
public final class ThermalPowerPonderGameTests {
    private ThermalPowerPonderGameTests() {}

    @GameTest(template = "empty", batch = "ponder_heat_rules", timeoutTicks = 50)
    public static void catalystBoundariesAndHeatLevelsMatchTutorial(GameTestHelper h) {
        var level = h.getLevel(); var p = h.absolutePos(new BlockPos(1, 80, 1));
        for (var heat : HeatLevel.values()) {
            level.setBlock(p.above(), AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat), 3);
            h.assertTrue(PyrrhotiteHeatResolver.resolve(level, p) == heat, "Blaze heat mismatch " + heat);
            var expected = switch (heat) { case NONE -> null; case SMOULDERING, FADING -> MagneticStrength.WEAK;
                case KINDLED -> MagneticStrength.STRONG; case SEETHING -> MagneticStrength.EXTREME; };
            h.assertTrue(PyrrhotiteHeatResolver.strengthForHeat(heat) == expected, "Field strength mismatch " + heat);
        }
        level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
        var catalysts = java.util.List.of(MagBlocks.PYRRHOTITE_CATALYST.get(), MagBlocks.ENHANCED_PYRRHOTITE_CATALYST.get(), MagBlocks.COSMIC_PYRRHOTITE_CATALYST.get());
        for (var catalyst : catalysts) {
            int r = catalyst.transmitRadius();
            // A diagonal corner is inside the actual Chebyshev cube, not a spherical approximation.
            for (var offset : java.util.List.of(new BlockPos(r, 0, 0), new BlockPos(r, r, r), new BlockPos(r+1, 0, 0))) {
                var at = p.offset(offset);
                level.setBlock(at, catalyst.defaultBlockState(), 3); level.setBlock(at.above(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                var expected = offset.getX() == r ? HeatLevel.KINDLED : HeatLevel.NONE;
                h.assertTrue(PyrrhotiteHeatResolver.resolve(level, p) == expected, "Catalyst boundary wrong " + r + " " + offset);
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3); level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(p.east(3), catalysts.getFirst().defaultBlockState(), 3);
        level.setBlock(p.east(6), catalysts.getFirst().defaultBlockState(), 3);
        level.setBlock(p.east(6).above(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        h.assertTrue(PyrrhotiteHeatResolver.resolve(level, p) == HeatLevel.NONE, "Catalyst chaining unexpectedly supplies heat");
        level.setBlock(p.east(3).above(), Blocks.CAMPFIRE.defaultBlockState(), 3);
        level.setBlock(p.east(5), catalysts.get(1).defaultBlockState(), 3);
        level.setBlock(p.east(5).above(), Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        h.assertTrue(PyrrhotiteHeatResolver.resolve(level, p) == HeatLevel.KINDLED, "Hottest in-range source did not win");
        for (int x = 3; x <= 6; x++) for (int y = 0; y <= 1; y++) level.setBlock(p.offset(x,y,0), Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "ponder_live_heat", timeoutTicks = 230)
    public static void nativePyrrhotiteFieldStartsAndStopsOnHeatScans(GameTestHelper h) {
        var p = new BlockPos(1, 80, 1); h.setBlock(p, MagBlocks.PYRRHOTITE_BLOCK.get());
        h.runAfterDelay(2, () -> {
            var be = (PyrrhotiteBlockEntity) h.getBlockEntity(p);
            h.assertTrue(be.currentField() == null, "Cold Pyrrhotite field active");
            h.setBlock(p.east(3), MagBlocks.PYRRHOTITE_CATALYST.get());
            h.setBlock(p.east(3).above(), Blocks.MAGMA_BLOCK);
            h.runAfterDelay(MagConfig.pyrrhotiteScanTicks() + 2, () -> {
                h.assertTrue(be.currentField() != null && be.currentField().strength() == MagneticStrength.STRONG, "Native tick did not activate relayed heat");
                h.setBlock(p.east(3).above(), Blocks.AIR);
                h.runAfterDelay(MagConfig.pyrrhotiteResidualScanTicks() + 2, () -> {
                    h.assertTrue(be.currentField() == null && be.observedHeat() == HeatLevel.NONE, "Native residual scan retained removed heat");
                    h.setBlock(p, Blocks.AIR); h.setBlock(p.east(3), Blocks.AIR); h.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "ponder_gyro", timeoutTicks = 100)
    public static void nativeGyroCancelsSpinWithoutCancellingTranslation(GameTestHelper h) {
        var level = h.getLevel(); var p = h.absolutePos(new BlockPos(1, 100, 1));
        level.setBlock(p, MagBlocks.GYROSTABILIZER.get().defaultBlockState(), 3);
        level.setBlock(p.below(), Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, p, java.util.List.of(p, p.below()),
                new BoundingBox3i(p.getX(), p.getY()-1, p.getZ(), p.getX()+1, p.getY()+1, p.getZ()+1));
        var bounds = ship.getPlot().getBoundingBox();
        var aboard = new BlockPos(bounds.minX(), bounds.minY()+1, bounds.minZ());
        h.runAfterDelay(5, () -> {
            try {
                var gyro = (GyrostabilizerBlockEntity) level.getBlockEntity(aboard);
                h.assertTrue(gyro != null, "Native assembly lost Gyrostabilizer");
                var body = RigidBodyHandle.of(ship); h.assertTrue(body != null && body.isValid(), "Ship body absent");
                for (int mode = 0; mode < 3; mode++) {
                    gyro.clearEnergyForEmp(); level.setBlock(aboard.east(), mode == 1 ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                    if (mode > 0) gyro.energyBuffer().receiveEnergy(1000, false);
                    var linear = new Vector3d(1, .25, -.5); var angular = new Vector3d(.2, .3, .4);
                    body.addLinearAndAngularVelocity(new Vector3d(linear).sub(body.getLinearVelocity(new Vector3d())), new Vector3d(angular).sub(body.getAngularVelocity(new Vector3d())));
                    gyro.sable$tick(ship);
                    h.assertTrue(body.getLinearVelocity(new Vector3d()).distance(linear) < 1e-6, "Gyro changed translation");
                    h.assertTrue(body.getAngularVelocity(new Vector3d()).distance(mode == 0 ? angular : new Vector3d()) < 1e-6, "Gyro angular result wrong for power mode " + mode);
                    h.assertTrue(gyro.isStabilizing() == (mode > 0), "Gyro status wrong");
                    h.assertTrue(gyro.energyBuffer().getEnergyStored() == (mode == 0 ? 0 : mode == 1 ? 1000 : 980), "FE drain or redstone priority wrong");
                }
            } finally { SubLevelContainer.getContainer(level).removeSubLevel(ship, SubLevelRemovalReason.REMOVED); }
            // Off-ship power cannot stabilize or spend FE.
            level.setBlock(p, MagBlocks.GYROSTABILIZER.get().defaultBlockState(), 3);
            var ground = (GyrostabilizerBlockEntity) level.getBlockEntity(p); ground.energyBuffer().receiveEnergy(1000, false);
            GyrostabilizerBlockEntity.serverTick(level,p,ground.getBlockState(),ground);
            h.assertTrue(!ground.isStabilizing() && ground.energyBuffer().getEnergyStored() == 1000, "Ground gyro spent FE");
            level.setBlock(p, Blocks.AIR.defaultBlockState(),3); h.succeed();
        });
    }
}
