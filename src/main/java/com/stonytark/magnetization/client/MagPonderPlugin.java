package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Create Ponder scenes for Magnetization mechanics whose setup benefits from
 * an animated spatial explanation.
 *
 * <p>The scenes intentionally use Create's small hand-crank schematic as a
 * stable base template and then place the addon blocks through Ponder's world
 * instructions. This keeps the scenes data-pack independent while still
 * showing the real multiblock geometry and the same player-facing build facts
 * used by the in-world preview overlay.
 */
public final class MagPonderPlugin implements PonderPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("magnetization/Ponder");
    private static final ResourceLocation SCHEMATIC =
            ResourceLocation.fromNamespaceAndPath("create", "hand_crank");
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    public static final MagPonderPlugin INSTANCE = new MagPonderPlugin();

    private MagPonderPlugin() {}

    /** Common-safe probe used by the optional runtime GameTest. */
    public static boolean hasCopycatsSceneTarget() {
        return com.stonytark.magnetization.config.MagConfig.copycatsCompatEnabled()
                && BuiltInRegistries.BLOCK.containsKey(
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

        int coreRegistered = 0;
        int optionalRegistered = 0;
        for (final PonderSceneCatalog.Scene definition : PonderSceneCatalog.coreScenes()) {
            if (registerScene(blocks, definition)) coreRegistered++;
        }

        for (final PonderSceneCatalog.Scene definition : PonderSceneCatalog.optionalScenes()) {
            final boolean enabled = switch (definition.kind()) {
                case STEAM_RAILS -> com.stonytark.magnetization.config.MagConfig.steamRailsCompatEnabled();
                case COPYCATS -> com.stonytark.magnetization.config.MagConfig.copycatsCompatEnabled();
                default -> true;
            };
            if (enabled && registerScene(blocks, definition)) optionalRegistered++;
        }
        LOGGER.info("Registered {} core and {} optional Magnetization Ponder scenes",
                coreRegistered, optionalRegistered);
    }

    private static boolean registerScene(final PonderSceneRegistrationHelper<Block> blocks,
                                         final PonderSceneCatalog.Scene definition) {
        final List<Block> targets = definition.targets().stream()
                .map(ResourceLocation::parse)
                .map(BuiltInRegistries.BLOCK::getOptional)
                .flatMap(java.util.Optional::stream)
                .toList();
        if (targets.size() != definition.targets().size()) {
            if (definition.kind() == PonderSceneCatalog.Kind.STEAM_RAILS
                    || definition.kind() == PonderSceneCatalog.Kind.COPYCATS) {
                LOGGER.debug("Skipping optional Ponder scene {} because one of {} is unavailable",
                        definition.id(), definition.targets());
            } else {
                LOGGER.warn("Skipping core Ponder scene {} because one of {} is unavailable",
                        definition.id(), definition.targets());
            }
            return false;
        }
        blocks.forComponents(targets.toArray(Block[]::new))
                .addStoryBoard(SCHEMATIC, storyBoard(definition, targets.getFirst()));
        return true;
    }

    private static PonderStoryBoard storyBoard(final PonderSceneCatalog.Scene definition,
                                                final Block primaryTarget) {
        return switch (definition.kind()) {
            case TOKAMAK -> (scene, util) -> tokamakRing(scene, util, definition);
            case FUSION_PANEL -> (scene, util) -> fusionPanel(scene, util, definition);
            case RAILGUN -> (scene, util) -> railgunPair(scene, util, definition);
            case GAS_EXCITER -> (scene, util) -> gasExciter(scene, util, definition);
            case GAS_VENT -> (scene, util) -> gasVent(scene, util, definition);
            case AIR_SEPARATOR -> (scene, util) -> airSeparator(scene, util, definition);
            case ION_THRUSTER -> (scene, util) -> ionThruster(scene, util, definition);
            case RARE_EARTH -> (scene, util) -> rareEarthMagnets(scene, util, definition);
            case STEAM_RAILS -> (scene, util) -> steamRailsMagnetism(scene, util, definition);
            case COPYCATS -> (scene, util) -> copycatMagnetism(scene, util, definition);
            case GENERIC -> machineScene(definition, primaryTarget);
        };
    }

    private static void prepare(final SceneBuilder scene, final PonderSceneCatalog.Scene definition) {
        scene.title(definition.id(), definition.title());
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

    private static void tokamakRing(final SceneBuilder scene, final SceneBuildingUtil util,
                                    final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos center = util.grid().at(2, 1, 2);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                scene.world().setBlock(util.grid().at(x, 1, z),
                        MagBlocks.TOKAMAK_CONTROLLER.get().defaultBlockState(), false);
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x == 0 || x == 4 || z == 0 || z == 4) {
                    scene.world().setBlock(util.grid().at(x, 1, z),
                            MagBlocks.TOKAMAK_COIL.get().defaultBlockState(), false);
                }
            }
        }
        show(scene, util, util.grid().at(0, 1, 0), util.grid().at(4, 1, 4));
        text(scene, util, util.grid().at(0, 1, 0), util.grid().at(4, 1, 4),
                definition.text(0));
        scene.overlay().showOutlineWithText(util.select().position(center), 80)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(90);
        text(scene, util, center, util.grid().at(4, 1, 4),
                definition.text(2));
    }

    private static void fusionPanel(final SceneBuilder scene, final SceneBuildingUtil util,
                                    final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
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
                definition.text(0));
        scene.overlay().showOutlineWithText(util.select().position(center), 80)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(90);
        text(scene, util, center, util.grid().at(3, 1, 3),
                definition.text(2));
    }

    private static void railgunPair(final SceneBuilder scene, final SceneBuildingUtil util,
                                    final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
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
                definition.text(0));
        scene.overlay().showOutlineWithText(util.select().fromTo(first, second), 80)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(90);
    }

    private static void gasExciter(final SceneBuilder scene, final SceneBuildingUtil util,
                                   final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos exciter = util.grid().at(1, 1, 2);
        scene.world().setBlock(exciter, MagBlocks.GAS_EXCITER.get().defaultBlockState(), false);
        for (int x = 2; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2), MagBlocks.XENON_BLOCK.get().defaultBlockState()
                    .setValue(com.stonytark.magnetization.content.fluid.ExcitableGasBlock.EXCITED, false), false);
        }
        show(scene, util, exciter, util.grid().at(4, 1, 2));
        text(scene, util, util.grid().at(2, 1, 2), util.grid().at(4, 1, 2),
                definition.text(0));
        for (int x = 2; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2), MagBlocks.XENON_BLOCK.get().defaultBlockState()
                    .setValue(com.stonytark.magnetization.content.fluid.ExcitableGasBlock.EXCITED, true), false);
        }
        scene.overlay().showOutlineWithText(util.select().position(exciter), 90)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(100);
    }

    private static void gasVent(final SceneBuilder scene, final SceneBuildingUtil util,
                                final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos vent = util.grid().at(2, 1, 2);
        final BlockPos exciter = util.grid().at(1, 1, 2);
        scene.world().setBlock(vent, MagBlocks.GAS_VENT.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST), false);
        scene.world().setBlock(exciter, MagBlocks.GAS_EXCITER.get().defaultBlockState(), false);
        show(scene, util, exciter, vent);
        text(scene, util, vent, vent,
                definition.text(0));
        for (int x = 3; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2), MagBlocks.PROXY_GAS_CLOUD.get().defaultBlockState()
                    .setValue(com.stonytark.magnetization.content.gas.ProxyGasCloudBlock.EXCITED, true), false);
        }
        show(scene, util, util.grid().at(3, 1, 2), util.grid().at(4, 1, 2));
        text(scene, util, exciter, util.grid().at(4, 1, 2),
                definition.text(1));
    }

    private static void airSeparator(final SceneBuilder scene, final SceneBuildingUtil util,
                                     final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos separator = util.grid().at(2, 2, 2);
        scene.world().setBlock(separator, MagBlocks.AIR_SEPARATOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), false);
        show(scene, util, separator, separator);
        text(scene, util, separator, separator,
                definition.text(0));

        final BlockPos shaft = separator.south();
        scene.world().setBlock(shaft, com.simibubi.create.AllBlocks.SHAFT.get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), false);
        show(scene, util, shaft, shaft);
        text(scene, util, shaft, shaft,
                definition.text(1));

        final BlockPos[] outputs = {separator.north(), separator.east(), separator.west(),
                separator.above(), separator.below()};
        final Block[] markers = {Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS,
                Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS};
        for (int i = 0; i < outputs.length; i++) {
            scene.world().setBlock(outputs[i], markers[i].defaultBlockState(), false);
            show(scene, util, outputs[i], outputs[i]);
        }
        text(scene, util, separator.below(), separator.above(),
                definition.text(2));
        scene.overlay().showOutlineWithText(util.select().position(separator), 90)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(3))
                .placeNearTarget();
        scene.idle(100);
    }

    private static void ionThruster(final SceneBuilder scene, final SceneBuildingUtil util,
                                    final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos thruster = util.grid().at(2, 1, 3);
        scene.world().setBlock(thruster, MagBlocks.ION_THRUSTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH), false);
        final BlockPos helium = util.grid().at(1, 1, 1);
        final BlockPos xenon = util.grid().at(2, 1, 1);
        final BlockPos radon = util.grid().at(3, 1, 1);
        scene.world().setBlock(helium, MagBlocks.HELIUM_BLOCK.get().defaultBlockState(), false);
        scene.world().setBlock(xenon, MagBlocks.XENON_BLOCK.get().defaultBlockState(), false);
        scene.world().setBlock(radon, MagBlocks.RADON_BLOCK.get().defaultBlockState(), false);
        show(scene, util, helium, radon);
        show(scene, util, thruster, thruster);
        text(scene, util, helium, radon,
                definition.text(0));
        scene.overlay().showOutlineWithText(util.select().position(thruster), 90)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(100);
    }

    private static void rareEarthMagnets(final SceneBuilder scene, final SceneBuildingUtil util,
                                         final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final Block[] ores = {MagBlocks.BASTNASITE_ORE.get(), MagBlocks.MONAZITE_ORE.get(),
                MagBlocks.COBALTITE_ORE.get(), MagBlocks.BORAX_ORE.get()};
        for (int x = 1; x <= ores.length; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 1), ores[x - 1].defaultBlockState(), false);
        }
        show(scene, util, util.grid().at(1, 1, 1), util.grid().at(4, 1, 1));
        text(scene, util, util.grid().at(1, 1, 1), util.grid().at(4, 1, 1),
                definition.text(0));

        final BlockPos smco = util.grid().at(1, 1, 3);
        final BlockPos ndfeb = util.grid().at(3, 1, 3);
        scene.world().setBlock(smco, MagBlocks.SAMARIUM_COBALT_MAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(ndfeb, MagBlocks.NEODYMIUM_MAGNET.get().defaultBlockState(), false);
        show(scene, util, smco, ndfeb);
        text(scene, util, smco, smco,
                definition.text(1));
        scene.overlay().showOutlineWithText(util.select().position(ndfeb), 90)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(2))
                .placeNearTarget();
        scene.idle(100);
    }

    private static void steamRailsMagnetism(final SceneBuilder scene, final SceneBuildingUtil util,
                                             final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos coupler = util.grid().at(2, 1, 2);
        final Block railwaysCoupler = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("railways", "track_coupler"));
        scene.world().setBlock(coupler, railwaysCoupler.defaultBlockState(), false);
        final BlockPos magnet = util.grid().at(4, 1, 2);
        scene.world().setBlock(magnet, MagBlocks.ELECTROMAGNET.get().defaultBlockState(), false);
        show(scene, util, coupler, magnet);
        text(scene, util, coupler, coupler,
                definition.text(0));
        scene.overlay().showOutlineWithText(util.select().position(magnet), 100)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(110);
    }

    private static void copycatMagnetism(final SceneBuilder scene, final SceneBuildingUtil util,
                                         final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        final BlockPos copycat = util.grid().at(2, 1, 2);
        scene.world().setBlock(copycat, BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("copycats", "copycat_block")).defaultBlockState(), false);
        final BlockPos material = util.grid().at(3, 1, 2);
        scene.world().setBlock(material, Blocks.IRON_BLOCK.defaultBlockState(), false);
        show(scene, util, copycat, material);
        text(scene, util, copycat, material,
                definition.text(0));
        scene.overlay().showOutlineWithText(util.select().position(copycat), 90)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(100);
    }

    private static PonderStoryBoard machineScene(final PonderSceneCatalog.Scene definition, final Block block) {
        return (scene, util) -> {
            prepare(scene, definition);
            final BlockPos pos = util.grid().at(2, 1, 2);
            scene.world().setBlock(pos, block.defaultBlockState(), false);
            show(scene, util, pos, pos);
            text(scene, util, pos, pos, definition.text(0));
            if (definition.rightClickHint()) {
                scene.overlay().showControls(util.vector().topOf(pos),
                                net.createmod.catnip.math.Pointing.DOWN, 70)
                        .rightClick();
            }
            scene.idle(80);
        };
    }
}
