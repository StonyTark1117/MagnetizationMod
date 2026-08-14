package com.stonytark.magnetization.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity;
import com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reproducible, development-only 1.4.1 manual-playtest worlds. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class PlaytestWorldSetup {
    private static final String ENABLED_PROPERTY = "magnetization.playtest";
    private static final String PRESET_PROPERTY = "magnetization.playtestPreset";
    private static final String ROOT_TAG = "magnetization:playtest_setup";
    // Bump whenever the staged footprint changes so existing disposable saves
    // are rebuilt on the next login instead of silently retaining an old lab.
    private static final int VERSION = 5;
    private static final int LAB_X = 64;
    private static final int LAB_Z = 64;

    private PlaytestWorldSetup() {}

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("playtest")
                .requires(source -> enabled() && source.hasPermission(2))
                .then(Commands.literal("lab")
                        .then(Commands.literal("setup").executes(ctx -> setup(ctx.getSource(), Preset.LAB)))
                        .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource(), Preset.LAB)))
                        .then(Commands.literal("kit").executes(ctx -> kit(ctx.getSource(), Preset.LAB))))
                .then(Commands.literal("survival")
                        .then(Commands.literal("setup").executes(ctx -> setup(ctx.getSource(), Preset.SURVIVAL)))
                        .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource(), Preset.SURVIVAL)))
                        .then(Commands.literal("kit").executes(ctx -> kit(ctx.getSource(), Preset.SURVIVAL))))
                .then(Commands.literal("goto")
                        .then(Commands.argument("station", StringArgumentType.word())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        STATIONS.keySet(), builder))
                                .executes(ctx -> goTo(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "station")))))
                .then(Commands.literal("scenario")
                        .then(Commands.literal("persistence")
                                .then(Commands.literal("seed")
                                        .executes(ctx -> seedPersistence(ctx.getSource())))
                                .then(Commands.literal("verify")
                                        .executes(ctx -> verifyPersistence(ctx.getSource()))))
                        .then(Commands.literal("railgun_auto")
                                .executes(ctx -> seedRailgunAutoAssembly(ctx.getSource())))
                        .then(Commands.literal("thruster_exhaust")
                                .executes(ctx -> seedThrusterExhaust(ctx.getSource())))
                        .then(Commands.literal("gas_exciter_hud")
                                .executes(ctx -> seedGasExciterHud(ctx.getSource()))))
                .then(Commands.literal("where").executes(ctx -> where(ctx.getSource())));
    }

    private static final Map<String, BlockPos> STATIONS = Map.ofEntries(
            Map.entry("overview", new BlockPos(2, 2, 2)),
            Map.entry("gallery", new BlockPos(8, 2, 8)),
            Map.entry("electrolyzer", new BlockPos(2, 2, 21)),
            // Stand on the south coil and look directly into the center core;
            // this remains stable through the chat-fade delay and avoids the
            // perimeter coil intercepting the controller interaction.
            Map.entry("tokamak", new BlockPos(14, 1, 19)),
            Map.entry("fusion", new BlockPos(27, 2, 22)),
            Map.entry("railgun", new BlockPos(37, 2, 25)),
            Map.entry("dipoles", new BlockPos(52, 2, 22)),
            Map.entry("automation", new BlockPos(4, 2, 35)),
            Map.entry("gallium", new BlockPos(16, 2, 35)),
            Map.entry("golems", new BlockPos(16, 2, 35)),
            // Stand east of the supply chest so entity HUD captures cannot
            // accidentally target the chest through the golem model.
            Map.entry("golems_active", new BlockPos(20, 0, 35)),
            Map.entry("golem_magnetite_hud", new BlockPos(20, 0, 35)),
            Map.entry("golem_pyrrhotite_hud", new BlockPos(20, 0, 35)),
            Map.entry("golem_hematite_hud", new BlockPos(20, 0, 35)),
            Map.entry("golem_titanomagnetite_hud", new BlockPos(20, 0, 35)),
            Map.entry("golem_gallium_hud", new BlockPos(20, 0, 35)),
            Map.entry("golem_mr_fluid_soft_hud", new BlockPos(20, 0, 35)),
            Map.entry("golem_mr_fluid_hardened_hud", new BlockPos(20, 0, 35)),
            Map.entry("ship", new BlockPos(32, 2, 40)),
            Map.entry("portal", new BlockPos(51, 2, 40)),
            Map.entry("thruster_exhaust", new BlockPos(32, 2, 45)),
            Map.entry("gas_excitation", new BlockPos(8, 2, 56)),
            Map.entry("gas_exciter_hud", new BlockPos(2, 2, 56)),
            Map.entry("gas_detector", new BlockPos(8, 2, 56)),
            Map.entry("ponder_tokamak", new BlockPos(2, 2, 2)),
            Map.entry("ponder_railgun", new BlockPos(2, 2, 2)),
            Map.entry("air_separator", new BlockPos(20, 2, 56)),
            Map.entry("ion_thruster", new BlockPos(33, 2, 56)),
            Map.entry("rare_earth", new BlockPos(51, 2, 58)));

    @SubscribeEvent
    public static void onLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        final Preset preset = Preset.parse(System.getProperty(PRESET_PROPERTY, ""));
        if (preset == null) return;
        final CompoundTag state = state(player);
        if (state.getInt("Version") == VERSION && preset.id.equals(state.getString("Preset"))) return;
        setup(player, preset, false);
    }

    private static int setup(final CommandSourceStack source, final Preset preset) {
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("This playtest setup must be run by a player."));
            return 0;
        }
        setup(player, preset, true);
        return 1;
    }

    private static void setup(final ServerPlayer player, final Preset preset, final boolean relocate) {
        final ServerLevel level = player.serverLevel();
        final BlockPos anchor = relocate ? chooseAnchor(player) : savedAnchor(player, preset);
        clear(level, anchor);
        buildFloor(level, anchor);
        if (preset == Preset.LAB) buildLab(level, anchor);
        else buildSurvival(level, anchor);
        saveState(player, preset, anchor);
        giveKit(player, preset);
        player.setGameMode(preset == Preset.LAB ? GameType.CREATIVE : GameType.SURVIVAL);
        level.setDefaultSpawnPos(anchor.offset(2, 1, 2), 0.0f);
        player.teleportTo(level, anchor.getX() + 2.5, anchor.getY() + 2.0, anchor.getZ() + 2.5,
                java.util.Set.of(), 0.0f, 0.0f);
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        level.setDayTime(6000L);
        level.setWeatherParameters(0, 120000, false, false);
        final String version = net.neoforged.fml.ModList.get().getModContainerById(Magnetization.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("development");
        player.sendSystemMessage(Component.literal("Magnetization " + version + " " + preset.display
                + " staged at " + pos(anchor) + ". Use /magnetization playtest " + preset.id
                + " reset to rebuild it."));
    }

    private static int reset(final CommandSourceStack source, final Preset preset) {
        final ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("This playtest reset must be run by a player."));
            return 0;
        }
        final BlockPos anchor = savedAnchor(player, preset);
        clear(player.serverLevel(), anchor);
        buildFloor(player.serverLevel(), anchor);
        if (preset == Preset.LAB) buildLab(player.serverLevel(), anchor);
        else buildSurvival(player.serverLevel(), anchor);
        giveKit(player, preset);
        player.serverLevel().setDefaultSpawnPos(anchor.offset(2, 1, 2), 0.0f);
        player.teleportTo(player.serverLevel(), anchor.getX() + 2.5, anchor.getY() + 2.0,
                anchor.getZ() + 2.5, java.util.Set.of(), 0.0f, 0.0f);
        source.sendSuccess(() -> Component.literal(preset.display + " rebuilt at " + pos(anchor)), false);
        return 1;
    }

    private static int kit(final CommandSourceStack source, final Preset preset) {
        try {
            giveKit(source.getPlayerOrException(), preset);
            source.sendSuccess(() -> Component.literal(preset.display + " inventory kit restored."), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int where(final CommandSourceStack source) {
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final CompoundTag tag = state(player);
            source.sendSuccess(() -> Component.literal("Playtest preset=" + tag.getString("Preset")
                    + " anchor=" + tag.getInt("X") + "," + tag.getInt("Y") + "," + tag.getInt("Z")), false);
            return 1;
        } catch (final Exception exception) {
            return 0;
        }
    }

    private static int goTo(final CommandSourceStack source, final String stationName) {
        final BlockPos offset = STATIONS.get(stationName.toLowerCase(Locale.ROOT));
        if (offset == null) {
            source.sendFailure(Component.literal("Unknown station '" + stationName + "'. Options: "
                    + String.join(", ", STATIONS.keySet())));
            return 0;
        }
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final CompoundTag tag = state(player);
            if (tag.getInt("Version") != VERSION) {
                source.sendFailure(Component.literal("No playtest preset has been staged for this player."));
                return 0;
            }
            final BlockPos anchor = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
            final BlockPos target = anchor.offset(offset);
            final float pitch = switch (stationName.toLowerCase(Locale.ROOT)) {
                case "tokamak" -> 65.0f;
                case "railgun" -> 30.0f;
                case "fusion" -> 0.0f;
                case "dipoles", "air_separator" -> 10.0f;
                case "electrolyzer" -> 15.0f;
                case "golems_active", "golem_magnetite_hud", "golem_pyrrhotite_hud",
                        "golem_hematite_hud", "golem_titanomagnetite_hud", "golem_gallium_hud",
                        "golem_mr_fluid_soft_hud", "golem_mr_fluid_hardened_hud" -> 0.0f;
                default -> 15.0f;
            };
            player.teleportTo(player.serverLevel(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                    Set.of(), 180.0f, pitch);
            source.sendSuccess(() -> Component.literal("Station " + stationName + " at " + pos(target)), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
    }

    private static int seedPersistence(final CommandSourceStack source) {
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final BlockPos anchor = currentAnchor(player);
            seedPersistence(player.serverLevel(), anchor);
            source.sendSuccess(() -> Component.literal("PLAYTEST_ASSERT SEEDED persistence"), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("Unable to seed persistence scenario: " + exception.getMessage()));
            return 0;
        }
    }

    private static int seedGasExciterHud(final CommandSourceStack source) {
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final BlockPos anchor = currentAnchor(player);
            final BlockPos pos = anchor.offset(2, 0, 51);
            final var exciter = blockEntity(player.serverLevel(), pos,
                    com.stonytark.magnetization.content.gas.GasExciterBlockEntity.class);
            player.serverLevel().setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            player.serverLevel().setBlock(pos.east(), MagBlocks.ARGON_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            // Capability receive limits are per call/tick. Fill the complete
            // fixture buffer so it remains active after command chat fades and
            // the HUD screenshot is taken, rather than accepting one 100-FE
            // call and going idle five ticks later.
            while (exciter.energyBuffer().getEnergyStored() < exciter.energyBuffer().getMaxEnergyStored()
                    && exciter.energyBuffer().receiveEnergy(
                            exciter.energyBuffer().getMaxEnergyStored(), false) > 0) {
                // Continue until the configured capacity or a zero-receive setup.
            }
            com.stonytark.magnetization.content.gas.GasExciterBlockEntity.serverTick(
                    player.serverLevel(), pos, player.serverLevel().getBlockState(pos), exciter);
            source.sendSuccess(() -> Component.literal("Active Gas Exciter HUD fixture staged"), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("Could not stage Gas Exciter HUD: " + exception.getMessage()));
            return 0;
        }
    }

    private static int verifyPersistence(final CommandSourceStack source) {
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final BlockPos anchor = currentAnchor(player);
            final boolean valid = persistenceStateValid(player.serverLevel(), anchor);
            if (!valid) {
                source.sendFailure(Component.literal("PLAYTEST_ASSERT FAIL persistence "
                        + persistenceSummary(player.serverLevel(), anchor)));
                return 0;
            }
            source.sendSuccess(() -> Component.literal("PLAYTEST_ASSERT PASS persistence"), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal("PLAYTEST_ASSERT FAIL persistence: " + exception.getMessage()));
            return 0;
        }
    }

    private static int seedRailgunAutoAssembly(final CommandSourceStack source) {
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final int staged = stageRailgunAutoAssembly(player.serverLevel(), currentPresetAnchor(player));
            source.sendSuccess(() -> Component.literal(
                    "PLAYTEST_ASSERT SEEDED railgun_auto blocks=" + staged), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal(
                    "PLAYTEST_ASSERT FAIL railgun_auto: " + exception.getMessage()));
            return 0;
        }
    }

    private static int seedThrusterExhaust(final CommandSourceStack source) {
        try {
            final ServerPlayer player = source.getPlayerOrException();
            final int blocks = stageThrusterExhaust(player.serverLevel(), currentAnchor(player));
            source.sendSuccess(() -> Component.literal(
                    "PLAYTEST_ASSERT SEEDED thruster_exhaust blocks=" + blocks), false);
            return 1;
        } catch (final Exception exception) {
            source.sendFailure(Component.literal(
                    "PLAYTEST_ASSERT FAIL thruster_exhaust: " + exception.getMessage()));
            return 0;
        }
    }

    private static BlockPos currentAnchor(final ServerPlayer player) {
        final CompoundTag tag = state(player);
        if (tag.getInt("Version") != VERSION || !Preset.LAB.id.equals(tag.getString("Preset"))) {
            throw new IllegalStateException("This scenario requires a staged lab preset");
        }
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    private static BlockPos currentPresetAnchor(final ServerPlayer player) {
        final CompoundTag tag = state(player);
        if (tag.getInt("Version") != VERSION || Preset.parse(tag.getString("Preset")) == null) {
            throw new IllegalStateException("This scenario requires a staged playtest preset");
        }
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    static void seedPersistence(final ServerLevel level, final BlockPos anchor) {
        final ElectrolyzerBlockEntity electrolyzer = blockEntity(level, anchor.offset(2, 0, 17),
                ElectrolyzerBlockEntity.class);
        electrolyzer.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        fillEnergy(electrolyzer.energyBuffer());

        final TokamakControllerBlockEntity tokamak = blockEntity(level, anchor.offset(14, 0, 18),
                TokamakControllerBlockEntity.class);
        tokamak.coolantHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        tokamak.fuelContainer().setItem(0, new ItemStack(MagItems.TRITIUM_CELL.get(), 2));
        fillEnergy(tokamak.energyBuffer());
        TokamakControllerBlockEntity.serverTick(level, tokamak.getBlockPos(), tokamak.getBlockState(), tokamak);

        final FusionThrusterBlockEntity fusion = blockEntity(level, anchor.offset(26, 1, 18),
                FusionThrusterBlockEntity.class);
        FusionThrusterBlockEntity.serverTick(level, fusion.getBlockPos(), fusion.getBlockState(), fusion);
        fusion.fluidHandler().fill(new FluidStack(MagFluids.HELIUM_3.get(), 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        fusion.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2_000),
                IFluidHandler.FluidAction.EXECUTE);
        fillEnergy(fusion.energyBuffer());

        final RailgunEmitterBlockEntity railgun = blockEntity(level, anchor.offset(37, 0, 22),
                RailgunEmitterBlockEntity.class);
        fillEnergy(railgun.energyBuffer());
        railgun.setManualMode(true);
        railgun.setRailLength(8);
        railgun.setArcState(RailgunEmitterBlockEntity.ArcState.HOLDING);
        railgun.setChanged();
    }

    static int stageRailgunAutoAssembly(final ServerLevel level, final BlockPos anchor) {
        final BlockPos first = anchor.offset(37, 0, 22);
        final BlockPos second = anchor.offset(41, 0, 22);
        buildRail(level, first);
        buildRail(level, second);
        final RailgunEmitterBlockEntity a = blockEntity(level, first, RailgunEmitterBlockEntity.class);
        final RailgunEmitterBlockEntity b = blockEntity(level, second, RailgunEmitterBlockEntity.class);
        fillEnergy(a.energyBuffer());
        fillEnergy(b.energyBuffer());
        // Match the proven end-to-end fixture: a paired remote keeps the newly
        // assembled projectile held in the channel long enough for visual review
        // instead of launching it before the 12-second chat-fade boundary.
        a.remoteContainer().setItem(0, new ItemStack(MagItems.RAILGUN_REMOTE.get()));
        a.setManualMode(true);
        b.setManualMode(true);
        a.setAutoAssemble(true);
        b.setAutoAssemble(true);
        a.setRailLength(8);
        b.setRailLength(8);

        int staged = 0;
        for (int across = 1; across <= 3; across++) {
            for (int along = 1; along <= 3; along++) {
                final Block block = (across + along) % 2 == 0 ? Blocks.IRON_BLOCK : Blocks.COPPER_BLOCK;
                set(level, first.offset(across, -1, -along), block);
                staged++;
            }
        }
        return staged;
    }

    static int stageThrusterExhaust(final ServerLevel level, final BlockPos anchor) {
        // Four active engine styles on one connected Sable test craft. They face
        // south so their exhaust points back toward the station camera while the
        // craft accelerates safely north through the marked ship lane.
        final BlockPos origin = anchor.offset(28, 4, 39);
        final Direction facing = Direction.SOUTH;
        final List<BlockPos> blocks = new ArrayList<>();

        setFacing(level, origin, MagBlocks.MICRO_THRUSTER.get(), facing);
        set(level, origin.east(), Blocks.IRON_BLOCK);
        setFacing(level, origin.east(2), MagBlocks.MHD_JET.get(), facing);
        set(level, origin.east(3), Blocks.IRON_BLOCK);
        setFacing(level, origin.east(4), MagBlocks.ION_THRUSTER.get(), facing);
        set(level, origin.east(5), Blocks.IRON_BLOCK);
        for (int x = 0; x <= 5; x++) blocks.add(origin.east(x));

        final BlockPos fusionBase = origin.offset(6, -1, 0);
        buildFusionPanel(level, fusionBase, facing);
        for (int x = 0; x < 5; x++) for (int y = 0; y < 3; y++) {
            blocks.add(fusionBase.offset(x, y, 0));
        }

        final var micro = blockEntity(level, origin,
                com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity.class);
        micro.fluidHandler().fill(new FluidStack(MagFluids.FERROFLUID.get(), 4_000),
                IFluidHandler.FluidAction.EXECUTE);
        micro.energyBuffer().receiveEnergy(16_000, false);

        final var mhd = blockEntity(level, origin.east(2),
                com.stonytark.magnetization.content.jet.MhdJetBlockEntity.class);
        mhd.setMagnet(new ItemStack(MagItems.NEODYMIUM_MAGNET.get()));
        mhd.fluidHandler().fill(new FluidStack(MagFluids.GALLIUM.get(), 4_000),
                IFluidHandler.FluidAction.EXECUTE);
        mhd.energyBuffer().receiveEnergy(16_000, false);

        final var ion = blockEntity(level, origin.east(4),
                com.stonytark.magnetization.content.jet.IonThrusterBlockEntity.class);
        ion.fluidHandler().fill(new FluidStack(MagFluids.XENON.get(), 4_000),
                IFluidHandler.FluidAction.EXECUTE);
        ion.energyBuffer().receiveEnergy(16_000, false);

        final FusionThrusterBlockEntity fusion = blockEntity(level, fusionBase.offset(1, 1, 0),
                FusionThrusterBlockEntity.class);
        fusion.fluidHandler().fill(new FluidStack(MagFluids.HELIUM_3.get(), 4_000),
                IFluidHandler.FluidAction.EXECUTE);
        fusion.fluidHandler().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4_000),
                IFluidHandler.FluidAction.EXECUTE);
        fusion.energyBuffer().receiveEnergy(32_000, false);

        final var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                origin.getX(), fusionBase.getY(), origin.getZ(),
                fusionBase.getX() + 5, fusionBase.getY() + 3, origin.getZ() + 1);
        final var ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                level, origin, blocks, bounds);
        if (ship.getMassTracker().isInvalid()) {
            throw new IllegalStateException("Thruster preview assembled with invalid mass");
        }
        return blocks.size();
    }

    static boolean persistenceStateValid(final ServerLevel level, final BlockPos anchor) {
        final ElectrolyzerBlockEntity electrolyzer = blockEntity(level, anchor.offset(2, 0, 17),
                ElectrolyzerBlockEntity.class);
        final TokamakControllerBlockEntity tokamak = blockEntity(level, anchor.offset(14, 0, 18),
                TokamakControllerBlockEntity.class);
        final FusionThrusterBlockEntity fusion = blockEntity(level, anchor.offset(26, 1, 18),
                FusionThrusterBlockEntity.class);
        final RailgunEmitterBlockEntity railgun = blockEntity(level, anchor.offset(37, 0, 22),
                RailgunEmitterBlockEntity.class);
        return electrolyzer.waterAmount() > 0 && electrolyzer.energyBuffer().getEnergyStored() > 0
                && !tokamak.fuelContainer().isEmpty() && tokamak.coolantStored() > 0
                && tokamak.energyBuffer().getEnergyStored() > 0
                && fusion.guiStat1() >= 2_000 && fusion.coolantStored() >= 2_000
                && fusion.energyBuffer().getEnergyStored() > 0
                && railgun.energyBuffer().getEnergyStored() > 0 && railgun.manualMode()
                && railgun.railLength() == 8;
    }

    private static String persistenceSummary(final ServerLevel level, final BlockPos anchor) {
        final ElectrolyzerBlockEntity electrolyzer = blockEntity(level, anchor.offset(2, 0, 17),
                ElectrolyzerBlockEntity.class);
        final TokamakControllerBlockEntity tokamak = blockEntity(level, anchor.offset(14, 0, 18),
                TokamakControllerBlockEntity.class);
        final FusionThrusterBlockEntity fusion = blockEntity(level, anchor.offset(26, 1, 18),
                FusionThrusterBlockEntity.class);
        final RailgunEmitterBlockEntity railgun = blockEntity(level, anchor.offset(37, 0, 22),
                RailgunEmitterBlockEntity.class);
        return "electrolyzer=" + electrolyzer.waterAmount() + "mB/"
                + electrolyzer.energyBuffer().getEnergyStored() + "FE tokamak="
                + tokamak.fuelContainer().getItem(0).getCount() + "fuel/" + tokamak.coolantStored() + "mB water/"
                + tokamak.energyBuffer().getEnergyStored() + "FE fusion=" + fusion.guiStat1() + "mB/"
                + fusion.coolantStored() + "mB water/"
                + fusion.energyBuffer().getEnergyStored() + "FE railgun="
                + railgun.energyBuffer().getEnergyStored() + "FE/manual=" + railgun.manualMode()
                + "/length=" + railgun.railLength();
    }

    private static <T> T blockEntity(final ServerLevel level, final BlockPos pos, final Class<T> type) {
        final Object blockEntity = level.getBlockEntity(pos);
        if (!type.isInstance(blockEntity)) {
            throw new IllegalStateException("Expected " + type.getSimpleName() + " at " + pos);
        }
        return type.cast(blockEntity);
    }

    private static void fillEnergy(final net.neoforged.neoforge.energy.IEnergyStorage storage) {
        int previous = -1;
        while (storage.getEnergyStored() < storage.getMaxEnergyStored()
                && storage.getEnergyStored() != previous) {
            previous = storage.getEnergyStored();
            storage.receiveEnergy(storage.getMaxEnergyStored(), false);
        }
    }

    private static void buildFloor(final ServerLevel level, final BlockPos anchor) {
        fill(level, anchor.offset(0, -1, 0), anchor.offset(LAB_X - 1, -1, LAB_Z - 1), Blocks.SMOOTH_STONE);
        for (int x = 0; x < LAB_X; x++) {
            set(level, anchor.offset(x, 0, 0), Blocks.YELLOW_CONCRETE);
            set(level, anchor.offset(x, 0, LAB_Z - 1), Blocks.YELLOW_CONCRETE);
        }
        for (int z = 0; z < LAB_Z; z++) {
            set(level, anchor.offset(0, 0, z), Blocks.YELLOW_CONCRETE);
            set(level, anchor.offset(LAB_X - 1, 0, z), Blocks.YELLOW_CONCRETE);
        }
    }

    /** Package-private entry used by the in-world integration test. */
    static void stageForTest(final ServerLevel level, final BlockPos anchor, final String presetName) {
        final Preset preset = Preset.parse(presetName);
        if (preset == null) throw new IllegalArgumentException("Unknown playtest preset: " + presetName);
        clear(level, anchor);
        buildFloor(level, anchor);
        if (preset == Preset.LAB) buildLab(level, anchor);
        else buildSurvival(level, anchor);
    }

    static void clearForTest(final ServerLevel level, final BlockPos anchor) {
        clear(level, anchor);
    }

    private static void buildLab(final ServerLevel level, final BlockPos a) {
        label(level, a.offset(2, 0, 2), "1.4.1 TEST LAB", "Reset: /magnetization", "playtest lab reset", "Stations ->");
        buildGallery(level, a.offset(2, 0, 5));
        buildAllItemsStorage(level, a.offset(2, 0, 10));

        label(level, a.offset(2, 0, 15), "ELECTROLYZER", "Inputs + rejection", "Full-output stall", "Supplies in chest");
        set(level, a.offset(2, 0, 17), MagBlocks.ELECTROLYZER.get());
        set(level, a.offset(4, 0, 17), MagBlocks.ELECTROLYZER.get());
        stockChest(level, a.offset(3, 0, 19), List.of(
                stack(Items.WATER_BUCKET, 8), stack(MagItems.HYDROGEN_BUCKET.get(), 4),
                stack(MagItems.TRITIUM_BUCKET.get(), 2), stack(Items.BUCKET, 8), stack(Items.REDSTONE_BLOCK, 4)));

        label(level, a.offset(12, 0, 15), "TOKAMAK", "Formed 3x3 + 5x5", "Solid cores + coolant", "Fuel + coolant chest");
        final BlockPos tokamak = a.offset(14, 0, 18);
        buildTokamak(level, tokamak, 1);
        buildTokamak(level, a.offset(19, 0, 18), 2);
        stockChest(level, a.offset(12, 0, 20), List.of(stack(MagItems.DEUTERIUM_CELL.get(), 8),
                stack(MagItems.TRITIUM_CELL.get(), 8), stack(MagItems.HELIUM_3_CELL.get(), 8),
                stack(Items.WATER_BUCKET, 8), stack(MagItems.DEUTERIUM_OXIDE_BUCKET.get(), 8),
                stack(MagItems.GALLIUM_BUCKET.get(), 8)));

        label(level, a.offset(23, 0, 15), "FUSION PANEL", "5x3 formed panel", "Heavy water coolant via frame", "Fuel + coolant chest");
        buildFusionPanel(level, a.offset(25, 0, 18));
        stockChest(level, a.offset(23, 0, 20), List.of(stack(MagItems.HELIUM_3_BUCKET.get(), 8),
                stack(MagItems.TRITIUM_BUCKET.get(), 8), stack(MagItems.HYDROGEN_BUCKET.get(), 8),
                stack(Items.WATER_BUCKET, 8), stack(MagItems.DEUTERIUM_OXIDE_BUCKET.get(), 8),
                stack(MagItems.GALLIUM_BUCKET.get(), 8)));

        label(level, a.offset(35, 0, 15), "RAILGUN", "Two 8-block rails", "Auto + remote", "Target lane north");
        buildRail(level, a.offset(37, 0, 22));
        buildRail(level, a.offset(41, 0, 22));
        stockChest(level, a.offset(35, 0, 20), List.of(stack(MagItems.RAILGUN_REMOTE.get(), 4),
                stack(Items.IRON_BLOCK, 32), stack(Items.COPPER_BLOCK, 32), stack(Items.REDSTONE_BLOCK, 8)));

        label(level, a.offset(48, 0, 15), "DIPOLES", "All six facings", "Redstone levels", "GUI + field checks");
        int i = 0;
        for (final Direction facing : Direction.values()) {
            final BlockPos pos = a.offset(48 + (i % 6) * 2, 0, 18 + (i / 6) * 3);
            BlockState state = MagBlocks.DIPOLE_ELECTROMAGNET.get().defaultBlockState();
            if (state.hasProperty(DirectionalBlock.FACING)) state = state.setValue(DirectionalBlock.FACING, facing);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            set(level, pos.below(), Blocks.REDSTONE_BLOCK);
            i++;
        }

        label(level, a.offset(2, 0, 27), "AUTOMATION", "Wrong inputs", "Empty containers", "Hopper/pipe safety");
        set(level, a.offset(2, 0, 30), Blocks.CHEST);
        set(level, a.offset(3, 0, 30), Blocks.HOPPER);
        set(level, a.offset(4, 0, 30), MagBlocks.ELECTROLYZER.get());
        stockChest(level, a.offset(2, 0, 32), List.of(stack(Items.COAL, 16), stack(Items.LAVA_BUCKET, 4),
                stack(Items.WATER_BUCKET, 8), stack(MagItems.TRITIUM_CELL.get(), 8), stack(Items.BUCKET, 16)));

        label(level, a.offset(14, 0, 27), "GOLEM LAB", "All oxide bodies", "Heat + polarizer", "Targets and MR checks");
        set(level, a.offset(14, 0, 30), MagBlocks.SOLID_GALLIUM.get());
        set(level, a.offset(16, 0, 30), MagBlocks.HARDENED_MR_FLUID.get());
        set(level, a.offset(18, 0, 30), MagBlocks.PERMANENT_MAGNET.get());
        stockChest(level, a.offset(16, 0, 32), List.of(stack(MagItems.MR_FLUID_GOLEM_SPAWN_EGG.get(), 8),
                stack(MagItems.MAGNETITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.PYRRHOTITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.HEMATITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.TITANOMAGNETITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.GALLIUM_BUCKET.get(), 8), stack(MagItems.MR_FLUID_BUCKET.get(), 8),
                stack(MagItems.MAGNETITE_BLOCK.get(), 16), stack(MagItems.PYRRHOTITE_BLOCK.get(), 16),
                stack(MagItems.HEMATITE_BLOCK.get(), 16), stack(MagItems.TITANOMAGNETITE_BLOCK.get(), 16),
                stack(MagItems.HEMATITE_LENS.get(), 2), stack(Items.CARVED_PUMPKIN, 16),
                stack(Items.CAMPFIRE, 4), stack(Items.LAVA_BUCKET, 4), stack(Items.IRON_INGOT, 32)));

        label(level, a.offset(25, 0, 27), "SHIP TEST LANE", "Assemble on blue pad", "Rotate + move", "Thruster/railgun parts");
        fill(level, a.offset(25, 0, 30), a.offset(39, 0, 42), Blocks.LIGHT_BLUE_CONCRETE);
        stockChest(level, a.offset(26, 1, 31), List.of(stack(Items.IRON_BLOCK, 64), stack(MagItems.FUSION_THRUSTER.get(), 16),
                stack(MagItems.TOKAMAK_COIL.get(), 32), stack(MagItems.GYROSTABILIZER.get(), 8),
                stack(MagItems.INDUCTION_PAD.get(), 16)));

        label(level, a.offset(43, 0, 27), "PORTAL LANE", "Build portal on purple", "Transfer inventory", "Check remotes/fields");
        fill(level, a.offset(43, 0, 30), a.offset(60, 0, 42), Blocks.PURPLE_CONCRETE);
        stockChest(level, a.offset(44, 1, 31), List.of(stack(Items.OBSIDIAN, 64), stack(Items.FLINT_AND_STEEL, 2),
                stack(MagItems.RAILGUN_EMITTER.get(), 8), stack(MagItems.RAILGUN_REMOTE.get(), 8),
                stack(MagItems.POLARITY_INVERTER.get(), 8), stack(MagItems.MAGNETITE_BLOCK.get(), 32)));

        label(level, a.offset(27, 0, 43), "THRUSTER EXHAUST", "Run scenario command", "4 active plume styles", "Cooled Fusion mist");

        label(level, a.offset(2, 0, 48), "GAS EXCITATION", "Dormant + lit gases", "Detector + vent", "Supplies in chest");
        set(level, a.offset(2, 0, 51), MagBlocks.GAS_EXCITER.get());
        set(level, a.offset(2, -1, 51), Blocks.REDSTONE_BLOCK);
        final List<Block> gases = List.of(MagBlocks.HELIUM_BLOCK.get(), MagBlocks.NEON_BLOCK.get(),
                MagBlocks.ARGON_BLOCK.get(), MagBlocks.KRYPTON_BLOCK.get(), MagBlocks.XENON_BLOCK.get(),
                MagBlocks.RADON_BLOCK.get());
        for (int gas = 0; gas < gases.size(); gas++) set(level, a.offset(3 + gas * 2, 0, 51), gases.get(gas));
        set(level, a.offset(15, 0, 51), MagBlocks.GAS_VENT.get());
        stockChest(level, a.offset(8, 0, 54), List.of(stack(MagItems.GAS_DETECTOR.get(), 2),
                stack(MagItems.HELIUM_BUCKET.get(), 4), stack(MagItems.NEON_BUCKET.get(), 4),
                stack(MagItems.ARGON_BUCKET.get(), 4), stack(MagItems.KRYPTON_BUCKET.get(), 4),
                stack(MagItems.XENON_BUCKET.get(), 4), stack(MagItems.RADON_BUCKET.get(), 4)));

        label(level, a.offset(18, 0, 48), "AIR SEPARATOR", "Five output tanks", "Assign every face", "Module + filters");
        set(level, a.offset(20, 0, 51), MagBlocks.AIR_SEPARATOR.get());
        stockChest(level, a.offset(22, 0, 51), List.of(stack(MagItems.ISOTOPE_SEPARATION_MODULE.get(), 4),
                stack(MagItems.AIR_FILTER.get(), 16), stack(MagItems.HELIUM_3_CRYSTAL.get(), 16),
                stack(Items.BUCKET, 16)));

        label(level, a.offset(29, 0, 48), "ION THRUSTER", "Ship-only drive", "Gas profiles + exhaust", "Propellants in chest");
        set(level, a.offset(31, 0, 51), MagBlocks.ION_THRUSTER.get());
        stockChest(level, a.offset(34, 0, 51), List.of(stack(MagItems.HELIUM_BUCKET.get(), 4),
                stack(MagItems.NEON_BUCKET.get(), 4), stack(MagItems.ARGON_BUCKET.get(), 4),
                stack(MagItems.KRYPTON_BUCKET.get(), 4), stack(MagItems.XENON_BUCKET.get(), 4),
                stack(MagItems.RADON_BUCKET.get(), 4), stack(Items.REDSTONE_BLOCK, 8)));

        label(level, a.offset(40, 0, 48), "RARE EARTH", "Four precursor ores", "SmCo -> NdFeB", "Chains in chests");
        final List<Block> rareEarthBlocks = List.of(
                MagBlocks.BASTNASITE_ORE.get(), MagBlocks.DEEPSLATE_BASTNASITE_ORE.get(),
                MagBlocks.MONAZITE_ORE.get(), MagBlocks.DEEPSLATE_MONAZITE_ORE.get(),
                MagBlocks.COBALTITE_ORE.get(), MagBlocks.DEEPSLATE_COBALTITE_ORE.get(),
                MagBlocks.BORAX_ORE.get(), MagBlocks.DEEPSLATE_BORAX_ORE.get(),
                MagBlocks.SAMARIUM_COBALT_BLOCK.get(), MagBlocks.NEODYMIUM_BLOCK.get(),
                MagBlocks.SAMARIUM_COBALT_MAGNET.get(), MagBlocks.NEODYMIUM_MAGNET.get());
        for (int block = 0; block < rareEarthBlocks.size(); block++) {
            set(level, a.offset(40 + (block % 6) * 3, 0, 51 + (block / 6) * 3), rareEarthBlocks.get(block));
        }
        stockChest(level, a.offset(58, 0, 54), List.of(
                stack(MagItems.BASTNASITE_CONCENTRATE.get(), 16), stack(MagItems.MONAZITE_CONCENTRATE.get(), 16),
                stack(MagItems.COBALTITE_CONCENTRATE.get(), 16), stack(MagItems.BORON_DUST.get(), 16),
                stack(MagItems.SAMARIUM_COBALT_ALLOY.get(), 16), stack(MagItems.NEODYMIUM_ALLOY.get(), 16),
                stack(MagItems.SAMARIUM_COBALT_MAGNET_BLANK.get(), 8), stack(MagItems.NEODYMIUM_MAGNET_BLANK.get(), 8),
                stack(MagItems.SINTERED_SAMARIUM_COBALT.get(), 8), stack(MagItems.SINTERED_NEODYMIUM.get(), 8)));
    }

    private static void buildSurvival(final ServerLevel level, final BlockPos a) {
        label(level, a.offset(2, 0, 2), "SURVIVAL CHAIN", "Raw inputs only", "Water -> Helium-3", "No output fuels supplied");
        fill(level, a.offset(1, 0, 4), a.offset(20, 0, 15), Blocks.OAK_PLANKS);
        set(level, a.offset(2, 1, 5), Blocks.CRAFTING_TABLE);
        set(level, a.offset(4, 1, 5), Blocks.FURNACE);
        set(level, a.offset(6, 1, 5), Blocks.BLAST_FURNACE);
        set(level, a.offset(9, 1, 5), MagBlocks.ELECTROLYZER.get());
        final BlockPos tokamak = a.offset(14, 1, 7);
        set(level, tokamak, MagBlocks.TOKAMAK_CONTROLLER.get());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx != 0 || dz != 0) set(level, tokamak.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
        }
        buildFusionPanel(level, a.offset(22, 1, 6));
        label(level, a.offset(35, 0, 15), "SURVIVAL RAILGUN", "Powered 8-block rail",
                "Vanilla payload blocks", "Craft and bind a remote");
        buildRail(level, a.offset(37, 0, 22));
        stockChest(level, a.offset(2, 1, 9), List.of(stack(Items.IRON_INGOT, 64), stack(Items.COPPER_INGOT, 64),
                stack(Items.REDSTONE, 64), stack(Items.QUARTZ, 32), stack(Items.COAL, 64),
                stack(MagItems.RAW_MAGNETITE.get(), 64), stack(MagItems.RAW_LITHIUM.get(), 64),
                stack(MagItems.RAW_GALLIUM.get(), 32), stack(MagItems.HELIUM_3_CRYSTAL.get(), 16),
                stack(MagItems.BASTNASITE_ORE.get(), 32), stack(MagItems.MONAZITE_ORE.get(), 32),
                stack(MagItems.COBALTITE_ORE.get(), 32), stack(MagItems.BORAX_ORE.get(), 32)));
        stockChest(level, a.offset(4, 1, 9), List.of(stack(Items.BUCKET, 16), stack(Items.WATER_BUCKET, 16),
                stack(Items.GLASS_BOTTLE, 32), stack(Items.CHEST, 8), stack(Items.HOPPER, 8),
                stack(Items.COPPER_BLOCK, 32), stack(Items.IRON_BLOCK, 32)));
        stockChest(level, a.offset(6, 1, 9), List.of(stack(MagItems.ELECTROLYZER.get(), 2),
                stack(MagItems.TOKAMAK_CONTROLLER.get(), 2), stack(MagItems.TOKAMAK_COIL.get(), 64),
                stack(MagItems.FUSION_THRUSTER.get(), 16), stack(MagItems.MHD_JET.get(), 4),
                stack(MagItems.MICRO_THRUSTER.get(), 4), stack(MagItems.GAS_EXCITER.get(), 2),
                stack(MagItems.GAS_VENT.get(), 2), stack(MagItems.AIR_SEPARATOR.get(), 2),
                stack(MagItems.ION_THRUSTER.get(), 2)));
    }

    private static void buildGallery(final ServerLevel level, final BlockPos origin) {
        label(level, origin, "TEXTURE GALLERY", "Ore/material/fuel", "Inspect all lighting", "Items in chests ->");
        final List<Block> blocks = List.of(MagBlocks.LITHIUM_ORE.get(), MagBlocks.DEEPSLATE_LITHIUM_ORE.get(),
                MagBlocks.HELIUM_3_GEODE.get(), MagBlocks.SOLID_HELIUM_3.get(),
                MagBlocks.SOLID_GALLIUM.get(), MagBlocks.MAGNETITE_BLOCK.get(),
                MagBlocks.HEMATITE_BLOCK.get(), MagBlocks.PYRRHOTITE_BLOCK.get(), MagBlocks.TITANOMAGNETITE_BLOCK.get(),
                MagBlocks.ELECTROLYZER.get(), MagBlocks.TOKAMAK_CONTROLLER.get(), MagBlocks.FUSION_THRUSTER.get(),
                MagBlocks.RAILGUN_EMITTER.get(), MagBlocks.DIPOLE_ELECTROMAGNET.get(), MagBlocks.MHD_JET.get(),
                MagBlocks.GAS_EXCITER.get(), MagBlocks.GAS_VENT.get(), MagBlocks.AIR_SEPARATOR.get(),
                MagBlocks.ION_THRUSTER.get(), MagBlocks.SAMARIUM_COBALT_BLOCK.get(), MagBlocks.NEODYMIUM_BLOCK.get(),
                MagBlocks.SAMARIUM_COBALT_MAGNET.get(), MagBlocks.NEODYMIUM_MAGNET.get());
        for (int i = 0; i < blocks.size(); i++) {
            set(level, origin.offset(8 + (i % 24) * 2, 0, (i / 24) * 2), blocks.get(i));
        }
    }

    private static void buildAllItemsStorage(final ServerLevel level, final BlockPos origin) {
        final List<ItemStack> all = new ArrayList<>();
        MagItems.REGISTER.getEntries().forEach(holder -> all.add(new ItemStack(holder.get())));
        int chest = 0;
        for (int from = 0; from < all.size(); from += 27) {
            stockChest(level, origin.offset(chest * 2, 0, 0), all.subList(from, Math.min(all.size(), from + 27)));
            chest++;
        }
    }

    private static void buildFusionPanel(final ServerLevel level, final BlockPos base) {
        buildFusionPanel(level, base, Direction.NORTH);
    }

    private static void buildFusionPanel(final ServerLevel level, final BlockPos base,
                                         final Direction facing) {
        for (int x = 0; x < 5; x++) for (int y = 0; y < 3; y++) {
            final boolean interior = x > 0 && x < 4 && y == 1;
            BlockState state = interior ? MagBlocks.FUSION_THRUSTER.get().defaultBlockState()
                    : MagBlocks.TOKAMAK_COIL.get().defaultBlockState();
            if (interior && state.hasProperty(DirectionalBlock.FACING)) {
                state = state.setValue(DirectionalBlock.FACING, facing);
            }
            level.setBlock(base.offset(x, y, 0), state, Block.UPDATE_ALL);
        }
    }

    private static void buildTokamak(final ServerLevel level, final BlockPos center,
                                     final int halfEdge) {
        for (int dx = -halfEdge; dx <= halfEdge; dx++) {
            for (int dz = -halfEdge; dz <= halfEdge; dz++) {
                final boolean perimeter = Math.abs(dx) == halfEdge || Math.abs(dz) == halfEdge;
                set(level, center.offset(dx, 0, dz), perimeter
                        ? MagBlocks.TOKAMAK_COIL.get() : MagBlocks.TOKAMAK_CONTROLLER.get());
            }
        }
    }

    private static void buildRail(final ServerLevel level, final BlockPos emitter) {
        BlockState state = MagBlocks.RAILGUN_EMITTER.get().defaultBlockState();
        if (state.hasProperty(DirectionalBlock.FACING)) state = state.setValue(DirectionalBlock.FACING, Direction.NORTH);
        level.setBlock(emitter, state, Block.UPDATE_ALL);
        set(level, emitter.below(), Blocks.REDSTONE_BLOCK);
        for (int z = 1; z <= 8; z++) set(level, emitter.north(z), Blocks.COPPER_BLOCK);
    }

    private static void giveKit(final ServerPlayer player, final Preset preset) {
        player.getInventory().clearContent();
        final List<ItemStack> kit = preset == Preset.LAB ? labKit() : survivalKit();
        for (final ItemStack stack : kit) {
            if (!player.getInventory().add(stack.copy())) player.drop(stack.copy(), false);
        }
    }

    private static List<ItemStack> labKit() {
        return List.of(stack(MagItems.FIELD_COMPASS.get(), 1), stack(MagItems.ORE_COMPASS.get(), 1),
                stack(MagItems.RAILGUN_REMOTE.get(), 2), stack(MagItems.MAGNETIC_GRAPPLE.get(), 1),
                stack(MagItems.REPULSOR_GUN.get(), 1), stack(MagItems.MR_FLUID_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.MAGNETITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.PYRRHOTITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.HEMATITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.TITANOMAGNETITE_GOLEM_SPAWN_EGG.get(), 4),
                stack(MagItems.HYDROGEN_BUCKET.get(), 4), stack(MagItems.DEUTERIUM_CELL.get(), 8),
                stack(MagItems.TRITIUM_CELL.get(), 8), stack(MagItems.HELIUM_3_CELL.get(), 8),
                stack(MagItems.TRITIUM_BUCKET.get(), 4), stack(MagItems.HELIUM_3_BUCKET.get(), 4),
                stack(MagItems.RAW_LITHIUM.get(), 32), stack(MagItems.LITHIUM.get(), 32),
                stack(MagItems.RAW_GALLIUM.get(), 32), stack(MagItems.GALLIUM_INGOT.get(), 32),
                stack(MagItems.SOLID_GALLIUM.get(), 16), stack(MagItems.DIPOLE_ELECTROMAGNET.get(), 16),
                stack(MagItems.ELECTROLYZER.get(), 8), stack(MagItems.TOKAMAK_CONTROLLER.get(), 8),
                stack(MagItems.TOKAMAK_COIL.get(), 64), stack(MagItems.FUSION_THRUSTER.get(), 32),
                stack(MagItems.RAILGUN_EMITTER.get(), 16), stack(MagItems.MHD_JET.get(), 8),
                stack(MagItems.MICRO_THRUSTER.get(), 8), stack(MagItems.GAS_DETECTOR.get(), 1),
                stack(MagItems.GAS_EXCITER.get(), 8), stack(MagItems.GAS_VENT.get(), 8),
                stack(MagItems.AIR_SEPARATOR.get(), 8), stack(MagItems.ION_THRUSTER.get(), 8),
                stack(MagItems.NEODYMIUM_MAGNET.get(), 8), stack(MagItems.HEMATITE_LENS.get(), 1),
                stack(MagItems.MAGNETITE_BLOCK.get(), 16), stack(MagItems.PYRRHOTITE_BLOCK.get(), 16),
                stack(MagItems.HEMATITE_BLOCK.get(), 16), stack(MagItems.TITANOMAGNETITE_BLOCK.get(), 16),
                stack(Items.CARVED_PUMPKIN, 16), stack(Items.COPPER_BLOCK, 64),
                stack(Items.IRON_BLOCK, 64), stack(Items.REDSTONE_BLOCK, 32), stack(Items.WATER_BUCKET, 8));
    }

    private static List<ItemStack> survivalKit() {
        return List.of(stack(Items.IRON_PICKAXE, 1), stack(Items.IRON_AXE, 1), stack(Items.IRON_SHOVEL, 1),
                stack(Items.BREAD, 32), stack(Items.TORCH, 64), stack(Items.COAL, 32), stack(Items.BUCKET, 8),
                stack(Items.WATER_BUCKET, 4), stack(MagItems.FIELD_COMPASS.get(), 1),
                stack(MagItems.RAW_MAGNETITE.get(), 16), stack(MagItems.RAW_LITHIUM.get(), 16),
                stack(MagItems.RAW_GALLIUM.get(), 8));
    }

    private static void clear(final ServerLevel level, final BlockPos anchor) {
        fill(level, anchor.offset(-2, -1, -2), anchor.offset(LAB_X + 1, 14, LAB_Z + 1), Blocks.AIR);
    }

    private static void fill(final ServerLevel level, final BlockPos from, final BlockPos to, final Block block) {
        for (final BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void set(final ServerLevel level, final BlockPos pos, final Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void setFacing(final ServerLevel level, final BlockPos pos, final Block block,
                                  final Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(DirectionalBlock.FACING)) state = state.setValue(DirectionalBlock.FACING, facing);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static void stockChest(final ServerLevel level, final BlockPos pos, final List<ItemStack> contents) {
        set(level, pos, Blocks.CHEST);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        for (int slot = 0; slot < Math.min(chest.getContainerSize(), contents.size()); slot++) {
            chest.setItem(slot, contents.get(slot).copy());
        }
        chest.setChanged();
    }

    private static void label(final ServerLevel level, final BlockPos pos, final String... lines) {
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ROTATION_16)) state = state.setValue(BlockStateProperties.ROTATION_16, 8);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        var text = sign.getFrontText();
        for (int line = 0; line < Math.min(4, lines.length); line++) {
            text = text.setMessage(line, Component.literal(lines[line]));
        }
        sign.setText(text, true);
        sign.setChanged();
    }

    private static ItemStack stack(final Item item, final int count) {
        return new ItemStack(item, count);
    }

    private static BlockPos chooseAnchor(final ServerPlayer player) {
        final BlockPos p = player.blockPosition();
        return new BlockPos((p.getX() >> 4) << 4, p.getY(), (p.getZ() >> 4) << 4);
    }

    private static BlockPos savedAnchor(final ServerPlayer player, final Preset preset) {
        final CompoundTag tag = state(player);
        // Preserve the established disposable-world anchor across fixture version
        // bumps. Re-anchoring at the player's current station made upgrades build
        // an entire lab around remote visual-isolation fixtures.
        if (preset.id.equals(tag.getString("Preset"))
                && tag.contains("X") && tag.contains("Y") && tag.contains("Z")) {
            return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
        return chooseAnchor(player);
    }

    private static void saveState(final ServerPlayer player, final Preset preset, final BlockPos anchor) {
        final CompoundTag tag = state(player);
        tag.putInt("Version", VERSION);
        tag.putString("Preset", preset.id);
        tag.putInt("X", anchor.getX());
        tag.putInt("Y", anchor.getY());
        tag.putInt("Z", anchor.getZ());
        player.getPersistentData().put(ROOT_TAG, tag);
    }

    private static CompoundTag state(final ServerPlayer player) {
        return player.getPersistentData().contains(ROOT_TAG)
                ? player.getPersistentData().getCompound(ROOT_TAG) : new CompoundTag();
    }

    private static String pos(final BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private enum Preset {
        LAB("lab", "Test Lab"), SURVIVAL("survival", "Survival Progression");

        private final String id;
        private final String display;

        Preset(final String id, final String display) {
            this.id = id;
            this.display = display;
        }

        private static Preset parse(final String value) {
            if (value == null || value.isBlank()) return null;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
