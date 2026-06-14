package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticField;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper over the slice of Sable's API the addon uses, so call-sites stay
 * out of {@code dev.ryanhcode.sable.*} packages.
 */
public final class SableBridge {

    /** Per-tick timestep — vanilla Minecraft is 20 ticks/second. Used to convert force [N] to Δv [m/s]. */
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/SableBridge");
    /** Per-tag throttle for failure logs. Keeps a single noisy native panic
     *  from flooding the log at 20 Hz while still surfacing the *first*
     *  occurrence and a periodic reminder. */
    private static final long WARN_THROTTLE_MS = 30_000L;
    private static final ConcurrentHashMap<String, Long> LAST_WARN_MS = new ConcurrentHashMap<>();

    private static void warnThrottled(final String tag, final Throwable t, final ServerSubLevel sub) {
        final long now = System.currentTimeMillis();
        final Long prev = LAST_WARN_MS.put(tag, now);
        if (prev != null && (now - prev) < WARN_THROTTLE_MS) return;
        final String shipId = sub == null ? "<null>" : String.valueOf(sub.getUniqueId());
        LOG.warn("Sable interop failed [{}] for ship {}: {}", tag, shipId, t.toString(), t);
    }

    private SableBridge() {}

    /**
     * @return the {@link ServerSubLevel} containing {@code worldPos}, or {@code null}
     *         if the position is in the open world (not on a Sable contraption) or
     *         if we're on the client.
     */
    public static @Nullable ServerSubLevel subLevelAt(final Level level, final Vec3 worldPos) {
        return subLevelAt(level, BlockPos.containing(worldPos));
    }

    public static @Nullable ServerSubLevel subLevelAt(final Level level, final BlockPos pos) {
        final SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub instanceof ServerSubLevel server ? server : null;
    }

    /**
     * Collect the UUIDs of every sub-level connected to {@code host} through the
     * Sable assembly graph — i.e. every part of the same Create: Aeronautics craft,
     * including parts joined to it by bearings, springs and hinges (each of which is
     * its own rigid body). Always includes {@code host} itself.
     *
     * <p>Used to keep an emitter from applying force to its own craft: excluding
     * only the single sub-level the emitter sits on leaves the constraint-connected
     * subgroups free to be yanked around, which fights the joints holding the craft
     * together and can spike the physics solver.
     *
     * <p>{@link dev.ryanhcode.sable.api.SubLevelHelper#getConnectedChain} is
     * {@code @ApiStatus.Internal}, so this is wrapped defensively: any failure
     * (signature drift, early-load nulls) falls back to just the host's own id, which
     * reproduces the pre-1.1.4 single-sub-level exclusion rather than throwing.
     *
     * <p>Cached per host with a short TTL: the chain only changes on
     * assembly/disassembly, but a magnet-heavy craft has many emitters each calling
     * this every tick — without the cache they'd all re-walk the same assembly graph
     * and rebuild the same set every tick. The cache collapses that to one walk per
     * craft per TTL window. A detach/attach is reflected within {@link #CHAIN_CACHE_TTL}
     * ticks, which is harmless (a subgroup is briefly still-excluded or not-yet-excluded).
     */
    public static java.util.Set<java.util.UUID> connectedChainIds(final ServerSubLevel host, final long now) {
        final java.util.UUID id = host.getUniqueId();
        final ChainEntry cached = CHAIN_CACHE.get(id);
        if (cached != null && now - cached.tick() < CHAIN_CACHE_TTL) return cached.ids();

        final java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
        ids.add(id);
        try {
            for (final SubLevel sub : dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(host)) {
                if (sub != null) ids.add(sub.getUniqueId());
            }
        } catch (final Throwable t) {
            warnThrottled("connectedChainIds", t, host);
        }
        CHAIN_CACHE.put(id, new ChainEntry(ids, now));
        pruneChainCacheIfDue(now);
        return ids;
    }

    /** TTL (ticks) for the connected-chain cache. 20 = 1 s, mirroring the ship
     *  magnetic-state scan interval. */
    private static final long CHAIN_CACHE_TTL = 20L;
    /** How long (ticks) a chain entry may go un-refreshed before it's pruned, so
     *  destroyed/unloaded ships don't pin entries for the session. */
    private static final long CHAIN_CACHE_EVICT = 600L;
    private static final ConcurrentHashMap<java.util.UUID, ChainEntry> CHAIN_CACHE = new ConcurrentHashMap<>();
    private static long lastChainPruneTick = 0L;

    private record ChainEntry(java.util.Set<java.util.UUID> ids, long tick) {}

    private static void pruneChainCacheIfDue(final long now) {
        // Coarse full sweep (not every call) to bound the map: drop entries that
        // haven't been refreshed within the evict window — i.e. ships no longer
        // hosting an active emitter (destroyed, unloaded, or powered down).
        if (now - lastChainPruneTick < CHAIN_CACHE_EVICT) return;
        lastChainPruneTick = now;
        CHAIN_CACHE.entrySet().removeIf(e -> now - e.getValue().tick() >= CHAIN_CACHE_EVICT);
    }

    /**
     * Transform a sub-level-local field into world space using a contraption's
     * current pose. Both the origin and the directional axis are rotated and
     * translated, so as the ship moves and rotates the field follows.
     *
     * <p>Callers pass the pose directly (via {@code host.logicalPose()}) — never
     * re-query by the BE's blockPos. On a contraption, {@code getBlockPos()} is
     * a sub-level-local position and looking it up against the outer world level
     * silently fails, leaving the field in local space. That presents as
     * "magnets stuck to the cardinal direction they were placed in", because
     * the local axis never updates as the ship rotates.
     */
    public static MagneticField promoteToWorldSpace(
            final Pose3dc pose, final MagneticField field
    ) {
        final Vec3 worldOrigin = pose.transformPosition(field.origin());
        final Vec3 worldAxis = pose.transformNormal(field.axis()).normalize();
        return new MagneticField(
                worldOrigin, worldAxis,
                field.polarity(), field.strength(), field.shape(),
                field.customRange());
    }

    /**
     * Apply a force at a point that lives on the same contraption as the
     * caller (e.g. an electromagnet on a ship pushing that ship around). Both
     * point and force are expressed in the sub-level's local frame.
     *
     * <p>Force units: Newtons. Mass: kg. We integrate over one tick to a
     * velocity delta {@code Δv = F · Δt / m} and inject it directly via
     * {@link RigidBodyHandle#addLinearAndAngularVelocity}. Sable's force-queue
     * pipeline (which routes through Rapier's {@code applyForceAndTorque})
     * produced no visible motion in testing; direct velocity injection
     * reliably moves the rigid body.
     *
     * <p>The {@link QueuedForceGroup#applyAndRecordPointForce} call below is
     * preserved purely for display: it surfaces our force in Sable's
     * force-group gizmo for debugging.
     *
     * <p><b>Caller is responsible for not invoking this with a stale or
     * unregistered sub-level.</b> {@link FieldApplicator} guards against
     * phantoms (invalid mass, zero mass, UUID not resolvable) before calling.
     */
    public static void applyLocalImpulse(
            final ServerSubLevel subLevel,
            final Vector3d pointLocal,
            final Vector3d forceLocal
    ) {
        // Re-check phantom conditions defensively — caller filters but races
        // are possible if a sub-level is removed between query and apply.
        if (subLevel.getMassTracker().isInvalid() || subLevel.getMassTracker().getMass() <= 0.0) return;

        // Display-only entry: surfaces the force in Sable's gizmo.
        final QueuedForceGroup group = subLevel.getOrCreateQueuedForceGroup(ForceGroups.MAGNETIC_FORCE.get());
        group.applyAndRecordPointForce(pointLocal, forceLocal);

        // Convert force [N] → per-tick velocity delta [m/s].
        final double mass = Math.max(subLevel.getMassTracker().getMass(), 0.0001);
        final double scale = TICK_DT_SECONDS / mass;
        final RigidBodyHandle handle;
        try {
            handle = RigidBodyHandle.of(subLevel);
        } catch (final Throwable t) {
            warnThrottled("applyLocalImpulse:handleOf", t, subLevel);
            return;
        }
        if (handle == null) return;
        final Vector3d dvLocal = new Vector3d(forceLocal.x * scale, forceLocal.y * scale, forceLocal.z * scale);

        // Torque from an off-center impulse: τ = r × F where r = pointLocal − COM.
        // Δω = I⁻¹ · τ · Δt. Sable hands us the inverse inertia tensor in the
        // sub-level's local frame, which matches our pointLocal/forceLocal frame,
        // so no rotation is needed for the I⁻¹ step. An impulse at the COM
        // produces r = 0 and contributes no torque — the math degrades cleanly.
        final org.joml.Vector3dc comC = subLevel.getMassTracker().getCenterOfMass();
        final Vector3d r = new Vector3d(
                pointLocal.x - comC.x(),
                pointLocal.y - comC.y(),
                pointLocal.z - comC.z());
        final Vector3d torque = new Vector3d();
        r.cross(forceLocal, torque);
        final Vector3d dOmegaLocal = new Vector3d();
        subLevel.getMassTracker().getInverseInertiaTensor().transform(torque, dOmegaLocal);
        dOmegaLocal.mul(TICK_DT_SECONDS);

        if (dvLocal.lengthSquared() < 1.0e-8 && dOmegaLocal.lengthSquared() < 1.0e-10) return;

        // Sable's getLinearVelocity / getAngularVelocity return GLOBAL (world)
        // velocities, and addLinearAndAngularVelocity is their counterpart — it
        // also operates in world frame. Our dvLocal and dOmegaLocal are in the
        // ship's local frame, so we must rotate them into world space before
        // adding. Skipping this conversion presents as "ships spin near a magnet"
        // and "repelled despite having opposite poles" once a ship has rotated
        // off identity, because the impulse direction gets rotated by the ship's
        // own orientation. The fix is just an orientation transform — no
        // translation, since velocities are direction vectors.
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3d dvWorld = new Vector3d(dvLocal);
        pose.transformNormal(dvWorld);
        final Vector3d dOmegaWorld = new Vector3d(dOmegaLocal);
        pose.transformNormal(dOmegaWorld);

        try {
            handle.addLinearAndAngularVelocity(dvWorld, dOmegaWorld);
        } catch (final Throwable t) {
            // Java-side exceptions (e.g. NPE in handle internals) — log and
            // continue. Native Rapier panics will still abort, which is why
            // FieldApplicator filters out phantom sub-levels upstream.
            warnThrottled("applyLocalImpulse:addVelocity", t, subLevel);
        }
    }

    /**
     * Apply a force whose location and direction are known in <em>world</em> space
     * to a sub-level — transforms both into the sub-level's local frame first.
     * Use this for environmental emitters: a magnet on the ground pulling a ship
     * that flies overhead.
     */
    /**
     * Damp the sub-level's current angular velocity by a fraction. Used by
     * cooperative-anchor pairs to keep a docked ship level: two anchors bound
     * to the same ship each call this, so spin gets bled off until it settles.
     *
     * @param factor 0..1; the angular velocity is reduced by this fraction
     *               per call. 0.3 ≈ 30% damp per tick.
     */
    /**
     * Damp the sub-level's current linear velocity by a fraction. Called once
     * per tick per ship touched by a magnetic impulse, so a ship under constant
     * magnetic pull reaches a terminal velocity instead of accelerating forever.
     * {@code factor} 0..1 — the linear velocity is reduced by this fraction per
     * call. 0.02 ≈ 2% per tick = terminal velocity at ~50× the per-tick
     * acceleration cap.
     */
    public static void dampLinearVelocity(final ServerSubLevel subLevel, final double factor) {
        if (factor <= 0.0d) return;
        if (subLevel.getMassTracker().isInvalid() || subLevel.getMassTracker().getMass() <= 0.0) return;
        final RigidBodyHandle handle;
        try {
            handle = RigidBodyHandle.of(subLevel);
        } catch (final Throwable t) {
            warnThrottled("dampLinearVelocity:handleOf", t, subLevel);
            return;
        }
        if (handle == null) return;
        final Vector3d v = new Vector3d();
        try {
            handle.getLinearVelocity(v);
        } catch (final Throwable t) {
            warnThrottled("dampLinearVelocity:getLinearVelocity", t, subLevel);
            return;
        }
        if (v.lengthSquared() < 1.0e-8) return;
        final Vector3d delta = new Vector3d(-v.x * factor, -v.y * factor, -v.z * factor);
        try {
            handle.addLinearAndAngularVelocity(delta, new Vector3d(0, 0, 0));
        } catch (final Throwable t) {
            warnThrottled("dampLinearVelocity:addVelocity", t, subLevel);
        }
    }

    public static void dampAngularVelocity(final ServerSubLevel subLevel, final double factor) {
        if (subLevel.getMassTracker().isInvalid() || subLevel.getMassTracker().getMass() <= 0.0) return;
        final RigidBodyHandle handle;
        try {
            handle = RigidBodyHandle.of(subLevel);
        } catch (final Throwable t) {
            warnThrottled("dampAngularVelocity:handleOf", t, subLevel);
            return;
        }
        if (handle == null) return;
        final Vector3d w = new Vector3d();
        try {
            handle.getAngularVelocity(w);
        } catch (final Throwable t) {
            warnThrottled("dampAngularVelocity:getAngularVelocity", t, subLevel);
            return;
        }
        if (w.lengthSquared() < 1.0e-8) return;
        // Negative scaled: adding -f*w to w produces (1-f)*w.
        final Vector3d delta = new Vector3d(-w.x * factor, -w.y * factor, -w.z * factor);
        try {
            handle.addLinearAndAngularVelocity(new Vector3d(0, 0, 0), delta);
        } catch (final Throwable t) {
            warnThrottled("dampAngularVelocity:addVelocity", t, subLevel);
        }
    }

    public static void applyWorldImpulse(
            final ServerSubLevel subLevel,
            final Vec3 worldPoint,
            final Vec3 worldImpulse
    ) {
        final Pose3dc pose = subLevel.logicalPose();
        final Vec3 localPointVec = pose.transformPositionInverse(worldPoint);
        final Vec3 localImpulseVec = pose.transformNormalInverse(worldImpulse);
        applyLocalImpulse(
                subLevel,
                new Vector3d(localPointVec.x, localPointVec.y, localPointVec.z),
                new Vector3d(localImpulseVec.x, localImpulseVec.y, localImpulseVec.z)
        );
    }
}
