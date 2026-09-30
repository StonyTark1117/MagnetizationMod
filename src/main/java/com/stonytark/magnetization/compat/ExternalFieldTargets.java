package com.stonytark.magnetization.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Conservative per-pass field recipients. Includes fluid cells and remote-effect
 * entry points, not just entities that can receive an ordinary local impulse. */
public final class ExternalFieldTargets {
    private final List<AABB> bounds = new ArrayList<>();

    public void add(final AABB box) { bounds.add(box); }

    /** Inclusive comparisons retain touching bounds and large ships whose center
     * lies outside the field. Unknown adapter ranges must never be culled. */
    public boolean mayReach(final BlockPos emitter, final double range) {
        if (!Double.isFinite(range)) return true;
        final double x = emitter.getX() + 0.5d, y = emitter.getY() + 0.5d, z = emitter.getZ() + 0.5d;
        for (final AABB box : bounds) {
            if (box.maxX >= x - range && box.minX <= x + range
                    && box.maxY >= y - range && box.minY <= y + range
                    && box.maxZ >= z - range && box.minZ <= z + range) return true;
        }
        return false;
    }
}
