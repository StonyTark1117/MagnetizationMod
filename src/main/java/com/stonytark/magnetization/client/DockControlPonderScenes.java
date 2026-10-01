package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.EmitterPreset;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.content.docking.DockingState;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunRemoteItem;
import com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Real tutorial configuration and item state; motion illustrates the server physics. */
final class DockControlPonderScenes {
    static final BlockPos ANCHOR = new BlockPos(1, 1, 3);
    static final BlockPos SWITCH = new BlockPos(1, 1, 1);
    static final BlockPos LAMP = SWITCH.east();
    static final BlockPos PEER = new BlockPos(3, 1, 3);
    static final BlockPos SOURCE = new BlockPos(1, 1, 3);
    static final BlockPos DESTINATION = new BlockPos(4, 1, 3);
    static final BlockPos TRACTOR = new BlockPos(1, 1, 3);
    static final BlockPos RAIL = new BlockPos(1, 1, 2);
    static final BlockPos OTHER_RAIL = new BlockPos(1, 1, 4);
    static final UUID SHIP = UUID.fromString("cd2c6058-2f52-4f39-8cff-9ef49280f522");
    static final DockingState.Limits LIMITS = new DockingState.Limits(8, 1, .2, .1, .1, 40);

    private DockControlPonderScenes() {}

    private static void prepare(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        scene.title(definition.id(), definition.title());
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.75f);
        scene.showBasePlate();
        scene.world().showSection(util.select().fromTo(0, 1, 0, 6, 1, 6), Direction.DOWN);
    }

    private static void caption(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, String text) {
        scene.overlay().showOutlineWithText(util.select().position(pos), 100)
                .colored(PonderPalette.INPUT).text(text).pointAt(Vec3.atCenterOf(pos).add(0, 1.5, 0))
                .placeNearTarget().attachKeyFrame();
        scene.idle(110);
    }

    private static void direction(SceneBuilder scene, Vec3 start, Vec3 delta) {
        scene.overlay().showBigLine(PonderPalette.OUTPUT, start, start.add(delta), 95);
    }

    private static void bindAnchor(SceneBuilder scene, BlockPos pos) {
        scene.world().modifyBlockEntity(pos, MagneticAnchorBlockEntity.class, be -> {
            be.setRedstoneLevel(15);
            var tag = be.saveWithoutMetadata(be.getLevel().registryAccess());
            tag.putUUID("BoundShip", SHIP);
            be.loadWithComponents(tag, be.getLevel().registryAccess());
        });
    }

    /** Shipping state machine supplies the illustrated status; no live Sable ship exists in Ponder. */
    static DockingState dockingSample(int stage) {
        var state = new DockingState();
        state.update(new DockingState.Reading(SHIP, 3, 0, 0, null), LIMITS, 0);
        if (stage >= 2) state.update(new DockingState.Reading(SHIP, .5, 0, 0, null), LIMITS, 20);
        if (stage >= 3) state.update(new DockingState.Reading(SHIP, .5, 0, 0, null), LIMITS, 60);
        if (stage == 4 || stage == 5) state.update(new DockingState.Reading(SHIP, stage == 4 ? 2 : 15, 0, 0, null), LIMITS, 61);
        return state;
    }

    static DockingState.Mode dockingMode(int stage) {
        return switch (stage) {
            case 0 -> DockingState.Mode.PROXIMITY;
            case 1 -> DockingState.Mode.TARGET_PRESENT;
            case 4 -> DockingState.Mode.ANALOG_DISTANCE;
            case 5 -> DockingState.Mode.TARGET_LOST;
            default -> DockingState.Mode.SETTLED;
        };
    }

    private static void dockStatus(SceneBuilder scene, int stage) {
        var sample = dockingSample(stage);
        int signal = sample.signal(dockingMode(stage), LIMITS.range(), 0);
        scene.world().modifyBlockEntity(SWITCH, MagneticSwitchBlockEntity.class, be -> {
            while (be.mode() != dockingMode(stage)) be.cycleMode();
            var tag = be.saveWithoutMetadata(be.getLevel().registryAccess());
            tag.putInt("Signal", signal);
            tag.putString("DockReason", sample.reason().name().toLowerCase(java.util.Locale.ROOT));
            tag.putUUID("DockTarget", SHIP);
            tag.putBoolean("DockSeen", sample.seen());
            be.loadWithComponents(tag, be.getLevel().registryAccess());
        });
        scene.world().setBlock(LAMP, Blocks.REDSTONE_LAMP.defaultBlockState()
                .setValue(BlockStateProperties.LIT, signal > 0), false);
    }

    static void docking(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        scene.world().setBlock(ANCHOR, MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState()
                .setValue(BlockStateProperties.POWERED, true), false);
        scene.world().setBlock(ANCHOR.south(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(SWITCH, MagBlocks.MAGNETIC_SWITCH.get().defaultBlockState(), false);
        scene.world().setBlock(LAMP, Blocks.REDSTONE_LAMP.defaultBlockState(), false);
        bindAnchor(scene, ANCHOR);
        scene.world().setBlock(new BlockPos(4, 2, 3), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(5, 2, 3), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(4, 3, 3), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        var hull = scene.world().showIndependentSection(util.select().fromTo(4, 2, 3, 5, 3, 3), Direction.DOWN);
        direction(scene, new Vec3(4.5, 2.5, 3.5), new Vec3(-1, 0, 0));
        scene.world().moveSection(hull, new Vec3(-.5, 0, 0), 55);
        scene.idle(15);
        caption(scene, util, ANCHOR, definition.text(0));

        scene.overlay().showControls(Vec3.atCenterOf(ANCHOR), Pointing.RIGHT, 45).rightClick().whileSneaking();
        scene.idle(45);
        scene.overlay().showControls(Vec3.atCenterOf(SWITCH), Pointing.RIGHT, 90).rightClick().whileSneaking();
        scene.world().modifyBlockEntity(SWITCH, MagneticSwitchBlockEntity.class, be -> be.linkAnchor(ANCHOR));
        dockStatus(scene, 1);
        caption(scene, util, SWITCH, definition.text(1));

        scene.overlay().showControls(Vec3.atCenterOf(SWITCH), Pointing.RIGHT, 90).rightClick();
        scene.world().moveSection(hull, new Vec3(-1.7, 0, 0), 55);
        dockStatus(scene, 2);
        caption(scene, util, SWITCH, definition.text(2));
        dockStatus(scene, 3);
        scene.effects().indicateRedstone(SWITCH);
        caption(scene, util, LAMP, definition.text(3));

        scene.overlay().showControls(Vec3.atCenterOf(SWITCH), Pointing.RIGHT, 90).rightClick();
        scene.world().moveSection(hull, new Vec3(1, 0, 0), 55);
        dockStatus(scene, 4);
        caption(scene, util, SWITCH, definition.text(4));

        scene.world().moveSection(hull, new Vec3(6, 0, 0), 55);
        dockStatus(scene, 5);
        caption(scene, util, SWITCH, definition.text(5));
        scene.world().moveSection(hull, new Vec3(-7, 0, 0), 1);
        scene.world().setBlock(PEER, MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState()
                .setValue(BlockStateProperties.POWERED, true), false);
        scene.world().setBlock(PEER.south(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        bindAnchor(scene, PEER);
        dockStatus(scene, 6);
        direction(scene, Vec3.atCenterOf(ANCHOR), new Vec3(1, 1, 0));
        direction(scene, Vec3.atCenterOf(PEER), new Vec3(-1, 1, 0));
        scene.world().rotateSection(hull, 0, 20, 0, 20);
        scene.idle(20);
        scene.world().rotateSection(hull, 0, -10, 0, 20);
        scene.idle(20);
        scene.world().rotateSection(hull, 0, -6, 0, 20);
        scene.idle(20);
        scene.world().rotateSection(hull, 0, -4, 0, 20);
        caption(scene, util, PEER, definition.text(6));
    }

    static void imprint(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        scene.world().setBlock(SOURCE, MagBlocks.ELECTROMAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(DESTINATION, MagBlocks.REPULSOR_COIL.get().defaultBlockState(), false);
        scene.world().modifyBlockEntity(SOURCE, AbstractEmitterBlockEntity.class, be -> {
            be.setStrengthOverride(MagneticStrength.EXTREME); be.setRangeOverride(64);
            be.setPolarityOverride(MagneticPolarity.SOUTH);
        });
        var operator = scene.world().createEntity(level -> {
            var stand = new ArmorStand(level, 2.5, 1, 1.5);
            stand.setNoGravity(true); stand.setInvulnerable(true); stand.setShowArms(true);
            stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MagItems.IMPRINT_MODULE.get()));
            return stand;
        });
        scene.idle(15);
        caption(scene, util, SOURCE, definition.text(0));
        scene.world().modifyEntity(operator, entity -> {
            var source = (AbstractEmitterBlockEntity)entity.level().getBlockEntity(SOURCE);
            var preset = new EmitterPreset(source.configuredStrength(), source.effectivePolarity(MagneticPolarity.NORTH),
                    (int)Math.round(source.effectiveRange(source.configuredStrength())), net.minecraft.resources.ResourceLocation.parse("magnetization:electromagnet"));
            ((ArmorStand)entity).getMainHandItem().set(MagDataComponents.EMITTER_PRESET.get(), preset);
        });
        scene.overlay().showControls(Vec3.atCenterOf(SOURCE), Pointing.RIGHT, 90)
                .rightClick().whileSneaking().withItem(new ItemStack(MagItems.IMPRINT_MODULE.get()));
        caption(scene, util, SOURCE, definition.text(1));
        // Illustrate an explicitly stated destination configuration, without changing the viewer's server config.
        scene.world().modifyBlockEntity(DESTINATION, AbstractEmitterBlockEntity.class, be -> {
            be.setStrengthOverride(MagneticStrength.MEDIUM); be.setRangeOverride(16); be.setPolarityOverride(MagneticPolarity.SOUTH);
        });
        scene.overlay().showControls(Vec3.atCenterOf(DESTINATION), Pointing.RIGHT, 90)
                .rightClick().whileSneaking().withItem(new ItemStack(MagItems.IMPRINT_MODULE.get()));
        caption(scene, util, DESTINATION, definition.text(2));
        scene.world().modifyEntity(operator, entity -> ((ArmorStand)entity).getMainHandItem().remove(MagDataComponents.EMITTER_PRESET.get()));
        scene.overlay().showControls(new Vec3(2.5, 2, 1.5), Pointing.RIGHT, 90)
                .rightClick().withItem(new ItemStack(MagItems.IMPRINT_MODULE.get()));
        caption(scene, util, new BlockPos(2, 1, 1), definition.text(3));
    }

    static void tractor(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        scene.world().setBlock(TRACTOR, MagBlocks.TRACTOR_BEAM.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST), false);
        scene.world().setBlock(new BlockPos(4, 2, 3), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(4, 3, 3), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        var hull = scene.world().showIndependentSection(util.select().fromTo(4, 2, 3, 4, 3, 3), Direction.DOWN);
        scene.overlay().showControls(Vec3.atCenterOf(TRACTOR), Pointing.RIGHT, 90)
                .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.WRENCH.get()));
        direction(scene, Vec3.atCenterOf(TRACTOR), new Vec3(3, 0, 0));
        scene.idle(15);
        caption(scene, util, TRACTOR, definition.text(0));
        scene.world().setBlock(TRACTOR.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(TRACTOR, MagBlocks.TRACTOR_BEAM.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST).setValue(BlockStateProperties.POWERED, true), false);
        scene.world().modifyBlockEntity(TRACTOR, AbstractEmitterBlockEntity.class, be -> be.setRedstoneLevel(15));
        direction(scene, new Vec3(4.5, 2.5, 3.5), new Vec3(-2, 0, 0));
        scene.world().moveSection(hull, new Vec3(-1, 0, 0), 60);
        caption(scene, util, TRACTOR, definition.text(1));
        scene.world().setBlock(TRACTOR, MagBlocks.TRACTOR_BEAM.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH).setValue(BlockStateProperties.POWERED, true), false);
        scene.world().moveSection(hull, new Vec3(-2, 0, 2), 1);
        direction(scene, Vec3.atCenterOf(TRACTOR), new Vec3(0, 0, 2));
        direction(scene, new Vec3(1.5, 2.5, 5.5), new Vec3(0, 0, -1));
        scene.world().moveSection(hull, new Vec3(0, 0, -.8), 60);
        caption(scene, util, TRACTOR, definition.text(2));
        scene.world().setBlock(TRACTOR.west(), MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        direction(scene, new Vec3(1.5, 2.5, 4.7), new Vec3(0, 0, 1));
        scene.world().moveSection(hull, new Vec3(0, 0, .8), 60);
        caption(scene, util, TRACTOR, definition.text(3));
    }

    static void remote(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        scene.scaleSceneView(.60f);
        for (var breech : java.util.List.of(RAIL, OTHER_RAIL)) {
            scene.world().setBlock(breech, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                    .setValue(DirectionalBlock.FACING, Direction.EAST).setValue(BlockStateProperties.POWERED, true), false);
            scene.world().setBlock(breech.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
            for (int x = 2; x <= 5; x++) scene.world().setBlock(new BlockPos(x, 1, breech.getZ()), Blocks.COPPER_BLOCK.defaultBlockState(), false);
            scene.world().modifyBlockEntity(breech, RailgunEmitterBlockEntity.class, be -> { be.setRailLength(4); be.setRedstonePowered(true); });
        }
        scene.world().setBlock(new BlockPos(3, 2, 3), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(4, 2, 3), Blocks.IRON_BLOCK.defaultBlockState(), false);
        var hull = scene.world().showIndependentSection(util.select().fromTo(3, 2, 3, 4, 2, 3), Direction.DOWN);
        var operator = scene.world().createEntity(level -> {
            var stand = new ArmorStand(level, 3.5, 1, 1.5);
            stand.setNoGravity(true); stand.setInvulnerable(true); stand.setShowArms(true);
            return stand;
        });
        scene.idle(15);
        caption(scene, util, RAIL, definition.text(0));
        scene.overlay().showControls(Vec3.atCenterOf(RAIL), Pointing.RIGHT, 90)
                .rightClick().withItem(new ItemStack(MagItems.RAILGUN_REMOTE.get()));
        scene.world().modifyBlockEntity(RAIL, RailgunEmitterBlockEntity.class, be -> {
            var remote = new ItemStack(MagItems.RAILGUN_REMOTE.get());
            RailgunRemoteItem.bind(remote, be, be.getLevel().dimension());
            be.remoteContainer().setItem(0, remote); be.setManualMode(true);
        });
        scene.world().modifyBlockEntity(OTHER_RAIL, RailgunEmitterBlockEntity.class, be -> be.setManualMode(true));
        caption(scene, util, RAIL, definition.text(1));
        scene.world().modifyEntity(operator, entity -> {
            var be = (RailgunEmitterBlockEntity)entity.level().getBlockEntity(RAIL);
            ((ArmorStand)entity).setItemSlot(EquipmentSlot.MAINHAND, be.remoteContainer().getItem(0).copy());
            be.remoteContainer().setItem(0, ItemStack.EMPTY);
        });
        for (var breech : java.util.List.of(RAIL, OTHER_RAIL)) scene.world().modifyBlockEntity(breech,
                RailgunEmitterBlockEntity.class, be -> be.setArcState(RailgunEmitterBlockEntity.ArcState.HOLDING));
        caption(scene, util, new BlockPos(3, 2, 3), definition.text(2));
        scene.world().modifyEntity(operator, entity -> entity.setPos(3.5, 3, 3.5));
        caption(scene, util, new BlockPos(3, 2, 3), definition.text(3));
        scene.overlay().showControls(Vec3.atCenterOf(RAIL), Pointing.RIGHT, 90)
                .rightClick().withItem(new ItemStack(MagItems.RAILGUN_REMOTE.get()));
        for (var breech : java.util.List.of(RAIL, OTHER_RAIL)) scene.world().modifyBlockEntity(breech,
                RailgunEmitterBlockEntity.class, be -> be.setArcState(RailgunEmitterBlockEntity.ArcState.LAUNCHING));
        scene.world().moveSection(hull, new Vec3(2, 0, 0), 55);
        direction(scene, new Vec3(3.5, 2.5, 3.5), new Vec3(2, 0, 0));
        scene.overlay().showOutlineWithText(util.select().position(RAIL), 100)
                .colored(PonderPalette.INPUT).text(definition.text(4)).pointAt(Vec3.atCenterOf(RAIL).add(0, 1.5, 0))
                .placeNearTarget().attachKeyFrame();
        for (int tick = 1; tick <= 55; tick++) {
            final double x = 3.5 + 2.0 * tick / 55;
            scene.world().modifyEntity(operator, entity -> entity.setPos(x, 3, 3.5)); scene.idle(1);
        }
        scene.idle(55);
        for (var breech : java.util.List.of(RAIL, OTHER_RAIL)) scene.world().modifyBlockEntity(breech,
                RailgunEmitterBlockEntity.class, be -> { be.setArcState(RailgunEmitterBlockEntity.ArcState.COOLDOWN); be.unpair(); });
        scene.world().modifyEntity(operator, entity -> RailgunRemoteItem.clearBinding(((ArmorStand)entity).getMainHandItem()));
        scene.overlay().showControls(Vec3.atCenterOf(RAIL), Pointing.RIGHT, 90)
                .rightClick().whileSneaking().withItem(new ItemStack(MagItems.RAILGUN_REMOTE.get()));
        caption(scene, util, RAIL, definition.text(5));
    }
}
