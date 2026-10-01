package com.stonytark.magnetization.content.shaft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShaftRangeIndexTest {
    private record Source(Vec3 center, double range) {}
    @Test void broadPhaseRetainsEveryExactCandidateInOriginalOrder() {
        var random = new Random(347);
        var sources = new ArrayList<Source>();
        for (int i = 0; i < 1000; i++) sources.add(new Source(new Vec3(
                random.nextDouble() * 400 - 200, random.nextDouble() * 100 - 50,
                random.nextDouble() * 400 - 200), 1 + random.nextInt(64)));
        for (int pass = 0; pass < 3; pass++) {
            var index = new ShaftRangeIndex<>(sources, Source::center, Source::range);
            for (var source : sources) {
                for (var target : List.of(source.center, source.center.add(source.range, 0, 0),
                        source.center.add(-source.range, 0, 0), source.center.add(0, 0, source.range))) {
                    var expected = sources.stream().filter(s -> target.distanceToSqr(s.center) <= s.range * s.range).toList();
                    var actual = index.near(target).stream().filter(s -> target.distanceToSqr(s.center) <= s.range * s.range).toList();
                    assertEquals(expected, actual);
                }
            }
            // New pass reads translated/rotated ship positions and changed ranges.
            sources.replaceAll(s -> new Source(new Vec3(-s.center.z + .125, s.center.y, s.center.x - 40), s.range / 2));
        }
    }

    @Test void nonFiniteCoordinatesUseOriginalFullScanSemantics() {
        var sources = List.of(new Source(Vec3.ZERO, 4), new Source(new Vec3(Double.NaN, 0, 0), 4));
        assertEquals(sources, new ShaftRangeIndex<>(sources, Source::center, Source::range).near(Vec3.ZERO));
        assertEquals(List.of(), new ShaftRangeIndex<Source>(List.of(), Source::center, Source::range).near(Vec3.ZERO));
    }
}
