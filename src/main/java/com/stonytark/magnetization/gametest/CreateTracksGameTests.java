package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.inducer.StructuralInducerBlockEntity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.ShipMagneticScanner;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Real-runtime checks for Create: Tracks vehicles on the Aeronautics/Sable stack. */
@GameTestHolder("magnetization_createtracks")
@PrefixGameTestTemplate(false)
public final class CreateTracksGameTests {
    private CreateTracksGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 140, batch = "createTracksInducer")
    public static void inducerAssemblesTrackMountAndResultingVehicleReactsToField(
            final GameTestHelper helper) {
        final Block trackMount = block("track_mount");
        helper.assertTrue(trackMount.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "Create: Tracks mount is not counted in craft susceptibility");

        final BlockPos inducerRel = new BlockPos(4, 1, 4);
        final BlockPos mountRel = inducerRel.above(3);
        helper.setBlock(inducerRel, MagBlocks.STRUCTURAL_INDUCER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN));
        helper.setBlock(mountRel, trackMount);
        helper.setBlock(mountRel.east(), Blocks.IRON_BLOCK);
        helper.setBlock(mountRel.west(), Blocks.IRON_BLOCK);
        final StructuralInducerBlockEntity inducer =
                (StructuralInducerBlockEntity) helper.getBlockEntity(inducerRel);
        // Let Tracks initialize its kinetic BE and held suspension state before
        // Sable serializes it into a new plot.
        helper.runAfterDelay(4, () -> {
            inducer.setExternalSignal(15);
            StructuralInducerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(inducerRel),
                    inducer.getBlockState(), inducer);
            helper.assertTrue(inducer.trackedStructureCount() == 1,
                    "Structural Inducer did not initially adopt the Tracks assembly as one structure");
        });

        helper.runAfterDelay(24, () -> {
            final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
            final ServerSubLevel vehicle = container.getAllSubLevels().stream()
                    .filter(ServerSubLevel.class::isInstance)
                    .map(ServerSubLevel.class::cast)
                    .findFirst().orElse(null);
            helper.assertTrue(vehicle != null,
                    "Structural Inducer did not carry the Track Mount block entity into one Sable craft");
            if (vehicle == null) return;
            try {
                final var state = ShipMagneticScanner.scan(vehicle);
                helper.assertTrue(state.ferrousBlockCount() >= 3,
                        "Tracked vehicle lost its mount/iron susceptibility after assembly");
                final RigidBodyHandle handle = RigidBodyHandle.of(vehicle);
                helper.assertTrue(handle != null, "Induced tracked vehicle has no physics handle");
                if (handle == null) return;
                final Vec3 center = new Vec3(vehicle.logicalPose().position().x(),
                        vehicle.logicalPose().position().y(), vehicle.logicalPose().position().z());
                final MagneticField field = new MagneticField(center.add(5, 0, 0), new Vec3(1, 0, 0),
                        MagneticPolarity.SOUTH, MagneticStrength.EXTREME,
                        MagneticField.Shape.OMNIDIRECTIONAL, 16.0d);
                final Vector3d before = handle.getLinearVelocity(new Vector3d());
                FieldApplicator.applyToSubLevelsOnly(helper.getLevel(), field, null, null);
                final Vector3d after = handle.getLinearVelocity(new Vector3d());
                helper.assertTrue(new Vector3d(after).sub(before).lengthSquared() > 1.0e-8,
                        "Create: Tracks vehicle did not react to a magnetic field as a Sable craft");
                helper.succeed();
            } finally {
                container.removeSubLevel(vehicle, SubLevelRemovalReason.REMOVED);
            }
        });
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("tracks", path));
    }
}
