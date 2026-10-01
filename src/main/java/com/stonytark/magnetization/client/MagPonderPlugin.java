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

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Create Ponder scenes for Magnetization mechanics whose setup benefits from
 * an animated spatial explanation.
 *
 * <p>The scenes intentionally use the addon's bounded empty workshop schematic as a
 * stable base template and then place the addon blocks through Ponder's world
 * instructions. This keeps the scenes data-pack independent while still
 * showing the real multiblock geometry and the same player-facing build facts
 * used by the in-world preview overlay.
 */
public final class MagPonderPlugin implements PonderPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("magnetization/Ponder");
    private static final ResourceLocation SCHEMATIC =
            ResourceLocation.fromNamespaceAndPath("magnetization", "empty_workshop");
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
            case MACHINE -> machineScene(definition, primaryTarget);
            case MAGNETIC_SHAFT -> (scene, util) -> magneticShafts(scene, util, definition);
            case GENERIC -> genericScene(definition, primaryTarget);
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
        scene.scaleSceneView(0.75f);
        pipe(scene, util, new BlockPos(0, 2, 2), Direction.Axis.Y);
        scene.world().setBlock(new BlockPos(0, 3, 2), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(5, 1, 2), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        show(scene, util, new BlockPos(0, 3, 2), new BlockPos(0, 2, 2));
        show(scene, util, new BlockPos(5, 1, 2), new BlockPos(5, 1, 2));
        scene.overlay().showControls(util.vector().topOf(center), net.createmod.catnip.math.Pointing.DOWN, 80)
                .rightClick().withItem(new ItemStack(MagItems.DEUTERIUM_CELL.get()));
        scene.world().modifyBlockEntity(center, com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity.class, be -> {
            be.fuelContainer().setItem(0, new ItemStack(MagItems.DEUTERIUM_CELL.get()));
            be.coolantHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        });
        scene.overlay().showBigLine(PonderPalette.OUTPUT, Vec3.atCenterOf(new BlockPos(4, 1, 2)), Vec3.atCenterOf(new BlockPos(5, 1, 2)), 80);
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
        scene.scaleSceneView(0.85f);
        final BlockPos center = util.grid().at(2, 2, 2);
        panel(scene, 1, 3);
        show(scene, util, util.grid().at(1, 1, 2), util.grid().at(3, 3, 2));
        text(scene, util, center, center, definition.text(0));
        // Replace the small frame with a wider, still NORTH-facing valid panel.
        panel(scene, 0, 4);
        show(scene, util, util.grid().at(0, 1, 2), util.grid().at(4, 3, 2));
        text(scene, util, util.grid().at(0, 1, 2), util.grid().at(4, 3, 2), definition.text(1));
        pipe(scene, util, new BlockPos(2, 1, 3), Direction.Axis.Z);
        scene.world().setBlock(new BlockPos(2, 1, 4), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        show(scene, util, new BlockPos(2, 1, 3), new BlockPos(2, 1, 4));
        scene.overlay().showBigLine(PonderPalette.INPUT, Vec3.atCenterOf(new BlockPos(2, 1, 4)),
                Vec3.atCenterOf(new BlockPos(2, 1, 2)), 80);
        scene.world().modifyBlockEntity(center, com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.class,
                be -> be.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE));
        text(scene, util, center, new BlockPos(2, 1, 4), definition.text(2));
    }

    private static void panel(final SceneBuilder scene, final int minX, final int maxX) {
        for (int x = minX; x <= maxX; x++) for (int y = 1; y <= 3; y++) {
            final boolean interior = x > minX && x < maxX && y == 2;
            scene.world().setBlock(new BlockPos(x, y, 2), interior
                    ? MagBlocks.FUSION_THRUSTER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH)
                    : MagBlocks.TOKAMAK_COIL.get().defaultBlockState(), false);
        }
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
        final BlockPos projectile = new BlockPos(2, 1, 2);
        scene.world().setBlock(projectile, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final var launched = scene.world().showIndependentSection(util.select().position(projectile), Direction.DOWN);
        for (final BlockPos emitter : List.of(first, second)) {
            scene.world().modifyBlockEntity(emitter, com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.class,
                    be -> be.energyBuffer().receiveEnergy(100000, false));
        }
        scene.overlay().showBigLine(PonderPalette.OUTPUT, Vec3.atCenterOf(projectile), new Vec3(6, 1.5, 2.5), 70);
        scene.world().moveSection(launched, new Vec3(3, 0, 0), 60);
        scene.idle(80);
    }

    private static void gasExciter(final SceneBuilder scene, final SceneBuildingUtil util,
                                   final PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        // These native gas/cloud volumes are invisible in Ponder's block sections.
        // Explicitly described glass markers keep the tutorial volume visible.
        final BlockPos exciter = util.grid().at(1, 1, 2);
        scene.world().setBlock(exciter, MagBlocks.GAS_EXCITER.get().defaultBlockState(), false);
        for (int x = 2; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2), Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), false);
        }
        show(scene, util, exciter, util.grid().at(4, 1, 2));
        text(scene, util, util.grid().at(2, 1, 2), util.grid().at(4, 1, 2),
                definition.text(0));
        for (int x = 2; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2), Blocks.PINK_STAINED_GLASS.defaultBlockState(), false);
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
        pipe(scene, util, vent.south(), Direction.Axis.Z);
        scene.world().setBlock(vent.south(2), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        show(scene, util, vent.south(), vent.south(2));
        scene.overlay().showBigLine(PonderPalette.INPUT, Vec3.atCenterOf(vent.south(2)), Vec3.atCenterOf(vent), 80);
        show(scene, util, exciter, vent);
        text(scene, util, vent, vent,
                definition.text(0));
        for (int x = 3; x <= 4; x++) {
            scene.world().setBlock(util.grid().at(x, 1, 2), Blocks.PINK_STAINED_GLASS.defaultBlockState(), false);
        }
        show(scene, util, util.grid().at(3, 1, 2), util.grid().at(4, 1, 2));
        scene.overlay().showControls(util.vector().topOf(vent.east()), net.createmod.catnip.math.Pointing.DOWN, 80)
                .rightClick().withItem(new ItemStack(Items.BUCKET));
        scene.world().createItemEntity(Vec3.atCenterOf(vent.east()).add(0, 1, 0), Vec3.ZERO, new ItemStack(Items.BUCKET));
        text(scene, util, exciter, util.grid().at(4, 1, 2), definition.text(1));
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
        new CreateSceneBuilder(scene).world().setKineticSpeed(util.select().position(shaft), 64);
        new CreateSceneBuilder(scene).world().setKineticSpeed(util.select().position(separator), 64);
        text(scene, util, shaft, shaft,
                definition.text(1));

        final BlockPos[] outputs = {separator.above(), separator.west(), separator.below(),
                separator.east(), separator.north()};
        final Block[] markers = {Blocks.LIGHT_BLUE_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS,
                Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS};
        for (int i = 0; i < outputs.length; i++) {
            scene.world().setBlock(outputs[i], markers[i].defaultBlockState(), false);
            show(scene, util, outputs[i], outputs[i]);
        }
        text(scene, util, separator.below(), separator.above(),
                definition.text(2));
        scene.overlay().showControls(util.vector().topOf(separator), net.createmod.catnip.math.Pointing.DOWN, 70)
                .rightClick().withItem(new ItemStack(MagItems.ISOTOPE_SEPARATION_MODULE.get()));
        scene.world().modifyBlockEntity(separator, com.stonytark.magnetization.content.gas.AirSeparatorBlockEntity.class,
                be -> be.installUpgrade());
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
        scene.world().setBlock(helium, Blocks.WHITE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(xenon, Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(radon, Blocks.LIME_STAINED_GLASS.defaultBlockState(), false);
        show(scene, util, helium, radon);
        show(scene, util, thruster, thruster);
        text(scene, util, helium, radon,
                definition.text(0));
        pipe(scene, util, thruster.west(), Direction.Axis.X);
        scene.world().setBlock(thruster.east(), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(thruster.above(), Blocks.IRON_BLOCK.defaultBlockState(), false);
        show(scene, util, thruster.west(), thruster.east());
        scene.world().modifyBlockEntity(thruster, com.stonytark.magnetization.content.jet.IonThrusterBlockEntity.class, be -> {
            be.fluidHandler().fill(new FluidStack(MagFluids.XENON.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
            be.energyBuffer().receiveEnergy(100000, false);
        });
        final var hull = scene.world().showIndependentSection(util.select().fromTo(thruster, thruster.above()), Direction.DOWN);
        scene.world().moveSection(hull, new Vec3(0, 0, -1.5), 60);
        scene.overlay().showBigLine(PonderPalette.RED, Vec3.atCenterOf(thruster), Vec3.atCenterOf(thruster.south(2)), 80);
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

        final BlockPos press = new BlockPos(0, 2, 3);
        scene.world().setBlock(press.below(), AllBlocks.BASIN.getDefaultState(), false);
        scene.world().setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState(), false);
        show(scene, util, press.below(), press);
        // Display actual branch intermediates in processing order, rather than ores and results only.
        for (final net.minecraft.world.item.Item item : List.of(MagItems.SAMARIUM_OXIDE.get(),
                MagItems.SAMARIUM_POWDER.get(), MagItems.SAMARIUM_COBALT_ALLOY.get(), MagItems.SAMARIUM_COBALT_PLATE.get(),
                MagItems.SAMARIUM_COBALT_MAGNET_BLANK.get(), MagItems.SINTERED_SAMARIUM_COBALT.get())) {
            scene.overlay().showControls(util.vector().topOf(press), net.createmod.catnip.math.Pointing.DOWN, 35).withItem(new ItemStack(item));
            scene.idle(40);
        }
        final BlockPos smco = util.grid().at(1, 1, 3);
        final BlockPos ndfeb = util.grid().at(3, 1, 3);
        scene.world().setBlock(smco, MagBlocks.SAMARIUM_COBALT_MAGNET.get().defaultBlockState(), false);
        scene.world().setBlock(ndfeb, MagBlocks.NEODYMIUM_MAGNET.get().defaultBlockState(), false);
        show(scene, util, smco, ndfeb);
        text(scene, util, smco, smco,
                definition.text(1));
        for (final net.minecraft.world.item.Item item : List.of(MagItems.NEODYMIUM_OXIDE.get(), MagItems.DYSPROSIUM_OXIDE.get(),
                MagItems.BORON_DUST.get(), MagItems.NEODYMIUM_ALLOY_PLATE.get(), MagItems.NEODYMIUM_MAGNET_BLANK.get(), MagItems.SINTERED_NEODYMIUM.get())) {
            scene.overlay().showControls(util.vector().topOf(press), net.createmod.catnip.math.Pointing.DOWN, 35).withItem(new ItemStack(item));
            scene.idle(40);
        }
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
        for (int x = 0; x <= 4; x++) {
            scene.world().setBlock(new BlockPos(x, 0, 1), AllBlocks.TRACK.getDefaultState().setValue(com.simibubi.create.content.trains.track.TrackBlock.SHAPE,
                    com.simibubi.create.content.trains.track.TrackShape.XO), false);
        }
        scene.world().setBlock(new BlockPos(1, 1, 1), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(3, 1, 1), Blocks.IRON_BLOCK.defaultBlockState(), false);
        scene.world().setBlock(new BlockPos(2, 1, 1), Blocks.CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X), false);
        show(scene, util, new BlockPos(0, 0, 1), new BlockPos(4, 0, 1));
        final var consist = scene.world().showIndependentSection(util.select().fromTo(1, 1, 1, 3, 1, 1), Direction.DOWN);
        scene.world().moveSection(consist, new Vec3(1, 0, 0), 50);
        text(scene, util, coupler, coupler,
                definition.text(0));
        scene.world().moveSection(consist, new Vec3(-1, 0, 0), 70);
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
        scene.overlay().showControls(util.vector().topOf(copycat), net.createmod.catnip.math.Pointing.DOWN, 70)
                .rightClick().withItem(new ItemStack(Blocks.IRON_BLOCK));
        scene.world().modifyBlockEntity(copycat, BlockEntity.class, CopycatsPonderMaterials::applyIron);
        text(scene, util, copycat, material,
                definition.text(0));
        final var assembled = scene.world().makeSectionIndependent(util.select().position(copycat));
        scene.world().moveSection(assembled, new Vec3(0, 1, 0), 50);
        scene.overlay().showControls(util.vector().topOf(copycat.above()), net.createmod.catnip.math.Pointing.DOWN, 70)
                .withItem(new ItemStack(com.simibubi.create.AllItems.GOGGLES.get()));
        scene.overlay().showOutlineWithText(util.select().position(copycat), 90)
                .colored(PonderPalette.OUTPUT)
                .text(definition.text(1))
                .placeNearTarget();
        scene.idle(100);
    }

    private static void magneticShafts(SceneBuilder scene, SceneBuildingUtil util, PonderSceneCatalog.Scene definition) {
        prepare(scene, definition);
        var source = util.grid().at(1, 1, 2);
        var receiver = util.grid().at(3, 1, 2);
        scene.world().setBlock(source, MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), false);
        scene.world().setBlock(source.west(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST), false);
        scene.world().setBlock(receiver, MagBlocks.MAGNETIC_SHAFT.get().defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), false);
        show(scene, util, source.west(), receiver);
        text(scene, util, source, receiver, definition.text(0));
        scene.world().setBlock(util.grid().at(2, 1, 3), MagBlocks.SAMARIUM_COBALT_MAGNETIC_SHAFT.get().defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 3), MagBlocks.NEODYMIUM_MAGNETIC_SHAFT.get().defaultBlockState(), false);
        show(scene, util, util.grid().at(2, 1, 3), util.grid().at(3, 1, 3));
        text(scene, util, source, util.grid().at(3, 1, 3), definition.text(1));
        scene.world().setBlock(source.west(), Blocks.AIR.defaultBlockState(), false);
        text(scene, util, source, receiver, definition.text(2));
        text(scene, util, source, receiver, definition.text(3));
    }

    private static PonderStoryBoard genericScene(final PonderSceneCatalog.Scene definition, final Block block) {
        return (scene, util) -> {
            prepare(scene, definition);
            final BlockPos pos = new BlockPos(2, 1, 2);
            scene.world().setBlock(pos, block.defaultBlockState(), false);
            show(scene, util, pos, pos);
            text(scene, util, pos, pos, definition.text(0));
        };
    }

    private static PonderStoryBoard machineScene(final PonderSceneCatalog.Scene definition, final Block block) {
        return (scene, util) -> {
            prepare(scene, definition);
            final BlockPos pos = new BlockPos(2, 1, 2);
            BlockState state = block.defaultBlockState();
            if (state.hasProperty(DirectionalBlock.FACING)) state = state.setValue(DirectionalBlock.FACING,
                    definition.id().equals("homopolar_motor") ? Direction.EAST
                            : definition.id().equals("dipole_electromagnet") ? Direction.WEST : Direction.SOUTH);
            scene.world().setBlock(pos, state, false);
            show(scene, util, pos, pos);
            text(scene, util, pos, pos, definition.text(0));
            if (definition.rightClickHint()) scene.overlay().showControls(util.vector().topOf(pos),
                    net.createmod.catnip.math.Pointing.DOWN, 70).rightClick();
            switch (definition.id()) {
                case "electrolyzer", "mhd_jet", "micro_thruster" -> fluidMachine(scene, util, definition, pos);
                case "homopolar_motor" -> motor(scene, util, definition, pos);
                case "solar_sail", "kinetic_coil", "structural_inducer" -> movingMachine(scene, util, definition, pos);
                case "dipole_electromagnet" -> dipole(scene, util, definition, pos);
                default -> throw new IllegalArgumentException("Missing demonstration: " + definition.id());
            }
        };
    }

    private static void pipe(final SceneBuilder scene, final SceneBuildingUtil util, final BlockPos pos,
                             final Direction.Axis axis) {
        BlockState state = AllBlocks.FLUID_PIPE.getDefaultState();
        if (axis == Direction.Axis.X) state = state.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true);
        if (axis == Direction.Axis.Y) state = state.setValue(BlockStateProperties.UP, true).setValue(BlockStateProperties.DOWN, true);
        if (axis == Direction.Axis.Z) state = state.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.SOUTH, true);
        scene.world().setBlock(pos, state, false);
    }

    private static void supplyDiagram(final SceneBuilder scene, final SceneBuildingUtil util, final BlockPos pos) {
        pipe(scene, util, pos.west(), Direction.Axis.X);
        scene.world().setBlock(pos.west(2), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(pos.east(), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        show(scene, util, pos.west(2), pos.east());
        scene.overlay().showBigLine(PonderPalette.INPUT, Vec3.atCenterOf(pos.west(2)), Vec3.atCenterOf(pos), 80);
        scene.overlay().showBigLine(PonderPalette.RED, Vec3.atCenterOf(pos.east()), Vec3.atCenterOf(pos), 80);
    }

    private static void fluidMachine(final SceneBuilder scene, final SceneBuildingUtil util,
                                     final PonderSceneCatalog.Scene definition, final BlockPos pos) {
        supplyDiagram(scene, util, pos);
        if (definition.id().equals("electrolyzer")) {
            scene.world().modifyBlockEntity(pos, com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity.class, be -> {
                be.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                be.energyBuffer().receiveEnergy(100000, false);
            });
        } else if (definition.id().equals("mhd_jet")) {
            scene.overlay().showControls(util.vector().topOf(pos), net.createmod.catnip.math.Pointing.DOWN, 70)
                    .rightClick().withItem(new ItemStack(MagBlocks.PERMANENT_MAGNET.get()));
            scene.world().modifyBlockEntity(pos, com.stonytark.magnetization.content.jet.MhdJetBlockEntity.class, be -> {
                be.setMagnet(new ItemStack(MagBlocks.PERMANENT_MAGNET.get()));
                be.fluidHandler().fill(new FluidStack(MagFluids.GALLIUM.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                be.energyBuffer().receiveEnergy(100000, false);
            });
        } else {
            scene.world().modifyBlockEntity(pos, com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity.class, be -> {
                be.fluidHandler().fill(new FluidStack(MagFluids.FERROFLUID.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                be.energyBuffer().receiveEnergy(100000, false);
            });
        }
        text(scene, util, pos.west(2), pos.east(), definition.text(1));
        if (definition.id().equals("electrolyzer")) {
            final BlockPos output = pos.north();
            pipe(scene, util, output, Direction.Axis.Z);
            show(scene, util, output, output);
            scene.world().setBlock(output.north(), Blocks.WHITE_STAINED_GLASS.defaultBlockState(), false);
            final var hydrogen = scene.world().showIndependentSection(util.select().position(output.north()), Direction.DOWN);
            scene.world().moveSection(hydrogen, new Vec3(0, 1.5, 0), 60);
        } else {
            scene.world().setBlock(pos.above(), Blocks.IRON_BLOCK.defaultBlockState(), false);
            final var craft = scene.world().showIndependentSection(util.select().fromTo(pos, pos.above()), Direction.DOWN);
            scene.overlay().showBigLine(PonderPalette.RED, Vec3.atCenterOf(pos), Vec3.atCenterOf(pos.south(2)), 80);
            scene.world().moveSection(craft, new Vec3(0, 0, -1), 60);
        }
        text(scene, util, pos, pos.above(), definition.text(2));
    }

    private static void motor(final SceneBuilder scene, final SceneBuildingUtil util,
                              final PonderSceneCatalog.Scene definition, final BlockPos pos) {
        final BlockPos shaft = pos.east();
        scene.world().setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), false);
        show(scene, util, shaft, shaft);
        scene.overlay().showControls(util.vector().topOf(pos), net.createmod.catnip.math.Pointing.DOWN, 70)
                .rightClick().withItem(new ItemStack(MagBlocks.PERMANENT_MAGNET.get()));
        scene.world().modifyBlockEntity(pos, com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity.class,
                be -> be.setMagnet(new ItemStack(MagBlocks.PERMANENT_MAGNET.get())));
        final var create = new CreateSceneBuilder(scene);
        create.world().setKineticSpeed(util.select().fromTo(pos, shaft),
                com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity.speedFor(new ItemStack(MagBlocks.PERMANENT_MAGNET.get())));
        text(scene, util, pos, shaft, definition.text(1));
        scene.world().modifyBlockEntity(pos, com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity.class,
                be -> be.setMagnet(new ItemStack(MagBlocks.NEODYMIUM_MAGNET.get())));
        create.world().setKineticSpeed(util.select().fromTo(pos, shaft),
                com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity.speedFor(new ItemStack(MagBlocks.NEODYMIUM_MAGNET.get())));
        text(scene, util, pos, shaft, definition.text(2));
    }

    private static void movingMachine(final SceneBuilder scene, final SceneBuildingUtil util,
                                      final PonderSceneCatalog.Scene definition, final BlockPos pos) {
        final BlockPos hull = definition.id().equals("structural_inducer") ? pos.north(2) : pos.above();
        scene.world().setBlock(hull, Blocks.IRON_BLOCK.defaultBlockState(), false);
        final var craft = scene.world().showIndependentSection(definition.id().equals("solar_sail")
                ? util.select().fromTo(pos, hull) : util.select().position(hull), Direction.DOWN);
        if (definition.id().equals("structural_inducer")) {
            scene.world().setBlock(pos.west(), Blocks.GOLD_BLOCK.defaultBlockState(), false);
            show(scene, util, pos.west(), pos.west());
        }
        scene.world().moveSection(craft, definition.id().equals("solar_sail") ? new Vec3(0, 0, -1.5)
                : definition.id().equals("structural_inducer") ? new Vec3(0, 0, 0.5) : new Vec3(1.5, 0, 0), 60);
        text(scene, util, pos, hull, definition.text(1));
        if (definition.id().equals("kinetic_coil")) {
            scene.world().setBlock(pos.east(), Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true), false);
            show(scene, util, pos.east(), pos.east());
            scene.effects().indicateRedstone(pos);
        } else if (definition.id().equals("solar_sail")) {
            scene.world().setBlock(pos.west(), Blocks.BLACK_STAINED_GLASS.defaultBlockState(), false);
            show(scene, util, pos.west(), pos.west());
            scene.overlay().showControls(util.vector().topOf(pos), net.createmod.catnip.math.Pointing.DOWN, 70).rightClick();
            scene.world().modifyBlockEntity(pos, com.stonytark.magnetization.content.sail.SolarSailBlockEntity.class,
                    com.stonytark.magnetization.content.sail.SolarSailBlockEntity::toggleNightDisabled);
        } else scene.world().moveSection(craft, new Vec3(0, 0, 0.5), 60);
        text(scene, util, pos, hull, definition.text(2));
    }

    private static void dipole(final SceneBuilder scene, final SceneBuildingUtil util,
                               final PonderSceneCatalog.Scene definition, final BlockPos pos) {
        scene.world().setBlock(pos.west(), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(pos.east(), Blocks.RED_STAINED_GLASS.defaultBlockState(), false);
        scene.world().setBlock(pos.south(), Blocks.GOLD_BLOCK.defaultBlockState(), false);
        show(scene, util, pos.west(), pos.east());
        show(scene, util, pos.south(), pos.south());
        text(scene, util, pos.west(), pos.east(), definition.text(1));
        final var target = scene.world().createItemEntity(Vec3.atCenterOf(pos.west()).add(0, 1, 0),
                new Vec3(-0.06, 0, 0), new ItemStack(Items.IRON_INGOT));
        scene.world().modifyEntity(target, e -> e.setNoGravity(true));
        scene.overlay().showBigLine(PonderPalette.OUTPUT, Vec3.atCenterOf(pos.west()), Vec3.atCenterOf(pos.west(2)), 80);
        text(scene, util, pos.west(), pos.east(), definition.text(2));
    }
}
