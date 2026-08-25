package com.stonytark.magnetization.compat.simulatedmissiles;

import com.squishy.cbcaeronauticsmissiles.content.guidance.GuidanceComputerBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Loaded-only implementation; directly uses the addon's public guidance hook. */
final class MagSimulatedMissilesLoadedCompat {
    private MagSimulatedMissilesLoadedCompat() {}

    static int disableGuidanceInPulse(final ServerLevel level, final Vec3 center,
                                      final double radius) {
        final var container = SubLevelContainer.getContainer(level);
        if (container == null || radius <= 0.0d) return 0;
        final BoundingBox3d broadPhase = new BoundingBox3d(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        int disabled = 0;
        for (final SubLevel candidate : container.queryIntersecting(broadPhase)) {
            if (!(candidate instanceof ServerSubLevel missile)
                    || !sphereIntersects(center, radius, missile.boundingBox())
                    || !hasGuidanceComputer(missile)) continue;
            GuidanceComputerBlockEntity.disableMissileGuidance(missile);
            disabled++;
        }
        return disabled;
    }

    static boolean hasGuidanceComputer(final ServerSubLevel subLevel) {
        for (final var actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof GuidanceComputerBlockEntity) return true;
        }
        return false;
    }

    private static boolean sphereIntersects(final Vec3 center, final double radius,
                                            final BoundingBox3dc box) {
        final double x = Math.max(box.minX(), Math.min(center.x, box.maxX()));
        final double y = Math.max(box.minY(), Math.min(center.y, box.maxY()));
        final double z = Math.max(box.minZ(), Math.min(center.z, box.maxZ()));
        final double dx = x - center.x;
        final double dy = y - center.y;
        final double dz = z - center.z;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
