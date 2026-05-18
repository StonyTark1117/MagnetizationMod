package com.stonytark.magnetization.content.anchor;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Strong omnidirectional pull, but with sticky targeting: the first ship that
 * enters the anchor's range while powered becomes the bound target. The binding
 * is persisted to NBT and re-acquired across world reload, so a docked airship
 * stays docked. While bound, force is applied only to that ship — bystander
 * ships passing through the field aren't affected.
 */
public class MagneticAnchorBlockEntity extends AbstractEmitterBlockEntity {

    private static final Logger DEBUG_LOG = LoggerFactory.getLogger("magnetization/AnchorDebug");
    private long lastDebugTick = -1L;

    /** Per-tick angular damp applied when this anchor has at least one peer
     *  bound to the same ship. 30% per second (every 20 ticks). */
    private static final double COOP_ANGULAR_DAMP_FACTOR = 0.30d;
    /** Throttle for the coop-peer scan + damp call. Once per second is plenty. */
    private static final int COOP_TICK_INTERVAL = 20;

    private @Nullable UUID boundShipId = null;
    private long lastCoopTick = Long.MIN_VALUE;

    public MagneticAnchorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_ANCHOR.get(), pos, state);
    }

    @Override
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        try { return MagConfig.ANCHOR_MAX_RANGE.get() / 2.0d; }
        catch (final Throwable t) { return tier.range(); }
    }

    public @Nullable UUID boundShipId() {
        return boundShipId;
    }

    /** Drop the binding (e.g. on player wrench-interaction or de-power for too long). */
    public void releaseBinding() {
        if (boundShipId != null) {
            boundShipId = null;
            setChanged();
        }
    }

    @Override
    public void resetOverrides() {
        super.resetOverrides();
        // Sneak-wrench on an anchor also clears its sticky ship binding so players
        // can re-bind to a different vessel without depowering the anchor.
        releaseBinding();
    }

    @Override
    protected @Nullable Predicate<ServerSubLevel> shipFilter() {
        final UUID id = boundShipId;
        return id == null ? null : sub -> id.equals(sub.getUniqueId());
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        final boolean powered = isPowered();
        if (!(level instanceof ServerLevel server)) return null;
        if (!powered) {
            debugLog(server, "computeField: anchor at {} unpowered", getBlockPos().toShortString());
            return null;
        }

        final MagneticStrength strength = effectiveStrength(MagneticStrength.STRONG);
        final double range = effectiveRange(strength);
        // Pick a target if we don't have one yet — closest sub-level whose bbox intersects ours.
        if (boundShipId == null) {
            final ServerSubLevel target = pickClosestShip(server, range);
            if (target != null) {
                boundShipId = target.getUniqueId();
                setChanged();
                DEBUG_LOG.info("anchor at {} BOUND to ship {}", getBlockPos().toShortString(), target.getUniqueId());
            } else {
                debugLog(server, "computeField: anchor at {} powered but no ship in range",
                        getBlockPos().toShortString());
            }
        }

        // No target → no field. Holding the binding but waiting for the ship to come back into range
        // also produces no field (the ship may be unloaded or far away).
        if (boundShipId == null) return null;

        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        final SubLevel target = container.getSubLevel(boundShipId);
        if (target == null) {
            // Stale binding (ship was shattered or unloaded). Drop it so we re-bind
            // to a live ship next tick rather than emitting a useless field forever.
            debugLog(server, "computeField: anchor at {} clearing stale binding {} (lookup null)",
                    getBlockPos().toShortString(), boundShipId);
            boundShipId = null;
            setChanged();
            return null;
        }

        debugLog(server, "computeField: anchor at {} EMITTING field, bound={}",
                getBlockPos().toShortString(), boundShipId);
        // Cooperative anchor stabilization: when one or more peer anchors in
        // this level share the same boundShipId, damp the bound ship's angular
        // velocity so a multi-anchor dock keeps the airship level instead of
        // letting it spin from accumulated torques. Throttled — every-tick
        // damping would over-stiffen.
        if (target instanceof ServerSubLevel boundShip
                && server.getGameTime() - lastCoopTick >= COOP_TICK_INTERVAL
                && hasCoopPeer(server)) {
            lastCoopTick = server.getGameTime();
            SableBridge.dampAngularVelocity(boundShip, COOP_ANGULAR_DAMP_FACTOR);
        }
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                new Vec3(0, 1, 0),
                effectivePolarity(MagneticPolarity.SOUTH),
                strength,
                MagneticField.Shape.OMNIDIRECTIONAL,
                range == strength.range() ? 0.0d : range
        );
    }

    /** Walk every loaded emitter pos in this level to find another anchor with
     *  the same {@code boundShipId}. The scan is O(N emitters) but throttled to
     *  once per second per anchor, so cumulative cost is O(N) per second per
     *  anchor in a coop arrangement — fine at typical scale. */
    private boolean hasCoopPeer(final ServerLevel server) {
        final UUID mine = boundShipId;
        if (mine == null) return false;
        final BlockPos myPos = getBlockPos();
        final boolean[] found = { false };
        EmitterRegistry.forEach(server, (lvl, p) -> {
            if (found[0] || p.equals(myPos)) return;
            if (lvl.getBlockEntity(p) instanceof MagneticAnchorBlockEntity peer
                    && peer.isPowered()
                    && mine.equals(peer.boundShipId())) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /** Throttle: log at most once every 20 ticks (1s) so the console isn't spammed.
     *  Gated behind config.debug.debugLogging to keep production logs clean. */
    private void debugLog(final ServerLevel server, final String fmt, final Object... args) {
        if (!MagConfig.debugLogging()) return;
        final long tick = server.getGameTime();
        if (tick - lastDebugTick < 20L) return;
        lastDebugTick = tick;
        DEBUG_LOG.info(fmt, args);
    }

    private @Nullable ServerSubLevel pickClosestShip(final ServerLevel level, final double range) {
        final Vec3 origin = Vec3.atCenterOf(getBlockPos());
        final BoundingBox3d searchBox = new BoundingBox3d(
                origin.x - range, origin.y - range, origin.z - range,
                origin.x + range, origin.y + range, origin.z + range
        );
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        final ServerSubLevel host = SableBridge.subLevelAt(level, getBlockPos());
        ServerSubLevel best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (SubLevel sub : container.queryIntersecting(searchBox)) {
            if (!(sub instanceof ServerSubLevel server)) continue;
            // exclude any host the anchor itself sits on (nonsensical to bind to your own ship)
            if (host == server) continue;
            // Reject phantom/zombie sub-levels — Sable can leave them in the spatial
            // index after a shatter or partial unload. A live ship has positive mass.
            if (server.getMassTracker().isInvalid()
                    || server.getMassTracker().getMass() <= 0.0) continue;
            // Verify the sub-level is still resolvable by UUID — guards against
            // entries that exist in the spatial index but not the registry.
            if (container.getSubLevel(server.getUniqueId()) == null) continue;
            final var box = sub.boundingBox();
            final double dx = origin.x - clamp(origin.x, box.minX(), box.maxX());
            final double dy = origin.y - clamp(origin.y, box.minY(), box.maxY());
            final double dz = origin.z - clamp(origin.z, box.minZ(), box.maxZ());
            final double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDistSqr) {
                bestDistSqr = d2;
                best = server;
            }
        }
        return best;
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundShipId != null) tag.putUUID("BoundShip", boundShipId);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundShipId = tag.hasUUID("BoundShip") ? tag.getUUID("BoundShip") : null;
    }

    @Override
    public void fillCrashReportCategory(final net.minecraft.CrashReportCategory category) {
        super.fillCrashReportCategory(category);
        category.setDetail("Magnetization Anchor Bound Ship",
                () -> boundShipId == null ? "<unbound>" : boundShipId.toString());
    }
}
