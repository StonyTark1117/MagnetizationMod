package com.stonytark.magnetization.command;

import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.worldgen.AnomalyBiome;
import com.stonytark.magnetization.worldgen.PetrifiedForestBiome;
import com.stonytark.magnetization.api.EquippedArmor;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * /magnetization debug field <pos> — prints current field state at a block pos.
 * /magnetization debug forceAt <pos> <x> <y> <z> — prints the world-space force
 * vector that the field at {@code pos} would exert on a unit-mass test particle
 * at the given world coordinates. Useful for sanity-checking shape/falloff math.
 *
 * /magnetization spawn_test_ship — places a small ferromagnetic platform 4
 * blocks in front of the running player and assembles it into a Sable sub-level
 * (a ship). Convenience for testing emitter-vs-ship interactions without
 * needing to hand-build and run /sable assemble.
 */
public final class MagCommands {

    private MagCommands() {}

    public static void onRegister(final RegisterCommandsEvent event) {
        // Per-subtree permission predicates — each reads its config value live,
        // so admins can /reload after editing the config to change access
        // without restarting the server. Lowering a permission to 0 makes the
        // subtree available to all players; raising to 3-4 locks it to higher
        // op tiers.
        final Predicate<CommandSourceStack> debugPerm =
                src -> src.hasPermission(MagConfig.commandDebugPermission());
        final Predicate<CommandSourceStack> spawnPerm =
                src -> src.hasPermission(MagConfig.commandSpawnTestPermission());
        final Predicate<CommandSourceStack> shipUtilPerm =
                src -> src.hasPermission(MagConfig.commandShipUtilPermission());
        final Predicate<CommandSourceStack> lirmPerm =
                src -> src.hasPermission(MagConfig.commandLirmPermission());
        final Predicate<CommandSourceStack> tpPerm =
                src -> src.hasPermission(MagConfig.commandTpPermission());
        // Targeting another player via /magnetization tp <biome> <player> requires
        // op-level even when self-teleport is unrestricted, so a normal player
        // can't yank others out of their build.
        final Predicate<CommandSourceStack> tpOtherPerm =
                src -> src.hasPermission(MagConfig.commandSpawnTestPermission());

        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("magnetization")
                .then(buildDebugSubtree(debugPerm))
                .then(buildSpawnTestShipSubtree(spawnPerm))
                .then(Commands.literal("spawn_test_anchor")
                        .requires(spawnPerm)
                        .executes(ctx -> spawnTestAnchor(ctx.getSource())))
                .then(Commands.literal("spawn_meteorite")
                        .requires(spawnPerm)
                        .executes(ctx -> spawnMeteorite(ctx.getSource())))
                .then(Commands.literal("fast_grow_sapling")
                        .requires(spawnPerm)
                        .executes(ctx -> fastGrowSapling(ctx.getSource())))
                .then(Commands.literal("spawn_crater")
                        .requires(spawnPerm)
                        .executes(ctx -> spawnCrater(ctx.getSource())))
                .then(Commands.literal("clear_phantoms")
                        .requires(shipUtilPerm)
                        .executes(ctx -> clearPhantoms(ctx.getSource())))
                .then(Commands.literal("shatter_all_ships")
                        .requires(shipUtilPerm)
                        .executes(ctx -> shatterAllShips(ctx.getSource())))
                .then(Commands.literal("push_nearest_ship")
                        .requires(shipUtilPerm)
                        .executes(ctx -> pushNearestShip(ctx.getSource(), 5.0)))
                .then(buildLirmSubtree(lirmPerm))
                .then(buildTpSubtree(tpPerm, tpOtherPerm))
                // Read-only QoL — no permission gate. Useful to every player.
                .then(Commands.literal("version")
                        .executes(ctx -> printVersion(ctx.getSource())))
                .then(Commands.literal("stats")
                        .executes(ctx -> printStats(ctx.getSource())))
                .then(Commands.literal("help")
                        .executes(ctx -> printHelp(ctx.getSource())))
                // Read-only but exposes server config — same permission as debug.
                .then(Commands.literal("config")
                        .requires(debugPerm)
                        .then(Commands.literal("show")
                                .executes(ctx -> showConfig(ctx.getSource(), null))
                                .then(Commands.argument("filter", StringArgumentType.greedyString())
                                        .executes(ctx -> showConfig(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "filter"))))));

        event.getDispatcher().register(root);
    }

    // ---------------- subtree builders ----------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugSubtree(
            final Predicate<CommandSourceStack> debugPerm) {
        return Commands.literal("debug")
                .requires(debugPerm)
                .then(Commands.literal("field")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> printField(ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("forceAt")
                        .then(Commands.argument("emitter", BlockPosArgument.blockPos())
                                .then(Commands.argument("target", BlockPosArgument.blockPos())
                                        .executes(ctx -> printForceAt(ctx.getSource(),
                                                BlockPosArgument.getBlockPos(ctx, "emitter"),
                                                BlockPosArgument.getBlockPos(ctx, "target"))))))
                // /magnetization debug rotate <deg>           — yaw the nearest ship
                // /magnetization debug rotate <deg> yaw|pitch|roll
                .then(Commands.literal("rotate")
                        .then(Commands.argument("degrees", DoubleArgumentType.doubleArg(-360, 360))
                                .executes(ctx -> rotateNearestShip(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "degrees"), "yaw"))
                                .then(Commands.literal("yaw")
                                        .executes(ctx -> rotateNearestShip(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "degrees"), "yaw")))
                                .then(Commands.literal("pitch")
                                        .executes(ctx -> rotateNearestShip(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "degrees"), "pitch")))
                                .then(Commands.literal("roll")
                                        .executes(ctx -> rotateNearestShip(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "degrees"), "roll")))))
                // /magnetization debug energy <pos>             — print FE buffer + power source
                // /magnetization debug energy <pos> fill        — fill to capacity
                // /magnetization debug energy <pos> drain       — empty to zero
                // /magnetization debug energy <pos> set <n>     — set buffer to n FE
                .then(Commands.literal("energy")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> printEnergy(ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos")))
                                .then(Commands.literal("fill")
                                        .executes(ctx -> setEmitterEnergy(ctx.getSource(),
                                                BlockPosArgument.getBlockPos(ctx, "pos"), -1)))
                                .then(Commands.literal("drain")
                                        .executes(ctx -> setEmitterEnergy(ctx.getSource(),
                                                BlockPosArgument.getBlockPos(ctx, "pos"), 0)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setEmitterEnergy(ctx.getSource(),
                                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                                        IntegerArgumentType.getInteger(ctx, "amount")))))))
                // /magnetization debug curios — list contents of every Curios slot the player has
                .then(Commands.literal("curios")
                        .executes(ctx -> listCurios(ctx.getSource())))
                // /magnetization debug scan_fields            — list active emitters in 32-block radius
                // /magnetization debug scan_fields <radius>   — same, custom radius
                .then(Commands.literal("scan_fields")
                        .executes(ctx -> scanFields(ctx.getSource(), 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> scanFields(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                // /magnetization debug ae2_meteorites — list AE2-sourced meteorite entries
                .then(Commands.literal("ae2_meteorites")
                        .executes(ctx -> listAeMeteorites(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLirmSubtree(
            final Predicate<CommandSourceStack> lirmPerm) {
        return Commands.literal("lirm")
                .requires(lirmPerm)
                // /magnetization lirm strike            — lightning bolt on the player
                // /magnetization lirm strike <pos>       — lightning bolt at <pos>
                // /magnetization lirm stamp [north|south]— manually LIRM-stamp held item
                // /magnetization lirm inspect            — print LIRM state of held + armor
                // /magnetization lirm clear              — clear LIRM stamp from held item
                .then(Commands.literal("strike")
                        .executes(ctx -> strikePlayer(ctx.getSource()))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> strikeAt(ctx.getSource(),
                                        BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(Commands.literal("stamp")
                        .executes(ctx -> stampHeld(ctx.getSource(), null))
                        .then(Commands.literal("north")
                                .executes(ctx -> stampHeld(ctx.getSource(), MagneticPolarity.NORTH)))
                        .then(Commands.literal("south")
                                .executes(ctx -> stampHeld(ctx.getSource(), MagneticPolarity.SOUTH))))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspectLirm(ctx.getSource())))
                .then(Commands.literal("clear")
                        .executes(ctx -> clearHeldLirm(ctx.getSource())))
                .then(Commands.literal("fields")
                        .executes(ctx -> countLirmFields(ctx.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTpSubtree(
            final Predicate<CommandSourceStack> tpPerm,
            final Predicate<CommandSourceStack> tpOtherPerm) {
        // /magnetization tp <biome>           — teleport the running player
        // /magnetization tp <biome> <player>  — teleport another player (op-gated)
        return Commands.literal("tp")
                .requires(tpPerm)
                .then(Commands.literal("anomaly")
                        .executes(ctx -> tpSelf(ctx.getSource(), AnomalyBiome.KEY, "anomaly"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(tpOtherPerm)
                                .executes(ctx -> tpToBiome(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        AnomalyBiome.KEY, "anomaly"))))
                .then(Commands.literal("petrified_forest")
                        .executes(ctx -> tpSelf(ctx.getSource(), PetrifiedForestBiome.KEY, "petrified forest"))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(tpOtherPerm)
                                .executes(ctx -> tpToBiome(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player"),
                                        PetrifiedForestBiome.KEY, "petrified forest"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSpawnTestShipSubtree(
            final Predicate<CommandSourceStack> spawnPerm) {
        // /magnetization spawn_test_ship                       — 1×1×1 magnetite (default)
        // /magnetization spawn_test_ship <size>                — N×N×N magnetite
        // /magnetization spawn_test_ship <size> <material>     — N×N×N of the named block
        return Commands.literal("spawn_test_ship")
                .requires(spawnPerm)
                .executes(ctx -> spawnTestShip(ctx.getSource(), 1, "magnetite"))
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> spawnTestShip(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "size"), "magnetite"))
                        .then(Commands.argument("material", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[]{"magnetite", "iron", "raw_iron", "gold", "copper"}, b))
                                .executes(ctx -> spawnTestShip(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        StringArgumentType.getString(ctx, "material")))));
    }

    // ---------------- biome teleport ----------------

    /** Resolve "this command was run by a player" or fail-fast with a helpful
     *  message that hints at the cross-player form. */
    private static int tpSelf(final CommandSourceStack src,
                              final ResourceKey<Biome> biomeKey,
                              final String displayName) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final CommandSyntaxException e) {
            src.sendFailure(Component.literal(
                    "Run this as a player, or supply a player argument: /magnetization tp <biome> <player>"));
            return 0;
        }
        return tpToBiome(src, player, biomeKey, displayName);
    }

    /** Scan up to 6 400 blocks for the nearest cell of {@code biomeKey} and
     *  teleport {@code player} to the surface there. Mirrors vanilla
     *  {@code /locate biome} parameters (radius / horizontal / vertical
     *  step), then heightmap-walks to a safe surface Y so the player doesn't
     *  spawn inside a stone column. The scan runs in the player's own
     *  dimension, not the command-source's, so an op teleporting another
     *  player searches that player's surroundings. */
    private static int tpToBiome(final CommandSourceStack src,
                                 final ServerPlayer player,
                                 final ResourceKey<Biome> biomeKey,
                                 final String displayName) {
        final ServerLevel level = player.serverLevel();
        final BlockPos origin = player.blockPosition();

        // Same scan parameters vanilla's LocateCommand uses for biomes.
        final Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                (Holder<Biome> h) -> h.is(biomeKey), origin, 6400, 32, 64);
        if (found == null) {
            src.sendFailure(Component.literal(
                    "No nearby " + displayName + " biome within 6400 blocks of " + origin.toShortString() + "."));
            return 0;
        }

        final BlockPos hit = found.getFirst();
        // Force the target chunk to FULL generation so decorations + our
        // ChunkSurfaceRepaintHandler run before we read the heightmap.
        // SURFACE status leaves features and surface_repaint unfinished and
        // landed the player below the actual visible top.
        level.getChunk(hit.getX() >> 4, hit.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
        // MOTION_BLOCKING_NO_LEAVES skips snow/leaves stacks the heightmap
        // might otherwise count as solid, and walks down to actual terrain.
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(hit.getX(), 0, hit.getZ()));
        // Belt-and-suspenders: walk up if there's solid above (decorations
        // placed after heightmap update can briefly skew the result), and
        // down if we somehow ended above air pockets.
        while (surface.getY() < level.getMaxBuildHeight() - 1
                && !level.getBlockState(surface.above()).isAir()) {
            surface = surface.above();
        }
        // Drop the player one block above the surface so they don't suffocate.
        final double tx = surface.getX() + 0.5;
        final double ty = surface.getY() + 1.0;
        final double tz = surface.getZ() + 0.5;
        player.teleportTo(level, tx, ty, tz, player.getYRot(), player.getXRot());

        final int dist = (int) Math.round(Math.sqrt(origin.distSqr(surface)));
        final String who = player.getScoreboardName();
        final String surfaceStr = surface.toShortString();
        src.sendSuccess(() -> Component.literal(String.format(
                "Teleported %s to %s biome at %s (%d blocks away).",
                who, displayName, surfaceStr, dist)), true);
        return 1;
    }

    // ---------------- LIRM test commands ----------------

    /** Summons a vanilla lightning bolt directly on the player. Fires both
     *  the EntityJoinLevelEvent (→ log petrification) and EntityStruckByLightningEvent
     *  (→ LIRM stamp on a metal armor/tool piece) so a single command exercises
     *  the whole feature. Also deals normal lightning damage — wear armor or
     *  brace for it. */
    private static int strikePlayer(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player, or use /magnetization lirm strike <pos>."));
            return 0;
        }
        return spawnBolt(src.getLevel(), player.blockPosition(), src,
                "Struck " + player.getScoreboardName() + " with lightning.");
    }

    /** Summons a vanilla lightning bolt at a position. Useful for testing log
     *  petrification — point at a tree and any log within 3 blocks of the bolt
     *  has a 75% chance of becoming petrified wood. */
    private static int strikeAt(final CommandSourceStack src, final BlockPos pos) {
        return spawnBolt(src.getLevel(), pos, src,
                "Struck " + pos.toShortString() + " with lightning.");
    }

    private static int spawnBolt(final ServerLevel level, final BlockPos at,
                                 final CommandSourceStack src, final String successMsg) {
        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            src.sendFailure(Component.literal("Could not create lightning entity."));
            return 0;
        }
        bolt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(bolt);
        src.sendSuccess(() -> Component.literal(successMsg), true);
        return 1;
    }

    /** Manually applies a LIRM stamp to the held item, skipping the lightning
     *  step. Useful for testing decay (the 20-minute timer + LirmDecayHandler
     *  cleanup) without standing in a storm. If polarity is null, picks one at
     *  random — same as a real strike. */
    private static int stampHeld(final CommandSourceStack src, final MagneticPolarity preset) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("Hold an item in your main hand first."));
            return 0;
        }
        if (!held.is(MagTags.METAL_ARMOR) && !held.is(MagTags.METAL_TOOLS)) {
            src.sendFailure(Component.literal(
                    "Held item " + held.getItem() + " isn't tagged metal_armor or metal_tools."));
            return 0;
        }
        final MagneticPolarity pol = preset != null
                ? preset
                : (player.level().random.nextBoolean() ? MagneticPolarity.NORTH : MagneticPolarity.SOUTH);
        held.set(MagDataComponents.ARMOR_POLARITY.get(), pol);
        Lirm.stamp(held, player.level().getGameTime());
        src.sendSuccess(() -> Component.literal(
                "Stamped " + held.getItem() + " with LIRM " + pol.getSerializedName()
                        + " (decays over 20 min)."), true);
        return 1;
    }

    /** Lists every metal-armor / metal-tool the player carries, showing LIRM
     *  status: polarity, decay strength (1.0 = fresh / no LIRM, 0.0 = expired),
     *  and remaining ticks if a LIRM marker is active. */
    private static int inspectLirm(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final long now = player.level().getGameTime();
        final List<Component> lines = new ArrayList<>();
        for (final ItemStack armor : EquippedArmor.all(player)) {
            describeLirm(armor, now, lines, "armor");
        }
        describeLirm(player.getMainHandItem(), now, lines, "main");
        describeLirm(player.getOffhandItem(), now, lines, "off");
        if (lines.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No metal armor / tools to inspect."), false);
            return 1;
        }
        for (final Component line : lines) {
            src.sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    private static void describeLirm(final ItemStack stack, final long now,
                                     final List<Component> out, final String slot) {
        if (stack.isEmpty()) return;
        if (!stack.is(MagTags.METAL_ARMOR) && !stack.is(MagTags.METAL_TOOLS)) return;
        final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        final Long createdAt = stack.get(MagDataComponents.LIRM_CREATED_AT.get());
        final double strength = Lirm.strength(stack, now);
        final String polStr = pol == null ? "—" : pol.getSerializedName();
        final String lirmStr;
        if (createdAt == null) {
            lirmStr = pol == null ? "unmagnetized" : "permanent stamp";
        } else {
            final long remaining = Math.max(0, Lirm.DURATION_TICKS - (now - createdAt));
            lirmStr = String.format("LIRM %.0f%% (remaining %ds)", strength * 100, remaining / 20);
        }
        out.add(Component.literal(String.format("[%s] %s — polarity=%s, %s",
                slot, stack.getItem(), polStr, lirmStr))
                .withStyle(pol == MagneticPolarity.NORTH ? ChatFormatting.RED
                        : pol == MagneticPolarity.SOUTH ? ChatFormatting.AQUA
                        : ChatFormatting.GRAY));
    }

    /** Counts the active transient magnetic fields seeded by lightning in
     *  the running player's level — useful for verifying that strike-on-ground
     *  / log-petrification actually populates the registry. */
    private static int countLirmFields(final CommandSourceStack src) {
        final ServerLevel level = src.getLevel();
        final int n = com.stonytark.magnetization.content.effect.TemporaryLirmFields.activeCount(level);
        src.sendSuccess(() -> Component.literal(
                "Active transient LIRM fields in this level: " + n), false);
        return n;
    }

    /** Strips LIRM (the time-stamp + paired polarity) from the held item.
     *  Same operation the decay handler eventually runs automatically. */
    private static int clearHeldLirm(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("Hold an item in your main hand first."));
            return 0;
        }
        Lirm.clear(held);
        src.sendSuccess(() -> Component.literal(
                "Cleared LIRM + polarity from " + held.getItem() + "."), true);
        return 1;
    }

    /**
     * Teleport-rotate the nearest sub-level by {@code degrees} around the named axis
     * (yaw = world Y, pitch = world X, roll = world Z). Setup-free regression check
     * for the "magnet stuck on placement-cardinal" bug: place a directional emitter
     * on a ship, run this command, and watch whether the field axis follows. Uses
     * absolute teleport rather than angular velocity so the rotation is exact and
     * doesn't drift over ticks.
     */
    private static int rotateNearestShip(final CommandSourceStack src,
                                         final double degrees, final String axis) {
        final ServerLevel level = src.getLevel();
        final dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("Sable container unavailable in this level."));
            return 0;
        }
        final Vec3 origin = src.getPosition();

        dev.ryanhcode.sable.sublevel.ServerSubLevel best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (final dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s)) continue;
            if (s.getMassTracker().isInvalid() || s.getMassTracker().getMass() <= 0.0) continue;
            final var bb = s.boundingBox();
            final double cx = (bb.minX() + bb.maxX()) * 0.5;
            final double cy = (bb.minY() + bb.maxY()) * 0.5;
            final double cz = (bb.minZ() + bb.maxZ()) * 0.5;
            final double d2 = (cx - origin.x) * (cx - origin.x)
                    + (cy - origin.y) * (cy - origin.y)
                    + (cz - origin.z) * (cz - origin.z);
            if (d2 < bestDistSqr) {
                bestDistSqr = d2;
                best = s;
            }
        }
        if (best == null) {
            src.sendFailure(Component.literal("No live sub-level in the world."));
            return 0;
        }
        final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle =
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(best);
        if (handle == null) {
            src.sendFailure(Component.literal("Could not obtain RigidBodyHandle for the nearest ship."));
            return 0;
        }
        final int ax, ay, az;
        switch (axis) {
            case "pitch" -> { ax = 1; ay = 0; az = 0; }
            case "roll"  -> { ax = 0; ay = 0; az = 1; }
            default      -> { ax = 0; ay = 1; az = 0; } // yaw
        }
        // Compose in world frame: rotate the existing orientation by `delta` applied
        // from the world side, so "yaw 90°" yaws around world up regardless of the
        // ship's current orientation.
        final org.joml.Quaterniond current = new org.joml.Quaterniond(best.logicalPose().orientation());
        final org.joml.Quaterniond delta = new org.joml.Quaterniond(
                new org.joml.AxisAngle4d(Math.toRadians(degrees), ax, ay, az));
        final org.joml.Quaterniond next = new org.joml.Quaterniond();
        delta.mul(current, next);
        handle.teleport(best.logicalPose().position(), next);

        final dev.ryanhcode.sable.sublevel.ServerSubLevel rotated = best;
        src.sendSuccess(() -> Component.literal(String.format(
                "Rotated ship %s by %.1f° %s (mass=%.2f).",
                rotated.getUniqueId().toString().substring(0, 8),
                degrees, axis, rotated.getMassTracker().getMass())), true);
        return 1;
    }

    /**
     * Sweeps Sable's container for sub-levels with invalid or zero mass (artifacts
     * left behind by half-completed assemblies, partial unloads, or earlier debug
     * commands) and shatters them. Useful when {@code /magnetization spawn_test_ship}
     * has accumulated leftovers across iterations and the spatial query is bloated.
     */
    /**
     * Direct velocity injection into the nearest sub-level via Sable's
     * RigidBodyHandle, bypassing the FieldApplicator force pipeline entirely.
     * Diagnostic: if THIS moves the ship, the rigid body works fine and the
     * problem is in our force/impulse path; if not, the ship's physics is
     * sleeping or something else is gating motion.
     */
    private static int pushNearestShip(final CommandSourceStack src, final double speed) {
        final ServerLevel level = src.getLevel();
        final dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        final net.minecraft.world.phys.Vec3 origin = src.getPosition();

        dev.ryanhcode.sable.sublevel.ServerSubLevel best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (final dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s)) continue;
            if (s.getMassTracker().isInvalid() || s.getMassTracker().getMass() <= 0.0) continue;
            final var bb = s.boundingBox();
            final double cx = (bb.minX() + bb.maxX()) * 0.5;
            final double cy = (bb.minY() + bb.maxY()) * 0.5;
            final double cz = (bb.minZ() + bb.maxZ()) * 0.5;
            final double d2 = (cx - origin.x) * (cx - origin.x)
                    + (cy - origin.y) * (cy - origin.y)
                    + (cz - origin.z) * (cz - origin.z);
            if (d2 < bestDistSqr) {
                bestDistSqr = d2;
                best = s;
            }
        }
        if (best == null) {
            src.sendFailure(Component.literal("No live sub-level in the world."));
            return 0;
        }
        final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle =
                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(best);
        if (handle == null) {
            src.sendFailure(Component.literal("Could not obtain RigidBodyHandle for the nearest ship."));
            return 0;
        }
        // +X velocity (east) so motion is visually obvious. Speed in m/s.
        handle.addLinearAndAngularVelocity(
                new org.joml.Vector3d(speed, 0, 0),
                new org.joml.Vector3d(0, 0, 0));
        final dev.ryanhcode.sable.sublevel.ServerSubLevel pushed = best;
        src.sendSuccess(() -> Component.literal(String.format(
                "Pushed ship %s at +%s m/s on +X axis (mass=%.2f).",
                pushed.getUniqueId().toString().substring(0, 8),
                speed, pushed.getMassTracker().getMass())), true);
        return 1;
    }

    /**
     * Removes EVERY Sable sub-level in the loaded world and releases every
     * Magnetic Anchor binding. Use to wipe test debris between iterations
     * (e.g. when the world has accumulated leftover test ships and anchors
     * are stuck bound to the wrong UUID).
     */
    private static int shatterAllShips(final CommandSourceStack src) {
        final ServerLevel level = src.getLevel();
        final dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        final java.util.List<dev.ryanhcode.sable.sublevel.ServerSubLevel> all = new ArrayList<>();
        for (final dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s) all.add(s);
        }
        for (final dev.ryanhcode.sable.sublevel.ServerSubLevel s : all) {
            container.removeSubLevel(s, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
        // Release all anchor bindings so the next anchor tick re-acquires fresh.
        // We iterate the EmitterRegistry (which tracks all loaded emitter BEs)
        // rather than walking the chunk map directly.
        final int[] releasedHolder = { 0 };
        com.stonytark.magnetization.physics.EmitterRegistry.forEach(level, (lvl, p) -> {
            if (lvl.getBlockEntity(p) instanceof com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity anchor) {
                anchor.releaseBinding();
                releasedHolder[0]++;
            }
        });
        final int releasedAnchors = releasedHolder[0];
        final int shipCount = all.size();
        final int anchorCount = releasedAnchors;
        src.sendSuccess(() -> Component.literal(
                "Shattered " + shipCount + " sub-levels and released " + anchorCount + " anchor bindings."), true);
        return shipCount;
    }

    private static int clearPhantoms(final CommandSourceStack src) {
        final ServerLevel level = src.getLevel();
        final dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        final java.util.List<dev.ryanhcode.sable.sublevel.ServerSubLevel> toRemove = new ArrayList<>();
        for (final dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s)) continue;
            if (s.getMassTracker().isInvalid() || s.getMassTracker().getMass() <= 0.0) {
                toRemove.add(s);
            }
        }
        for (final dev.ryanhcode.sable.sublevel.ServerSubLevel s : toRemove) {
            container.removeSubLevel(s, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
        final int removed = toRemove.size();
        src.sendSuccess(() -> Component.literal("Cleared " + removed + " phantom sub-levels."), true);
        return removed;
    }

    private static int printField(final CommandSourceStack src, final BlockPos pos) {
        final ServerLevel level = src.getLevel();
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MagneticFieldSource source)) {
            src.sendFailure(Component.literal("No magnetic emitter at " + pos.toShortString()));
            return 0;
        }
        final MagneticField field = source.currentField();
        if (field == null) {
            src.sendSuccess(() -> Component.literal("Emitter at " + pos.toShortString() + " is currently OFF."), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal(String.format(
                "Field @ %s: tier=%s polarity=%s shape=%s range=%.1f origin=(%.2f,%.2f,%.2f) axis=(%.2f,%.2f,%.2f)",
                pos.toShortString(), field.strength(), field.polarity(), field.shape(), field.range(),
                field.origin().x, field.origin().y, field.origin().z,
                field.axis().x, field.axis().y, field.axis().z
        )), false);
        return 1;
    }

    /**
     * Spawns an N×N×N cube of the chosen material floating ~3 blocks above
     * the player's head, four blocks in their facing direction, then assembles
     * it with Sable. Spawning in mid-air confirms physics is live (gravity
     * acts immediately) and gives emitter forces room to move it without
     * fighting the world floor. Default 1×1×1 magnetite — the lightest
     * possible test target so even tiny field forces produce visible motion.
     */
    private static int spawnTestShip(final CommandSourceStack src, final int size, final String material) {
        final ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (final Exception e) {
            src.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos anchor = player.blockPosition()
                .relative(player.getDirection(), 4)
                .above(3);

        final BlockState block = switch (material.toLowerCase(Locale.ROOT)) {
            case "iron"      -> Blocks.IRON_BLOCK.defaultBlockState();
            case "raw_iron"  -> Blocks.RAW_IRON_BLOCK.defaultBlockState();
            case "gold"      -> Blocks.GOLD_BLOCK.defaultBlockState();
            case "copper"    -> Blocks.COPPER_BLOCK.defaultBlockState();
            case "magnetite" -> MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState();
            default          -> null;
        };
        if (block == null) {
            src.sendFailure(Component.literal(
                    "Unknown material '" + material + "'. Use magnetite|iron|raw_iron|gold|copper."));
            return 0;
        }

        final List<BlockPos> blocks = new ArrayList<>(size * size * size);
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    final BlockPos p = anchor.offset(dx, dy, dz);
                    if (!level.getBlockState(p).isAir()) {
                        src.sendFailure(Component.literal(
                                "Spawn cell " + p.toShortString() + " is obstructed by "
                                        + level.getBlockState(p).getBlock() + ". Move and try again."));
                        return 0;
                    }
                    blocks.add(p);
                }
            }
        }
        for (final BlockPos p : blocks) level.setBlockAndUpdate(p, block);

        final BoundingBox box = BoundingBox.encapsulatingPositions(blocks).orElseThrow();
        final BoundingBox3i bounds = new BoundingBox3i(box);
        bounds.set(
                bounds.minX - 1, bounds.minY - 1, bounds.minZ - 1,
                bounds.maxX + 1, bounds.maxY + 1, bounds.maxZ + 1);

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
        if (subLevel.getMassTracker().isInvalid()) {
            src.sendFailure(Component.literal(
                    "Assembly produced an invalid mass — overlap with an existing sub-level?"));
            return 0;
        }
        final double mass = subLevel.getMassTracker().getMass();
        src.sendSuccess(() -> Component.literal(String.format(
                "Spawned %d×%d×%d %s test ship (mass=%.2f) at %s. Falls under gravity; "
                        + "use /magnetization spawn_test_anchor to drop a powered anchor that yanks it.",
                size, size, size, material, mass, anchor.toShortString())), true);
        return blocks.size();
    }

    /**
     * Drops a Magnetic Anchor 4 blocks in front of the player at floor level
     * with a redstone block underneath, so the anchor is permanently powered.
     * Convenience for testing emitter→ship interactions without setting up
     * lever circuits by hand.
     */
    private static int spawnTestAnchor(final CommandSourceStack src) {
        final ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (final Exception e) {
            src.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos anchorPos = player.blockPosition().relative(player.getDirection(), 4);
        final BlockPos powerPos = anchorPos.below();

        for (final BlockPos p : List.of(anchorPos, powerPos)) {
            if (!level.getBlockState(p).isAir() && !level.getBlockState(p).canBeReplaced()) {
                src.sendFailure(Component.literal(
                        "Spawn area at " + p.toShortString() + " is obstructed by "
                                + level.getBlockState(p).getBlock() + ". Move and try again."));
                return 0;
            }
        }

        level.setBlockAndUpdate(powerPos, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(anchorPos, MagBlocks.MAGNETIC_ANCHOR.get().defaultBlockState());
        // Force a neighbor-update tick so the anchor's POWERED state syncs immediately.
        level.neighborChanged(anchorPos, level.getBlockState(powerPos).getBlock(), powerPos);

        src.sendSuccess(() -> Component.literal(
                "Placed powered Magnetic Anchor at " + anchorPos.toShortString()
                        + ". Spawn a test ship within 16 blocks; anchor will bind to it on the next tick."), true);
        return 1;
    }

    /** Place a fully-charged {@code meteorite_core} 3 blocks in front of the
     *  invoker. Useful for in-world testing of the decay/refill loop without
     *  travelling to a naturally-generated one. */
    private static int spawnMeteorite(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos pos = player.blockPosition().relative(player.getDirection(), 3);
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) {
            src.sendFailure(Component.literal(
                    "Spawn area at " + pos.toShortString() + " is obstructed."));
            return 0;
        }
        level.setBlockAndUpdate(pos, MagBlocks.METEORITE_CORE.get().defaultBlockState());
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity core) {
            core.refill(level.getGameTime());
        }
        src.sendSuccess(() -> Component.literal(
                "Placed fully-charged meteorite_core at " + pos.toShortString() + "."), true);
        return 1;
    }

    /** Dump every entry in {@link com.stonytark.magnetization.content.meteorite.MeteoriteFieldRegistry}
     *  for the player's level. Useful for verifying the AE2 scan hook caught
     *  the meteor structures in a freshly-generated world. */
    private static int listAeMeteorites(final CommandSourceStack src) {
        ServerLevel resolved;
        try { resolved = src.getPlayerOrException().serverLevel(); }
        catch (final Exception e) { resolved = src.getLevel(); }
        final ServerLevel level = resolved;
        final var entries = com.stonytark.magnetization.content.meteorite.MeteoriteFieldRegistry.snapshot(level);
        if (entries.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "MeteoriteFieldRegistry empty for " + level.dimension().location()
                            + ". AE2 absent, hook disabled, or no chunks with AE2 meteors have loaded.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "MeteoriteFieldRegistry has " + entries.size() + " entrie(s) in "
                        + level.dimension().location() + ":")
                .withStyle(ChatFormatting.GOLD), false);
        final long now = level.getGameTime();
        for (final var e : entries) {
            final long age = now - e.chargedAtTick();
            final String line = String.format("  %s — charged %d ticks ago",
                    e.pos().toShortString(), age);
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        return entries.size();
    }

    /** Walk the EmitterRegistry for the player's level and print every active
     *  magnetic field source within {@code radius} blocks. Surfaces the
     *  block id, strength tier, polarity, and chebyshev distance — quick way
     *  to confirm a pyrrhotite reactor or titanomagnetite recorder is actually
     *  emitting without having to hover-check each block manually. */
    private static int scanFields(final CommandSourceStack src, final int radius) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos origin = player.blockPosition();
        final int radiusSq = radius * radius;

        final java.util.List<String> hits = new java.util.ArrayList<>();
        for (final BlockPos pos : com.stonytark.magnetization.physics.EmitterRegistry.snapshot(level)) {
            if (origin.distSqr(pos) > radiusSq) continue;
            final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof com.stonytark.magnetization.api.MagneticFieldSource src2)) continue;
            final com.stonytark.magnetization.api.MagneticField field = src2.currentField();
            final String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock()).toString();
            final int dist = (int) Math.round(Math.sqrt(origin.distSqr(pos)));
            if (field == null) {
                hits.add(String.format("  %s @ %s (%d b) — inert", blockId, pos.toShortString(), dist));
            } else {
                hits.add(String.format("  %s @ %s (%d b) — %s %s",
                        blockId, pos.toShortString(), dist,
                        field.strength().name(), field.polarity().name()));
            }
        }
        if (hits.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "No emitters in EmitterRegistry within " + radius + " blocks of "
                            + origin.toShortString() + ".")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                hits.size() + " emitter(s) within " + radius + " blocks:")
                .withStyle(ChatFormatting.GOLD), false);
        for (final String line : hits) {
            src.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        return hits.size();
    }

    /** Spawn a full meteorite crater (carved bowl + magnetite/raw_magnetite
     *  fill + petrified_wood debris + meteorite_core at centre) at the
     *  invoker's surface position. Mirrors what the rare worldgen feature
     *  generates, so testers can validate the crater shape and tier loop
     *  without travelling tens of thousands of blocks. */
    private static int spawnCrater(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos origin = player.blockPosition();

        // Reuse the worldgen Feature directly. WorldGenLevel is implemented by
        // ServerLevel, so we can place a FeaturePlaceContext from runtime data.
        final net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration> ctx =
                new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                        java.util.Optional.empty(),
                        level,
                        level.getChunkSource().getGenerator(),
                        level.getRandom(),
                        origin,
                        net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);

        final boolean placed = com.stonytark.magnetization.registry.MagFeatures.METEORITE_CRATER.get().place(ctx);
        if (!placed) {
            src.sendFailure(Component.literal("Crater feature returned false — surface column rejected."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "Spawned a meteorite crater at " + origin.toShortString()
                        + ". Look for the core block at the bowl floor."), true);
        return 1;
    }

    /** Find the nearest planted {@code meteorite_sapling} within 8 blocks and
     *  rewind its plantedAtTick so it matures on the next 200-tick interval
     *  (max ~10 seconds). Lets the user verify the promotion path without
     *  waiting 30 in-game minutes. */
    private static int fastGrowSapling(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final Exception e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos origin = player.blockPosition();
        BlockPos found = null;
        com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity foundBe = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    final BlockPos p = origin.offset(dx, dy, dz);
                    final BlockEntity be = level.getBlockEntity(p);
                    if (!(be instanceof com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity sapling)) continue;
                    final double d = origin.distSqr(p);
                    if (d < bestDistSqr) { bestDistSqr = d; found = p; foundBe = sapling; }
                }
            }
        }
        if (found == null) {
            src.sendFailure(Component.literal("No meteorite_sapling within 8 blocks."));
            return 0;
        }
        // Stamp a planted-tick far enough in the past that next 200-tick check fires the promote.
        // We use reflection-free path: set via a tag write through saveAdditional isn't trivial,
        // so call refill-equivalent: the sapling exposes growthProgress() but no setter.
        // Instead we re-emit the load NBT with a backdated PlantedAt.
        final BlockPos targetPos = found;
        final long now = level.getGameTime();
        final long backdate = now - (com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity.GROW_TICKS - 5);
        final net.minecraft.nbt.CompoundTag tag = foundBe.saveWithoutMetadata(level.registryAccess());
        tag.putLong("PlantedAt", backdate);
        foundBe.loadWithComponents(tag, level.registryAccess());
        foundBe.setChanged();
        src.sendSuccess(() -> Component.literal(
                "Rewound meteorite_sapling at " + targetPos.toShortString()
                        + " — will mature on the next growth tick (≤10s)."), true);
        return 1;
    }

    private static int printForceAt(final CommandSourceStack src, final BlockPos emitterPos, final BlockPos targetPos) {
        final ServerLevel level = src.getLevel();
        final BlockEntity be = level.getBlockEntity(emitterPos);
        if (!(be instanceof MagneticFieldSource source) || source.currentField() == null) {
            src.sendFailure(Component.literal("Emitter at " + emitterPos.toShortString() + " inactive or absent."));
            return 0;
        }
        final MagneticField field = source.currentField();
        final Vec3 sample = Vec3.atCenterOf(targetPos);
        final Vec3 force = FieldApplicator.forceAt(field, sample);
        src.sendSuccess(() -> Component.literal(String.format(
                "Force @ %s from %s: (%.3f, %.3f, %.3f) magnitude=%.3f",
                targetPos.toShortString(), emitterPos.toShortString(),
                force.x, force.y, force.z, force.length()
        )), false);
        return 1;
    }

    // ---------------- FE/RF energy commands ----------------

    /** Reads an emitter's FE buffer + reports which power source is currently
     *  driving the field. Skips the need to set up a real FE source if all you
     *  want to do is sanity-check the GUI/HUD/Jade readout. */
    private static int printEnergy(final CommandSourceStack src, final BlockPos pos) {
        final ServerLevel level = src.getLevel();
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractEmitterBlockEntity emitter)) {
            src.sendFailure(Component.literal("No emitter at " + pos.toShortString() + "."));
            return 0;
        }
        final IEnergyStorage buffer = emitter.getEnergyBuffer();
        final int stored = buffer.getEnergyStored();
        final int cap = buffer.getMaxEnergyStored();
        final String source = emitter.isEnergyPowered() ? "energy"
                : (emitter.isRedstonePowered() ? "redstone" : "idle");
        final double pct = 100.0 * stored / Math.max(1, cap);
        src.sendSuccess(() -> Component.literal(String.format(
                "Energy @ %s: %,d / %,d FE (%.1f%%), source=%s",
                pos.toShortString(), stored, cap, pct, source)), false);
        return stored;
    }

    /** Bypasses the buffer's normal {@code maxReceive} cap so a single command
     *  can fill an empty buffer or drain it without a cabled-up FE source.
     *  Passing {@code -1} means "fill to capacity"; any non-negative value is
     *  clamped to [0, capacity]. */
    private static int setEmitterEnergy(final CommandSourceStack src, final BlockPos pos, final int requested) {
        final ServerLevel level = src.getLevel();
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractEmitterBlockEntity emitter)) {
            src.sendFailure(Component.literal("No emitter at " + pos.toShortString() + "."));
            return 0;
        }
        final int cap = emitter.getEnergyBuffer().getMaxEnergyStored();
        final int target = requested < 0 ? cap : Math.min(requested, cap);
        emitter.setEnergyForDebug(target);
        src.sendSuccess(() -> Component.literal(String.format(
                "Set emitter buffer @ %s to %,d / %,d FE.",
                pos.toShortString(), target, cap)), true);
        return target;
    }

    // ---------------- Curios slot inspector ----------------

    /** Lists every item in the running player's Curios slots so testers can
     *  confirm the Field Compass / Magnetic Grapple / Repulsor Gun are
     *  actually equipped where they think. No-op (with a friendly error) when
     *  Curios isn't installed. */
    private static int listCurios(final CommandSourceStack src) {
        final ServerPlayer player;
        try { player = src.getPlayerOrException(); }
        catch (final CommandSyntaxException e) {
            src.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (!ModList.get().isLoaded("curios")) {
            src.sendFailure(Component.literal("Curios is not installed."));
            return 0;
        }
        final List<Component> lines = new ArrayList<>();
        int total = 0;
        try {
            final var handler = player.getCapability(
                    top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
            if (handler == null) {
                src.sendFailure(Component.literal("Player has no Curios inventory."));
                return 0;
            }
            for (final var entry : handler.getCurios().entrySet()) {
                final String slotId = entry.getKey();
                final var stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    final ItemStack s = stacks.getStackInSlot(i);
                    if (s.isEmpty()) continue;
                    lines.add(Component.literal(String.format("  [%s #%d] %s × %d",
                            slotId, i, s.getItem(), s.getCount()))
                            .withStyle(ChatFormatting.GRAY));
                    total++;
                }
            }
        } catch (final Throwable t) {
            src.sendFailure(Component.literal("Curios API call failed: " + t.getMessage()));
            return 0;
        }
        if (total == 0) {
            src.sendSuccess(() -> Component.literal(
                    "No items in any Curios slot."), false);
            return 0;
        }
        final int finalTotal = total;
        src.sendSuccess(() -> Component.literal("Curios slot contents (" + finalTotal + " items):")
                .withStyle(ChatFormatting.GOLD), false);
        for (final Component line : lines) src.sendSuccess(() -> line, false);
        return total;
    }

    // ---------------- Admin / user QoL ----------------

    /** Prints the resolved mod versions of Magnetization plus its key load-bearing
     *  dependencies — handy for bug reports. */
    private static int printVersion(final CommandSourceStack src) {
        final String mag      = modVersion("magnetization");
        final String create   = modVersion("create");
        final String sable    = modVersion("sable");
        final String aero     = modVersion("aeronautics");
        final String neoforge = modVersion("neoforge");
        src.sendSuccess(() -> Component.literal(String.format(
                "Magnetization %s — Create %s, Sable %s, Aeronautics %s, NeoForge %s",
                mag, create, sable, aero, neoforge)).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static String modVersion(final String modId) {
        return ModList.get().getModContainerById(modId)
                .map(mc -> mc.getModInfo().getVersion().toString())
                .orElse("absent");
    }

    /** Counts loaded emitters, live Sable ships, and entities currently carrying
     *  the Magnetized effect in the source player's level. Useful both as an
     *  admin lag-diagnostic and as a quick "did anything wire up correctly?"
     *  sanity-check during testing. */
    private static int printStats(final CommandSourceStack src) {
        final ServerLevel level = src.getLevel();
        final int emitters = com.stonytark.magnetization.physics.EmitterRegistry.size(level);

        int ships = 0;
        final dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container != null) {
            for (final dev.ryanhcode.sable.sublevel.SubLevel sub : container.getAllSubLevels()) {
                if (sub instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel s
                        && !s.getMassTracker().isInvalid() && s.getMassTracker().getMass() > 0.0) {
                    ships++;
                }
            }
        }

        int magnetized = 0;
        final var magnetizedHolder = MagEffects.MAGNETIZED;
        for (final Entity e : level.getAllEntities()) {
            if (e instanceof LivingEntity le && le.hasEffect(magnetizedHolder)) {
                magnetized++;
            }
        }

        final int finalShips = ships;
        final int finalMag = magnetized;
        src.sendSuccess(() -> Component.literal(String.format(
                "Stats in %s: %d emitters loaded, %d Sable ships, %d magnetized entities.",
                level.dimension().location(), emitters, finalShips, finalMag)), false);
        return emitters + finalShips + finalMag;
    }

    /** Walks {@link MagConfig}'s static {@code ConfigValue} fields via reflection
     *  and prints their live values. Optional {@code filter} is a case-insensitive
     *  substring match on the field name — e.g. {@code /magnetization config show
     *  energy} surfaces the FE knobs. Read-only; admins who want to edit still
     *  go through the .toml. */
    private static int showConfig(final CommandSourceStack src, final String filter) {
        final java.lang.reflect.Field[] fields = MagConfig.class.getDeclaredFields();
        final List<Component> lines = new ArrayList<>();
        int shown = 0;
        for (final java.lang.reflect.Field f : fields) {
            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!ModConfigSpec.ConfigValue.class.isAssignableFrom(f.getType())) continue;
            if (filter != null && !f.getName().toLowerCase(Locale.ROOT)
                    .contains(filter.toLowerCase(Locale.ROOT))) continue;
            try {
                f.setAccessible(true);
                final ModConfigSpec.ConfigValue<?> cv = (ModConfigSpec.ConfigValue<?>) f.get(null);
                final Object value = cv.get();
                lines.add(Component.literal(String.format("  %s = %s", f.getName(), value))
                        .withStyle(ChatFormatting.GRAY));
                shown++;
            } catch (final Throwable t) {
                // Skip — config not yet loaded or reflection denied.
            }
        }
        if (shown == 0) {
            src.sendSuccess(() -> Component.literal(filter == null
                    ? "No config values to show (config not yet loaded?)."
                    : "No config values match filter '" + filter + "'."), false);
            return 0;
        }
        final int finalShown = shown;
        src.sendSuccess(() -> Component.literal(String.format(
                "Magnetization config — %d %s:", finalShown,
                filter == null ? "values" : "matching '" + filter + "'"))
                .withStyle(ChatFormatting.GOLD), false);
        for (final Component line : lines) src.sendSuccess(() -> line, false);
        return shown;
    }

    /** Lists every /magnetization subcommand the caller has permission for,
     *  with a one-line description. Permission-aware so a survival player on
     *  a public server only sees the read-only entries they can actually use. */
    private static int printHelp(final CommandSourceStack src) {
        record Entry(String label, String desc, Predicate<CommandSourceStack> perm) {}
        final Predicate<CommandSourceStack> any = s -> true;
        final Predicate<CommandSourceStack> debugP =
                s -> s.hasPermission(MagConfig.commandDebugPermission());
        final Predicate<CommandSourceStack> spawnP =
                s -> s.hasPermission(MagConfig.commandSpawnTestPermission());
        final Predicate<CommandSourceStack> shipP =
                s -> s.hasPermission(MagConfig.commandShipUtilPermission());
        final Predicate<CommandSourceStack> lirmP =
                s -> s.hasPermission(MagConfig.commandLirmPermission());
        final Predicate<CommandSourceStack> tpP =
                s -> s.hasPermission(MagConfig.commandTpPermission());

        final List<Entry> entries = List.of(
                new Entry("/magnetization version", "Print mod + Create + Sable + NeoForge versions.", any),
                new Entry("/magnetization help", "Show this list.", any),
                new Entry("/magnetization stats", "Loaded emitters / Sable ships / magnetized entity count.", any),
                new Entry("/magnetization tp anomaly [player]", "Teleport to the nearest Magnetic Anomaly biome.", tpP),
                new Entry("/magnetization tp petrified_forest [player]", "Teleport to the nearest Petrified Forest biome.", tpP),
                new Entry("/magnetization config show [filter]", "Dump live config values (optional substring filter).", debugP),
                new Entry("/magnetization debug field <pos>", "Print current magnetic field state at an emitter.", debugP),
                new Entry("/magnetization debug forceAt <emitter> <target>", "Print force vector on a unit-mass at <target>.", debugP),
                new Entry("/magnetization debug rotate <deg> [yaw|pitch|roll]", "Rotate the nearest ship in place.", debugP),
                new Entry("/magnetization debug energy <pos> [fill|drain|set <amount>]", "Read or write an emitter's FE buffer.", debugP),
                new Entry("/magnetization debug curios", "List items in the player's Curios slots.", debugP),
                new Entry("/magnetization debug scan_fields [radius]", "List active emitters around the player (default 32).", debugP),
                new Entry("/magnetization debug ae2_meteorites", "Dump AE2-sourced meteorite registry entries.", debugP),
                new Entry("/magnetization spawn_test_ship [size] [material]", "Drop a small ferromagnetic test ship mid-air.", spawnP),
                new Entry("/magnetization spawn_test_anchor", "Drop a powered Magnetic Anchor 4 blocks ahead.", spawnP),
                new Entry("/magnetization spawn_meteorite", "Drop a fully-charged meteorite_core 3 blocks ahead.", spawnP),
                new Entry("/magnetization fast_grow_sapling", "Rewind the nearest meteorite_sapling — matures ≤10s.", spawnP),
                new Entry("/magnetization spawn_crater", "Generate a full meteorite crater at your feet.", spawnP),
                new Entry("/magnetization clear_phantoms", "Remove sub-levels with invalid mass.", shipP),
                new Entry("/magnetization shatter_all_ships", "Remove every Sable sub-level; release every anchor.", shipP),
                new Entry("/magnetization push_nearest_ship", "Inject +5 m/s velocity on +X into the nearest ship.", shipP),
                new Entry("/magnetization lirm strike [pos]", "Summon vanilla lightning bolt on player or at <pos>.", lirmP),
                new Entry("/magnetization lirm stamp [north|south]", "Manually LIRM-stamp the held metal item.", lirmP),
                new Entry("/magnetization lirm inspect", "List LIRM state of held + armor.", lirmP),
                new Entry("/magnetization lirm clear", "Strip LIRM stamp from held item.", lirmP),
                new Entry("/magnetization lirm fields", "Count active transient LIRM fields in this level.", lirmP)
        );

        src.sendSuccess(() -> Component.literal("Magnetization commands:")
                .withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (final Entry e : entries) {
            if (!e.perm().test(src)) continue;
            final MutableComponent line = Component.literal("  " + e.label())
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" — " + e.desc()).withStyle(ChatFormatting.GRAY));
            src.sendSuccess(() -> line, false);
            shown++;
        }
        return shown;
    }
}
