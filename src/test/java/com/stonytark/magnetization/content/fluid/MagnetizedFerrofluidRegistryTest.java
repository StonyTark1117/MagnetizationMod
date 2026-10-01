package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MagnetizedFerrofluidRegistryTest {
    @AfterEach void clear() {
        final var map = MagnetizedFerrofluidRegistry.forLevel(null);
        if (!map.isEmpty()) map.clear();
    }

    @Test void nearbyLookupMatchesFullScanAtNegativeBoundariesAndDistantPools() {
        final var random = new Random(304);
        for (int i = 0; i < 1000; i++) {
            MagnetizedFerrofluidRegistry.add(null, new BlockPos(random.nextInt(2048) - 1024,
                    random.nextInt(200) - 60, random.nextInt(2048) - 1024), MagneticPolarity.NORTH);
        }
        for (int x : new int[]{-17, -16, -1, 0, 15, 16}) {
            final var pos = new BlockPos(x, 20, x);
            MagnetizedFerrofluidRegistry.add(null, pos, MagneticPolarity.SOUTH);
            for (double offset : new double[]{-4, -3.99, 0, 3.99, 4, 4.01}) {
                final Vec3 target = Vec3.atCenterOf(pos).add(offset, 0, 0);
                final Set<BlockPos> expected = new HashSet<>(), actual = new HashSet<>();
                for (var p : MagnetizedFerrofluidRegistry.forLevel(null).keySet()) {
                    if (Vec3.atCenterOf(p).distanceToSqr(target) <= 16) expected.add(p);
                }
                for (var p : MagnetizedFerrofluidRegistry.snapshotNear(null, BlockPos.containing(target), 4)) {
                    if (Vec3.atCenterOf(p).distanceToSqr(target) <= 16) actual.add(p);
                }
                assertEquals(expected, actual);
            }
        }
        assertTrue(MagnetizedFerrofluidRegistry.snapshotNear(null, new BlockPos(100000, 20, 100000), 4).isEmpty());
    }

    @Test void mutableViewsKeepTheIndexCoherentAndSnapshotsDefensive() {
        final BlockPos a = new BlockPos(-1, 0, -1), b = a.above(), c = new BlockPos(48, 0, 48);
        MagnetizedFerrofluidRegistry.add(null, a, MagneticPolarity.NORTH);
        final var map = MagnetizedFerrofluidRegistry.forLevel(null);
        map.put(b, MagneticPolarity.SOUTH);
        map.compute(c, (key, old) -> MagneticPolarity.NORTH);
        assertEquals(Set.of(a, b), new HashSet<>(MagnetizedFerrofluidRegistry.snapshotNear(null, a, 4)));
        final var snapshot = MagnetizedFerrofluidRegistry.snapshotNear(null, c, 4);
        map.keySet().remove(c);
        assertEquals(List.of(c), snapshot);
        assertTrue(MagnetizedFerrofluidRegistry.snapshotNear(null, c, 4).isEmpty());
        final var it = map.entrySet().iterator();
        final var entry = it.next();
        entry.setValue(MagneticPolarity.SOUTH);
        assertEquals(MagneticPolarity.SOUTH, map.get(entry.getKey()));
        it.remove();
        assertFalse(MagnetizedFerrofluidRegistry.snapshotNear(null, a, 4).contains(entry.getKey()));
        final var keys = map.keySet().iterator();
        keys.next(); keys.remove();
        assertTrue(MagnetizedFerrofluidRegistry.snapshotNear(null, a, 4).isEmpty());
        map.putAll(Map.of(a, MagneticPolarity.NORTH, c, MagneticPolarity.SOUTH));
        MagnetizedFerrofluidRegistry.dropChunk(null, new ChunkPos(a));
        assertEquals(Set.of(c), map.keySet());
        map.values().remove(MagneticPolarity.SOUTH);
        assertTrue(MagnetizedFerrofluidRegistry.snapshotNear(null, c, 4).isEmpty());
        map.put(a, MagneticPolarity.NORTH);
        map.clear();
        assertTrue(MagnetizedFerrofluidRegistry.snapshotNear(null, a, 4).isEmpty());
    }
}
