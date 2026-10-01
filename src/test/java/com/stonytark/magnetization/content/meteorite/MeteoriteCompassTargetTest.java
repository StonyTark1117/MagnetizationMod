package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.content.meteorite.MeteoriteFieldRegistry.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MeteoriteCompassTargetTest {
    private static final Vec3 ORIGIN = BlockPos.ZERO.getCenter();

    @Test
    void nearestVirtualMeteoriteNeedsNoCoreBlockEntity() {
        final Entry far = new Entry(new BlockPos(100, 0, 0), 100);
        final Entry near = new Entry(new BlockPos(-10, 0, 0), 100);
        assertEquals(near, MeteoriteFieldRegistry.nearestActive(List.of(far, near), ORIGIN, 512, 200, 12000));
    }

    @Test
    void skipsDecayedNearestSourceButKeepsWeakSource() {
        final Entry dead = new Entry(new BlockPos(1, 0, 0), 0);
        final Entry weak = new Entry(new BlockPos(10, 0, 0), 1);
        assertEquals(weak, MeteoriteFieldRegistry.nearestActive(List.of(dead, weak), ORIGIN, 512, 12000, 12000));
        assertNull(MeteoriteFieldRegistry.nearestActive(List.of(weak), ORIGIN, 512, 12001, 12000));
    }

    @Test
    void honorsConfiguredRangeIncludingVerticalDistance() {
        final Entry above = new Entry(new BlockPos(0, 32, 0), 0);
        assertNull(MeteoriteFieldRegistry.nearestActive(List.of(above), ORIGIN, 32, 1, 12000));
        assertEquals(above, MeteoriteFieldRegistry.nearestActive(List.of(above), ORIGIN, 33, 1, 12000));
    }

    @Test
    void noSourcesProducesNoTarget() {
        assertNull(MeteoriteFieldRegistry.nearestActive(List.of(), ORIGIN, 512, 0, 12000));
    }
}
