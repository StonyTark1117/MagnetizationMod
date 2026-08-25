package com.stonytark.magnetization.gametest;

import com.squishy.cbcaeronauticsmissiles.content.guidance.GuidanceComputerBlockEntity;
import com.squishy.cbcaeronauticsmissiles.content.network.TargetSolution;
import com.stonytark.magnetization.compat.simulatedmissiles.MagSimulatedMissilesCompat;
import com.stonytark.magnetization.config.MagConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** End-to-end EMP guidance loss against the published missile addon. */
@GameTestHolder("magnetization_simulated_missiles")
@PrefixGameTestTemplate(false)
public final class SimulatedMissilesGameTests {
    private SimulatedMissilesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "missileEmp")
    public static void intersectingEmpPermanentlyDisablesGuidance(final GameTestHelper helper) {
        final boolean master = MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.get();
        final boolean guidance = MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.get();
        final BlockPos world = helper.absolutePos(new BlockPos(4, 8, 4));
        final ServerSubLevel missile = assembleGuidanceShip(helper, world);
        helper.assertTrue(missile != null && MagSimulatedMissilesCompat.hasGuidanceComputer(missile),
                "Assembled missile fixture has no guidance computer actor");
        GuidanceComputerBlockEntity.setTarget(missile,
                TargetSolution.ground(helper.getLevel().dimension().location(), Vec3.atCenterOf(world.east(20))));
        // Sable publishes a newly assembled body's world AABB during its tick.
        // Wait for that real broad-phase state before exercising the EMP query.
        helper.runAfterDelay(3L, () -> {
            try {
                MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.set(true);
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(true);
                helper.assertTrue(MagSimulatedMissilesCompat.disableGuidanceInPulse(
                                helper.getLevel(), Vec3.atCenterOf(world.west(11)), 12.0d) == 1,
                        "EMP sphere did not recognize the intersecting missile AABB");
                final var tag = missile.getUserDataTag();
                helper.assertTrue(tag != null && tag.getBoolean("CbcAeronauticsMissilesGuidanceDisabled")
                                && !GuidanceComputerBlockEntity.prepareMissileGuidance(missile),
                        "EMP did not permanently clear and disable missile guidance");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.set(master);
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(guidance);
                remove(helper, missile);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "missileEmp")
    public static void outsideOrDisabledPulseLeavesGuidanceAlone(final GameTestHelper helper) {
        final boolean master = MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.get();
        final boolean guidance = MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.get();
        final BlockPos world = helper.absolutePos(new BlockPos(4, 8, 4));
        final ServerSubLevel missile = assembleGuidanceShip(helper, world);
        GuidanceComputerBlockEntity.setTarget(missile,
                TargetSolution.ground(helper.getLevel().dimension().location(), Vec3.atCenterOf(world.east(20))));
        helper.runAfterDelay(3L, () -> {
            try {
                MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.set(true);
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(true);
                helper.assertTrue(MagSimulatedMissilesCompat.disableGuidanceInPulse(
                                helper.getLevel(), Vec3.atCenterOf(world.east(30)), 12.0d) == 0,
                        "Out-of-range EMP affected missile guidance");
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(false);
                helper.assertTrue(MagSimulatedMissilesCompat.disableGuidanceInPulse(
                                helper.getLevel(), Vec3.atCenterOf(world), 12.0d) == 0,
                        "Disabled missile EMP compatibility still ran");
                final var tag = missile.getUserDataTag();
                helper.assertTrue(tag == null || !tag.getBoolean("CbcAeronauticsMissilesGuidanceDisabled"),
                        "Outside or disabled EMP permanently changed guidance");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.set(master);
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(guidance);
                remove(helper, missile);
            }
        });
    }

    private static ServerSubLevel assembleGuidanceShip(final GameTestHelper helper, final BlockPos pos) {
        final Block guidance = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("cbcaeronauticsmissiles", "guidance_computer"));
        helper.getLevel().setBlock(pos, guidance.defaultBlockState(), Block.UPDATE_ALL);
        final var bounds = new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        final ServerSubLevel ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), pos, java.util.List.of(pos), bounds);
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel());
        container.physicsSystem().getPipeline().teleport(ship,
                new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d),
                new Quaterniond());
        return ship;
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel ship) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel());
        if (container != null && ship != null && !ship.isRemoved()) {
            container.removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
    }
}
