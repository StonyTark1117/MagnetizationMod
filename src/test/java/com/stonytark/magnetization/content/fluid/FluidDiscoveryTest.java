package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FluidDiscoveryTest {
    @AfterEach void clear() {
        FerrofluidSourceRegistry.snapshot(null).forEach(p -> FerrofluidSourceRegistry.remove(null, p));
        var map = MagnetizedFerrofluidRegistry.forLevel(null);
        if (!map.isEmpty()) map.clear();
    }

    private void assertRegions(Collection<BlockPos> sources, FluidTargetRegions regions) {
        var chunks = new LinkedHashSet<Long>();
        var sections = new HashSet<Long>();
        for (var p : sources) { chunks.add(ChunkPos.asLong(p)); sections.add(SectionPos.asLong(p)); }
        assertEquals(sources.size(), regions.sourceCount());
        assertEquals(new ArrayList<>(chunks), regions.chunks());
        assertEquals(sections, new HashSet<>(regions.sections()));
    }

    @Test void cachedRegionsFollowMembershipChangesAndWritableViews() {
        var random = new Random(723);
        MagnetizedFerrofluidRegistry.add(null, BlockPos.ZERO, MagneticPolarity.NORTH);
        var map = MagnetizedFerrofluidRegistry.forLevel(null);
        for (int i = 0; i < 400; i++) {
            var p = new BlockPos(random.nextInt(128) - 64, random.nextInt(80) - 40, random.nextInt(128) - 64);
            FerrofluidSourceRegistry.add(null, p);
            map.put(p, MagneticPolarity.NORTH);
        }
        for (int i = 0; i < 200; i++) {
            assertRegions(FerrofluidSourceRegistry.snapshot(null), FerrofluidSourceRegistry.targetRegions(null));
            assertRegions(map.keySet(), MagnetizedFerrofluidRegistry.targetRegions(null));
            assertSame(FerrofluidSourceRegistry.targetRegions(null), FerrofluidSourceRegistry.targetRegions(null));
            assertSame(MagnetizedFerrofluidRegistry.targetRegions(null), MagnetizedFerrofluidRegistry.targetRegions(null));
            var p = FerrofluidSourceRegistry.snapshot(null).iterator().next();
            FerrofluidSourceRegistry.remove(null, p);
            if (!map.isEmpty()) {
                var it = map.entrySet().iterator();
                var entry = it.next();
                var before = MagnetizedFerrofluidRegistry.targetRegions(null);
                entry.setValue(MagneticPolarity.SOUTH);
                assertSame(before, MagnetizedFerrofluidRegistry.targetRegions(null));
                it.remove();
            }
        }
        var old = MagnetizedFerrofluidRegistry.targetRegions(null);
        var first = map.keySet().iterator().next();
        MagnetizedFerrofluidRegistry.dropChunk(null, new ChunkPos(first));
        assertRegions(map.keySet(), MagnetizedFerrofluidRegistry.targetRegions(null));
        assertNotSame(old, MagnetizedFerrofluidRegistry.targetRegions(null));
        map.clear();
        assertEquals(FluidTargetRegions.EMPTY, MagnetizedFerrofluidRegistry.targetRegions(null));
        assertTrue(old.sourceCount() > 0);
    }

    @Test void regionBoundsMatchOriginalCellCenteredBoundsIncludingNegatives() {
        for (int x = -65; x <= 65; x++) for (int z = -33; z <= 33; z++) {
            assertEquals((int) Math.floor((x + .5 - 32) / 16), Math.floorDiv(x, 16) - 2);
            assertEquals((int) Math.floor((x + .5 + 32) / 16), Math.floorDiv(x, 16) + 2);
            assertEquals((int) Math.floor((z + .5 - 32) / 16), Math.floorDiv(z, 16) - 2);
            assertEquals((int) Math.floor((z + .5 + 32) / 16), Math.floorDiv(z, 16) + 2);
        }
    }

    @Test void lazyAnchorsKeepOriginalBucketAndTieOrder() {
        var anchors = new ArrayList<BlockPos>();
        var random = new Random(932);
        for (int i = 0; i < 2000; i++) anchors.add(new BlockPos(random.nextInt(256) - 128, i, random.nextInt(256) - 128));
        var grid = FerrofluidCreepHandler.buildAnchorGrid(anchors);
        for (double x : new double[]{-16.01, -16, -.01, 0, 15.99, 16}) {
            for (double range : new double[]{0, 4, 16, 32, 48, 48.01, 128}) {
                int r = (int) Math.ceil(range / 16), cx = Math.floorDiv((int) Math.floor(x), 16);
                var expected = new ArrayList<BlockPos>();
                if (r > 3) expected.addAll(anchors);
                else for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    long key = ((long) (cx + dx) << 32) | ((cx + dz) & 0xFFFFFFFFL);
                    expected.addAll(grid.getOrDefault(key, List.of()));
                }
                var result = FerrofluidCreepHandler.anchorsNear(new Vec3(x, 0, x), range, anchors, grid);
                for (int pass = 0; pass < 2; pass++) {
                    var actual = new ArrayList<BlockPos>();
                    var it = result.iterator();
                    while (it.hasNext()) { assertTrue(it.hasNext()); actual.add(it.next()); }
                    assertThrows(NoSuchElementException.class, it::next);
                    assertEquals(expected, actual);
                }
            }
        }
    }
}
