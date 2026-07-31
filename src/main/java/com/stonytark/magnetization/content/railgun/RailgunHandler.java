package com.stonytark.magnetization.content.railgun;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.SableBridge;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
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

import java.util.HashSet;
import java.util.Set;

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

    private RailgunHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (!MagConfig.railgunEnabled()) return;
        if ((server.getGameTime() % MagConfig.railgunTicks()) != 0L) return;

        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        final Set<BlockPos> snapshot = RailgunRegistry.snapshot(server);
        if (snapshot.isEmpty()) return;
        final Set<BlockPos> processed = new HashSet<>();

        for (final BlockPos pos : snapshot) {
            if (processed.contains(pos)) continue;
            if (!(server.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity be)) continue;
            try {
                processEmitter(server, container, snapshot, processed, pos, be);
            } catch (final RuntimeException ex) {
                // Sable's sub-level queries can transiently throw while a ship is
                // mid-assembly/teleport/removal in the channel (e.g. "No sub-level
                // at <chunk>"). One emitter's transient failure must not crash the
                // whole server tick — skip it this tick; it recovers next tick.
                processed.add(pos);
            }
        }
    }

    /** Process a single emitter's arc; factored out so {@link #onLevelTick} can
     *  isolate per-emitter Sable query failures without aborting the tick. */
    private static void processEmitter(final ServerLevel server, final @Nullable SubLevelContainer container,
                                       final Set<BlockPos> snapshot, final Set<BlockPos> processed,
                                       final BlockPos pos, final RailgunEmitterBlockEntity be) {
        final BlockState state = be.getBlockState();
        if (!state.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)) return;
        final Direction facing = state.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING);

        final int l1 = walkRail(server, pos, facing);
        be.setRailLength(l1);
        if (l1 < MagConfig.railgunMinLength()) return;

        // Pairing: find parallel siblings; >1 → arc dissipated.
        final SiblingResult sib = findSibling(server, pos, facing, snapshot);
        if (sib.dissipated) { processed.add(pos); be.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE); return; }
        if (sib.pos == null) return;     // no pair yet — wait
        if (processed.contains(sib.pos)) return;     // partner already claimed by another arc this tick
        if (!(server.getBlockEntity(sib.pos) instanceof RailgunEmitterBlockEntity sibBe)) return;

        processed.add(pos);
        processed.add(sib.pos);

        // The lower BlockPos owns the arc; the other mirrors for display.
        final boolean iAmMaster = compare(pos, sib.pos) <= 0;
        final BlockPos masterPos = iAmMaster ? pos : sib.pos;
        final BlockPos otherPos = iAmMaster ? sib.pos : pos;
        final RailgunEmitterBlockEntity master = iAmMaster ? be : sibBe;
        final RailgunEmitterBlockEntity other = iAmMaster ? sibBe : be;

        if (!master.isPowered() || !other.isPowered()) {
            master.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
            other.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE);
            return;
        }

        final int l2 = walkRail(server, otherPos, facing);
        final int effL = Math.min(l1, l2);
        if (effL >= MagConfig.railgunMinLength()) {
            for (final net.minecraft.server.level.ServerPlayer player : server.players()) {
                if (player.distanceToSqr(masterPos.getX() + 0.5, masterPos.getY() + 0.5,
                        masterPos.getZ() + 0.5) <= 16.0 * 16.0) {
                    com.stonytark.magnetization.registry.MagTriggers.RAILGUN_COMPLETED
                            .get().trigger(player);
                }
            }
        }
        final boolean manual = master.manualMode() || other.manualMode();
        final AABB channel = channelBox(masterPos, otherPos, facing, effL);

        processArc(server, container, master, other, channel, facing, effL, manual);

        // Mirror to the display sibling.
        other.setArcState(master.arcState());
        other.setManualMode(manual);
    }

    private static void processArc(final ServerLevel server, final @Nullable SubLevelContainer container,
                                   final RailgunEmitterBlockEntity master, final RailgunEmitterBlockEntity other,
                                   final AABB channel, final Direction facing, final int effL, final boolean manual) {
        final boolean hasTarget = anyShipInChannel(container, server, channel) || anyEntityInChannel(server, channel);
        switch (master.arcState()) {
            case IDLE -> {
                if (hasTarget) {
                    if (manual) master.setArcState(RailgunEmitterBlockEntity.ArcState.HOLDING);
                    else if (MagConfig.railgunAutoFire()) {
                        master.setArcState(RailgunEmitterBlockEntity.ArcState.LAUNCHING);
                        master.setLaunchTicks(0);
                        triggerRailgunFire(server, master.getBlockPos());
                    }
                }
            }
            case HOLDING -> {
                if (!hasTarget) { master.setArcState(RailgunEmitterBlockEntity.ArcState.IDLE); break; }
                master.drawPower(MagConfig.railgunHoldFeCost());
                trapTargets(container, server, channel, facing, effL, true);   // hold: damp forward too
                if (master.consumeFireRequest() || other.consumeFireRequest()) {
                    master.setArcState(RailgunEmitterBlockEntity.ArcState.LAUNCHING);
                    master.setLaunchTicks(0);
                    triggerRailgunFire(server, master.getBlockPos());
                }
            }
            case LAUNCHING -> {
                final int feCost = MagConfig.railgunFeCostBase() + MagConfig.railgunFeCostPerLength() * effL;
                if (!hasTarget || master.launchTicks() >= MagConfig.railgunMaxLaunchTicks() || !master.drawPower(feCost)) {
                    master.setArcState(RailgunEmitterBlockEntity.ArcState.COOLDOWN);
                    master.setCooldownTicks(MagConfig.railgunCooldownTicks());
                } else {
                    accelerateTargets(container, server, channel, facing, effL);
                    master.setLaunchTicks(master.launchTicks() + 1);
                }
            }
            case COOLDOWN -> { /* decays in the BE tick, then re-arms to IDLE */ }
        }
    }

    private static void triggerRailgunFire(final ServerLevel server, final BlockPos pos) {
        for (final net.minecraft.server.level.ServerPlayer player : server.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 16.0 * 16.0) {
                com.stonytark.magnetization.registry.MagTriggers.RAILGUN_FIRED.get().trigger(player);
            }
        }
    }

    // ── Rail geometry ──

    /** Count contiguous {@code #railgun_rails} blocks stepping from the emitter in FACING. */
    public static int walkRail(final ServerLevel level, final BlockPos emitter, final Direction facing) {
        int len = 0;
        final BlockPos.MutableBlockPos cur = emitter.relative(facing).mutable();
        final int max = MagConfig.railgunMaxLength();
        while (len < max && level.getBlockState(cur).is(MagTags.RAILGUN_RAILS)) {
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
                                             final Direction facing, final Set<BlockPos> snapshot) {
        final int[] count = {0};
        final BlockPos found = scanSiblings(level, pos, facing, snapshot, count);
        if (count[0] > 1) return new SiblingResult(null, true);   // 3+ rails → dissipate
        if (found != null) {
            final int[] partnerCount = {0};
            scanSiblings(level, found, facing, snapshot, partnerCount);
            if (partnerCount[0] > 1) return new SiblingResult(null, true);   // partner is the middle of 3+
        }
        return new SiblingResult(found, false);
    }

    /** Count qualifying parallel siblings of {@code pos}; returns the last one found
     *  and writes the total into {@code countOut[0]}. */
    private static @Nullable BlockPos scanSiblings(final ServerLevel level, final BlockPos pos,
                                                   final Direction facing, final Set<BlockPos> snapshot,
                                                   final int[] countOut) {
        BlockPos found = null;
        int count = 0;
        final int maxGap = MagConfig.railgunMaxGap();
        for (final BlockPos other : snapshot) {
            if (other.equals(pos)) continue;
            if (!(level.getBlockEntity(other) instanceof RailgunEmitterBlockEntity)) continue;
            final BlockState s = level.getBlockState(other);
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
    private static AABB channelBox(final BlockPos master, final BlockPos other, final Direction facing, final int effL) {
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
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
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

    private static boolean anyShipInChannel(final @Nullable SubLevelContainer container, final ServerLevel server, final AABB channel) {
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
    private static void trapTargets(final @Nullable SubLevelContainer container, final ServerLevel server,
                                    final AABB channel, final Direction facing, final int effL, final boolean dampForward) {
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        final double lat = MagConfig.railgunLateralDamp();
        if (container != null) {
            for (final SubLevel sub : container.queryIntersecting(sable(channel))) {
                if (!(sub instanceof ServerSubLevel ship)) continue;
                final RigidBodyHandle h = RigidBodyHandle.of(ship);
                if (h == null || !h.isValid()) continue;
                final Vector3dc v = h.getLinearVelocity();
                final double along = v.x() * axis.x + v.y() * axis.y + v.z() * axis.z;
                final Vec3 lateral = new Vec3(v.x() - along * axis.x, v.y() - along * axis.y, v.z() - along * axis.z);
                final Vector3d dv = new Vector3d(-lateral.x * lat, -lateral.y * lat, -lateral.z * lat);
                if (dampForward) { dv.add(-along * axis.x * 0.5, -along * axis.y * 0.5, -along * axis.z * 0.5); }
                h.addLinearAndAngularVelocity(dv, new Vector3d());
            }
        }
    }

    private static void accelerateTargets(final @Nullable SubLevelContainer container, final ServerLevel server,
                                          final AABB channel, final Direction facing, final int effL) {
        final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
        final double magnitude = MagConfig.railgunForceBase() * Math.pow(effL, MagConfig.railgunForceExponent());
        final double lat = MagConfig.railgunLateralDamp();
        final double maxSpeed = MagConfig.railgunMaxSpeed();

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
                // Forward push (speed-capped).
                if (along < maxSpeed) {
                    final double mass = ship.getMassTracker().getMass();
                    final org.joml.Vector3dc com = ship.getMassTracker().getCenterOfMass();
                    SableBridge.applyWorldImpulse(ship,
                            new Vec3(com.x(), com.y(), com.z()),
                            new Vec3(axis.x * magnitude * mass, axis.y * magnitude * mass, axis.z * magnitude * mass));
                }
                breakAhead(server, ship.boundingBox(), facing);
            }
        }

        // Magnetic entities.
        final double entMag = magnitude * MagConfig.railgunEntityScale();
        for (final Entity e : server.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, channel, RailgunHandler::isMagnetizable)) {
            final Vec3 v = e.getDeltaMovement();
            final double along = v.dot(axis);
            final Vec3 lateral = v.subtract(axis.scale(along));
            Vec3 nv = lateral.scale(1.0 - lat).add(axis.scale(along));
            if (along < maxSpeed) nv = nv.add(axis.scale(entMag));
            e.setDeltaMovement(nv);
            e.hurtMarked = true;
        }
    }

    /** Smash obstructing blocks in a small leading slab ahead of the object along FACING.
     *  Rails + emitters + excavator-immune + bedrock + block entities are spared. */
    private static void breakAhead(final ServerLevel server, final BoundingBox3dc bb, final Direction facing) {
        if (!MagConfig.railgunBreaksBlocks()) return;
        final var n = facing.getNormal();
        final int budget = MagConfig.railgunDestroyBudgetPerTick();
        int broken = 0;
        final int x0 = (int) Math.floor(bb.minX()), x1 = (int) Math.ceil(bb.maxX());
        final int y0 = (int) Math.floor(bb.minY()), y1 = (int) Math.ceil(bb.maxY());
        final int z0 = (int) Math.floor(bb.minZ()), z1 = (int) Math.ceil(bb.maxZ());
        // One-block leading slab on the muzzle side.
        final int lead = 1;
        for (int x = x0; x <= x1 && broken < budget; x++) {
            for (int y = y0; y <= y1 && broken < budget; y++) {
                for (int z = z0; z <= z1 && broken < budget; z++) {
                    final BlockPos p = new BlockPos(
                            x + n.getX() * lead, y + n.getY() * lead, z + n.getZ() * lead);
                    if (breakIfObstructing(server, p)) broken++;
                }
            }
        }
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
