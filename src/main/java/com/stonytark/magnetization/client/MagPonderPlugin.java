package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;

import java.util.concurrent.atomic.AtomicBoolean;

/** Create Ponder scenes for Magnetization's buildable machines.
 *
 * <p>The scenes intentionally use Create's small hand-crank schematic as a
 * stable base template and then place the addon blocks through Ponder's world
 * instructions. This keeps the scenes data-pack independent while still
 * showing the real multiblock geometry and the same player-facing build facts
 * used by the in-world preview overlay.
 */
public final class MagPonderPlugin implements PonderPlugin {

    private static final ResourceLocation SCHEMATIC =
            ResourceLocation.fromNamespaceAndPath("create", "hand_crank");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    public static final MagPonderPlugin INSTANCE = new MagPonderPlugin();

    private MagPonderPlugin() {}

    /** Common-safe probe used by the optional runtime GameTest. */
    public static boolean hasCopycatsSceneTarget() {
        return BuiltInRegistries.BLOCK.containsKey(
                ResourceLocation.fromNamespaceAndPath("copycats", "copycat_block"));
    }

    /** Idempotent because client setup can be replayed by a dev environment. */
    public static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            net.createmod.ponder.foundation.PonderIndex.addPlugin(INSTANCE);
        }
    }

    @Override
    public String getModId() {
        return Magnetization.MOD_ID;
    }

    @Override
    public void registerScenes(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        final PonderSceneRegistrationHelper<Block> blocks =
                helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);

        blocks.forComponents(MagBlocks.TOKAMAK_CONTROLLER.get(), MagBlocks.TOKAMAK_COIL.get())
                .addStoryBoard(SCHEMATIC, MagPonderPlugin::tokamakRing);
        blocks.forComponents(MagBlocks.FUSION_THRUSTER.get())
                .addStoryBoard(SCHEMATIC, MagPonderPlugin::fusionPanel);
        blocks.forComponents(MagBlocks.RAILGUN_EMITTER.get())
                .addStoryBoard(SCHEMATIC, MagPonderPlugin::railgunPair);

        blocks.forComponents(MagBlocks.ELECTROLYZER.get())
                .addStoryBoard(SCHEMATIC, machineScene("electrolyzer", "Run an Electrolyzer",
                        "Feed it water and FE; it produces hydrogen for the fusion-fuel chain.",
                        MagBlocks.ELECTROLYZER.get()));
        blocks.forComponents(MagBlocks.MHD_JET.get())
                .addStoryBoard(SCHEMATIC, machineScene("mhd_jet", "Fuel an MHD Jet",
                        "Install a magnet, point the jet with a wrench, then feed it FE and liquid lithium.",
                        MagBlocks.MHD_JET.get()));
        blocks.forComponents(MagBlocks.MICRO_THRUSTER.get())
                .addStoryBoard(SCHEMATIC, machineScene("micro_thruster", "Fuel a Micro Thruster",
                        "Point the thruster with a wrench, fill its ferrofluid tank, and supply FE.",
                        MagBlocks.MICRO_THRUSTER.get()));
        blocks.forComponents(MagBlocks.SOLAR_SAIL.get())
                .addStoryBoard(SCHEMATIC, machineScene("solar_sail", "Use a Solar Sail",
                        "Mount panels on a ship and point them with a wrench; daylight and panel count drive thrust.",
                        MagBlocks.SOLAR_SAIL.get()));
        blocks.forComponents(MagBlocks.KINETIC_COIL.get())
                .addStoryBoard(SCHEMATIC, machineScene("kinetic_coil", "Use a Kinetic Coil",
                        "A passing magnetic ship induces FE and a redstone pulse in the coil.",
                        MagBlocks.KINETIC_COIL.get()));
        blocks.forComponents(MagBlocks.HOMOPOLAR_MOTOR.get())
                .addStoryBoard(SCHEMATIC, machineScene("homopolar_motor", "Drive a Homopolar Motor",
                        "Install a magnet and connect the Create shaft; output scales with the installed magnet.",
                        MagBlocks.HOMOPOLAR_MOTOR.get()));
        blocks.forComponents(MagBlocks.STRUCTURAL_INDUCER.get())
                .addStoryBoard(SCHEMATIC, machineScene("structural_inducer", "Launch a Structure",
                        "Power the inducer, point it with a wrench, and set its scan range before launching the structure ahead.",
                        MagBlocks.STRUCTURAL_INDUCER.get()));
        blocks.forComponents(MagBlocks.DIPOLE_ELECTROMAGNET.get())
                .addStoryBoard(SCHEMATIC, machineScene("dipole_electromagnet", "Aim a Dipole Electromagnet",
                        "Power it and use a wrench to aim the separated NORTH and SOUTH pole origins.",
                        MagBlocks.DIPOLE_ELECTROMAGNET.get()));

        // No hard Railways reference: the optional registry lookup keeps the
        // normal client classpath clean while adding our field behavior to the
        // port's existing coupler Ponder coverage when it is installed.
        BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("railways", "track_coupler"))
                .ifPresent(coupler -> blocks.forComponents(coupler)
                        .addStoryBoard(SCHEMATIC, MagPonderPlugin::steamRailsMagnetism));
        BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("copycats", "copycat_block"))
                .ifPresent(copycat -> blocks.forComponents(copycat)
                        .addStoryBoard(SCHEMATIC, MagPonderPlugin::copycatMagnetism));
    }

    private static void prepare(final SceneBuilder scene, final String id, final String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
    }

    private static void show(final SceneBuilder scene, final SceneBuildingUtil util,
                             final BlockPos from, final BlockPos to) {
        scene.world().showSection(util.select().fromTo(from, to), Direction.DOWN);
        scene.idle(12);
    }

    private static void text(final SceneBuilder scene, final SceneBuildingUtil util,
                             final BlockPos from, final BlockPos to, final String message) {
        scene.overlay().showOutlineWithText(util.select().fromTo(from, to), 80)
                .colored(PonderPalette.INPUT)
                .text(message)
                .placeNearTarget();
        scene.idle(90);
    }

    private static void tokamakRing(final SceneBuilder scene, final SceneBuildingUtil util) {
        prepare(scene, "tokamak_ring", "Build a Tokamak ring");
        final BlockPos center = util.grid().at(2, 1, 2);
        scene.world().setBlock(center, MagBlocks.TOKAMAK_CONTROLLER.get().defaultBlockState(), false);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (x != 2 || z != 2) {
                    scene.world().setBlock(util.grid().at(x, 1, z),
                            MagBlocks.TOKAMAK_COIL.get().defaultBlockState(), false);
                }
            }
        }
        show(scene, util, util.grid().at(1, 1, 1), util.grid().at(3, 1, 3));
        text(scene, util, util.grid().at(1, 1, 1), util.grid().at(3, 1, 3),
                "Surround the controller with exactly 8 Tokamak Coils.");
        scene.overlay().showOutlineWithText(util.select().position(center), 80)
                .colored(PonderPalette.OUTPUT)
                .text("Load a fuel cell into the controller once the ring is complete.")
                .placeNearTarget();
        scene.idle(90);
    }

    private static void fusionPanel(final SceneBuilder scene, final SceneBuildingUtil util) {
        prepare(scene, "fusion_panel", "Build a Fusion Thruster panel");
        final BlockPos center = util.grid().at(2, 1, 2);
        final Direction facing = Direction.NORTH;
        scene.world().setBlock(center,
                MagBlocks.FUSION_THRUSTER.get().defaultBlockState()
                        .setValue(DirectionalBlock.FACING, facing), false);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (x != 2 || z != 2) {
                    scene.world().setBlock(util.grid().at(x, 1, z),
                            MagBlocks.TOKAMAK_COIL.get().defaultBlockState(), false);
                }
            }
        }
        show(scene, util, util.grid().at(1, 1, 1), util.grid().at(3, 1, 3));
        text(scene, util, util.grid().at(1, 1, 1), util.grid().at(3, 1, 3),
                "A Fusion Thruster interior sits inside a one-block Tokamak-Coil frame.");
        scene.overlay().showOutlineWithText(util.select().position(center), 80)
                .colored(PonderPalette.OUTPUT)
                .text("Expand the interior into a solid rectangular panel; all interiors share one facing.")
                .placeNearTarget();
        scene.idle(90);
    }

    private static void railgunPair(final SceneBuilder scene, final SceneBuildingUtil util) {
        prepare(scene, "railgun_pair", "Build a paired Railgun");
        final Direction facing = Direction.EAST;
        final BlockPos first = util.grid().at(1, 1, 1);
        final BlockPos second = util.grid().at(1, 1, 3);
        final var emitterState = MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, facing);
        scene.world().setBlock(first, emitterState, false);
        scene.world().setBlock(second, emitterState, false);
        for (int x = 2; x <= 5; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 1), Blocks.COPPER_BLOCK.defaultBlockState(), false);
            scene.world().setBlock(util.grid().at(x, 1, 3), Blocks.COPPER_BLOCK.defaultBlockState(), false);
        }
        show(scene, util, util.grid().at(1, 1, 1), util.grid().at(5, 1, 3));
        text(scene, util, util.grid().at(1, 1, 1), util.grid().at(5, 1, 3),
                "Build two parallel rails with emitters facing the same direction.");
        scene.overlay().showOutlineWithText(util.select().fromTo(first, second), 80)
                .colored(PonderPalette.OUTPUT)
                .text("Both rails must reach the minimum length before an arc can launch a target.")
                .placeNearTarget();
        scene.idle(90);
    }

    private static void steamRailsMagnetism(final SceneBuilder scene, final SceneBuildingUtil util) {
        prepare(scene, "steam_rails_magnetism", "Move coupled trains with magnetic fields");
        final BlockPos coupler = util.grid().at(2, 1, 2);
        final Block railwaysCoupler = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("railways", "track_coupler"));
        scene.world().setBlock(coupler, railwaysCoupler.defaultBlockState(), false);
        final BlockPos magnet = util.grid().at(4, 1, 2);
        scene.world().setBlock(magnet, MagBlocks.ELECTROMAGNET.get().defaultBlockState(), false);
        show(scene, util, coupler, magnet);
        text(scene, util, coupler, coupler,
                "Steam 'n' Rails couplers share one train. A magnetic field accelerates or brakes the linked consist along its track.");
        scene.overlay().showOutlineWithText(util.select().position(magnet), 100)
                .colored(PonderPalette.OUTPUT)
                .text("Structural Inducers ignore assembled train entities; disassemble a train before treating its blocks as a structure.")
                .placeNearTarget();
        scene.idle(110);
    }

    private static void copycatMagnetism(final SceneBuilder scene, final SceneBuildingUtil util) {
        prepare(scene, "copycat_magnetism", "Copy magnetic material properties");
        final BlockPos copycat = util.grid().at(2, 1, 2);
        scene.world().setBlock(copycat, BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("copycats", "copycat_block")).defaultBlockState(), false);
        final BlockPos material = util.grid().at(3, 1, 2);
        scene.world().setBlock(material, Blocks.IRON_BLOCK.defaultBlockState(), false);
        show(scene, util, copycat, material);
        text(scene, util, copycat, material,
                "A Copycats+ block inherits magnetic susceptibility from its copied material, including after contraption assembly.");
        scene.overlay().showOutlineWithText(util.select().position(copycat), 90)
                .colored(PonderPalette.OUTPUT)
                .text("Create goggles report whether the stored material is ferromagnetic, diamagnetic, excluded, or nonmagnetic.")
                .placeNearTarget();
        scene.idle(100);
    }

    private static PonderStoryBoard machineScene(final String id, final String title,
                                                  final String message, final Block block) {
        return (scene, util) -> {
            prepare(scene, id, title);
            final BlockPos pos = util.grid().at(2, 1, 2);
            scene.world().setBlock(pos, block.defaultBlockState(), false);
            show(scene, util, pos, pos);
            text(scene, util, pos, pos, message);
            scene.overlay().showControls(util.vector().topOf(pos),
                            net.createmod.catnip.math.Pointing.DOWN, 70)
                    .rightClick();
            scene.idle(80);
        };
    }
}
