package com.stonytark.magnetization.compat.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.stonytark.magnetization.registry.MagBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ponder storyboards for the Magnetization addon. Scenes are pure
 * programmatic — the matching {@code .nbt} files in
 * {@code assets/magnetization/ponder/} are intentionally empty (single-cell air),
 * with all visible blocks placed via {@code world().setBlock(...)}.
 *
 * <p>Coordinate convention: scenes work in a {@code 5×3×5} (or similar) base plate
 * starting at (0, 1, 0). Y=0 is the underplate, Y=1 is the platform surface,
 * Y=2 is the layer above. Treat (cx, 1, cz) where cx,cz = size/2 as the focal cell.
 */
public final class MagPonderScenes {

    private MagPonderScenes() {}

    public static void register(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(MagBlocks.MAGNETIC_ANCHOR.getId(), "magnetic_anchor", MagPonderScenes::magneticAnchor);
        helper.addStoryBoard(MagBlocks.ELECTROMAGNET.getId(), "electromagnet", MagPonderScenes::electromagnet);
        helper.addStoryBoard(MagBlocks.REPULSOR_COIL.getId(), "repulsor_coil", MagPonderScenes::repulsorCoil);
        helper.addStoryBoard(MagBlocks.PERMANENT_MAGNET.getId(), "permanent_magnet", MagPonderScenes::permanentMagnet);
        helper.addStoryBoard(MagBlocks.KINETIC_ELECTROMAGNET.getId(), "kinetic_electromagnet", MagPonderScenes::kineticElectromagnet);
        helper.addStoryBoard(MagBlocks.TRACTOR_BEAM.getId(), "tractor_beam", MagPonderScenes::tractorBeam);
        helper.addStoryBoard(MagBlocks.MAGNETIC_SWITCH.getId(), "magnetic_switch", MagPonderScenes::magneticSwitch);
        helper.addStoryBoard(MagBlocks.MAGNETIC_EXCAVATOR.getId(), "magnetic_excavator", MagPonderScenes::magneticExcavator);
        helper.addStoryBoard(MagBlocks.POLARITY_INVERTER.getId(), "polarity_inverter", MagPonderScenes::polarityInverter);
    }

    // ---------------- Magnetic Anchor ----------------

    public static void magneticAnchor(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("magnetic_anchor", "Locking ships in place with the Magnetic Anchor");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos anchorPos = util.grid().at(2, 1, 2);
        final BlockPos leverPos = util.grid().at(0, 1, 2);
        final BlockPos shipPos = util.grid().at(2, 2, 4);

        // Phase 1: place the anchor + lever
        scene.world().setBlock(anchorPos,
                MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState(), false);
        scene.world().setBlock(leverPos,
                Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().fromTo(0, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The Magnetic Anchor binds the first ship to enter its range")
                .pointAt(util.vector().topOf(anchorPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // Phase 2: introduce a stand-in for a ship (independent section, can drift)
        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60)
                .text("A nearby ship drifts unrestrained")
                .pointAt(util.vector().centerOf(shipPos))
                .placeNearTarget();
        scene.world().moveSection(ship, new Vec3(0.0, 0.0, -0.6), 30);
        scene.idle(40);
        scene.world().moveSection(ship, new Vec3(0.0, 0.0, 0.6), 30);
        scene.idle(40);

        // Phase 3: power the anchor — ship locks in place
        scene.world().toggleRedstonePower(util.select().fromTo(0, 1, 2, 2, 1, 2));
        scene.world().modifyBlock(anchorPos,
                s -> s.hasProperty(BlockStateProperties.POWERED) ? s.setValue(BlockStateProperties.POWERED, true) : s,
                false);
        scene.effects().indicateRedstone(leverPos);
        scene.idle(10);

        scene.overlay().showText(80)
                .text("Powered, the anchor latches onto the closest ship")
                .pointAt(util.vector().topOf(anchorPos))
                .placeNearTarget()
                .colored(PonderPalette.RED)
                .attachKeyFrame();
        scene.effects().indicateSuccess(anchorPos);
        scene.idle(20);

        // Phase 4: highlight the anchor's range
        scene.overlay().chaseBoundingBoxOutline(
                PonderPalette.BLUE,
                "anchor-range",
                new AABB(anchorPos).inflate(2.5d),
                100);
        scene.overlay().showText(80)
                .text("Once locked, the binding persists across reloads — until power is cut")
                .pointAt(util.vector().centerOf(shipPos))
                .placeNearTarget();
        scene.idle(90);

        // Phase 5: cut power, ship is free again
        scene.world().toggleRedstonePower(util.select().fromTo(0, 1, 2, 2, 1, 2));
        scene.world().modifyBlock(anchorPos,
                s -> s.hasProperty(BlockStateProperties.POWERED) ? s.setValue(BlockStateProperties.POWERED, false) : s,
                false);
        scene.effects().indicateRedstone(leverPos);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("Cutting power releases the bond")
                .pointAt(util.vector().topOf(anchorPos))
                .placeNearTarget();
        scene.world().moveSection(ship, new Vec3(0.0, 0.0, 0.8), 30);
        scene.idle(40);

        scene.markAsFinished();
    }

    // ---------------- Electromagnet ----------------

    public static void electromagnet(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("electromagnet", "Pulling ships with the Electromagnet");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos emitterPos = util.grid().at(2, 1, 2);
        final BlockPos leverPos = util.grid().at(0, 1, 2);
        final BlockPos shipPos = util.grid().at(2, 2, 4);

        scene.world().setBlock(emitterPos,
                MagBlocks.ELECTROMAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(leverPos,
                Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().fromTo(0, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("The Electromagnet exerts an omnidirectional pull when powered")
                .pointAt(util.vector().topOf(emitterPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.DOWN);
        scene.idle(10);

        // Toggle on
        scene.world().toggleRedstonePower(util.select().fromTo(0, 1, 2, 2, 1, 2));
        scene.world().modifyBlock(emitterPos,
                s -> s.hasProperty(BlockStateProperties.POWERED) ? s.setValue(BlockStateProperties.POWERED, true) : s,
                false);
        scene.effects().indicateRedstone(leverPos);
        scene.idle(5);

        scene.overlay().showText(80)
                .text("Ships and ferromagnetic items are pulled toward it")
                .pointAt(util.vector().centerOf(shipPos))
                .placeNearTarget();
        // ship snaps inward
        scene.world().moveSection(ship, new Vec3(0.0, -1.0, -1.5), 35);
        scene.effects().indicateSuccess(emitterPos);
        scene.idle(45);

        scene.overlay().chaseBoundingBoxOutline(
                PonderPalette.RED,
                "field",
                new AABB(emitterPos).inflate(3.5d),
                100);
        scene.overlay().showText(80)
                .text("Range and strength scale with the configured power level")
                .pointAt(util.vector().topOf(emitterPos))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    // ---------------- Repulsor Coil ----------------

    public static void repulsorCoil(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("repulsor_coil", "Pushing ships with the Repulsor Coil");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos coilPos = util.grid().at(2, 1, 2);
        final BlockPos leverPos = util.grid().at(0, 1, 2);
        final BlockPos shipPos = util.grid().at(2, 3, 2);

        scene.world().setBlock(coilPos,
                MagBlocks.REPULSOR_COIL.get().defaultBlockState(), false);
        scene.world().setBlock(leverPos,
                Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().fromTo(0, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("The Repulsor Coil pushes ships outward in a 45° cone along its facing")
                .pointAt(util.vector().topOf(coilPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.UP);
        scene.idle(10);

        scene.world().toggleRedstonePower(util.select().fromTo(0, 1, 2, 2, 1, 2));
        scene.world().modifyBlock(coilPos,
                s -> s.hasProperty(BlockStateProperties.POWERED) ? s.setValue(BlockStateProperties.POWERED, true) : s,
                false);
        scene.effects().indicateRedstone(leverPos);
        scene.idle(5);

        scene.overlay().showText(70)
                .text("Pointed up, it makes a hover pad")
                .pointAt(util.vector().centerOf(shipPos))
                .placeNearTarget();
        // small hover bounce
        scene.world().moveSection(ship, new Vec3(0.0, 0.7, 0.0), 25);
        scene.idle(30);
        scene.world().moveSection(ship, new Vec3(0.0, -0.3, 0.0), 20);
        scene.idle(25);
        scene.world().moveSection(ship, new Vec3(0.0, 0.4, 0.0), 20);
        scene.idle(30);

        scene.overlay().showText(70)
                .text("Place sideways to line a propulsion tunnel")
                .pointAt(util.vector().topOf(coilPos))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();
    }

    // ---------------- Permanent Magnet ----------------

    public static void permanentMagnet(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("permanent_magnet", "Building tracks with Permanent Magnets");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos magnetA = util.grid().at(1, 1, 2);
        final BlockPos magnetB = util.grid().at(3, 1, 2);
        final BlockPos shipPos = util.grid().at(2, 2, 2);

        scene.world().setBlock(magnetA,
                MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(magnetB,
                MagBlocks.PERMANENT_MAGNET.get().defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Permanent Magnets emit a weak field with no power required")
                .pointAt(util.vector().topOf(magnetA))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(70)
                .text("Right-click flips the polarity")
                .pointAt(util.vector().topOf(magnetB))
                .placeNearTarget();
        scene.effects().indicateSuccess(magnetB);
        scene.idle(80);

        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.UP);
        scene.idle(10);

        // ship glides between opposing-pole magnets
        scene.world().moveSection(ship, new Vec3(-0.7, 0.0, 0.0), 30);
        scene.idle(35);
        scene.world().moveSection(ship, new Vec3(1.4, 0.0, 0.0), 35);
        scene.idle(40);
        scene.world().moveSection(ship, new Vec3(-0.7, 0.0, 0.0), 30);
        scene.idle(35);

        scene.overlay().chaseBoundingBoxOutline(
                PonderPalette.BLUE,
                "track",
                new AABB(magnetA).expandTowards(2.0, 1.0, 0.0),
                120);
        scene.overlay().showText(80)
                .text("Pair opposing magnets between world and ship to build a propulsion track")
                .pointAt(util.vector().centerOf(shipPos))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    // ---------------- Kinetic Electromagnet ----------------

    public static void kineticElectromagnet(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("kinetic_electromagnet", "Spinning the Kinetic Electromagnet with rotational force");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos emitterPos = util.grid().at(2, 1, 2);
        final BlockPos shipPos = util.grid().at(2, 2, 4);

        scene.world().setBlock(emitterPos,
                MagBlocks.KINETIC_ELECTROMAGNET.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(emitterPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("The Kinetic Electromagnet runs on Create rotation rather than redstone")
                .pointAt(util.vector().topOf(emitterPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(70)
                .text("Strength scales with RPM — faster shaft, stronger pull")
                .pointAt(util.vector().topOf(emitterPos))
                .placeNearTarget();
        scene.world().moveSection(ship, new Vec3(0.0, -1.0, -1.6), 35);
        scene.effects().rotationSpeedIndicator(emitterPos);
        scene.effects().indicateSuccess(emitterPos);
        scene.idle(45);

        scene.overlay().chaseBoundingBoxOutline(
                PonderPalette.RED,
                "field",
                new AABB(emitterPos).inflate(3.5d),
                100);
        scene.overlay().showText(80)
                .text("No redstone control — stop the shaft to stop the field")
                .pointAt(util.vector().centerOf(emitterPos))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    // ---------------- Tractor Beam ----------------

    public static void tractorBeam(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("tractor_beam", "Pulling along a single direction with the Tractor Beam");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos beamPos = util.grid().at(2, 1, 2);
        final BlockPos leverPos = util.grid().at(0, 1, 2);
        final BlockPos shipPos = util.grid().at(2, 2, 4);

        scene.world().setBlock(beamPos,
                MagBlocks.TRACTOR_BEAM.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.SOUTH), false);
        scene.world().setBlock(leverPos,
                Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, false), false);
        scene.world().showSection(util.select().fromTo(0, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("The Tractor Beam pulls ships along its facing direction")
                .pointAt(util.vector().blockSurface(beamPos, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.DOWN);
        scene.idle(10);

        scene.world().toggleRedstonePower(util.select().fromTo(0, 1, 2, 2, 1, 2));
        scene.world().modifyBlock(beamPos,
                s -> s.hasProperty(BlockStateProperties.POWERED) ? s.setValue(BlockStateProperties.POWERED, true) : s,
                false);
        scene.effects().indicateRedstone(leverPos);
        scene.idle(5);

        scene.overlay().showText(70)
                .text("Powered, the beam pulls ships toward the emitter face")
                .pointAt(util.vector().centerOf(shipPos))
                .placeNearTarget();
        scene.world().moveSection(ship, new Vec3(0.0, -1.0, -1.7), 35);
        scene.effects().indicateSuccess(beamPos);
        scene.idle(45);

        scene.overlay().showText(80)
                .text("Wrench-rotate to redirect the pull along any of six axes")
                .pointAt(util.vector().topOf(beamPos))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    // ---------------- Magnetic Switch ----------------

    public static void magneticSwitch(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("magnetic_switch", "Proximity sensing with the Magnetic Switch");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos switchPos = util.grid().at(2, 1, 2);
        final BlockPos lampPos = util.grid().at(2, 1, 0);
        final BlockPos shipPos = util.grid().at(2, 2, 4);

        scene.world().setBlock(switchPos,
                MagBlocks.MAGNETIC_SWITCH.get().defaultBlockState(), false);
        scene.world().setBlock(lampPos,
                Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, false), false);
        scene.world().setBlock(util.grid().at(2, 1, 1),
                Blocks.REDSTONE_WIRE.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(2, 1, 0, 2, 1, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("The Magnetic Switch outputs redstone proportional to the nearest ship's distance")
                .pointAt(util.vector().topOf(switchPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().setBlock(shipPos, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final ElementLink<WorldSectionElement> ship = scene.world()
                .showIndependentSection(util.select().position(shipPos), Direction.UP);
        scene.idle(10);

        // Ship approaches → signal climbs → lamp lights.
        scene.world().moveSection(ship, new Vec3(0.0, 0.0, -1.0), 30);
        scene.idle(35);
        scene.world().modifyBlock(lampPos,
                s -> s.hasProperty(BlockStateProperties.LIT) ? s.setValue(BlockStateProperties.LIT, true) : s,
                false);
        scene.effects().indicateSuccess(lampPos);
        scene.overlay().showText(70)
                .text("Closer ship → stronger signal (0 at 8 blocks, 15 at zero distance)")
                .pointAt(util.vector().topOf(switchPos))
                .placeNearTarget();
        scene.world().moveSection(ship, new Vec3(0.0, 0.0, -0.6), 25);
        scene.idle(80);

        // Ship retreats → lamp dims.
        scene.world().moveSection(ship, new Vec3(0.0, 0.0, 1.6), 35);
        scene.idle(40);
        scene.world().modifyBlock(lampPos,
                s -> s.hasProperty(BlockStateProperties.LIT) ? s.setValue(BlockStateProperties.LIT, false) : s,
                false);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("Wire it to anything redstone — doors, hoppers, contraption launchers")
                .pointAt(util.vector().topOf(lampPos))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();
    }

    // ---------------- Magnetic Excavator ----------------

    public static void magneticExcavator(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("magnetic_excavator", "Mining a column with the Magnetic Excavator");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos excavatorPos = util.grid().at(2, 3, 2);
        final BlockPos leverPos = util.grid().at(0, 3, 2);
        final BlockPos ore1 = util.grid().at(2, 2, 2);
        final BlockPos ore2 = util.grid().at(2, 1, 2);

        scene.world().setBlock(excavatorPos,
                MagBlocks.MAGNETIC_EXCAVATOR.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.DOWN), false);
        scene.world().setBlock(leverPos,
                Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, false), false);
        scene.world().setBlock(ore1, Blocks.IRON_ORE.defaultBlockState(), false);
        scene.world().setBlock(ore2, Blocks.IRON_ORE.defaultBlockState(), false);
        scene.world().showSection(util.select().fromTo(0, 3, 2, 2, 3, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 2, 2), Direction.UP);
        scene.idle(15);

        scene.overlay().showText(90)
                .text("The Magnetic Excavator rips ferromagnetic ores out of the column along its facing")
                .pointAt(util.vector().blockSurface(excavatorPos, Direction.DOWN))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().toggleRedstonePower(util.select().fromTo(0, 3, 2, 2, 3, 2));
        scene.world().modifyBlock(excavatorPos,
                s -> s.hasProperty(BlockStateProperties.POWERED) ? s.setValue(BlockStateProperties.POWERED, true) : s,
                false);
        scene.effects().indicateRedstone(leverPos);
        scene.idle(15);

        // Closest ore travels first
        scene.world().destroyBlock(ore1);
        scene.effects().indicateSuccess(ore1);
        scene.idle(25);

        scene.overlay().showText(70)
                .text("Each pulled block tunnels through any obstructions on its way to the emitter")
                .pointAt(util.vector().centerOf(ore2))
                .placeNearTarget();
        scene.idle(25);

        // Then the deeper one
        scene.world().destroyBlock(ore2);
        scene.effects().indicateSuccess(ore2);
        scene.idle(40);

        scene.overlay().showText(80)
                .text("Wrench to redirect — point sideways for horizontal veins, up for ceilings")
                .pointAt(util.vector().topOf(excavatorPos))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    // ---------------- Polarity Inverter ----------------

    public static void polarityInverter(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("polarity_inverter", "Flipping field polarity with the Polarity Inverter");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        final BlockPos emitterPos = util.grid().at(2, 1, 2);
        final BlockPos inverterPos = util.grid().at(3, 1, 2);
        final BlockPos secondInverter = util.grid().at(1, 1, 2);

        scene.world().setBlock(emitterPos,
                MagBlocks.ELECTROMAGNET.get().defaultBlockState()
                        .setValue(BlockStateProperties.POWERED, true), false);
        scene.world().showSection(util.select().position(emitterPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("Place a Polarity Inverter next to any emitter to flip its field")
                .pointAt(util.vector().topOf(emitterPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        // Place the first inverter
        scene.world().setBlock(inverterPos,
                MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(inverterPos), Direction.UP);
        scene.effects().indicateSuccess(inverterPos);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Field reverses — what was pulling now pushes (and vice-versa)")
                .pointAt(util.vector().centerOf(inverterPos))
                .placeNearTarget()
                .colored(PonderPalette.BLUE);
        scene.idle(80);

        // Place a second inverter — even count cancels
        scene.world().setBlock(secondInverter,
                MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), false);
        scene.world().showSection(util.select().position(secondInverter), Direction.UP);
        scene.effects().indicateSuccess(secondInverter);
        scene.idle(20);

        scene.overlay().showText(90)
                .text("Two inverters cancel — even count = no flip. Lets you sequence polarity without redstone.")
                .pointAt(util.vector().centerOf(emitterPos))
                .placeNearTarget()
                .colored(PonderPalette.RED);
        scene.idle(100);

        scene.markAsFinished();
    }
}
