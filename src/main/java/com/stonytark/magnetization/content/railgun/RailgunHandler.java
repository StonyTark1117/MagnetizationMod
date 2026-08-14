package com.stonytark.magnetization.content.railgun;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.SableBridge;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Drives Railgun arcs: the inverse of {@link com.stonytark.magnetization.content.effect.LenzBrakingHandler}.
 * Each tick it walks every registered emitter's rail, pairs it with a parallel
 * sibling, and — when a ship/magnetic entity is in the channel between the two
 * rails — accelerates it down the rail with force growing exponentially in length,
 * trapping it on-rail (lateral damp) and smashing obstructing blocks. Discrete
 * pulse/cooldown firing; the lower-BlockPos emitter of a pair owns the live state.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class RailgunHandler {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/Railgun");

    /**
     * Sable's force bridge integrates over one 1/20-second physics tick. The
     * original railgun coefficient was tuned before that conversion and left
     * short, practical rails adding only a fraction of a block/second per tick.
     * Restore the tick conversion and add a small launch calibration so existing
     * 0.6 configs receive the intended force without rewriting player files.
     * By default there is no speed or force ceiling: channel length is the
     * natural limit, and longer rails retain their exponential advantage. A
     * server may opt into a maximum effective rail length below.
     */
    private static final double SHIP_LAUNCH_FORCE_CALIBRATION = 24.0d;
    /** Keep a clamped Sable body just shy of vanilla's exact touching pose.
     * Rapier can treat that boundary as penetration after its own integration. */
    private static final double NON_BREAKING_COLLISION_STANDOFF = 0.05d;
    /** Avoid an invalid/extreme physics velocity turning one tick into an
     * unbounded world scan. This still covers 5,120 blocks/s of travel. */
    private static final int MAX_BLOCK_BREAK_SWEEP_DISTANCE = 256;
    /** Sable can take a few physics ticks to publish a freshly assembled body's
     *  valid mass and broad-phase bounds. Keep the new projectile attached to its
     *  arc during that initialization window instead of immediately cooling down. */
    private static final int AUTO_ASSEMBLY_GRACE_TICKS = 20;

    /**
     * Manual arcs temporarily turn captured ships into suspended payloads. The
     * weak level key keeps this transient state out of saves and lets closed
     * worlds disappear without a static reference leak.
     */
    private static final Map<ServerLevel, Map<UUID, HeldShip>> HELD_SHIPS = new WeakHashMap<>();
    /** Ships keep their railgun provenance after crossing the muzzle so their
     * swept block-breaking or non-destructive collision protection follows the
     * full coast, not just rail ticks. */
    private static final Map<ServerLevel, Map<UUID, LaunchedShip>> LAUNCHED_SHIPS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<ArcKey, PendingAssembly>> PENDING_ASSEMBLIES = new WeakHashMap<>();

    private record ArcKey(BlockPos first, BlockPos second) {
        private static ArcKey of(final BlockPos a, final BlockPos b) {
            return compare(a, b) <= 0 ? new ArcKey(a.immutable(), b.immutable())
                    : new ArcKey(b.immutable(), a.immutable());
        }

        private boolean contains(final BlockPos pos) {
            return first.equals(pos) || second.equals(pos);
        }
    }

    private static final class HeldShip {
        private final ArcKey owner;
        private final Pose3d anchor;
        private long lastRefreshTick;

        private HeldShip(final ArcKey owner, final Pose3d anchor, final long lastRefreshTick) {
            this.owner = owner;
            this.anchor = anchor;
            this.lastRefreshTick = lastRefreshTick;
        }
    }

    private record LaunchedShip(Direction facing, ArcKey owner, boolean breakBlocks) {}
    private record PendingAssembly(UUID shipId, long createdTick) {}

    private RailgunHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(server);
        if (!MagConfig.railgunEnabled()) {
            LAUNCHED_SHIPS.remove(server);
            PENDING_ASSEMBLIES.remove(server);
            return;
        }
        prunePendingAssemblies(server);
        processLaunchedShips(server, container);
        if ((server.getGameTime() % MagConfig.railgunTicks()) != 0L) return;

        final Set<BlockPos> snapshot = RailgunRegistry.snapshot(server);
        if (snapshot.isEmpty()) return;
        final Set<BlockPos> processed = new HashSet<>();

        for (final BlockPos pos : snapshot) {
            if (processed.contains(pos)) continue;
            final RailgunEmitterBlockEntity be = server.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity found
                    ? found : RailgunRegistry.find(server, pos);
            if (be == null) continue;
            try {
                processEmitter(server, container, snapshot, processed, pos, be,
                        SableBridge.subLevelOf(be));
            } catch (final RuntimeException ex) {
                // Sable's sub-level queries can transiently throw while a ship is
                // mid-assembly/teleport/removal in the channel (e.g. "No sub-level
                // at <chunk>"). One emitter's transient failure must not crash the
                // whole server tick — skip it this tick; it recovers next tick.
                processed.add(pos);
            }
        }
    }

    /**
     * Pin manually held ships before every Sable physics substep. Merely damping
     * velocity once per Minecraft tick leaves gravity free to move the body a
     * little on every step; over a long boarding pause that accumulated into a
     * ground collision. Resetting to the captured pose here makes HOLDING a true
     * suspension, while the small opposite-gravity seed lets Rapier finish the
     * substep at zero velocity instead of immediately beginning another fall.
     */
    @SubscribeEvent
    public static void onSablePrePhysicsTick(final ForgeSablePrePhysicsTickEvent event) {
        final var physics = event.getPhysicsSystem();
        final ServerLevel server = physics.getLevel();
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) {
            HELD_SHIPS.remove(server);
            LAUNCHED_SHIPS.remove(server);
            return;
        }

        // Resolve either launch mode before Rapier's discrete collision step:
        // clear the breaking sweep now, or clamp a non-breaking translation.
        protectLaunchedShipsBeforePhysics(server, container, event.getTimeStep());

        final Map<UUID, HeldShip> held = HELD_SHIPS.get(server);
        if (held == null || held.isEmpty()) return;

        final long now = server.getGameTime();
        final long maxAge = Math.max(1L, MagConfig.railgunTicks()) + 1L;
        final var iterator = held.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            final HeldShip suspension = entry.getValue();
            if (now - suspension.lastRefreshTick > maxAge) {
                iterator.remove();
                continue;
            }

            try {
                final SubLevel subLevel = container.getSubLevel(entry.getKey());
                if (!(subLevel instanceof ServerSubLevel ship) || ship.isRemoved()
                        || ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0d) {
                    iterator.remove();
                    continue;
                }
                final RigidBodyHandle handle = RigidBodyHandle.of(ship);
                if (handle == null || !handle.isValid()) {
                    iterator.remove();
                    continue;
                }

                final Vector3d linear = handle.getLinearVelocity(new Vector3d());
                final Vector3d angular = handle.getAngularVelocity(new Vector3d());
                final Vector3d supportedVelocity = DimensionPhysicsData.getGravity(
                        server, suspension.anchor.position(), new Vector3d()).mul(-event.getTimeStep());
                handle.teleport(suspension.anchor.position(), suspension.anchor.orientation());
                handle.addLinearAndAngularVelocity(supportedVelocity.sub(linear), angular.negate());
            } catch (final RuntimeException ex) {
                // Assembly, removal, and chunk handoff can invalidate a Sable
                // body between UUID lookup and the native handle operation.
                iterator.remove();
            }
        }
        if (held.isEmpty()) HELD_SHIPS.remove(server);
    }

    /** Process a single emitter's arc; factored out so {@link #onLevelTick} can
     *  isolate per-emitter Sable query failures without aborting the tick. */
    private static void processEmitter(final ServerLevel server, final @Nullable ServerSubLevelContainer container,
                                       final Set<BlockPos> snapshot, final Set<BlockPos> processed,
                                       final BlockPos pos, final RailgunEmitterBlockEntity be,
                                       final @Nullable ServerSubLevel owner) {
        final BlockState state = be.getBlockState();
        if (MagConfig.isBlockDisabled(state)) {
            be.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
            processed.add(pos);
            return;
        }
        if (!state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) return;
        final Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);

        final int l1 = walkRail(server, pos, facing);
        be.setRailLength(l1);
        if (l1 < MagConfig.railgunMinLength()) return;

        // Pairing: find parallel siblings; >1 → arc dissipated.
        final SiblingResult sib = findSibling(server, pos, facing, snapshot, owner);
        if (sib.dissipated) { processed.add(pos); be.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE); return; }
        if (sib.pos == null) return;     // no pair yet — wait
        if (processed.contains(sib.pos)) return;     // partner already claimed by another arc this tick
        final RailgunEmitterBlockEntity sibBe = server.getBlockEntity(sib.pos) instanceof RailgunEmitterBlockEntity found
                ? found : RailgunRegistry.find(server, sib.pos);
        if (sibBe == null) return;

        processed.add(pos);
        processed.add(sib.pos);

        // The lower BlockPos owns the arc; the other mirrors for display.
        final boolean iAmMaster = compare(pos, sib.pos) <= 0;
        final BlockPos masterPos = iAmMaster ? pos : sib.pos;
        final BlockPos otherPos = iAmMaster ? sib.pos : pos;
        final RailgunEmitterBlockEntity master = iAmMaster ? be : sibBe;
        final RailgunEmitterBlockEntity other = iAmMaster ? sibBe : be;

        // Re-read by role: registry iteration order is unspecified, so l1 may
        // belong to either the owner or its sibling.
        final int masterLength = walkRail(server, masterPos, facing);
        final int otherLength = walkRail(server, otherPos, facing);
        final int effL = Math.min(masterLength, otherLength);
        // Every arc-level readout must agree from either control block. Stored
        // FE deliberately remains local because each rail needs its own source.
        master.setRailLength(effL);
        other.setRailLength(effL);
        final boolean manual = master.manualMode() || other.manualMode();
        master.setManualMode(manual);
        other.setManualMode(manual);
        final boolean breakBlocks = master.breaksBlocks();
        other.setBreakBlocks(breakBlocks);
        final boolean autoAssemble = master.autoAssemble() || other.autoAssemble();
        master.setAutoAssemble(autoAssemble);
        other.setAutoAssemble(autoAssemble);

        if (!master.isPowered() || !other.isPowered()) {
            releaseHolds(server, ArcKey.of(masterPos, otherPos));
            master.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
            other.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
            return;
        }

        if (effL >= MagConfig.railgunMinLength()) {
            final Vec3 masterCenter = worldCenter(owner, masterPos);
            for (final net.minecraft.server.level.ServerPlayer player : server.players()) {
                if (player.distanceToSqr(masterCenter.x, masterCenter.y, masterCenter.z)
                        <= 16.0 * 16.0) {
                    com.stonytark.magnetization.registry.MagTriggers.RAILGUN_COMPLETED
                            .get().trigger(player);
                }
            }
        }
        final Vec3 axis = worldAxis(owner, facing);
        final AABB channel = channelBox(masterPos, otherPos, facing, effL, owner);

        processArc(server, container, master, other, masterPos, otherPos, facing,
                channel, axis, owner, effL, manual, breakBlocks, autoAssemble,
                ArcKey.of(masterPos, otherPos));

        // Mirror to the display sibling.
        other.setArcState(master.arcState());
    }

    private static void processArc(final ServerLevel server, final @Nullable ServerSubLevelContainer container,
                                   final RailgunEmitterBlockEntity master, final RailgunEmitterBlockEntity other,
                                   final BlockPos masterPos, final BlockPos otherPos, final Direction facing,
                                   final AABB channel, final Vec3 axis, final @Nullable ServerSubLevel owner,
                                   final int effL, final boolean manual,
                                   final boolean breakBlocks, final boolean autoAssemble, final ArcKey arc) {
        boolean hasTarget = anyShipInChannel(container, server, channel) || anyEntityInChannel(server, channel)
                || pendingAssemblyInGrace(server, container, arc);
        if (!hasTarget && autoAssemble && master.arcState() == RailgunEmitterBlockEntity.ArcState.IDLE) {
            final ServerSubLevel assembled = assembleStagedBlocks(
                    server, container, masterPos, otherPos, facing, effL, owner, axis);
            if (assembled != null) {
                PENDING_ASSEMBLIES.computeIfAbsent(server, ignored -> new HashMap<>())
                        .put(arc, new PendingAssembly(assembled.getUniqueId(), server.getGameTime()));
                hasTarget = true;
            }
        }
        switch (master.arcState()) {
            case IDLE -> {
                if (hasTarget) {
                    if (manual) {
                        master.setArcState(RailgunEmitterBlockEntity.ArcState.HOLDING);
                        trapTargets(container, server, channel, axis, true, arc);
                    }
                    else if (MagConfig.railgunAutoFire()) {
                        releaseHolds(server, arc);
                        master.setArcState(RailgunEmitterBlockEntity.ArcState.LAUNCHING);
                        master.setLaunchTicks(0);
                        triggerRailgunFire(server, master.getBlockPos(), owner);
                    }
                }
            }
            case HOLDING -> {
                if (!hasTarget) {
                    releaseHolds(server, arc);
                    master.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
                    break;
                }
                master.drawPower(MagConfig.railgunHoldFeCost());
                trapTargets(container, server, channel, axis, true, arc);   // hold: damp forward too
                if (master.consumeFireRequest() || other.consumeFireRequest()) {
                    releaseHolds(server, arc);
                    master.setArcState(RailgunEmitterBlockEntity.ArcState.LAUNCHING);
                    master.setLaunchTicks(0);
                    triggerRailgunFire(server, master.getBlockPos(), owner);
                }
            }
            case LAUNCHING -> {
                releaseHolds(server, arc);
                final int feCost = MagConfig.railgunFeCostBase() + MagConfig.railgunFeCostPerLength() * effL;
                if (!hasTarget || master.launchTicks() >= MagConfig.railgunMaxLaunchTicks() || !master.drawPower(feCost)) {
                    master.setArcState(RailgunEmitterBlockEntity.ArcState.COOLDOWN);
                    master.setCooldownTicks(MagConfig.railgunCooldownTicks());
                } else {
                    accelerateTargets(container, server, channel, axis, effL, breakBlocks, arc);
                    master.setLaunchTicks(master.launchTicks() + 1);
                }
            }
            case COOLDOWN -> {
                releaseHolds(server, arc);
                /* decays in the BE tick, then re-arms to IDLE */
            }
        }
    }

    /** Returns whether an arc's newly assembled projectile is still inside the
     *  short Sable initialization window. A valid broad-phase target supersedes
     *  this naturally; the grace entry then expires without affecting its launch. */
    private static boolean pendingAssemblyInGrace(final ServerLevel server,
                                                  final @Nullable ServerSubLevelContainer container,
                                                  final ArcKey arc) {
        final Map<ArcKey, PendingAssembly> pending = PENDING_ASSEMBLIES.get(server);
        if (pending == null) return false;
        final PendingAssembly assembly = pending.get(arc);
        if (assembly == null) return false;
        if (server.getGameTime() - assembly.createdTick() > AUTO_ASSEMBLY_GRACE_TICKS) {
            pending.remove(arc);
            if (pending.isEmpty()) PENDING_ASSEMBLIES.remove(server);
            return false;
        }
        if (container != null) {
            try {
                final SubLevel sub = container.getSubLevel(assembly.shipId());
                if (sub != null && sub.isRemoved()) {
                    pending.remove(arc);
                    if (pending.isEmpty()) PENDING_ASSEMBLIES.remove(server);
                    return false;
                }
            } catch (final RuntimeException ignored) {
                // Assembly publication is briefly racy with the physics pipeline;
                // the bounded grace period is specifically for that interval.
            }
        }
        return true;
    }

    private static void prunePendingAssemblies(final ServerLevel server) {
        final Map<ArcKey, PendingAssembly> pending = PENDING_ASSEMBLIES.get(server);
        if (pending == null) return;
        final long now = server.getGameTime();
        pending.values().removeIf(assembly -> now - assembly.createdTick() > AUTO_ASSEMBLY_GRACE_TICKS);
        if (pending.isEmpty()) PENDING_ASSEMBLIES.remove(server);
    }

    /** Assemble every non-air block strictly inside the paired rails and current
     *  capture thickness. The resulting Sable body's centre is projected onto the
     *  railgun's held-target line, which lifts/centres a staged projectile before
     *  the ordinary HOLDING/LAUNCHING state machine takes over. */
    private static @Nullable ServerSubLevel assembleStagedBlocks(
            final ServerLevel server, final @Nullable ServerSubLevelContainer container,
            final BlockPos masterPos, final BlockPos otherPos, final Direction facing,
            final int effL, final @Nullable ServerSubLevel owner, final Vec3 launchAxis) {
        if (container == null) return null;
        final List<BlockPos> blocks = stagedBlocks(server, masterPos, otherPos, facing, effL);
        if (blocks.isEmpty()) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        try {
            final ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(
                    server, blocks.getFirst(), blocks,
                    new BoundingBox3i(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
            final Pose3d pose = new Pose3d(ship.logicalPose());
            final Vec3 liveCenter = new Vec3(pose.position().x(), pose.position().y(), pose.position().z());
            final Vec3 launchOrigin = worldCenter(owner, masterPos)
                    .add(worldCenter(owner, otherPos)).scale(0.5d);
            final Vec3 targetCenter = launchOrigin.add(launchAxis.scale(
                    liveCenter.subtract(launchOrigin).dot(launchAxis)));
            container.physicsSystem().getPipeline().teleport(ship,
                    new Vector3d(targetCenter.x, targetCenter.y, targetCenter.z), pose.orientation());
            return ship;
        } catch (final Throwable failure) {
            LOG.error("Railgun auto-assembly failed for {} staged blocks between {} and {}",
                    blocks.size(), masterPos.toShortString(), otherPos.toShortString(), failure);
            return null;
        }
    }

    /** Collect the complete rectangular staging volume between, but never
     *  including, the two rails. A positive server limit rejects the whole load
     *  rather than truncating it into a damaged projectile; zero is unlimited. */
    private static List<BlockPos> stagedBlocks(final ServerLevel server,
                                               final BlockPos masterPos, final BlockPos otherPos,
                                               final Direction facing, final int effL) {
        final int dx = otherPos.getX() - masterPos.getX();
        final int dy = otherPos.getY() - masterPos.getY();
        final int dz = otherPos.getZ() - masterPos.getZ();
        final int gap = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (gap <= 1 || effL <= 0) return List.of();
        final int gx = Integer.signum(dx), gy = Integer.signum(dy), gz = Integer.signum(dz);
        final Direction.Axis thirdAxis = thirdAxis(facing.getAxis(), gapAxis(masterPos, otherPos, facing));
        final int halfThickness = MagConfig.railgunChannelHalfThickness();
        final int limit = MagConfig.railgunAutoAssembleMaxBlocks();
        final List<BlockPos> blocks = new ArrayList<>();
        for (int along = 1; along <= effL; along++) {
            for (int across = 1; across < gap; across++) {
                final BlockPos lane = masterPos.relative(facing, along)
                        .offset(gx * across, gy * across, gz * across);
                for (int height = -halfThickness; height <= halfThickness; height++) {
                    final BlockPos candidate = switch (thirdAxis) {
                        case X -> lane.offset(height, 0, 0);
                        case Y -> lane.offset(0, height, 0);
                        case Z -> lane.offset(0, 0, height);
                    };
                    if (!server.hasChunkAt(candidate) || server.getBlockState(candidate).isAir()) continue;
                    blocks.add(candidate.immutable());
                    if (limit > 0 && blocks.size() > limit) return List.of();
                }
            }
        }
        return blocks;
    }

    private static Direction.Axis thirdAxis(final Direction.Axis facing, final Direction.Axis gap) {
        if (facing != Direction.Axis.X && gap != Direction.Axis.X) return Direction.Axis.X;
        if (facing != Direction.Axis.Y && gap != Direction.Axis.Y) return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    private static void triggerRailgunFire(final ServerLevel server, final BlockPos pos,
                                           final @Nullable ServerSubLevel owner) {
        final Vec3 center = worldCenter(owner, pos);
        for (final net.minecraft.server.level.ServerPlayer player : server.players()) {
            if (player.distanceToSqr(center.x, center.y, center.z) <= 16.0 * 16.0) {
                com.stonytark.magnetization.registry.MagTriggers.RAILGUN_FIRED.get().trigger(player);
            }
        }
    }

    // ── Rail geometry ──

    /** Count contiguous {@code #railgun_rails} blocks stepping from the emitter in FACING. */
    public static int walkRail(final ServerLevel level, final BlockPos emitter, final Direction facing) {
        int len = 0;
        final BlockPos.MutableBlockPos cur = emitter.relative(facing).mutable();
        // Stop at unloaded chunks rather than loading terrain from the tick
        // handler. By default every contiguous loaded rail contributes to effL;
        // server admins may opt into a shared length/power ceiling.
        final int maxLength = MagConfig.railgunLengthLimitEnabled()
                ? MagConfig.railgunMaxLength() : Integer.MAX_VALUE;
        while (len < maxLength && level.isInWorldBounds(cur) && level.hasChunkAt(cur)
                && level.getBlockState(cur).is(MagTags.RAILGUN_RAILS)) {
            len++;
            cur.move(facing);
        }
        return len;
    }

    private record SiblingResult(@Nullable BlockPos pos, boolean dissipated) {}

    /** Find the unique parallel sibling emitter (same FACING, offset by 1..maxGap on
     *  ONE perpendicular axis, same along-FACING start). >1 qualifying → dissipated.
     *  Dissipation is GLOBAL, not per-emitter: with three collinear rails spaced
     *  6<g<=maxGap apart, an OUTER rail only sees the middle (count 1) while the
     *  middle sees both (count 2). Counting from one side alone would let the two
     *  outer rails each pair with the middle and drive overlapping channels. So we
     *  ALSO re-count from the chosen partner's perspective and dissipate if EITHER
     *  side sees more than one qualifying rail. */
    private static SiblingResult findSibling(final ServerLevel level, final BlockPos pos,
                                             final Direction facing, final Set<BlockPos> snapshot,
                                             final @Nullable ServerSubLevel owner) {
        final int[] count = {0};
        final BlockPos found = scanSiblings(level, pos, facing, snapshot, owner, count);
        if (count[0] > 1) return new SiblingResult(null, true);   // 3+ rails → dissipate
        if (found != null) {
            final int[] partnerCount = {0};
            scanSiblings(level, found, facing, snapshot, owner, partnerCount);
            if (partnerCount[0] > 1) return new SiblingResult(null, true);   // partner is the middle of 3+
        }
        return new SiblingResult(found, false);
    }

    /** Count qualifying parallel siblings of {@code pos}; returns the last one found
     *  and writes the total into {@code countOut[0]}. */
    private static @Nullable BlockPos scanSiblings(final ServerLevel level, final BlockPos pos,
                                                   final Direction facing, final Set<BlockPos> snapshot,
                                                   final @Nullable ServerSubLevel owner,
                                                   final int[] countOut) {
        BlockPos found = null;
        int count = 0;
        final int maxGap = MagConfig.railgunMaxGap();
        for (final BlockPos other : snapshot) {
            if (other.equals(pos)) continue;
            final RailgunEmitterBlockEntity otherEmitter = level.getBlockEntity(other) instanceof RailgunEmitterBlockEntity foundEmitter
                    ? foundEmitter : RailgunRegistry.find(level, other);
            if (otherEmitter == null) continue;
            if (SableBridge.subLevelOf(otherEmitter) != owner) continue;
            final BlockState s = level.getBlockState(other);
            if (MagConfig.isBlockDisabled(s)) continue;
            if (!s.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)
                    || s.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING) != facing) continue;
            final int dx = other.getX() - pos.getX();
            final int dy = other.getY() - pos.getY();
            final int dz = other.getZ() - pos.getZ();
            // Same along-FACING coordinate (breech lines aligned).
            if (along(facing, dx, dy, dz) != 0) continue;
            // Offset on exactly ONE perpendicular axis within the gap range.
            final int[] perp = perpComponents(facing, dx, dy, dz);
            final int a = Math.abs(perp[0]), b = Math.abs(perp[1]);
            final boolean oneAxis = (a >= 1 && a <= maxGap && b == 0) || (b >= 1 && b <= maxGap && a == 0);
            if (!oneAxis) continue;
            count++;
            found = other;
        }
        countOut[0] = count;
        return found;
    }

    /** World AABB of the channel: effL blocks along FACING from the emitter line,
     *  spanning the gap between the two rails, ±halfThickness on the third axis. */
    private static AABB channelBox(final BlockPos master, final BlockPos other, final Direction facing,
                                   final int effL, final @Nullable ServerSubLevel owner) {
        final int ht = MagConfig.railgunChannelHalfThickness();
        // Start from the union of the two emitter cells.
        int minX = Math.min(master.getX(), other.getX());
        int minY = Math.min(master.getY(), other.getY());
        int minZ = Math.min(master.getZ(), other.getZ());
        int maxX = Math.max(master.getX(), other.getX());
        int maxY = Math.max(master.getY(), other.getY());
        int maxZ = Math.max(master.getZ(), other.getZ());
        // Extend effL blocks along FACING.
        final var n = facing.getNormal();
        if (n.getX() > 0) maxX += effL; else if (n.getX() < 0) minX -= effL;
        if (n.getY() > 0) maxY += effL; else if (n.getY() < 0) minY -= effL;
        if (n.getZ() > 0) maxZ += effL; else if (n.getZ() < 0) minZ -= effL;
        // Thicken on the third (non-facing, non-gap) axis. Identify the gap axis.
        final Direction.Axis gapAxis = gapAxis(master, other, facing);
        if (facing.getAxis() != Direction.Axis.X && gapAxis != Direction.Axis.X) { minX -= ht; maxX += ht; }
        if (facing.getAxis() != Direction.Axis.Y && gapAxis != Direction.Axis.Y) { minY -= ht; maxY += ht; }
        if (facing.getAxis() != Direction.Axis.Z && gapAxis != Direction.Axis.Z) { minZ -= ht; maxZ += ht; }
        final AABB local = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        return owner == null ? local : transformBox(owner, local);
    }

    private static Vec3 worldCenter(final @Nullable ServerSubLevel owner, final BlockPos pos) {
        final Vec3 local = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return owner == null ? local : owner.logicalPose().transformPosition(local);
    }

    private static Vec3 worldAxis(final @Nullable ServerSubLevel owner, final Direction facing) {
        final Vec3 local = Vec3.atLowerCornerOf(facing.getNormal());
        return owner == null ? local : owner.logicalPose().transformNormal(local).normalize();
    }

    /** Transform all eight local channel corners into world space. */
    private static AABB transformBox(final ServerSubLevel owner, final AABB local) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            final Vec3 point = owner.logicalPose().transformPosition(new Vec3(
                    x == 0 ? local.minX : local.maxX,
                    y == 0 ? local.minY : local.maxY,
                    z == 0 ? local.minZ : local.maxZ));
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Direction.Axis gapAxis(final BlockPos a, final BlockPos b, final Direction facing) {
        if (a.getX() != b.getX() && facing.getAxis() != Direction.Axis.X) return Direction.Axis.X;
        if (a.getY() != b.getY() && facing.getAxis() != Direction.Axis.Y) return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    private static int along(final Direction facing, final int dx, final int dy, final int dz) {
        return switch (facing.getAxis()) { case X -> dx; case Y -> dy; case Z -> dz; };
    }

    private static int[] perpComponents(final Direction facing, final int dx, final int dy, final int dz) {
        return switch (facing.getAxis()) {
            case X -> new int[]{dy, dz};
            case Y -> new int[]{dx, dz};
            case Z -> new int[]{dx, dy};
        };
    }

    // ── Target detection + acceleration ──

    private static BoundingBox3d sable(final AABB box) {
        return new BoundingBox3d(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static boolean anyShipInChannel(final @Nullable ServerSubLevelContainer container, final ServerLevel server, final AABB channel) {
        if (container == null) return false;
        for (final SubLevel sub : container.queryIntersecting(sable(channel))) {
            if (sub instanceof ServerSubLevel ship && !ship.getMassTracker().isInvalid()
                    && ship.getMassTracker().getMass() > 0.0) return true;
        }
        return false;
    }

    private static boolean anyEntityInChannel(final ServerLevel server, final AABB channel) {
        return !server.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, channel, RailgunHandler::isMagnetizable).isEmpty();
    }

    private static boolean isMagnetizable(final Entity e) {
        return e.getType().is(MagTags.MAGNETIZABLE_ENTITIES) && e.isAlive();
    }

    /** Hold a target on the rail: damp lateral motion fully, and (if {@code dampForward})
     *  bleed forward speed toward the breech so it parks for boarding. */
    private static void trapTargets(final @Nullable ServerSubLevelContainer container, final ServerLevel server,
                                    final AABB channel, final Vec3 axis,
                                    final boolean dampForward, final ArcKey arc) {
        final double lat = MagConfig.railgunLateralDamp();
        if (container != null) {
            for (final SubLevel sub : container.queryIntersecting(sable(channel))) {
                if (!(sub instanceof ServerSubLevel ship)) continue;
                final RigidBodyHandle h = RigidBodyHandle.of(ship);
                if (h == null || !h.isValid()) continue;
                markHeld(server, container, ship, arc);
                final Vector3dc v = h.getLinearVelocity();
                final double along = v.x() * axis.x + v.y() * axis.y + v.z() * axis.z;
                final Vec3 lateral = new Vec3(v.x() - along * axis.x, v.y() - along * axis.y, v.z() - along * axis.z);
                final Vector3d dv = new Vector3d(-lateral.x * lat, -lateral.y * lat, -lateral.z * lat);
                if (dampForward) { dv.add(-along * axis.x * 0.5, -along * axis.y * 0.5, -along * axis.z * 0.5); }
                h.addLinearAndAngularVelocity(dv, new Vector3d());
            }
        }

        // Magnetic entities need the same hold-phase clamp as ships. Without
        // this, HOLDING only changes the arc state while an entity can retain
        // or acquire rail velocity before the remote is fired.
        for (final Entity e : server.getEntitiesOfClass(Entity.class, channel, RailgunHandler::isMagnetizable)) {
            final Vec3 v = e.getDeltaMovement();
            final double along = v.dot(axis);
            final Vec3 lateral = v.subtract(axis.scale(along));
            Vec3 nv = lateral.scale(1.0 - lat);
            if (dampForward) nv = nv.add(axis.scale(along * 0.5));
            e.setDeltaMovement(nv);
            e.hurtMarked = true;
        }
    }

    private static void markHeld(final ServerLevel server, final ServerSubLevelContainer container,
                                 final ServerSubLevel ship, final ArcKey owner) {
        final long now = server.getGameTime();
        final Map<UUID, HeldShip> held = HELD_SHIPS.computeIfAbsent(server, ignored -> new HashMap<>());
        final UUID id = ship.getUniqueId();
        final HeldShip existing = held.get(id);
        if (existing != null && existing.owner.equals(owner)
                && now - existing.lastRefreshTick <= Math.max(1L, MagConfig.railgunTicks()) + 1L) {
            existing.lastRefreshTick = now;
            return;
        }
        final Pose3d livePose = container.physicsSystem().getPipeline().readPose(ship, new Pose3d());
        held.put(id, new HeldShip(owner, new Pose3d(livePose), now));
    }

    private static void releaseHolds(final ServerLevel server, final ArcKey owner) {
        final Map<UUID, HeldShip> held = HELD_SHIPS.get(server);
        if (held == null) return;
        held.values().removeIf(suspension -> suspension.owner.equals(owner));
        if (held.isEmpty()) HELD_SHIPS.remove(server);
    }

    static void releaseHoldsForEmitter(final ServerLevel server, final BlockPos emitter) {
        final Map<UUID, HeldShip> held = HELD_SHIPS.get(server);
        if (held != null) {
            held.values().removeIf(suspension -> suspension.owner.contains(emitter));
            if (held.isEmpty()) HELD_SHIPS.remove(server);
        }
        final Map<ArcKey, PendingAssembly> pending = PENDING_ASSEMBLIES.get(server);
        if (pending != null) {
            pending.keySet().removeIf(arc -> arc.contains(emitter));
            if (pending.isEmpty()) PENDING_ASSEMBLIES.remove(server);
        }
    }

    /** Apply the GUI's block-breaking switch to the whole resolved arc. */
    public static boolean setArcBlockBreaking(final ServerLevel server, final BlockPos emitter,
                                              final boolean enabled) {
        final RailgunEmitterBlockEntity be = registeredEmitter(server, emitter);
        if (be == null) return false;
        be.setBreakBlocks(enabled);
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) return true;
        final Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
        final SiblingResult sibling = findSibling(server, emitter, facing,
                RailgunRegistry.snapshot(server), SableBridge.subLevelOf(be));
        final RailgunEmitterBlockEntity other = sibling.pos == null ? null : registeredEmitter(server, sibling.pos);
        if (!sibling.dissipated && other != null) {
            other.setBreakBlocks(enabled);
        }
        updateLaunchedShipsForEmitter(server, emitter, enabled);
        return true;
    }

    /** Apply the GUI's world-block auto-assembly mode to the whole resolved arc. */
    public static boolean setArcAutoAssemble(final ServerLevel server, final BlockPos emitter,
                                             final boolean enabled) {
        final RailgunEmitterBlockEntity be = registeredEmitter(server, emitter);
        if (be == null) return false;
        be.setAutoAssemble(enabled);
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) return true;
        final Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
        final SiblingResult sibling = findSibling(server, emitter, facing,
                RailgunRegistry.snapshot(server), SableBridge.subLevelOf(be));
        final RailgunEmitterBlockEntity other = sibling.pos == null ? null : registeredEmitter(server, sibling.pos);
        if (!sibling.dissipated && other != null) other.setAutoAssemble(enabled);
        return true;
    }

    /** Explicit remote unpairing is arc-wide, regardless of which emitter owns
     * the live state or which control the remote was originally inserted into. */
    public static void unpairArc(final ServerLevel server, final BlockPos emitter) {
        final RailgunEmitterBlockEntity be = registeredEmitter(server, emitter);
        if (be == null) return;
        be.setManualMode(false);
        if (be.arcState() == RailgunEmitterBlockEntity.ArcState.HOLDING) {
            be.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
        }
        final BlockState state = be.getBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) {
            final Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);
            final SiblingResult sibling = findSibling(server, emitter, facing,
                    RailgunRegistry.snapshot(server), SableBridge.subLevelOf(be));
            final RailgunEmitterBlockEntity other = sibling.pos == null ? null : registeredEmitter(server, sibling.pos);
            if (!sibling.dissipated && other != null) {
                other.setManualMode(false);
                if (other.arcState() == RailgunEmitterBlockEntity.ArcState.HOLDING) {
                    other.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
                }
            }
        }
        releaseHoldsForEmitter(server, emitter);
    }

    private static @Nullable RailgunEmitterBlockEntity registeredEmitter(final ServerLevel server,
                                                                          final BlockPos pos) {
        return server.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be
                ? be : RailgunRegistry.find(server, pos);
    }

    private static void accelerateTargets(final @Nullable ServerSubLevelContainer container, final ServerLevel server,
                                          final AABB channel, final Vec3 axis, final int effL,
                                          final boolean breakBlocks, final ArcKey arc) {
        final Direction facing = axisToDirection(axis);
        final double magnitude = MagConfig.railgunForceBase() * Math.pow(effL, MagConfig.railgunForceExponent());
        final double shipMagnitude = magnitude * SHIP_LAUNCH_FORCE_CALIBRATION;
        final double lat = MagConfig.railgunLateralDamp();

        // Ships.
        if (container != null) {
            for (final SubLevel sub : container.queryIntersecting(sable(channel))) {
                if (!(sub instanceof ServerSubLevel ship) || ship.getMassTracker().isInvalid()
                        || ship.getMassTracker().getMass() <= 0.0) continue;
                final RigidBodyHandle h = RigidBodyHandle.of(ship);
                if (h == null || !h.isValid()) continue;
                final Vector3dc v = h.getLinearVelocity();
                final double along = v.x() * axis.x + v.y() * axis.y + v.z() * axis.z;
                // Lateral damp (trap on rail).
                final Vec3 lateral = new Vec3(v.x() - along * axis.x, v.y() - along * axis.y, v.z() - along * axis.z);
                h.addLinearAndAngularVelocity(
                        new Vector3d(-lateral.x * lat, -lateral.y * lat, -lateral.z * lat), new Vector3d());
                // Forward push. Multiplying by mass makes the resulting
                // acceleration mass-independent after Sable's F/m integration,
                // so a large ship gets the same decisive launch as a small one.
                if (shipMagnitude > 0.0d) {
                    final double mass = ship.getMassTracker().getMass();
                    final org.joml.Vector3dc com = ship.getMassTracker().getCenterOfMass();
                    // MassTracker reports the centre of mass in ship-local
                    // coordinates, while applyWorldImpulse expects a world-space
                    // point. Passing the local value directly gives a launched
                    // ship a huge off-centre torque once it is away from the
                    // origin, which can spin the body into Sable's removal path.
                    final Vec3 worldCom = ship.logicalPose().transformPosition(
                            new Vec3(com.x(), com.y(), com.z()));
                    SableBridge.applyWorldImpulse(ship,
                            worldCom,
                            new Vec3(axis.x * shipMagnitude * mass,
                                    axis.y * shipMagnitude * mass,
                                    axis.z * shipMagnitude * mass));
                }
                final Vector3d launchedVelocity = h.getLinearVelocity(new Vector3d());
                double forwardSpeed = launchedVelocity.x * axis.x
                        + launchedVelocity.y * axis.y + launchedVelocity.z * axis.z;
                final BoundingBox3d physicsBounds = authoritativeBounds(container, ship);
                if (breakBlocks && MagConfig.railgunBreaksBlocks()) {
                    breakPathAhead(server, physicsBounds, facing, forwardSpeed, true);
                } else {
                    clampToCollisionSafeVelocity(server, physicsBounds, h, 1.0d / 20.0d);
                    final Vector3d guardedVelocity = h.getLinearVelocity(new Vector3d());
                    forwardSpeed = guardedVelocity.x * axis.x
                            + guardedVelocity.y * axis.y + guardedVelocity.z * axis.z;
                }
                if (forwardSpeed > 0.5d) {
                    LAUNCHED_SHIPS.computeIfAbsent(server, ignored -> new HashMap<>())
                            .put(ship.getUniqueId(), new LaunchedShip(facing, arc, breakBlocks));
                }
            }
        }

        // Magnetic entities.
        final double entMag = magnitude * MagConfig.railgunEntityScale();
        for (final Entity e : server.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, channel, RailgunHandler::isMagnetizable)) {
            final Vec3 v = e.getDeltaMovement();
            final double along = v.dot(axis);
            final Vec3 lateral = v.subtract(axis.scale(along));
            Vec3 nv = lateral.scale(1.0 - lat).add(axis.scale(along));
            if (entMag > 0.0d) nv = nv.add(axis.scale(entMag));
            e.setDeltaMovement(nv);
            e.hurtMarked = true;
        }
    }

    private static Direction axisToDirection(final Vec3 axis) {
        final double ax = Math.abs(axis.x), ay = Math.abs(axis.y), az = Math.abs(axis.z);
        if (ax >= ay && ax >= az) return axis.x >= 0.0 ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return axis.y >= 0.0 ? Direction.UP : Direction.DOWN;
        return axis.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Continue launch protection after a ship leaves the arc's channel.
     * Block-breaking launches carve their projected path here; non-breaking
     * launches are clamped immediately before Sable's physics step. Tracking
     * ends when the body disappears, becomes invalid, or no longer has
     * meaningful velocity along its original launch direction. */
    private static void processLaunchedShips(final ServerLevel server,
                                             final @Nullable ServerSubLevelContainer container) {
        final Map<UUID, LaunchedShip> launched = LAUNCHED_SHIPS.get(server);
        if (launched == null || launched.isEmpty()) return;
        if (container == null) {
            LAUNCHED_SHIPS.remove(server);
            return;
        }
        final var iterator = launched.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            try {
                final SubLevel subLevel = container.getSubLevel(entry.getKey());
                if (!(subLevel instanceof ServerSubLevel ship) || ship.isRemoved()
                        || ship.getMassTracker().isInvalid()) {
                    iterator.remove();
                    continue;
                }
                final RigidBodyHandle handle = RigidBodyHandle.of(ship);
                if (handle == null || !handle.isValid()) {
                    iterator.remove();
                    continue;
                }
                final Vector3d velocity = handle.getLinearVelocity(new Vector3d());
                final var normal = entry.getValue().facing().getNormal();
                final double forwardSpeed = velocity.x * normal.getX()
                        + velocity.y * normal.getY() + velocity.z * normal.getZ();
                if (!Double.isFinite(forwardSpeed) || forwardSpeed <= 0.5d) {
                    iterator.remove();
                    continue;
                }
                if (entry.getValue().breakBlocks() && MagConfig.railgunBreaksBlocks()) {
                    breakPathAhead(server, authoritativeBounds(container, ship),
                            entry.getValue().facing(), forwardSpeed, true);
                }
            } catch (final RuntimeException ex) {
                iterator.remove();
            }
        }
        if (launched.isEmpty()) LAUNCHED_SHIPS.remove(server);
    }

    /** Keep in-flight ships protected if an operator changes the arc switch
     * after launch. Enabling resumes carving; disabling immediately changes the
     * same tracked body to non-destructive swept collision handling. */
    private static void updateLaunchedShipsForEmitter(final ServerLevel server, final BlockPos emitter,
                                                       final boolean enabled) {
        final Map<UUID, LaunchedShip> launched = LAUNCHED_SHIPS.get(server);
        if (launched == null) return;
        launched.replaceAll((id, ship) -> ship.owner().contains(emitter)
                ? new LaunchedShip(ship.facing(), ship.owner(), enabled) : ship);
    }

    /** Protect every launch immediately before Sable integrates it. Breaking
     * launches clear their authoritative projected hull before Rapier can
     * collide and bounce; non-breaking launches clamp against vanilla's swept
     * collision shapes. */
    private static void protectLaunchedShipsBeforePhysics(final ServerLevel server,
                                                           final ServerSubLevelContainer container,
                                                           final double timeStep) {
        if (!Double.isFinite(timeStep) || timeStep <= 0.0d) return;
        final Map<UUID, LaunchedShip> launched = LAUNCHED_SHIPS.get(server);
        if (launched == null || launched.isEmpty()) return;

        final var iterator = launched.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            try {
                final SubLevel subLevel = container.getSubLevel(entry.getKey());
                if (!(subLevel instanceof ServerSubLevel ship) || ship.isRemoved()
                        || ship.getMassTracker().isInvalid()) {
                    iterator.remove();
                    continue;
                }
                final RigidBodyHandle handle = RigidBodyHandle.of(ship);
                if (handle == null || !handle.isValid()) {
                    iterator.remove();
                    continue;
                }
                final BoundingBox3d physicsBounds = authoritativeBounds(container, ship);
                if (entry.getValue().breakBlocks() && MagConfig.railgunBreaksBlocks()) {
                    final Vector3d velocity = handle.getLinearVelocity(new Vector3d());
                    final var normal = entry.getValue().facing().getNormal();
                    final double forwardSpeed = velocity.x * normal.getX()
                            + velocity.y * normal.getY() + velocity.z * normal.getZ();
                    // breakPathAhead is expressed in blocks/second at a 20 Hz
                    // tick. Scale a Sable substep to the same travel distance.
                    breakPathAhead(server, physicsBounds, entry.getValue().facing(),
                            forwardSpeed * timeStep * 20.0d, true);
                } else {
                    clampToCollisionSafeVelocity(server, physicsBounds, handle, timeStep);
                }
            } catch (final RuntimeException ex) {
                iterator.remove();
            }
        }
        if (launched.isEmpty()) LAUNCHED_SHIPS.remove(server);
    }

    private static BoundingBox3d authoritativeBounds(final ServerSubLevelContainer container,
                                                       final ServerSubLevel ship) {
        // readPose updates only position/orientation. Seed from logicalPose so
        // the plot-space rotation point and scale survive into the transform.
        final Pose3d physicsPose = new Pose3d(ship.logicalPose());
        container.physicsSystem().getPipeline().readPose(ship, physicsPose);
        return new BoundingBox3d(ship.getPlot().getBoundingBox()).transform(physicsPose);
    }

    private static void clampToCollisionSafeVelocity(final ServerLevel server, final BoundingBox3dc bounds,
                                                      final RigidBodyHandle handle, final double timeStep) {
        final Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return;
        final Vec3 requested = new Vec3(velocity.x * timeStep, velocity.y * timeStep, velocity.z * timeStep);
        final Vec3 collisionSafe = collisionSafeMovement(server, bounds, requested);
        if (collisionSafe.distanceToSqr(requested) <= 1.0E-12d) return;
        final Vec3 safe = new Vec3(
                collisionStandoff(requested.x, collisionSafe.x),
                collisionStandoff(requested.y, collisionSafe.y),
                collisionStandoff(requested.z, collisionSafe.z));

        final double inverseStep = 1.0d / timeStep;
        final Vector3d angular = handle.getAngularVelocity(new Vector3d());
        handle.addLinearAndAngularVelocity(
                new Vector3d(safe.x * inverseStep - velocity.x,
                        safe.y * inverseStep - velocity.y,
                        safe.z * inverseStep - velocity.z),
                angular.negate());
    }

    private static double collisionStandoff(final double requested, final double safe) {
        if (Math.abs(requested - safe) <= 1.0E-7d) return safe;
        final double magnitude = Math.max(0.0d, Math.abs(safe) - NON_BREAKING_COLLISION_STANDOFF);
        return Math.copySign(magnitude, safe == 0.0d ? requested : safe);
    }

    /** Return the collision-safe portion of a ship's requested translation.
     * Public for deterministic GameTest coverage without requiring a native
     * Rapier body. */
    public static Vec3 collisionSafeMovement(final ServerLevel server, final BoundingBox3dc bounds,
                                             final Vec3 requested) {
        if (!Double.isFinite(requested.x) || !Double.isFinite(requested.y)
                || !Double.isFinite(requested.z)) return Vec3.ZERO;
        final AABB vanillaBounds = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        return Entity.collideBoundingBox(null, requested, vanillaBounds, server, List.of());
    }

    /** Smash blocks across the volume the ship can traverse before the next
     * physics tick. Scanning only one leading slab allowed high-speed launches
     * to tunnel through obstructions. Rails, emitters, protected blocks, and
     * block entities remain immune through {@link #breakIfObstructing}. */
    public static int breakPathAhead(final ServerLevel server, final BoundingBox3dc bb,
                                     final Direction facing, final double forwardSpeed,
                                     final boolean arcEnabled) {
        if (!arcEnabled || !MagConfig.railgunBreaksBlocks()) return 0;
        final int budget = MagConfig.railgunDestroyBudgetPerTick();
        if (budget <= 0) return 0;
        int broken = 0;
        final int x0 = (int) Math.floor(bb.minX()), x1 = Math.max(x0, (int) Math.ceil(bb.maxX()) - 1);
        final int y0 = (int) Math.floor(bb.minY()), y1 = Math.max(y0, (int) Math.ceil(bb.maxY()) - 1);
        final int z0 = (int) Math.floor(bb.minZ()), z1 = Math.max(z0, (int) Math.ceil(bb.maxZ()) - 1);
        final double speed = Double.isFinite(forwardSpeed) ? Math.max(0.0d, forwardSpeed) : 0.0d;
        final int travel = Math.min(MAX_BLOCK_BREAK_SWEEP_DISTANCE,
                Math.max(0, (int) Math.ceil(speed / 20.0d)));
        final boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        final double leading = switch (facing.getAxis()) {
            case X -> positive ? bb.maxX() : bb.minX();
            case Y -> positive ? bb.maxY() : bb.minY();
            case Z -> positive ? bb.maxZ() : bb.minZ();
        };
        final int start = positive ? (int) Math.floor(leading) : (int) Math.ceil(leading) - 1;
        final int stepSign = positive ? 1 : -1;

        // Nearest slabs first so a low destruction budget always clears the
        // obstruction immediately in front of the ship before farther blocks.
        for (int step = 0; step <= travel && broken < budget; step++) {
            final int along = start + stepSign * step;
            for (int x = x0; x <= x1 && broken < budget; x++) {
                for (int y = y0; y <= y1 && broken < budget; y++) {
                    for (int z = z0; z <= z1 && broken < budget; z++) {
                        final BlockPos p = switch (facing.getAxis()) {
                            case X -> new BlockPos(along, y, z);
                            case Y -> new BlockPos(x, along, z);
                            case Z -> new BlockPos(x, y, along);
                        };
                        if (breakIfObstructing(server, p)) broken++;
                    }
                }
            }
        }
        return broken;
    }

    // Visible so a gametest can assert the safety carve-outs directly
    // (rails / emitters / excavator-immune / bedrock / other BEs are spared).
    public static boolean breakIfObstructing(final ServerLevel server, final BlockPos p) {
        final BlockState s = server.getBlockState(p);
        if (s.isAir()) return false;
        if (s.is(MagTags.RAILGUN_RAILS)) return false;            // never eat the track
        if (server.getBlockEntity(p) instanceof RailgunEmitterBlockEntity) return false;  // nor the controls
        if (s.is(MagTags.EXCAVATOR_IMMUNE)) return false;
        if (server.getBlockEntity(p) != null) return false;       // skip other BEs
        if (s.getDestroySpeed(server, p) < 0) return false;       // bedrock
        // Give claim/protection mods a veto, like a real break would.
        if (com.stonytark.magnetization.content.MagBlockBreaker.isBreakVetoed(server, p, s)) return false;
        if (com.stonytark.magnetization.content.MagBlockBreaker.dropsEnabled(server)) Block.dropResources(s, server, p);
        server.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if ((server.getGameTime() & 7L) == 0L) {
            server.playSound(null, p, s.getSoundType().getBreakSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.1f);
        }
        return true;
    }

    private static int compare(final BlockPos a, final BlockPos b) {
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        return Integer.compare(a.getZ(), b.getZ());
    }
}
