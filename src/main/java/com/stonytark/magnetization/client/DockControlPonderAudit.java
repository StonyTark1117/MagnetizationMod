package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunRemoteItem;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.stonytark.magnetization.gametest.LifecyclePresentationAudit.check;

/** Checks the native scene's configuration and item state at every rendered instruction. */
final class DockControlPonderAudit {
    private DockControlPonderAudit() {}

    private static ArmorStand operator(PonderScene scene) {
        var stands = new java.util.ArrayList<ArmorStand>();
        scene.forEachWorldEntity(ArmorStand.class, stands::add);
        check(stands.size() == 1, "Tutorial operator missing or duplicated");
        return stands.getFirst();
    }

    static void verify(PonderScene scene, String id, int stage) {
        var world = scene.getWorld();
        switch (id) {
            case "docking_signals" -> {
                var anchor = (MagneticAnchorBlockEntity)world.getBlockEntity(DockControlPonderScenes.ANCHOR);
                check(anchor != null && anchor.isPowered() && DockControlPonderScenes.SHIP.equals(anchor.boundShipId()), "Dock anchor not powered/bound");
                var sw = (MagneticSwitchBlockEntity)world.getBlockEntity(DockControlPonderScenes.SWITCH);
                check(sw != null && sw.mode() == DockControlPonderScenes.dockingMode(stage), "Dock switch mode stage " + stage);
                if (stage > 0) {
                    check(DockControlPonderScenes.ANCHOR.equals(sw.anchorPos()), "Switch linked to the wrong anchor");
                    var sample = DockControlPonderScenes.dockingSample(stage);
                    check(sw.signal() == sample.signal(sw.mode(), DockControlPonderScenes.LIMITS.range(), 0)
                            && sw.dockReason().equals(sample.reason().name().toLowerCase(java.util.Locale.ROOT)), "Dock state-machine sample differs from shown output at " + stage);
                    check(world.getBlockState(DockControlPonderScenes.LAMP).getValue(BlockStateProperties.LIT) == (sw.signal() > 0), "Dock lamp contradicts redstone output");
                    check(world.getBlockState(sw.getBlockPos()).getSignal(world, sw.getBlockPos(), Direction.UP) == sw.signal(), "Native switch redstone API differs");
                    check(world.getBlockState(sw.getBlockPos()).getAnalogOutputSignal(world, sw.getBlockPos()) == sw.signal(), "Native switch comparator API differs");
                } else check(sw.anchorPos() == null && sw.signal() == 0, "Dock switch linked before gesture");
                if (stage == 6) {
                    var peer = (MagneticAnchorBlockEntity)world.getBlockEntity(DockControlPonderScenes.PEER);
                    check(peer != null && peer.isPowered() && anchor.boundShipId().equals(peer.boundShipId()), "Cooperative anchors do not share their target");
                }
            }
            case "imprint_module" -> {
                var source = (AbstractEmitterBlockEntity)world.getBlockEntity(DockControlPonderScenes.SOURCE);
                check(source != null && source.configuredStrength() == MagneticStrength.EXTREME
                        && source.getRangeOverride() == 64 && source.getPolarityOverride() == MagneticPolarity.SOUTH, "Imprint source not configured as taught");
                var stack = operator(scene).getMainHandItem();
                check(stack.is(MagItems.IMPRINT_MODULE.get()), "Imprint not carried");
                var preset = stack.get(MagDataComponents.EMITTER_PRESET.get());
                check((preset != null) == (stage == 1 || stage == 2), "Capture/clear preset stage " + stage);
                if (preset != null) check(preset.strength() == MagneticStrength.EXTREME && preset.range() == 64
                        && preset.polarity() == MagneticPolarity.SOUTH && preset.sourceBlockId().toString().equals("magnetization:electromagnet"), "Stored imprint changed during projection");
                var target = (AbstractEmitterBlockEntity)world.getBlockEntity(DockControlPonderScenes.DESTINATION);
                check(target != null, "Imprint destination missing");
                if (stage >= 2) check(target.getStrengthOverride() == MagneticStrength.MEDIUM && target.getRangeOverride() == 16
                        && target.getPolarityOverride() == MagneticPolarity.SOUTH, "Destination clamp/clear example wrong");
                else check(target.getStrengthOverride() == null && target.getRangeOverride() == 0, "Destination changed before projection");
            }
            case "tractor_beam" -> {
                var pos = DockControlPonderScenes.TRACTOR;
                var be = (AbstractEmitterBlockEntity)world.getBlockEntity(pos);
                check(be != null && be.isPowered() == (stage > 0), "Tractor power stage " + stage);
                check(world.getBlockState(pos).getValue(DirectionalBlock.FACING) == (stage < 2 ? Direction.EAST : Direction.SOUTH), "Tractor facing stage " + stage);
                check(PolarityInverterBlock.shouldInvert(world, pos) == (stage == 3), "Tractor field not inverted at final stage");
                check(be.effectivePolarity(MagneticPolarity.SOUTH) == MagneticPolarity.SOUTH, "Default tractor polarity altered");
            }
            case "railgun_remote" -> {
                for (var pos : java.util.List.of(DockControlPonderScenes.RAIL, DockControlPonderScenes.OTHER_RAIL)) {
                    var be = (RailgunEmitterBlockEntity)world.getBlockEntity(pos);
                    check(be != null && be.isPowered() && be.railLength() == 4
                            && world.getBlockState(pos).getValue(DirectionalBlock.FACING) == Direction.EAST, "Remote rail power/geometry wrong");
                    for (int x = 2; x <= 5; x++) check(world.getBlockState(new net.minecraft.core.BlockPos(x, 1, pos.getZ())).is(Blocks.COPPER_BLOCK), "Remote rail segment missing");
                    check(be.manualMode() == (stage > 0 && stage < 5), "Remote manual pairing stage " + stage);
                    var expected = stage < 2 ? RailgunEmitterBlockEntity.ArcState.IDLE : stage < 4
                            ? RailgunEmitterBlockEntity.ArcState.HOLDING : stage == 4
                            ? RailgunEmitterBlockEntity.ArcState.LAUNCHING : RailgunEmitterBlockEntity.ArcState.COOLDOWN;
                    check(be.arcState() == expected, "Remote arc state stage " + stage);
                }
                var breech = (RailgunEmitterBlockEntity)world.getBlockEntity(DockControlPonderScenes.RAIL);
                check(breech.remoteContainer().getItem(0).isEmpty() == (stage != 1), "Remote GUI slot stage " + stage);
                var stand = operator(scene);
                var remote = stage == 1 ? breech.remoteContainer().getItem(0) : stand.getMainHandItem();
                if (stage > 0) {
                    check(remote.is(MagItems.RAILGUN_REMOTE.get()), "Bound remote missing");
                    check(stage == 5 ? RailgunRemoteItem.boundPos(remote) == null
                            : DockControlPonderScenes.RAIL.equals(RailgunRemoteItem.boundPos(remote))
                            && world.dimension().equals(RailgunRemoteItem.boundDim(remote)), "Remote binding/clear stage " + stage);
                }
                if (stage >= 3) check(Math.abs(stand.getY() - 3) < .01 && Math.abs(stand.getZ() - 3.5) < .01, "Operator did not board held ship");
                if (stage == 5) check(Math.abs(stand.getX() - 5.5) < .01, "Rider did not travel with launch");
            }
            default -> throw new IllegalArgumentException("Unknown dock/control scene " + id);
        }
    }
}
