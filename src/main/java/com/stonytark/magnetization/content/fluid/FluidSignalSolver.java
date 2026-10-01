package com.stonytark.magnetization.content.fluid;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;

/** Solve the same attenuating fixed point without repeatedly sweeping dark cells. */
final class FluidSignalSolver {
    private FluidSignalSolver() {}

    static int[] solve(final int[] external, final int[] neighbors) {
        final int[] power = new int[external.length];
        final IntArrayFIFOQueue pending = new IntArrayFIFOQueue();
        for (int i = 0; i < power.length; i++) {
            power[i] = Math.max(0, Math.min(15, external[i]));
            if (power[i] > 1) pending.enqueue(i);
        }
        while (!pending.isEmpty()) {
            final int source = pending.dequeueInt();
            final int offered = power[source] - 1;
            for (int direction = 0; direction < 6; direction++) {
                final int target = neighbors[source * 6 + direction];
                if (target < 0 || target >= power.length || power[target] >= offered) continue;
                power[target] = offered;
                if (offered > 1) pending.enqueue(target);
            }
        }
        return power;
    }
}
