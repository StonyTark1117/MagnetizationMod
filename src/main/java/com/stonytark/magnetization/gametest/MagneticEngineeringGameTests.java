package com.stonytark.magnetization.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity.Status.*;

@GameTestHolder("magnetization_engineering")
@PrefixGameTestTemplate(false)
public final class MagneticEngineeringGameTests {
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void shaftsShareLoadReverseStopConflictAndSwapRoles(GameTestHelper helper) {
        int originalRange = MagConfig.MAGNETIC_SHAFT_RANGE.get();
        MagConfig.MAGNETIC_SHAFT_RANGE.set(3);
        BlockPos a = new BlockPos(1, 2, 1), b = new BlockPos(3, 2, 1), c = new BlockPos(5, 2, 1);
        for (BlockPos pos : new BlockPos[]{a, b, c}) helper.setBlock(pos, MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(a.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.startSequence()
            .thenExecuteAfter(8, () -> motor(helper, a.west(), 32))
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper, a).status() == SOURCE, "Mechanically driven shaft is not a source");
                helper.assertTrue(shaft(helper, b).getSpeed() == 32, "Receiver did not match source RPM: " + shaft(helper,b).getSpeed());
                helper.assertTrue(shaft(helper, c).getSpeed() == 0, "Receiver retransmitted power outside source range");
                helper.assertTrue(shaft(helper,a).network.equals(shaft(helper,b).network), "Wireless load is not on the original Create network");
                helper.setBlock(b.north(), MagBlocks.KINETIC_ELECTROMAGNET.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
                motor(helper, a.west(), -64);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == -64, "Receiver failed reversal or speed change");
                // Put a real load on the receiver's shaft axis.
                helper.setBlock(b.above(), Blocks.AIR);
                helper.setBlock(b.east(), MagBlocks.KINETIC_ELECTROMAGNET.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
            })
            .thenExecuteAfter(8, () -> {
                var drive = (CreativeMotorBlockEntity)helper.getBlockEntity(a.west());
                var network = drive.getOrCreateNetwork();
                helper.assertTrue(network.calculateStress() >= 4 * 64, "Remote machine load never reached the drive");
                network.updateCapacityFor(drive, 1);
            })
            .thenExecuteAfter(4, () -> {
                helper.assertTrue(shaft(helper,a).isOverStressed() && shaft(helper,b).isOverStressed(), "Overload did not stop both ends");
                helper.setBlock(b.east(), Blocks.AIR);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == -64, "Network failed to recover when load was removed");
                motor(helper, a.west(), 0);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,a).getSpeed() == 0 && shaft(helper,b).getSpeed() == 0, "Depowered shafts sustained themselves");
                helper.setBlock(c.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
            })
            .thenExecuteAfter(8, () -> motor(helper, c.east(), 48))
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,c).status() == SOURCE && shaft(helper,b).getSpeed() == shaft(helper,c).getSpeed() && Math.abs(shaft(helper,b).getSpeed()) == 48, "Nearby shaft did not take over as source: c=" + shaft(helper,c).status() + "/" + shaft(helper,c).getSpeed() + " b=" + shaft(helper,b).status() + "/" + shaft(helper,b).getSpeed());
                motor(helper, a.west(), 32);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).status() == CONFLICT && shaft(helper,b).getSpeed() == 0, "Competing speeds did not stop the receiving network");
                helper.assertTrue(shaft(helper,a).getSpeed() == 32 && Math.abs(shaft(helper,c).getSpeed()) == 48, "Conflict overrode independent drives");
                helper.setBlock(c, Blocks.AIR);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == 32, "Removing conflicting source did not restore receiver");
                helper.setBlock(a, Blocks.AIR);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == 0, "Destroyed source left receiver powered");
                MagConfig.MAGNETIC_SHAFT_RANGE.set(originalRange);
            }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "engineeringShaftEdges")
    public static void multipleReceiversShareCapacityAndLocalDriveTakesOver(GameTestHelper helper) {
        int originalRange = MagConfig.MAGNETIC_SHAFT_RANGE.get();
        MagConfig.MAGNETIC_SHAFT_RANGE.set(8);
        BlockPos a = new BlockPos(1, 2, 1), b = new BlockPos(4, 2, 1), c = new BlockPos(1, 2, 5);
        for (var pos : new BlockPos[]{a, b, c}) helper.setBlock(pos, MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        helper.setBlock(a.west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.startSequence().thenExecuteAfter(8, () -> motor(helper, a.west(), 32))
            .thenExecuteAfter(8, () -> {
                var drive = (CreativeMotorBlockEntity) helper.getBlockEntity(a.west());
                for (var pos : new BlockPos[]{b,c}) {
                    helper.assertTrue(shaft(helper,pos).getSpeed() == 32, "Multiple receiver did not turn");
                    helper.assertTrue(shaft(helper,pos).network.equals(drive.network), "Receiver has a separate power budget");
                    helper.setBlock(pos.east(), MagBlocks.KINETIC_ELECTROMAGNET.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                }
            })
            .thenExecuteAfter(8, () -> {
                var drive = (CreativeMotorBlockEntity) helper.getBlockEntity(a.west());
                var network = drive.getOrCreateNetwork();
                helper.assertTrue(network.calculateStress() >= 8 * 32, "Both receiver loads did not reach the source");
                helper.assertTrue(network.sources.size() == 1, "Wireless reception invented capacity sources");
                // Restore the receiver from its real serialized state with no active manager link.
                var receiver = shaft(helper,b);
                var saved = receiver.saveWithoutMetadata(helper.getLevel().registryAccess());
                helper.setBlock(b, Blocks.AIR);
                helper.setBlock(b, MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
                receiver = shaft(helper,b);
                receiver.loadWithComponents(saved, helper.getLevel().registryAccess());
                receiver.initialize();
                helper.assertTrue(receiver.getSpeed() == 0, "Restored receiver trusted its saved wireless RPM");
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == 32, "Restored receiver did not reacquire source");
                MagConfig.MAGNETIC_SHAFT_RANGE.set(2);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == 0 && shaft(helper,c).getSpeed() == 0, "Out-of-range receivers remained powered");
                MagConfig.MAGNETIC_SHAFT_RANGE.set(8);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).getSpeed() == 32 && shaft(helper,c).getSpeed() == 32, "Receivers failed range re-entry");
                helper.setBlock(b.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
            })
            .thenExecuteAfter(4, () -> motor(helper,b.east(), -64))
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,b).status() == SOURCE && shaft(helper,b).getSpeed() == 64, "New local drive failed to replace wireless reception");
                helper.assertTrue(shaft(helper,a).getSpeed() == 32, "Local takeover overrode the original drive");
                helper.assertTrue(shaft(helper,c).status() == CONFLICT && shaft(helper,c).getSpeed() == 0, "Receiver did not expose source conflict");
                motor(helper,b.east(), -32);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,c).getSpeed() == 32, "Matching drives did not resolve conflict");
                helper.assertTrue(shaft(helper,c).transmitter() != null, "Matching drives did not select one source");
                helper.setBlock(a.west(), Blocks.AIR);
            })
            .thenExecuteAfter(8, () -> {
                helper.assertTrue(shaft(helper,a).status() == RECEIVING && shaft(helper,a).getSpeed() == 32, "Former source failed to become a receiver");
                helper.assertTrue(shaft(helper,c).getSpeed() == 32, "Surviving drive failed takeover");
                helper.setBlock(b.east(), Blocks.AIR);
            })
            .thenExecuteAfter(8, () -> {
                for (var pos : new BlockPos[]{a,b,c}) helper.assertTrue(shaft(helper,pos).getSpeed() == 0, "Source-free network self sustained: " + pos + " status=" + shaft(helper,pos).status() + " rpm=" + shaft(helper,pos).getSpeed() + " parent=" + shaft(helper,pos).source + " tx=" + shaft(helper,pos).transmitter());
                MagConfig.MAGNETIC_SHAFT_RANGE.set(originalRange);
            }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1000, batch = "engineeringChunkLifecycle")
    public static void sourceChunkUnloadStopsReceiverAndReloadReconnects(GameTestHelper helper) {
        var level = helper.getLevel();
        var source = new BlockPos(10000,80,10000);
        var receiver = source.east(63);
        int sourceX=source.getX()>>4, receiverX=receiver.getX()>>4, z=source.getZ()>>4;
        int originalRange = MagConfig.MAGNETIC_SHAFT_RANGE.get();
        MagConfig.MAGNETIC_SHAFT_RANGE.set(64);
        level.setChunkForced(sourceX,z,true); level.setChunkForced(receiverX,z,true);
        level.getChunk(sourceX,z); level.getChunk(receiverX,z);
        var shaftState=MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X);
        level.setBlock(source,shaftState,3); level.setBlock(receiver,shaftState,3);
        level.setBlock(source.west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST),3);
        helper.startSequence().thenExecuteAfter(20, () -> {
            var motor=(CreativeMotorBlockEntity)level.getBlockEntity(source.west());
            motor.generatedSpeed.setValue(32); motor.updateGeneratedRotation();
        }).thenExecuteAfter(12, () -> {
            helper.assertTrue(((MagneticShaftBlockEntity)level.getBlockEntity(receiver)).getSpeed()==32,"Cross-chunk receiver did not connect");
            level.setChunkForced(sourceX,z,false);
        }).thenWaitUntil(() -> helper.assertTrue(!level.hasChunkAt(source),"Source chunk is still loaded: " + level.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(source))))
          .thenExecuteAfter(8, () -> {
              helper.assertTrue(((MagneticShaftBlockEntity)level.getBlockEntity(receiver)).getSpeed()==0,"Unloaded source left receiver powered");
              helper.assertTrue(!level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(sourceX,z)),"Coupling kept the source forced");
              level.setChunkForced(sourceX,z,true);
          }).thenWaitUntil(() -> helper.assertTrue(level.hasChunkAt(source),"Reloaded source not available"))
          .thenExecuteAfter(20, () -> {
              helper.assertTrue(((MagneticShaftBlockEntity)level.getBlockEntity(receiver)).getSpeed()==32,"Reloaded source did not reconnect: source=" + ((MagneticShaftBlockEntity)level.getBlockEntity(source)).status() + "/" + ((MagneticShaftBlockEntity)level.getBlockEntity(source)).getSpeed() + " receiver=" + ((MagneticShaftBlockEntity)level.getBlockEntity(receiver)).status() + "/" + ((MagneticShaftBlockEntity)level.getBlockEntity(receiver)).getSpeed() + " registered=" + com.stonytark.magnetization.content.shaft.MagneticShaftNetwork.loadedShafts(level).size());
              level.setBlock(source,Blocks.AIR.defaultBlockState(),3); level.setBlock(source.west(),Blocks.AIR.defaultBlockState(),3);
              level.setBlock(receiver,Blocks.AIR.defaultBlockState(),3);
              level.setChunkForced(sourceX,z,false); level.setChunkForced(receiverX,z,false);
              MagConfig.MAGNETIC_SHAFT_RANGE.set(originalRange);
          }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 180, batch = "engineeringMaterials")
    public static void materialsUseIndependentConfiguredRangesAndSharedDriveCapacity(GameTestHelper helper) {
        var blocks = java.util.List.of(MagBlocks.MAGNETIC_SHAFT.get(), MagBlocks.SAMARIUM_COBALT_MAGNETIC_SHAFT.get(), MagBlocks.NEODYMIUM_MAGNETIC_SHAFT.get());
        int oldBase=MagConfig.MAGNETIC_SHAFT_RANGE.get(), oldCobalt=MagConfig.SAMARIUM_COBALT_SHAFT_RANGE.get(), oldNeo=MagConfig.NEODYMIUM_SHAFT_RANGE.get();
        MagConfig.MAGNETIC_SHAFT_RANGE.set(3); MagConfig.SAMARIUM_COBALT_SHAFT_RANGE.set(6); MagConfig.NEODYMIUM_SHAFT_RANGE.set(10);
        for(int i=0;i<3;i++) {
            var a=new BlockPos(1,2+i*20,1);
            helper.setBlock(a,blocks.get(i).defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
            helper.setBlock(a.west(),AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING,Direction.EAST));
            helper.setBlock(a.east(5),MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
        }
        helper.startSequence().thenExecuteAfter(8,()->{
            for(int i=0;i<3;i++) motor(helper,new BlockPos(0,2+i*20,1),32);
        }).thenExecuteAfter(12,()->{
            for(int i=0;i<3;i++) {
                var a=new BlockPos(1,2+i*20,1); var source=shaft(helper,a); var target=shaft(helper,a.east(5));
                helper.assertTrue(source.transmissionRange()==new int[]{3,6,10}[i],"Material ignored its independent server radius");
                helper.assertTrue(source.material().strength().ordinal()==i+1,"Material strength differs from permanent magnets");
                helper.assertTrue(target.getSpeed()==(i==0?0:32),"Source material did not determine reach to a weaker receiver");
                if(i>0) helper.assertTrue(source.network.equals(target.network),"Variant receiver invented its own power network");
                helper.assertTrue(!com.stonytark.magnetization.api.MagneticFieldSource.class.isInstance(source),"Rotational variant became an attraction emitter");
            }
            MagConfig.MAGNETIC_SHAFT_RANGE.set(7); MagConfig.SAMARIUM_COBALT_SHAFT_RANGE.set(2);
        }).thenExecuteAfter(12,()->{
            helper.assertTrue(shaft(helper,new BlockPos(6,2,1)).getSpeed()==32,"Increasing base radius did not reconnect");
            helper.assertTrue(shaft(helper,new BlockPos(6,22,1)).getSpeed()==0,"Reducing cobalt radius did not disconnect");
            helper.assertTrue(shaft(helper,new BlockPos(6,42,1)).getSpeed()==32,"Changing other material ranges changed neodymium");
            for(int i=0;i<3;i++) {
                var a=new BlockPos(1,2+i*20,1);
                helper.setBlock(a,Blocks.AIR); helper.setBlock(a.west(),Blocks.AIR); helper.setBlock(a.east(5),Blocks.AIR);
            }
            MagConfig.MAGNETIC_SHAFT_RANGE.set(oldBase); MagConfig.SAMARIUM_COBALT_SHAFT_RANGE.set(oldCobalt); MagConfig.NEODYMIUM_SHAFT_RANGE.set(oldNeo);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = "engineeringPhysics")
    public static void inspectionReportsAppliedForceAndCap(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2)).atY(180);
        var ship = assemble(level, base, java.util.List.of(base, base.east(), base.above()));
        var expected = new net.minecraft.world.phys.Vec3[]{net.minecraft.world.phys.Vec3.ZERO};
        double oldCap = MagConfig.MAX_ACCEL_PER_TICK.get(), oldLinear = MagConfig.SHIP_LINEAR_DRAG.get(), oldAngular = MagConfig.SHIP_ANGULAR_DRAG.get();
        helper.startSequence().thenExecuteAfter(12, () -> {
            MagConfig.MAX_ACCEL_PER_TICK.set(.1d); MagConfig.SHIP_LINEAR_DRAG.set(0d); MagConfig.SHIP_ANGULAR_DRAG.set(0d);
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            helper.assertTrue(handle != null, "No live physics body");
            var before = handle.getLinearVelocity(new org.joml.Vector3d());
            var center = ship.logicalPose().transformPosition(new org.joml.Vector3d(ship.getMassTracker().getCenterOfMass()));
            var origin = new net.minecraft.world.phys.Vec3(center.x + 2, center.y + 1, center.z + .7);
            var field = new com.stonytark.magnetization.api.MagneticField(origin, new net.minecraft.world.phys.Vec3(1,0,0),
                    com.stonytark.magnetization.api.MagneticPolarity.SOUTH, com.stonytark.magnetization.api.MagneticStrength.STRONG,
                    com.stonytark.magnetization.api.MagneticField.Shape.OMNIDIRECTIONAL, 16, 10000);
            com.stonytark.magnetization.physics.inspection.FieldInspectionTracker.watch(level, ship.getUniqueId());
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field);
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field.withPolarity(com.stonytark.magnetization.api.MagneticPolarity.NORTH));
            var after = handle.getLinearVelocity(new org.joml.Vector3d());
            after.sub(before).mul(ship.getMassTracker().getMass() / .05);
            expected[0] = new net.minecraft.world.phys.Vec3(after.x, after.y, after.z);
        }).thenExecuteAfter(1, () -> {
            var trace = com.stonytark.magnetization.physics.inspection.FieldInspectionTracker.latest(level, ship.getUniqueId());
            helper.assertTrue(trace.sources().size() == 2, "Missing source contributions: " + trace.sources().size());
            helper.assertTrue(trace.sources().stream().allMatch(c -> c.capped()), "Cap reduction was not reported");
            helper.assertTrue(trace.force().lengthSqr() > 0 && trace.force().distanceToSqr(expected[0]) < 1e-6,
                    "Reported force differs from actual velocity injection: " + trace.force() + " / " + expected[0]);
            helper.assertTrue(trace.sources().get(1).applied().lengthSqr() == 0, "Exhausted cap reported a force that was not applied");
            helper.assertTrue(trace.torque().lengthSqr() > 0, "Off-center turning tendency missing");
            MagConfig.MAX_ACCEL_PER_TICK.set(oldCap); MagConfig.SHIP_LINEAR_DRAG.set(oldLinear); MagConfig.SHIP_ANGULAR_DRAG.set(oldAngular);
            remove(level, ship);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 160, batch = "engineeringDock")
    public static void dockUsesRelativeMotionAndPersistsItsLink(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2)).atY(160);
        level.setBlock(base, MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState(), 3);
        var dock = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, base, java.util.List.of(base),
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(base.getX(), base.getY(), base.getZ(), base.getX()+1, base.getY()+1, base.getZ()+1));
        var target = assemble(level, base.east(2), java.util.List.of(base.east(2)));
        BlockPos switchPos = new BlockPos(2,2,2);
        helper.setBlock(switchPos, MagBlocks.MAGNETIC_SWITCH.get());
        helper.startSequence().thenExecuteAfter(12, () -> {
            BlockPos anchorPos = BlockPos.containing(dock.logicalPose().transformPositionInverse(base.getCenter()));
            // Locate the anchor by the actual plot bounds after assembly.
            var plot = dock.getPlot().getBoundingBox();
            anchorPos = new BlockPos(plot.minX(), plot.minY(), plot.minZ());
            var anchor = (com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity)level.getBlockEntity(anchorPos);
            helper.assertTrue(anchor != null, "Moving anchor missing from plot");
            var tag = anchor.saveWithoutMetadata(level.registryAccess());
            tag.putUUID("BoundShip", target.getUniqueId());
            anchor.loadWithComponents(tag, level.registryAccess());
            var a = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(dock);
            var b = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(target);
            setMotion(a, new org.joml.Vector3d(6,0,0), new org.joml.Vector3d());
            setMotion(b, new org.joml.Vector3d(6,0,0), new org.joml.Vector3d());
            var sample = com.stonytark.magnetization.content.docking.DockingMeasurements.sample(level, anchorPos).reading();
            helper.assertTrue(sample.unavailable() == null && sample.speed() < 1e-6, "Common dock/ship motion was treated as approach: " + sample);
            setMotion(b, new org.joml.Vector3d(7,0,0), new org.joml.Vector3d(0,.5,0));
            sample = com.stonytark.magnetization.content.docking.DockingMeasurements.sample(level, anchorPos).reading();
            helper.assertTrue(sample.speed() > .5 && sample.spin() > .4, "Relative translation/spin was not measured");
            var sw = (com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity)helper.getBlockEntity(switchPos);
            sw.linkAnchor(anchorPos);
            helper.assertTrue(sw.signal() == 15, "Linked target-present signal missing");
            sw.cycleMode(); // settled
            helper.assertTrue(sw.signal() == 0, "Moving ship incorrectly reported settled");
            var saved = sw.saveWithoutMetadata(level.registryAccess());
            sw.loadWithComponents(saved, level.registryAccess());
            helper.assertTrue(anchorPos.equals(sw.anchorPos()) && sw.mode() == com.stonytark.magnetization.content.docking.DockingState.Mode.SETTLED,
                    "Dock link/mode did not survive serialization");
            sw.cycleMode(); // lost
            remove(level, target);
            com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity.serverTick(level, sw.getBlockPos(), sw.getBlockState(), sw);
            remove(level, dock);
        }).thenExecuteAfter(5, () -> {
            var sw = (com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity)helper.getBlockEntity(switchPos);
            helper.assertTrue(sw.signal() == 15, "Previously seen target loss did not produce redstone");
        }).thenSucceed();
    }
    private static void setMotion(dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle body, org.joml.Vector3d linear, org.joml.Vector3d angular) {
        body.addLinearAndAngularVelocity(linear.sub(body.getLinearVelocity(new org.joml.Vector3d())), angular.sub(body.getAngularVelocity(new org.joml.Vector3d())));
    }
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel assemble(net.minecraft.server.level.ServerLevel level, BlockPos base, java.util.List<BlockPos> blocks) {
        for (var p : blocks) level.setBlock(p, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        return dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, base, blocks,
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(base.getX(),base.getY(),base.getZ(),base.getX()+3,base.getY()+3,base.getZ()+1));
    }
    private static void remove(net.minecraft.server.level.ServerLevel level, dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).removeSubLevel(ship,
                dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
    }

    private static MagneticShaftBlockEntity shaft(GameTestHelper helper, BlockPos pos) {
        return (MagneticShaftBlockEntity)helper.getBlockEntity(pos);
    }
    private static void motor(GameTestHelper helper, BlockPos pos, int rpm) {
        var motor = (CreativeMotorBlockEntity)helper.getBlockEntity(pos);
        motor.generatedSpeed.setValue(rpm);
        motor.updateGeneratedRotation();
    }
}
