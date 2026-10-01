package com.stonytark.magnetization.content.docking;

import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.anchor.MagneticAnchorBlockEntity;
import com.stonytark.magnetization.physics.SableBridge;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import static com.stonytark.magnetization.content.docking.DockingState.Reason.*;

public final class DockingMeasurements {
    private DockingMeasurements() {}
    public record Sample(DockingState.Reading reading, double range) {}

    public static Sample sample(ServerLevel level, BlockPos anchorPos) {
        if (anchorPos == null) return missing(UNLINKED);
        if (!level.hasChunkAt(anchorPos) || !(level.getBlockEntity(anchorPos) instanceof MagneticAnchorBlockEntity anchor))
            return missing(ANCHOR_UNAVAILABLE);
        if (anchor.boundShipId() == null) return missing(NO_TARGET);
        var container = SubLevelContainer.getContainer(level);
        if (container == null || !(container.getSubLevel(anchor.boundShipId()) instanceof ServerSubLevel target)
                || target.getMassTracker().isInvalid() || target.getMassTracker().getMass() <= 0)
            return new Sample(new DockingState.Reading(anchor.boundShipId(), Double.POSITIVE_INFINITY, 0, 0, TARGET_UNAVAILABLE), 8);
        var host = SableBridge.subLevelOf(anchor);
        Vec3 origin = host == null ? anchorPos.getCenter() : host.logicalPose().transformPosition(anchorPos.getCenter());
        // Measure to the oriented hull, not its oversized world-space AABB.
        Vector3d localVector = new Vector3d(origin.x, origin.y, origin.z);
        target.logicalPose().transformPositionInverse(localVector);
        Vec3 local = new Vec3(localVector.x, localVector.y, localVector.z);
        var bounds = target.getPlot().getBoundingBox();
        Vec3 contactLocal = new Vec3(Math.clamp(local.x, bounds.minX(), bounds.maxX() + 1.0),
                Math.clamp(local.y, bounds.minY(), bounds.maxY() + 1.0), Math.clamp(local.z, bounds.minZ(), bounds.maxZ() + 1.0));
        Vec3 contact = target.logicalPose().transformPosition(contactLocal);
        Motion shipMotion = motion(target, contact);
        Motion dockMotion = motion(host, contact);
        if (shipMotion == null || dockMotion == null) return missing(TARGET_UNAVAILABLE);
        double range = anchor.effectiveRange(anchor.effectiveStrength(MagneticStrength.STRONG));
        return new Sample(new DockingState.Reading(target.getUniqueId(), origin.distanceTo(contact),
                shipMotion.velocity().distance(dockMotion.velocity()),
                shipMotion.spin().distance(dockMotion.spin()), null), range);
    }
    private record Motion(Vector3d velocity, Vector3d spin) {}
    private static Motion motion(ServerSubLevel ship, Vec3 point) {
        if (ship == null) return new Motion(new Vector3d(), new Vector3d());
        if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0) return null;
        var body = RigidBodyHandle.of(ship);
        if (body == null) return null;
        Vector3d velocity = new Vector3d(), spin = new Vector3d();
        body.getLinearVelocity(velocity);
        body.getAngularVelocity(spin);
        Vector3d center = new Vector3d(ship.getMassTracker().getCenterOfMass());
        ship.logicalPose().transformPosition(center);
        Vector3d arm = new Vector3d(point.x, point.y, point.z).sub(center);
        velocity.add(new Vector3d(spin).cross(arm));
        return velocity.isFinite() && spin.isFinite() ? new Motion(velocity, spin) : null;
    }
    private static Sample missing(DockingState.Reason reason) {
        return new Sample(DockingState.Reading.missing(reason), 8);
    }
}
