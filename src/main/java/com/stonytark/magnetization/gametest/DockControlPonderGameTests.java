package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.docking.DockingMeasurements;
import com.stonytark.magnetization.content.item.ImprintModuleInteraction;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/** Native server interactions behind the docking/control tutorials; no Ponder script is invoked. */
@GameTestHolder("magnetization_regressions")
@PrefixGameTestTemplate(false)
public final class DockControlPonderGameTests {
    private DockControlPonderGameTests() {}

    @GameTest(template = "empty", batch = "ponder_imprint", timeoutTicks = 60)
    public static void imprintCapturesConfigurationClampsAndClears(GameTestHelper h) {
        var oldStrength = MagConfig.REPULSOR_MAX_STRENGTH.get();
        int oldRange = MagConfig.REPULSOR_MAX_RANGE.get();
        var sourcePos = new BlockPos(1, 80, 1);
        var targetPos = new BlockPos(3, 80, 1);
        try {
            MagConfig.REPULSOR_MAX_STRENGTH.set(MagneticStrength.MEDIUM);
            MagConfig.REPULSOR_MAX_RANGE.set(16);
            h.setBlock(sourcePos, MagBlocks.ELECTROMAGNET.get());
            h.setBlock(targetPos, MagBlocks.REPULSOR_COIL.get());
            var source = (AbstractEmitterBlockEntity)h.getBlockEntity(sourcePos);
            source.setStrengthOverride(MagneticStrength.EXTREME);
            source.setRangeOverride(64); source.setPolarityOverride(MagneticPolarity.SOUTH); source.setRedstoneLevel(1);
            var player = h.makeMockPlayer(GameType.CREATIVE);
            var module = new ItemStack(MagItems.IMPRINT_MODULE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, module); player.setShiftKeyDown(true);
            var capture = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, h.absolutePos(sourcePos),
                    new BlockHitResult(Vec3.atCenterOf(h.absolutePos(sourcePos)), Direction.UP, h.absolutePos(sourcePos), false));
            ImprintModuleInteraction.onImprintRightClickEmitter(capture);
            var preset = module.get(MagDataComponents.EMITTER_PRESET.get());
            h.assertTrue(capture.isCanceled() && preset != null && preset.strength() == MagneticStrength.EXTREME
                    && preset.polarity() == MagneticPolarity.SOUTH && preset.range() == 64
                    && preset.sourceBlockId().toString().equals("magnetization:electromagnet"), "Native capture stored throttled/missing configuration");
            var project = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, h.absolutePos(targetPos),
                    new BlockHitResult(Vec3.atCenterOf(h.absolutePos(targetPos)), Direction.UP, h.absolutePos(targetPos), false));
            ImprintModuleInteraction.onImprintRightClickEmitter(project);
            var target = (AbstractEmitterBlockEntity)h.getBlockEntity(targetPos);
            h.assertTrue(project.isCanceled() && target.getStrengthOverride() == MagneticStrength.MEDIUM
                    && target.getRangeOverride() == 16 && target.getPolarityOverride() == MagneticPolarity.SOUTH,
                    "Cross-emitter projection did not respect destination caps");
            h.assertTrue(preset.equals(module.get(MagDataComponents.EMITTER_PRESET.get())), "Projection consumed/changed stored preset");
            player.setShiftKeyDown(false);
            module.getItem().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
            h.assertTrue(!module.has(MagDataComponents.EMITTER_PRESET.get()) && target.getRangeOverride() == 16
                    && target.getPolarityOverride() == MagneticPolarity.SOUTH, "Air clear failed or altered destination");
            h.succeed();
        } finally {
            MagConfig.REPULSOR_MAX_STRENGTH.set(oldStrength); MagConfig.REPULSOR_MAX_RANGE.set(oldRange);
            h.setBlock(sourcePos, Blocks.AIR); h.setBlock(targetPos, Blocks.AIR);
        }
    }

    @GameTest(template = "empty", batch = "ponder_tractor", timeoutTicks = 60)
    public static void nativeTractorFacingAndInverterReverseForce(GameTestHelper h) {
        var pos = new BlockPos(2, 80, 2);
        h.setBlock(pos.south(), Blocks.REDSTONE_BLOCK);
        for (var facing : Direction.values()) {
            h.setBlock(pos, MagBlocks.TRACTOR_BEAM.get().defaultBlockState().setValue(DirectionalBlock.FACING, facing));
            var be = (AbstractEmitterBlockEntity)h.getBlockEntity(pos);
            be.setRedstoneLevel(15);
            AbstractEmitterBlockEntity.serverTick(h.getLevel(), h.absolutePos(pos), be.getBlockState(), be);
            var field = be.currentField();
            var axis = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            h.assertTrue(field != null && field.shape() == MagneticField.Shape.DIRECTIONAL && field.axis().equals(axis)
                    && field.polarity() == MagneticPolarity.SOUTH, "Native tractor field differs from facing " + facing);
            var sample = field.origin().add(axis.scale(2));
            var pull = FieldApplicator.forceAt(field, sample);
            h.assertTrue(pull.dot(axis) < -1e-6 && FieldApplicator.forceAt(field, field.origin().subtract(axis.scale(2))).equals(Vec3.ZERO),
                    "Default tractor force is not opposite facing, or acts behind emitter");
            h.setBlock(pos.above(), MagBlocks.POLARITY_INVERTER.get());
            AbstractEmitterBlockEntity.serverTick(h.getLevel(), h.absolutePos(pos), be.getBlockState(), be);
            var flipped = be.currentField();
            h.assertTrue(flipped != null && flipped.axis().equals(axis) && flipped.polarity() == MagneticPolarity.NORTH
                    && FieldApplicator.forceAt(flipped, sample).dot(axis) > 1e-6, "Inverter failed to reverse native tractor force: " + flipped);
            h.setBlock(pos.above(), Blocks.AIR);
            h.setBlock(pos, Blocks.AIR);
        }
        h.setBlock(pos.south(), Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "ponder_cooperative_dock", timeoutTicks = 100)
    public static void anchorsCaptureCooperateAndSwitchMeasuresDistance(GameTestHelper h) {
        var level = h.getLevel();
        var base = h.absolutePos(new BlockPos(2, 2, 2)).atY(220);
        var targetBlock = base.east(3);
        level.setBlock(targetBlock, Blocks.IRON_BLOCK.defaultBlockState(), 3);
        var ship = SubLevelAssemblyHelper.assembleBlocks(level, targetBlock, java.util.List.of(targetBlock),
                new BoundingBox3i(targetBlock.getX(), 220, targetBlock.getZ(), targetBlock.getX()+1, 221, targetBlock.getZ()+1));
        level.setBlock(base, MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState(), 3);
        level.setBlock(base.south(2), MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState(), 3);
        level.setBlock(base.north(2), MagBlocks.MAGNETIC_SWITCH.get().defaultBlockState(), 3);
        h.runAfterDelay(12, () -> {
            try {
                var anchor = (MagneticAnchorBlockEntity)level.getBlockEntity(base);
                var peer = (MagneticAnchorBlockEntity)level.getBlockEntity(base.south(2));
                anchor.setRangeOverride(8); peer.setRangeOverride(8);
                anchor.setRedstoneLevel(15);
                AbstractEmitterBlockEntity.serverTick(level, base, anchor.getBlockState(), anchor);
                h.assertTrue(ship.getUniqueId().equals(anchor.boundShipId()), "Native anchor did not capture nearest ship");
                var body = RigidBodyHandle.of(ship);
                body.addLinearAndAngularVelocity(new Vector3d().sub(body.getLinearVelocity(new Vector3d())),
                        new Vector3d(0, 1, 0).sub(body.getAngularVelocity(new Vector3d())));
                AbstractEmitterBlockEntity.serverTick(level, base, anchor.getBlockState(), anchor);
                h.assertTrue(Math.abs(body.getAngularVelocity(new Vector3d()).y - 1) < .01, "Solo anchor incorrectly applied cooperative damping");
                peer.setRedstoneLevel(15);
                AbstractEmitterBlockEntity.serverTick(level, peer.getBlockPos(), peer.getBlockState(), peer);
                h.assertTrue(ship.getUniqueId().equals(peer.boundShipId()), "Peer did not capture same ship");
                h.assertTrue(Math.abs(body.getAngularVelocity(new Vector3d()).y - .7) < .01, "Cooperative anchor did not damp native angular velocity by 30 percent");
                AbstractEmitterBlockEntity.serverTick(level, peer.getBlockPos(), peer.getBlockState(), peer);
                h.assertTrue(Math.abs(body.getAngularVelocity(new Vector3d()).y - .7) < .01, "Cooperative damping ignored its tick throttle");
                var sw = (MagneticSwitchBlockEntity)level.getBlockEntity(base.north(2));
                sw.linkAnchor(base);
                h.assertTrue(sw.signal() == 15, "Captured target did not produce PRESENT output");
                sw.cycleMode(); // settled; spinning target
                h.assertTrue(sw.signal() == 0, "Spinning target incorrectly reported settled");
                sw.cycleMode(); // lost
                h.assertTrue(sw.signal() == 0, "Present target incorrectly reported lost");
                sw.cycleMode(); // analog distance
                var sample = DockingMeasurements.sample(level, base);
                int expected = (int)Math.round(15 * (1 - sample.reading().distance()/sample.range()));
                h.assertTrue(sw.signal() == expected && expected > 0 && expected < 15, "Native distance output differs from measured hull distance");
                anchor.setRedstoneLevel(0);
                AbstractEmitterBlockEntity.serverTick(level, base, anchor.getBlockState(), anchor);
                h.assertTrue(ship.getUniqueId().equals(anchor.boundShipId()), "Powering off released capture binding");
                h.succeed();
            } finally {
                SubLevelContainer.getContainer(level).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                level.setBlock(base, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(base.south(2), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(base.north(2), Blocks.AIR.defaultBlockState(), 3);
            }
        });
    }
}
