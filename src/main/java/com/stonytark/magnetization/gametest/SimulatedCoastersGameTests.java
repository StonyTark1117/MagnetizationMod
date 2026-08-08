package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.simulatedcoasters.MagSimulatedCoastersCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.inducer.StructuralInducerBlockEntity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathEdge;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathGraphManager;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathNode;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathTrackFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Isolated tests against the published Create: Coasters Simulated runtime. */
@GameTestHolder("magnetization_simulatedcoasters")
@PrefixGameTestTemplate(false)
public final class SimulatedCoastersGameTests {
    private SimulatedCoastersGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "coasterFieldCompat")
    public static void coasterCartFieldReactionHonorsConfig(final GameTestHelper helper) {
        final boolean original = MagConfig.SIMULATED_COASTERS_FIELD_REACTION.get();
        final Vec3 cartPosition = Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 8, 6)));
        final ServerSubLevel cart = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), cartPosition, new Quaterniond());
        if (cart == null) {
            helper.fail("Create: Coasters Simulated could not spawn its minimal cart");
            return;
        }
        helper.runAfterDelay(12L, () -> {
            try {
                helper.assertTrue(MagSimulatedCoastersCompat.isCoasterCart(cart),
                        "Published Coasters runtime did not recognize its spawned cart sublevel");
                final RigidBodyHandle handle = RigidBodyHandle.of(cart);
                helper.assertTrue(handle != null, "Spawned coaster cart has no Sable physics handle");
                final MagneticField field = new MagneticField(
                        cartPosition.add(5.0d, 0.0d, 0.0d), new Vec3(1, 0, 0),
                        MagneticPolarity.SOUTH, MagneticStrength.EXTREME,
                        MagneticField.Shape.OMNIDIRECTIONAL, 16.0d);

                MagConfig.SIMULATED_COASTERS_FIELD_REACTION.set(false);
                final Vector3d disabledBefore = handle.getLinearVelocity(new Vector3d());
                FieldApplicator.applyToSubLevelsOnly(helper.getLevel(), field, null, null);
                final Vector3d disabledAfter = handle.getLinearVelocity(new Vector3d());
                helper.assertTrue(new Vector3d(disabledAfter).sub(disabledBefore).lengthSquared() < 1.0e-10,
                        "Disabled coaster field reaction still changed velocity");

                MagConfig.SIMULATED_COASTERS_FIELD_REACTION.set(true);
                final Vector3d enabledBefore = handle.getLinearVelocity(new Vector3d());
                FieldApplicator.applyToSubLevelsOnly(helper.getLevel(), field, null, null);
                final Vector3d enabledAfter = handle.getLinearVelocity(new Vector3d());
                helper.assertTrue(new Vector3d(enabledAfter).sub(enabledBefore).lengthSquared() > 1.0e-8,
                        "Enabled coaster cart did not react to the magnetic field");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_COASTERS_FIELD_REACTION.set(original);
                remove(helper, cart);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "coasterInducerCompat")
    public static void structuralInducerRecognizesEngagedTrainButIgnoresLooseCart(final GameTestHelper helper) {
        final boolean original = MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.get();
        final BlockPos inducerPos = new BlockPos(4, 2, 4);
        helper.setBlock(inducerPos, MagBlocks.STRUCTURAL_INDUCER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN));
        final StructuralInducerBlockEntity inducer =
                (StructuralInducerBlockEntity) helper.getBlockEntity(inducerPos);
        final Vec3 scanCenter = Vec3.atCenterOf(helper.absolutePos(inducerPos.above(6)));
        // Keep the loose cart inside the inducer cone but well outside the
        // track snap radius, so Coasters cannot legitimately auto-engage it.
        final Vec3 cartPosition = scanCenter.add(0.0d, 0.0d, 5.0d);
        final ServerSubLevel looseCart = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), cartPosition, new Quaterniond());
        final Vec3 engagedPosition = scanCenter.add(2.0d, 0.0d, 0.0d);
        final BlockPos railFrom = BlockPos.containing(engagedPosition.add(-3.0d, 0.0d, 0.0d));
        final BlockPos railTo = railFrom.offset(6, 0, 0);
        final CoasterPathEdge edge = CoasterPathEdge.straight(railFrom, railTo,
                Vec3.atCenterOf(railFrom), Vec3.atCenterOf(railTo));
        final var graph = CoasterPathGraphManager.get(helper.getLevel());
        graph.upsertNode(new CoasterPathNode(railFrom, new Vec3(0, 1, 0)));
        graph.upsertNode(new CoasterPathNode(railTo, new Vec3(0, 1, 0)));
        graph.addEdge(edge);
        final ServerSubLevel engagedCart = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), engagedPosition, new Quaterniond(),
                new CoasterPathTrackFrame.GraphHit(engagedPosition, edge, 0.5d));
        if (looseCart == null || engagedCart == null) {
            helper.fail("Create: Coasters Simulated could not spawn the inducer test cart");
            return;
        }
        helper.runAfterDelay(12L, () -> {
            try {
                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(false);
                helper.assertTrue(!MagSimulatedCoastersCompat.structuralInducerCanAdopt(engagedCart),
                        "Disabled Structural Inducer coaster compatibility still accepted an engaged cart");

                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(true);
                helper.assertTrue(!MagSimulatedCoastersCompat.structuralInducerCanAdopt(looseCart),
                        "A loose/disengaged coaster cart was accepted as a rollercoaster structure");
                helper.assertTrue(MagSimulatedCoastersCompat.structuralInducerCanAdopt(engagedCart),
                        "A rail-engaged coaster cart was not accepted as a rollercoaster structure");
                inducer.setExternalSignal(15);
                StructuralInducerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(inducerPos),
                        inducer.getBlockState(), inducer);
                helper.assertTrue(!inducer.isTrackingStructure(looseCart.getUniqueId()),
                        "Structural Inducer tracked a loose coaster cart in its scan cone");
                helper.assertTrue(inducer.isTrackingStructure(engagedCart.getUniqueId()),
                        "Structural Inducer did not track a rail-engaged coaster cart");
                helper.assertTrue(inducer.trackedStructureCount() == 1,
                        "A rail-engaged coaster assembly was not counted as one structure");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(original);
                remove(helper, looseCart);
                remove(helper, engagedCart);
            }
        });
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel cart) {
        final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container != null && container.getSubLevel(cart.getUniqueId()) != null) {
            container.removeSubLevel(cart, SubLevelRemovalReason.REMOVED);
        }
    }
}
