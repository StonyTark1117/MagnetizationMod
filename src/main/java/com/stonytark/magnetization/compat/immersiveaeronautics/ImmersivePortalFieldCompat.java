package com.stonytark.magnetization.compat.immersiveaeronautics;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.physics.FieldApplicator;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.function.Predicate;

/** Makes magnetic ship forces follow the space transformation of Immersive Portals. */
public final class ImmersivePortalFieldCompat {

    private ImmersivePortalFieldCompat() {}

    /**
     * Projects a source field through each portal aperture it can reach. Only ships
     * are handled on the destination side: vanilla entities already belong to a
     * single dimension and portal recursion is deliberately stopped here.
     */
    public static void applyThroughPortals(
            final ServerLevel source,
            final MagneticField field,
            final @Nullable ServerSubLevel exclude,
            final @Nullable Predicate<ServerSubLevel> shipFilter
    ) {
        final double range = field.range();
        final Vec3 origin = field.origin();
        final AABB search = new AABB(origin, origin).inflate(range);

        for (final Portal portal : source.getEntitiesOfClass(Portal.class, search)) {
            if (!portal.isPortalValid()
                    || portal.getDistanceToNearestPointInPortal(origin) > range) continue;

            final ServerLevel destination = source.getServer().getLevel(portal.getDestDim());
            if (destination == null) continue;

            final double scale = Math.abs(portal.getScaling());
            if (!Double.isFinite(scale) || scale <= 0.0d) continue;

            final Vec3 transformedAxis = portal.transformLocalVec(field.axis()).normalize();
            final MagneticField transformed = new MagneticField(
                    portal.transformPoint(origin),
                    transformedAxis,
                    field.polarity(),
                    field.strength(),
                    field.shape(),
                    range * scale,
                    field.forceOverride());

            final Predicate<ServerSubLevel> throughAperture = ship -> {
                if (shipFilter != null && !shipFilter.test(ship)) return false;
                final var box = ship.boundingBox();
                final Vec3 center = new Vec3(
                        (box.minX() + box.maxX()) * 0.5d,
                        (box.minY() + box.maxY()) * 0.5d,
                        (box.minZ() + box.maxZ()) * 0.5d);
                final Vec3 sourceSideCenter = portal.inverseTransformPoint(center);
                return portal.rayTrace(origin, sourceSideCenter) != null;
            };

            FieldApplicator.applyToSubLevelsOnly(destination, transformed, exclude, throughAperture);
        }
    }
}
