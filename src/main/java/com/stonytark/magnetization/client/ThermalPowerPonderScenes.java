package com.stonytark.magnetization.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import com.stonytark.magnetization.content.gyro.GyrostabilizerBlockEntity;
import com.stonytark.magnetization.content.induction.InductionPadBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Spatial demonstrations; moving sections and charge gauges are explicitly illustrative. */
final class ThermalPowerPonderScenes {
    static final BlockPos HEAT_TARGET = new BlockPos(1, 2, 2);
    static final BlockPos GYRO = new BlockPos(2, 2, 2);
    static final BlockPos PAD = new BlockPos(1, 1, 2);
    private ThermalPowerPonderScenes() {}

    private static void prepare(SceneBuilder s, PonderSceneCatalog.Scene d, int width) {
        s.title(d.id(), d.title()); s.configureBasePlate(0, 0, width); s.showBasePlate();
        s.scaleSceneView(width == 9 ? 0.65f : 0.85f);
    }

    private static void text(SceneBuilder s, SceneBuildingUtil u, BlockPos p, String text) {
        s.overlay().showOutlineWithText(u.select().position(p), 100)
                .colored(PonderPalette.INPUT).text(text).placeNearTarget();
    }

    private static void burner(SceneBuilder s, BlockPos p, HeatLevel heat) {
        s.world().setBlock(p, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, heat), false);
    }

    static void heat(SceneBuilder s, SceneBuildingUtil u, PonderSceneCatalog.Scene d) {
        prepare(s, d, 9);
        s.world().setBlock(HEAT_TARGET, MagBlocks.PYRRHOTITE_BLOCK.get().defaultBlockState(), false);
        s.world().showSection(u.select().fromTo(0, 1, 0, 8, 4, 4), Direction.DOWN);
        s.idle(15); text(s, u, HEAT_TARGET, d.text(0)); s.idle(110);
        HeatLevel[] heats = {HeatLevel.SMOULDERING, HeatLevel.KINDLED, HeatLevel.SEETHING};
        for (int i = 0; i < heats.length; i++) {
            s.addKeyframe(); burner(s, HEAT_TARGET.below(), heats[i]); heatGauge(s, u, heats[i]);
            text(s, u, HEAT_TARGET, d.text(i + 1)); s.idle(110);
        }
        s.world().setBlock(HEAT_TARGET.below(), Blocks.AIR.defaultBlockState(), false);
        Block[] catalysts = {MagBlocks.PYRRHOTITE_CATALYST.get(), MagBlocks.ENHANCED_PYRRHOTITE_CATALYST.get(), MagBlocks.COSMIC_PYRRHOTITE_CATALYST.get()};
        for (int i = 0; i < 3; i++) {
            s.addKeyframe(); clearRelays(s);
            BlockPos p = HEAT_TARGET.east(3 + i * 2);
            s.world().setBlock(p, catalysts[i].defaultBlockState(), false); burner(s, p.above(), HeatLevel.SEETHING); heatGauge(s, u, HeatLevel.SEETHING);
            s.overlay().showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(HEAT_TARGET), Vec3.atCenterOf(p), 100);
            text(s, u, HEAT_TARGET, d.text(i + 4)); s.idle(110);
        }
        s.addKeyframe(); clearRelays(s);
        for (int distance : new int[]{3, 6}) s.world().setBlock(HEAT_TARGET.east(distance), catalysts[0].defaultBlockState(), false);
        burner(s, HEAT_TARGET.east(6).above(), HeatLevel.SEETHING);
        heatGauge(s, u, HeatLevel.NONE);
        text(s, u, HEAT_TARGET, d.text(7)); s.idle(110);
        s.addKeyframe(); clearRelays(s);
        s.world().setBlock(HEAT_TARGET.east(3), catalysts[0].defaultBlockState(), false);
        burner(s, HEAT_TARGET.east(3).above(), HeatLevel.SMOULDERING);
        s.world().setBlock(HEAT_TARGET.east(5), catalysts[1].defaultBlockState(), false);
        burner(s, HEAT_TARGET.east(5).above(), HeatLevel.SEETHING);
        heatGauge(s, u, HeatLevel.SEETHING);
        text(s, u, HEAT_TARGET, d.text(8)); s.idle(110);
        s.addKeyframe();
        for (int distance : new int[]{3, 5}) s.world().setBlock(HEAT_TARGET.east(distance).above(), Blocks.AIR.defaultBlockState(), false);
        heatGauge(s, u, HeatLevel.NONE);
        text(s, u, HEAT_TARGET, d.text(9)); s.idle(110);
    }

    private static void heatGauge(SceneBuilder s, SceneBuildingUtil u, HeatLevel heat) {
        int cells = switch (heat) { case NONE -> 0; case SMOULDERING, FADING -> 1; case KINDLED -> 3; case SEETHING -> 4; };
        for (int x = 1; x <= 4; x++) s.world().setBlock(new BlockPos(x, 1, 4),
                (x <= cells ? Blocks.BLUE_STAINED_GLASS : Blocks.AIR).defaultBlockState(), false);
        s.world().modifyBlockEntityNBT(u.select().position(HEAT_TARGET),
                com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity.class,
                tag -> tag.putString("ObservedHeat", heat.name()));
    }

    private static void clearRelays(SceneBuilder s) {
        for (int x = 4; x <= 8; x++) for (int y = 2; y <= 3; y++)
            s.world().setBlock(new BlockPos(x, y, 2), Blocks.AIR.defaultBlockState(), false);
    }

    static void gyro(SceneBuilder s, SceneBuildingUtil u, PonderSceneCatalog.Scene d) {
        prepare(s, d, 5);
        for (int x = 1; x <= 3; x++) s.world().setBlock(new BlockPos(x, 1, 2), Blocks.IRON_BLOCK.defaultBlockState(), false);
        s.world().setBlock(GYRO, MagBlocks.GYROSTABILIZER.get().defaultBlockState(), false);
        var hull = s.world().showIndependentSection(u.select().fromTo(1, 1, 1, 3, 2, 3), Direction.DOWN);
        s.world().configureCenterOfRotation(hull, Vec3.atCenterOf(GYRO.below()));
        s.idle(15);
        s.world().rotateSection(hull, 0, 35, 0, 70); s.world().moveSection(hull, new Vec3(0.5, 0, 0), 70);
        gyroText(s, d.text(0)); s.idle(110);
        s.addKeyframe(); s.world().setBlock(GYRO.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        gyroState(s, u, true, true, 0);
        s.world().moveSection(hull, new Vec3(0.7, 0, 0), 90);
        gyroText(s, d.text(1)); s.idle(110);
        s.addKeyframe(); s.world().setBlock(GYRO.east(), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        gyroState(s, u, true, true, 1000); s.world().moveSection(hull, new Vec3(-0.7, 0, 0), 90);
        gyroText(s, d.text(2)); s.idle(110);
        s.addKeyframe(); s.world().setBlock(GYRO.east(), Blocks.AIR.defaultBlockState(), false);
        gyroState(s, u, false, false, 0); s.world().rotateSection(hull, 0, 35, 0, 90);
        s.world().moveSection(hull, new Vec3(-0.5, 0, 0), 90);
        gyroText(s, d.text(3)); s.idle(110);
    }

    private static void gyroText(SceneBuilder s, String message) {
        // Independent captions avoid outlining the schematic coordinates of a moving section.
        s.overlay().showText(100).colored(PonderPalette.INPUT).text(message).independent(70);
    }

    private static void gyroState(SceneBuilder s, SceneBuildingUtil u, boolean powered, boolean stabilizing, int energy) {
        s.world().modifyBlock(GYRO, state -> state.setValue(BlockStateProperties.POWERED, powered), false);
        s.world().modifyBlockEntityNBT(u.select().position(GYRO), GyrostabilizerBlockEntity.class,
                tag -> { tag.putInt("Energy", energy); tag.putBoolean("Stabilizing", stabilizing); });
    }

    private static void inductionText(SceneBuilder s, String message) {
        s.overlay().showText(100).colored(PonderPalette.INPUT).text(message)
                .pointAt(Vec3.atCenterOf(PAD).add(0, 1.5, 0)).placeNearTarget();
    }

    static void induction(SceneBuilder s, SceneBuildingUtil u, PonderSceneCatalog.Scene d) {
        prepare(s, d, 7);
        s.world().setBlock(PAD, MagBlocks.INDUCTION_PAD.get().defaultBlockState(), false);
        s.world().showSection(u.select().fromTo(0, 1, 0, 6, 4, 5), Direction.DOWN);
        var carrier = s.world().createEntity(level -> {
            var stand = new ArmorStand(level, 2.5, 1, 2.5); stand.setNoGravity(true); stand.setInvulnerable(true);
            stand.setShowArms(true); stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT)); return stand;
        });
        s.idle(15); inductionText(s, d.text(0)); s.idle(110);
        s.addKeyframe(); s.world().setBlock(PAD.west(), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        s.world().modifyBlockEntityNBT(u.select().position(PAD), InductionPadBlockEntity.class, tag -> tag.putInt("Energy", 16000));
        inductionText(s, d.text(1)); s.idle(110);
        s.addKeyframe();
        for (int x = 2; x <= 4; x++) s.world().setBlock(new BlockPos(x, 1, 4), Blocks.LIME_STAINED_GLASS.defaultBlockState(), false);
        s.world().modifyBlockEntityNBT(u.select().position(PAD), InductionPadBlockEntity.class, tag -> tag.putInt("Energy", 8000));
        inductionText(s, d.text(2)); s.idle(110);
        s.addKeyframe(); inductionText(s, d.text(3)); s.idle(110);
        s.addKeyframe(); s.world().modifyEntity(carrier, entity -> entity.setPos(6.5, 1, 2.5));
        s.world().setBlock(PAD.west(), Blocks.AIR.defaultBlockState(), false);
        inductionText(s, d.text(4)); s.idle(110);
    }
}
