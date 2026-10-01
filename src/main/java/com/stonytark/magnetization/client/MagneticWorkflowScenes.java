package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.content.repulsor.RepulsorCoilBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Spatial tutorials with real block configuration and explicitly illustrated movement. */
final class MagneticWorkflowScenes {
    static final BlockPos WORLD_MAGNET = new BlockPos(1, 1, 3);
    static final BlockPos SHIP_MARKER = new BlockPos(4, 2, 3);
    static final BlockPos SHIP_MAGNET = new BlockPos(4, 1, 4);
    static final BlockPos EXCAVATOR = new BlockPos(3, 3, 2);
    static final BlockPos BARREL = EXCAVATOR.west();

    private MagneticWorkflowScenes() {}

    private static void prepare(final SceneBuilder scene, final PonderSceneCatalog.Scene definition) {
        scene.title(definition.id(), definition.title());
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.75f);
        scene.showBasePlate();
    }

    private static void show(final SceneBuilder scene, final SceneBuildingUtil util,
                             final BlockPos from, final BlockPos to) {
        scene.world().showSection(util.select().fromTo(from, to), Direction.DOWN);
        scene.idle(12);
    }

    private static void explain(final SceneBuilder scene, final SceneBuildingUtil util,
                                final BlockPos point, final String message) {
        scene.overlay().showOutlineWithText(util.select().position(point), 80)
                .colored(PonderPalette.INPUT).text(message).placeNearTarget().attachKeyFrame();
        scene.idle(90);
    }

    private static void arrow(final SceneBuilder scene, final BlockPos point, final Vec3 direction) {
        scene.overlay().showBigLine(PonderPalette.OUTPUT, Vec3.atCenterOf(point),
                Vec3.atCenterOf(point).add(direction), 75);
    }

    static void basics(final SceneBuilder scene, final SceneBuildingUtil util,
                       final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        scene.world().setBlock(WORLD_MAGNET, MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(WORLD_MAGNET.above(), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(4, 1, 3), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(SHIP_MARKER, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        show(scene, util, WORLD_MAGNET, WORLD_MAGNET.above());
        final var hull = scene.world().showIndependentSection(
                util.select().fromTo(3, 1, 3, 5, 2, 4), Direction.DOWN);
        arrow(scene, SHIP_MARKER, new Vec3(1, 0, 0));
        scene.world().moveSection(hull, new Vec3(0.6, 0, 0), 55);
        explain(scene, util, WORLD_MAGNET, definition.text(0));

        scene.overlay().showControls(Vec3.atCenterOf(WORLD_MAGNET), Pointing.DOWN, 70).rightClick();
        scene.world().setBlock(WORLD_MAGNET, MagBlocks.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.POLARITY, MagneticPolarity.SOUTH), false);
        scene.world().setBlock(WORLD_MAGNET.above(), Blocks.RED_STAINED_GLASS.defaultBlockState(), false);
        arrow(scene, SHIP_MARKER, new Vec3(-1, 0, 0));
        scene.world().moveSection(hull, new Vec3(-0.6, 0, 0), 55);
        explain(scene, util, WORLD_MAGNET, definition.text(1));

        scene.world().setBlock(WORLD_MAGNET, MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(WORLD_MAGNET.north(), MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        show(scene, util, WORLD_MAGNET.north(), WORLD_MAGNET.north());
        arrow(scene, SHIP_MARKER, new Vec3(-1, 0, 0));
        scene.world().moveSection(hull, new Vec3(-0.3, 0, 0), 55);
        explain(scene, util, WORLD_MAGNET.north(), definition.text(2));

        scene.world().setBlock(WORLD_MAGNET.west(), MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        scene.world().setBlock(WORLD_MAGNET.above(), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        show(scene, util, WORLD_MAGNET.west(), WORLD_MAGNET.west());
        arrow(scene, SHIP_MARKER, new Vec3(1, 0, 0));
        scene.world().moveSection(hull, new Vec3(0.3, 0, 0), 55);
        explain(scene, util, WORLD_MAGNET.west(), definition.text(3));

        scene.world().setBlock(new BlockPos(3, 1, 3), MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        scene.world().setBlock(SHIP_MARKER, Blocks.RED_STAINED_GLASS.defaultBlockState(), false);
        arrow(scene, SHIP_MARKER, new Vec3(-1, 0, 0));
        scene.world().moveSection(hull, new Vec3(-0.3, 0, 0), 55);
        explain(scene, util, SHIP_MARKER, definition.text(4));

        scene.world().setBlock(new BlockPos(5, 1, 3), MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        scene.world().setBlock(SHIP_MARKER, Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        arrow(scene, SHIP_MARKER, new Vec3(1, 0, 0));
        scene.world().moveSection(hull, new Vec3(0.3, 0, 0), 55);
        explain(scene, util, SHIP_MARKER, definition.text(5));

        scene.world().setBlock(SHIP_MAGNET, MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        scene.idle(20);
        scene.overlay().showControls(Vec3.atCenterOf(SHIP_MAGNET), Pointing.DOWN, 70).rightClick();
        scene.world().setBlock(SHIP_MAGNET, MagBlocks.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(PermanentMagnetBlock.POLARITY, MagneticPolarity.SOUTH), false);
        explain(scene, util, SHIP_MAGNET, definition.text(6));
    }

    static void excavator(final SceneBuilder scene, final SceneBuildingUtil util,
                          final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        scene.world().setBlock(EXCAVATOR, MagBlocks.MAGNETIC_EXCAVATOR.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN), false);
        scene.world().setBlock(BARREL, Blocks.BARREL.defaultBlockState(), false);
        for (int x : new int[]{2, 4}) {
            scene.world().setBlock(new BlockPos(x, 1, 2), Blocks.IRON_ORE.defaultBlockState(), false);
            scene.world().setBlock(new BlockPos(x, 2, 2), Blocks.STONE.defaultBlockState(), false);
            show(scene, util, new BlockPos(x, 1, 2), new BlockPos(x, 2, 2));
        }
        show(scene, util, BARREL, EXCAVATOR);
        scene.overlay().showControls(Vec3.atCenterOf(EXCAVATOR), Pointing.DOWN, 70)
                .rightClick().withItem(new ItemStack(com.simibubi.create.AllItems.WRENCH.get()));
        arrow(scene, EXCAVATOR, new Vec3(0, -2, 0));
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, EXCAVATOR,
                new net.minecraft.world.phys.AABB(1, 1, 0, 6, 2, 5), 75);
        explain(scene, util, EXCAVATOR, definition.text(0));

        scene.overlay().showControls(Vec3.atCenterOf(EXCAVATOR), Pointing.DOWN, 70).rightClick();
        scene.world().modifyBlockEntity(EXCAVATOR, MagneticExcavatorBlockEntity.class, be -> {
            be.setRangeOverride(4); be.setStrengthOverride(MagneticStrength.STRONG); be.setInFlightCapOverride(2);
        });
        explain(scene, util, EXCAVATOR, definition.text(1));

        scene.overlay().showControls(Vec3.atCenterOf(EXCAVATOR), Pointing.DOWN, 70)
                .rightClick().withItem(new ItemStack(Items.REDSTONE));
        scene.world().modifyBlockEntity(EXCAVATOR, MagneticExcavatorBlockEntity.class,
                be -> be.getRedstoneFuelSlot().setItem(0, new ItemStack(Items.REDSTONE)));
        scene.effects().indicateRedstone(EXCAVATOR);
        explain(scene, util, EXCAVATOR, definition.text(2));

        final var pulls = new java.util.ArrayList<ElementLink<WorldSectionElement>>();
        for (int x : new int[]{2, 4}) {
            final BlockPos ore = new BlockPos(x, 1, 2);
            scene.world().destroyBlock(ore.above());
            final var pull = scene.world().showIndependentSection(util.select().position(ore), Direction.UP);
            pulls.add(pull);
            scene.world().moveSection(pull, new Vec3(x == 2 ? 0.5 : -0.5, 1.5, 0), 60);
        }
        explain(scene, util, EXCAVATOR, definition.text(3));

        // Populate the real container as the tutorial's arrival outcome; Ponder does not run Sable mining.
        for (final var pull : pulls) scene.world().hideIndependentSection(pull, Direction.UP);
        for (int x : new int[]{2, 4}) scene.world().setBlock(new BlockPos(x, 1, 2), Blocks.AIR.defaultBlockState(), false);
        scene.world().modifyBlockEntity(BARREL, BarrelBlockEntity.class,
                be -> be.setItem(0, new ItemStack(Items.RAW_IRON, 2)));
        arrow(scene, EXCAVATOR, new Vec3(-1, 0, 0));
        scene.overlay().showControls(Vec3.atCenterOf(BARREL), Pointing.DOWN, 70)
                .withItem(new ItemStack(Items.RAW_IRON, 2));
        explain(scene, util, BARREL, definition.text(4));
        explain(scene, util, EXCAVATOR, definition.text(5));
    }

    static void transport(final SceneBuilder scene, final SceneBuildingUtil util,
                          final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        for (int x = 1; x <= 3; x++) {
            final BlockPos coil = new BlockPos(x, 1, 3);
            scene.world().setBlock(coil, MagBlocks.REPULSOR_COIL.get().defaultBlockState()
                    .setValue(DirectionalBlock.FACING, Direction.UP).setValue(BlockStateProperties.POWERED, true), false);
            scene.world().setBlock(coil.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
            scene.world().modifyBlockEntity(coil, RepulsorCoilBlockEntity.class, be -> be.setRedstoneLevel(15));
        }
        show(scene, util, new BlockPos(1, 1, 2), new BlockPos(3, 1, 3));
        final BlockPos craft = new BlockPos(1, 2, 3);
        scene.world().setBlock(craft, Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(craft.above(), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        final var hull = scene.world().showIndependentSection(util.select().fromTo(craft, craft.above()), Direction.UP);
        arrow(scene, craft, new Vec3(0, 1, 0));
        explain(scene, util, new BlockPos(2, 1, 3), definition.text(0));

        for (int x = 1; x <= 3; x++) {
            final BlockPos coil = new BlockPos(x, 1, 3);
            if (x == 3) scene.overlay().showControls(Vec3.atCenterOf(coil), Pointing.DOWN, 70)
                    .rightClick().withItem(new ItemStack(MagItems.VECTOR_CORE.get()));
            scene.world().modifyBlockEntity(coil, RepulsorCoilBlockEntity.class, be -> {
                be.setVectorCore(true);
                while (be.thrustDirection() != Direction.EAST) be.cycleThrustDir();
            });
        }
        arrow(scene, craft, new Vec3(2, 0, 0));
        explain(scene, util, new BlockPos(2, 1, 3), definition.text(1));

        scene.world().moveSection(hull, new Vec3(2, 0, 0), 55);
        explain(scene, util, new BlockPos(2, 1, 3), definition.text(2));

        for (int x = 4; x <= 6; x++) scene.world().setBlock(new BlockPos(x, 1, 3), Blocks.COPPER_BLOCK.defaultBlockState(), false);
        show(scene, util, new BlockPos(4, 1, 3), new BlockPos(6, 1, 3));
        // Shorter equal-duration motions make the decreasing speed visible without claiming real physics.
        scene.world().moveSection(hull, new Vec3(1, 0, 0), 25);
        scene.idle(25);
        scene.world().moveSection(hull, new Vec3(0.5, 0, 0), 25);
        scene.idle(25);
        scene.world().moveSection(hull, new Vec3(0.2, 0, 0), 25);
        arrow(scene, new BlockPos(5, 2, 3), new Vec3(-1, 0, 0));
        explain(scene, util, new BlockPos(5, 1, 3), definition.text(3));
    }
}
