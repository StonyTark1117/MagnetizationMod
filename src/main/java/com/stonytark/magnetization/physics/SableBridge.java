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

/**
 * Thin wrapper over the slice of Sable's API the addon uses, so call-sites stay
 * out of {@code dev.ryanhcode.sable.*} packages.
 */
public final class SableBridge {

    /** Per-tick timestep — vanilla Minecraft is 20 ticks/second. Used to convert force [N] to Δv [m/s]. */
    private static final double TICK_DT_SECONDS = 1.0 / 20.0;

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
     * @return the {@link SubLevel} (server or client) at {@code pos}, or {@code null}
     *         if not on a contraption.
     */
    public static @Nullable SubLevel anySubLevelAt(final Level level, final BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos);
    }

    /**
     * If the emitter at {@code emitterPos} is itself sitting on a contraption,
     * its blockstate-derived origin and axis are in sub-level-local coordinates;
     * transform them into world coordinates so the field interacts correctly with
     * other ships. If the emitter is in the open world, returns {@code field}
     * unchanged.
     */
    public static MagneticField promoteToWorldSpace(
            final Level level, final BlockPos emitterPos, final MagneticField field
    ) {
        final SubLevel host = anySubLevelAt(level, emitterPos);
        if (host == null) return field;
        final Pose3dc pose = host.logicalPose();
        final Vec3 worldOrigin = pose.transformPosition(field.origin());
        final Vec3 worldAxis = pose.transformNormal(field.axis()).normalize();
        return new MagneticField(worldOrigin, worldAxis, field.polarity(), field.strength(), field.shape());
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
            return;
        }
        if (handle == null) return;
        final Vector3d dv = new Vector3d(forceLocal.x * scale, forceLocal.y * scale, forceLocal.z * scale);
        if (dv.lengthSquared() < 1.0e-8) return;
        try {
            handle.addLinearAndAngularVelocity(dv, new Vector3d(0, 0, 0));
        } catch (final Throwable t) {
            // Java-side exceptions (e.g. NPE in handle internals) — silently
            // skip. Native Rapier panics will still abort, which is why
            // FieldApplicator filters out phantom sub-levels upstream.
        }
        // Note: torque from off-center forces is dropped. For small ships the
        // rotational effect is negligible vs the translational pull.
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
    public static void dampAngularVelocity(final ServerSubLevel subLevel, final double factor) {
        if (subLevel.getMassTracker().isInvalid() || subLevel.getMassTracker().getMass() <= 0.0) return;
        final RigidBodyHandle handle;
        try {
            handle = RigidBodyHandle.of(subLevel);
        } catch (final Throwable t) {
            return;
        }
        if (handle == null) return;
        final Vector3d w = new Vector3d();
        try {
            handle.getAngularVelocity(w);
        } catch (final Throwable t) {
            return;
        }
        if (w.lengthSquared() < 1.0e-8) return;
        // Negative scaled: adding -f*w to w produces (1-f)*w.
        final Vector3d delta = new Vector3d(-w.x * factor, -w.y * factor, -w.z * factor);
        try {
            handle.addLinearAndAngularVelocity(new Vector3d(0, 0, 0), delta);
        } catch (final Throwable t) {
            // Native panics fall through quietly — caller filtered phantoms.
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
