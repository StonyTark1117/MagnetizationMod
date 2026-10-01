package com.stonytark.magnetization.physics.inspection;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ShipInspectionTarget {
    private ShipInspectionTarget() {}
    public static @Nullable SubLevel find(Player player) {
        var container = SubLevelContainer.getContainer(player.level());
        if (container == null) return null;
        Vec3 start = player.getEyePosition(), end = start.add(player.getLookAngle().scale(64));
        double nearest = 64 * 64;
        SubLevel selected = null;
        for (var ship : container.getAllSubLevels()) {
            var b = ship.boundingBox();
            var box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
            var hit = box.clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearest) { nearest = distance; selected = ship; }
        }
        return selected;
    }
}
