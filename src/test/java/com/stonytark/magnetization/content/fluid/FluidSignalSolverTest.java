package com.stonytark.magnetization.content.fluid;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class FluidSignalSolverTest {
    @Test void matchesLegacyRelaxationAcrossCyclesSourcesAndCappedEdges() {
        final Random random = new Random(8675309);
        for (int trial = 0; trial < 400; trial++) {
            final int size = 1 + random.nextInt(150);
            final int[] edges = new int[size * 6];
            Arrays.fill(edges, -1);
            // Undirected six-neighbor lattice with holes and a discarded cap fringe.
            for (int i = 0; i < size; i++) for (int d = 0; d < 3; d++) {
                final int next = i + new int[]{1, 7, 49}[d];
                if (random.nextBoolean()) continue;
                edges[i * 6 + d * 2] = next;
                if (next < size) edges[next * 6 + d * 2 + 1] = i;
            }
            final int[] external = new int[size];
            for (int i = 0; i < size; i++) if (random.nextInt(12) == 0) external[i] = random.nextInt(16);
            assertArrayEquals(legacy(external, edges), FluidSignalSolver.solve(external, edges));
            // Power removal and changed sources must not retain any prior result.
            Arrays.fill(external, 0);
            assertArrayEquals(external, FluidSignalSolver.solve(external, edges));
        }
    }

    private static int[] legacy(int[] external, int[] edges) {
        final int[] power = external.clone();
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < power.length; i++) {
                int best = external[i];
                for (int d = 0; d < 6; d++) {
                    int n = edges[i * 6 + d];
                    if (n >= 0 && n < power.length) best = Math.max(best, power[n] - 1);
                }
                best = Math.min(15, best);
                if (best != power[i]) { power[i] = best; changed = true; }
            }
        } while (changed);
        return power;
    }
}
