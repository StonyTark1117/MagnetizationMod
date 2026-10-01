package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import com.stonytark.magnetization.content.fluid.FluidRedstone;
import com.stonytark.magnetization.content.fluid.HardenedMrFluidBlock;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.registry.MagBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;

/** Scripted visual transitions; native server tests independently exercise the taught mechanics. */
final class MaterialControlPonderScenes {
    static final BlockPos CENTER = new BlockPos(2, 2, 2);
    static final BlockPos STAMPER = new BlockPos(1, 1, 3);
    private MaterialControlPonderScenes() {}

    private static void prepare(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        scene.title(definition.id(), definition.title());
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.scaleSceneView(0.85f);
        scene.world().showSection(util.select().fromTo(0, 1, 0, 4, 4, 4), Direction.DOWN);
    }

    private static void caption(SceneBuilder scene, SceneBuildingUtil util, BlockPos target, String message) {
        scene.overlay().showOutlineWithText(util.select().position(target), 100)
                .colored(PonderPalette.INPUT).text(message).placeNearTarget();
    }

    static void mrFluidBridge(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        for (int x : new int[]{0, 4}) {
            scene.world().setBlock(new BlockPos(x, 1, 2), Blocks.STONE_BRICKS.defaultBlockState(), false);
            scene.world().setBlock(new BlockPos(x, 2, 2), Blocks.STONE_BRICKS.defaultBlockState(), false);
        }
        for (int x = 1; x <= 3; x++) scene.world().setBlock(new BlockPos(x, 1, 2), Blocks.DIRT.defaultBlockState(), false);
        fluid(scene, false, false);
        scene.idle(15);
        caption(scene, util, CENTER, definition.text(0)); scene.idle(110);
        scene.addKeyframe();
        scene.world().setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        fluid(scene, true, true);
        scene.idle(20);
        for (int x = 1; x <= 3; x++) scene.world().setBlock(new BlockPos(x, 1, 2), Blocks.AIR.defaultBlockState(), false);
        caption(scene, util, CENTER, definition.text(1)); scene.idle(110);
        scene.addKeyframe();
        scene.world().setBlock(new BlockPos(2, 2, 4), MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        scene.overlay().showBigLine(PonderPalette.BLUE, Vec3.atCenterOf(new BlockPos(2, 2, 4)), Vec3.atCenterOf(CENTER), 40);
        scene.idle(40);
        scene.world().setBlock(new BlockPos(1, 2, 1), Blocks.AIR.defaultBlockState(), false);
        fluid(scene, true, false);
        caption(scene, util, CENTER, definition.text(2)); scene.idle(110);
        scene.addKeyframe();
        scene.world().setBlock(new BlockPos(2, 2, 4), Blocks.AIR.defaultBlockState(), false);
        fluid(scene, false, false);
        for (int x = 2; x <= 3; x++) scene.world().setBlock(new BlockPos(x, 2, 2), Blocks.AIR.defaultBlockState(), false);
        for (int x = 1; x <= 3; x++) scene.world().setBlock(new BlockPos(x, 1, 2), MagBlocks.MR_FLUID_BLOCK.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, x == 1 ? 8 : x), false);
        caption(scene, util, CENTER, definition.text(3)); scene.idle(110);
    }

    private static void fluid(SceneBuilder scene, boolean hard, boolean powered) {
        for (int x = 1; x <= 3; x++) {
            var state = hard ? MagBlocks.HARDENED_MR_FLUID.get().defaultBlockState()
                    .setValue(HardenedMrFluidBlock.SOURCE, x == 1)
                    : MagBlocks.MR_FLUID_BLOCK.get().defaultBlockState().setValue(LiquidBlock.LEVEL, x == 1 ? 0 : x);
            scene.world().setBlock(new BlockPos(x, 2, 2), state.setValue(FluidRedstone.POWER, powered ? 15 : 0), false);
        }
    }

    static void fieldStrength(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        scene.world().setBlock(CENTER, MagBlocks.SAMARIUM_COBALT_MAGNET.get().defaultBlockState(), false);
        scene.idle(15);
        caption(scene, util, CENTER, definition.text(0)); scene.idle(110);
        var neighbors = new BlockPos[]{CENTER.west(), CENTER.east(), CENTER.south()};
        for (int i = 0; i < neighbors.length; i++) {
            scene.addKeyframe();
            scene.world().setBlock(neighbors[i], MagBlocks.PERMANENT_MAGNET.get().defaultBlockState()
                    .setValue(PermanentMagnetBlock.POLARITY, MagneticPolarity.NORTH), false);
            caption(scene, util, CENTER, definition.text(i + 1)); scene.idle(110);
        }
        scene.addKeyframe();
        for (var pos : neighbors) scene.world().setBlock(pos, Blocks.AIR.defaultBlockState(), false);
        caption(scene, util, CENTER, definition.text(4)); scene.idle(110);
        for (int i = 0; i < 2; i++) {
            scene.addKeyframe();
            scene.world().setBlock(neighbors[i], MagBlocks.HEMATITE_BLOCK.get().defaultBlockState(), false);
            caption(scene, util, CENTER, definition.text(5 + i)); scene.idle(110);
        }
    }

    static void equipment(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, util, definition);
        scene.world().setBlock(STAMPER, MagBlocks.ELECTROMAGNET.get().defaultBlockState(), false);
        var north = new BlockPos(1, 1, 1);
        scene.world().setBlock(north, MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        var wearer = scene.world().createEntity(level -> {
            var stand = new ArmorStand(level, 3, 1, 1.5);
            stand.setNoGravity(true); stand.setInvulnerable(true);
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            return stand;
        });
        scene.idle(15);
        scene.overlay().showControls(Vec3.atCenterOf(STAMPER).add(0, 1, 0), Pointing.DOWN, 90)
                .rightClick().withItem(new ItemStack(Items.IRON_HELMET));
        caption(scene, util, STAMPER, definition.text(0)); scene.idle(110);
        int[] buttons = {EmitterMenu.BUTTON_POLARITY_NORTH, EmitterMenu.BUTTON_POLARITY_SOUTH, EmitterMenu.BUTTON_POLARITY_CLEAR};
        for (int i = 0; i < buttons.length; i++) {
            scene.addKeyframe();
            final int button = buttons[i];
            // Invoke the shipping slot/button handler in the Ponder world. Isolated inventory prevents
            // a tutorial from touching the real player's equipment or inventory.
            scene.world().modifyEntity(wearer, entity -> {
                var player = Minecraft.getInstance().player;
                var menu = new EmitterMenu(0, new Inventory(player), ContainerLevelAccess.create(entity.level(), STAMPER),
                        STAMPER, EmitterMenu.CAP_ARMOR | EmitterMenu.CAP_POLARITY);
                var stand = (ArmorStand) entity;
                menu.getSlot(0).set(stand.getItemBySlot(EquipmentSlot.HEAD).copy());
                menu.clickMenuButton(player, button);
                stand.setItemSlot(EquipmentSlot.HEAD, menu.armorStack().copy());
                stand.setPos(3, 1, 1.5);
            });
            final double direction = i == 1 ? -1 : 1;
            scene.overlay().showBigLine(PonderPalette.OUTPUT, new Vec3(3, 2, 1.5), new Vec3(3 + direction, 2, 1.5), 90);
            caption(scene, util, STAMPER, definition.text(i + 1));
            for (int tick = 1; tick <= 35; tick++) {
                final double x = 3 + direction * tick / 35.0;
                scene.world().modifyEntity(wearer, entity -> entity.setPos(x, 1, 1.5));
                scene.idle(1);
            }
            scene.idle(75);
        }
    }
}
