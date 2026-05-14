package com.stonytark.magnetization.command;

import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

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
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("magnetization")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("debug")
                        .then(Commands.literal("field")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            final BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
                                            return printField(ctx.getSource(), pos);
                                        })))
                        .then(Commands.literal("forceAt")
                                .then(Commands.argument("emitter", BlockPosArgument.blockPos())
                                        .then(Commands.argument("target", BlockPosArgument.blockPos())
                                                .executes(ctx -> {
                                                    final BlockPos emit = BlockPosArgument.getBlockPos(ctx, "emitter");
                                                    final BlockPos tgt = BlockPosArgument.getBlockPos(ctx, "target");
                                                    return printForceAt(ctx.getSource(), emit, tgt);
                                                })))))
                .then(Commands.literal("spawn_test_ship")
                        .executes(ctx -> spawnTestShip(ctx.getSource())))
                .then(Commands.literal("spawn_test_anchor")
                        .executes(ctx -> spawnTestAnchor(ctx.getSource())))
                .then(Commands.literal("clear_phantoms")
                        .executes(ctx -> clearPhantoms(ctx.getSource())))
                .then(Commands.literal("shatter_all_ships")
                        .executes(ctx -> shatterAllShips(ctx.getSource())))
                .then(Commands.literal("push_nearest_ship")
                        .executes(ctx -> pushNearestShip(ctx.getSource(), 5.0)))
                .then(Commands.literal("lirm")
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
                                .executes(ctx -> clearHeldLirm(ctx.getSource()))));

        event.getDispatcher().register(root);
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
        for (final ItemStack armor : player.getArmorSlots()) {
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
                .withStyle(pol == MagneticPolarity.NORTH ? ChatFormatting.AQUA
                        : pol == MagneticPolarity.SOUTH ? ChatFormatting.RED
                        : ChatFormatting.GRAY));
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
     * Spawns a single Magnetite Block floating ~3 blocks above the player's
     * head, four blocks in their facing direction, then assembles it with
     * Sable. A 1×1×1 ship is the lightest possible test target — even tiny
     * field forces produce visible motion. Spawning in mid-air confirms
     * physics is live (gravity acts immediately) and gives emitter forces
     * room to move it without fighting the world floor.
     */
    private static int spawnTestShip(final CommandSourceStack src) {
        final ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (final Exception e) {
            src.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        final ServerLevel level = src.getLevel();
        final BlockPos pos = player.blockPosition()
                .relative(player.getDirection(), 4)
                .above(3);

        if (!level.getBlockState(pos).isAir()) {
            src.sendFailure(Component.literal(
                    "Spawn position " + pos.toShortString() + " is obstructed by "
                            + level.getBlockState(pos).getBlock() + ". Move and try again."));
            return 0;
        }

        final BlockState magnetite = MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState();
        level.setBlockAndUpdate(pos, magnetite);

        final List<BlockPos> blocks = List.of(pos);
        final BoundingBox box = BoundingBox.encapsulatingPositions(blocks).orElseThrow();
        final BoundingBox3i bounds = new BoundingBox3i(box);
        bounds.set(
                bounds.minX - 1, bounds.minY - 1, bounds.minZ - 1,
                bounds.maxX + 1, bounds.maxY + 1, bounds.maxZ + 1);

        final ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, pos, blocks, bounds);
        if (subLevel.getMassTracker().isInvalid()) {
            src.sendFailure(Component.literal(
                    "Assembly produced an invalid mass — overlap with an existing sub-level?"));
            return 0;
        }
        final double mass = subLevel.getMassTracker().getMass();
        src.sendSuccess(() -> Component.literal(String.format(
                "Spawned 1×1×1 magnetite test ship (mass=%.2f) at %s. Falls under gravity; "
                        + "use /magnetization spawn_test_anchor to drop a powered anchor that yanks it.",
                mass, pos.toShortString())), true);
        return 1;
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
}
