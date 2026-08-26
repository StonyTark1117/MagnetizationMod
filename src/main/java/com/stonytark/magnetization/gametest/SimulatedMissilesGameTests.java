package com.stonytark.magnetization.gametest;

import com.squishy.cbcaeronauticsmissiles.content.guidance.GuidanceComputerBlockEntity;
import com.squishy.cbcaeronauticsmissiles.content.motor.RocketMotorBlockEntity;
import com.squishy.cbcaeronauticsmissiles.content.network.TargetSolution;
import com.stonytark.magnetization.compat.simulatedmissiles.MagSimulatedMissilesCompat;
import com.stonytark.magnetization.config.MagConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** End-to-end EMP guidance loss against the published missile addon. */
@GameTestHolder("magnetization_simulated_missiles")
@PrefixGameTestTemplate(false)
public final class SimulatedMissilesGameTests {
    private SimulatedMissilesGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "missileEmp")
    public static void partiallyIntersectingEmpPermanentlyDisablesOnlyGuidance(
            final GameTestHelper helper) {
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
                final var box = missile.boundingBox();
                final Vec3 center = new Vec3(box.minX() - 1.0d,
                        (box.minY() + box.maxY()) * 0.5d,
                        (box.minZ() + box.maxZ()) * 0.5d);
                assertGuidanceOnlyDisabled(helper, missile, center, 1.25d,
                        "partially intersecting");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.set(master);
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(guidance);
                remove(helper, missile);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "missileEmp")
    public static void fullyContainedEmpPermanentlyDisablesOnlyGuidance(
            final GameTestHelper helper) {
        final boolean master = MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.get();
        final boolean guidance = MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.get();
        final BlockPos world = helper.absolutePos(new BlockPos(4, 8, 4));
        final ServerSubLevel missile = assembleGuidanceShip(helper, world);
        helper.runAfterDelay(3L, () -> {
            try {
                MagConfig.SIMULATED_MISSILES_COMPAT_ENABLED.set(true);
                MagConfig.SIMULATED_MISSILES_GUIDANCE_EMP_ENABLED.set(true);
                final var box = missile.boundingBox();
                final Vec3 center = new Vec3((box.minX() + box.maxX()) * 0.5d,
                        (box.minY() + box.maxY()) * 0.5d,
                        (box.minZ() + box.maxZ()) * 0.5d);
                final double dx = box.maxX() - box.minX();
                final double dy = box.maxY() - box.minY();
                final double dz = box.maxZ() - box.minZ();
                assertGuidanceOnlyDisabled(helper, missile, center,
                        Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.5d + 1.0d,
                        "fully containing");
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
        final List<BlockPos> positions = List.of(pos, pos.east(), pos.east(2),
                pos.east(3), pos.east(4));
        final List<String> blockIds = List.of("guidance_computer", "actuator_fins",
                "rocket_motor", "aircraft_proximity_fuze", "octagonal_copycat");
        for (int i = 0; i < positions.size(); i++) {
            final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                    "cbcaeronauticsmissiles", blockIds.get(i)));
            helper.getLevel().setBlock(positions.get(i), block.defaultBlockState(), Block.UPDATE_ALL);
        }
        final var bounds = new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 5, pos.getY() + 1, pos.getZ() + 1);
        final ServerSubLevel ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                helper.getLevel(), pos, positions, bounds);
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel());
        container.physicsSystem().getPipeline().teleport(ship,
                new Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d),
                new Quaterniond());
        GuidanceComputerBlockEntity.setTarget(ship,
                TargetSolution.ground(helper.getLevel().dimension().location(),
                        Vec3.atCenterOf(pos.east(20).above(12))));
        final var tag = ship.getUserDataTag();
        tag.putBoolean("CbcAeronauticsMissilesReleasedMissile", true);
        tag.putString("MagnetizationPayloadSentinel", "preserve");
        tag.putInt("MagnetizationPhysicsSentinel", 314159);
        return ship;
    }

    private static void assertGuidanceOnlyDisabled(final GameTestHelper helper,
                                                   final ServerSubLevel missile,
                                                   final Vec3 center,
                                                   final double radius,
                                                   final String geometry) {
        GuidanceComputerBlockEntity computer = null;
        RocketMotorBlockEntity motor = null;
        final Map<BlockPos, CompoundTag> actorNbt = new LinkedHashMap<>();
        final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> actorBlocks =
                new LinkedHashMap<>();
        for (final var actor : missile.getPlot().getBlockEntityActors()) {
            if (!(actor instanceof BlockEntity blockEntity)) continue;
            if (actor instanceof GuidanceComputerBlockEntity guidance) computer = guidance;
            if (actor instanceof RocketMotorBlockEntity rocketMotor) motor = rocketMotor;
            actorBlocks.put(blockEntity.getBlockPos(), blockEntity.getBlockState());
        }
        helper.assertTrue(computer != null && motor != null,
                "Rich missile fixture lost its guidance computer or rocket motor");
        if (computer == null || motor == null) return;
        final var powderCharge = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("createbigcannons", "powder_charge"))
                .asItem();
        motor.setItem(0, new ItemStack(powderCharge));
        helper.assertTrue(!motor.getItem(0).isEmpty()
                        && RocketMotorBlockEntity.isPowderCharge(motor.getItem(0)),
                "Rich missile fixture could not load a native powder charge");
        helper.assertTrue(GuidanceComputerBlockEntity.prepareMissileGuidance(missile),
                "Rich missile fixture could not resolve its native actuator/target");
        final BlockPos actuator = computer.getActuatorPos().orElse(null);
        helper.assertTrue(actuator != null, "Guidance computer did not retain an actuator link");
        if (actuator == null) return;
        computer.sable$physicsTick(missile, RigidBodyHandle.of(missile), 0.05d);
        final Vector3d commandBefore = GuidanceComputerBlockEntity.getActuatorCommand(
                missile, actuator);
        helper.assertTrue(commandBefore.lengthSquared() > 0.0d,
                "Native missile guidance did not publish a pre-EMP actuator command");
        for (final var actor : missile.getPlot().getBlockEntityActors()) {
            if (actor instanceof BlockEntity blockEntity) {
                actorNbt.put(blockEntity.getBlockPos(), blockEntity.saveWithoutMetadata(
                        helper.getLevel().registryAccess()));
            }
        }
        final Vector3d velocityBefore = RigidBodyHandle.of(missile).getLinearVelocity(
                new Vector3d());
        final CompoundTag beforeTag = missile.getUserDataTag().copy();

        helper.assertTrue(MagSimulatedMissilesCompat.disableGuidanceInPulse(
                        helper.getLevel(), center, radius) == 1,
                "EMP sphere did not recognize the " + geometry + " missile AABB");
        final CompoundTag afterTag = missile.getUserDataTag();
        helper.assertTrue(afterTag.getBoolean("CbcAeronauticsMissilesGuidanceDisabled")
                        && !afterTag.contains("GuidanceTargetKind")
                        && !afterTag.contains("GuidanceTargetDimension")
                        && !afterTag.contains("GuidanceTargetX")
                        && !afterTag.contains("GuidanceTargetY")
                        && !afterTag.contains("GuidanceTargetZ")
                        && !afterTag.contains("GuidanceTargetBody")
                        && !GuidanceComputerBlockEntity.prepareMissileGuidance(missile),
                "EMP did not permanently clear and disable missile guidance");
        helper.assertTrue(GuidanceComputerBlockEntity.getActuatorCommand(missile, actuator)
                        .lengthSquared() == 0.0d,
                "EMP guidance loss did not clear the actuator command");
        helper.assertTrue(afterTag.getBoolean("CbcAeronauticsMissilesReleasedMissile")
                        == beforeTag.getBoolean("CbcAeronauticsMissilesReleasedMissile")
                        && afterTag.getString("MagnetizationPayloadSentinel")
                        .equals(beforeTag.getString("MagnetizationPayloadSentinel"))
                        && afterTag.getInt("MagnetizationPhysicsSentinel")
                        == beforeTag.getInt("MagnetizationPhysicsSentinel"),
                "EMP guidance loss altered non-guidance missile user data");
        helper.assertTrue(motor.getItem(0).is(powderCharge) && motor.getItem(0).getCount() == 1,
                "EMP guidance loss altered the rocket motor inventory");
        final Map<BlockPos, BlockEntity> actorsAfter = new LinkedHashMap<>();
        for (final var actor : missile.getPlot().getBlockEntityActors()) {
            if (actor instanceof BlockEntity blockEntity) {
                actorsAfter.put(blockEntity.getBlockPos(), blockEntity);
            }
        }
        for (final var entry : actorNbt.entrySet()) {
            final BlockEntity actor = actorsAfter.get(entry.getKey());
            helper.assertTrue(actor != null
                            && actor.saveWithoutMetadata(helper.getLevel().registryAccess())
                            .equals(entry.getValue())
                            && actor.getBlockState().equals(actorBlocks.get(entry.getKey())),
                    "EMP guidance loss altered a motor/fuze/payload actor at " + entry.getKey());
        }
        final Vector3d velocityAfter = RigidBodyHandle.of(missile).getLinearVelocity(
                new Vector3d());
        helper.assertTrue(velocityAfter.equals(velocityBefore),
                "EMP guidance loss directly changed ordinary Sable velocity");
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel ship) {
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(helper.getLevel());
        if (container != null && ship != null && !ship.isRemoved()) {
            container.removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
    }
}
