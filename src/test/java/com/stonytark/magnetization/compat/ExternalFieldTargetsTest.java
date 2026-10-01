package com.stonytark.magnetization.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExternalFieldTargetsTest {
    @Test
    void rejectsSeparationOnEveryAxisIncludingNegativeCoordinates() {
        final BlockPos emitter = new BlockPos(-33, -80, -17);
        final double x = emitter.getX() + .5, y = emitter.getY() + .5, z = emitter.getZ() + .5;
        for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
            final double tx = x + (axis == 0 ? sign * 33 : 0);
            final double ty = y + (axis == 1 ? sign * 33 : 0);
            final double tz = z + (axis == 2 ? sign * 33 : 0);
            final var targets = new ExternalFieldTargets();
            targets.add(new AABB(tx, ty, tz, tx, ty, tz));
            assertFalse(targets.mayReach(emitter, 32), "axis=" + axis + " sign=" + sign);
        }
    }

    @Test
    void retainsTouchingBoundsLargeShipsAndThinApertures() {
        final var targets = new ExternalFieldTargets();
        targets.add(new AABB(.5, 32.5, .5, .5, 32.5, .5));
        assertTrue(targets.mayReach(BlockPos.ZERO, 32));
        assertFalse(targets.mayReach(BlockPos.ZERO, 31.999));
        targets.add(new AABB(30, 0, 0, 1000, 5, 5));
        assertTrue(targets.mayReach(BlockPos.ZERO, 32), "Hull edge matters, not the ship center");
        final var aperture = new ExternalFieldTargets();
        aperture.add(new AABB(1, 0, 0, 1, 3, 3));
        assertTrue(aperture.mayReach(BlockPos.ZERO, 4));
    }

    @Test
    void emptyRecipientsRejectKnownBoundsButNeverGuessUnknownAdapterRange() {
        final var targets = new ExternalFieldTargets();
        assertFalse(targets.mayReach(BlockPos.ZERO, 32));
        assertTrue(targets.mayReach(BlockPos.ZERO, Double.POSITIVE_INFINITY));
    }
}
