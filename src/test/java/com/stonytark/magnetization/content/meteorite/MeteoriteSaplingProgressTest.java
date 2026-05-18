package com.stonytark.magnetization.content.meteorite;

import org.junit.jupiter.api.Test;

import static com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity.GROW_TICKS;
import static com.stonytark.magnetization.content.meteorite.MeteoriteSaplingBlockEntity.progressForElapsed;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Boundary tests for the sapling growth-progress fraction. The WTHIT tooltip
 *  reads this; an off-by-one or sign mistake here would show "100% growth"
 *  on a freshly-planted sapling or negative values on a clock-rewind. */
class MeteoriteSaplingProgressTest {

    private static final float EPS = 0.001f;

    @Test
    void zeroElapsedIsZero() {
        assertEquals(0f, progressForElapsed(0L), EPS);
    }

    @Test
    void negativeElapsedClampsToZero() {
        // Sentinel for clock-rewind: world time can move backwards if the host
        // toggles daylight cycle / time set commands. Don't surface negative.
        assertEquals(0f, progressForElapsed(-1L), EPS);
        assertEquals(0f, progressForElapsed(-GROW_TICKS), EPS);
    }

    @Test
    void halfwayIsHalf() {
        assertEquals(0.5f, progressForElapsed(GROW_TICKS / 2L), EPS);
    }

    @Test
    void exactlyMatureIsOne() {
        assertEquals(1f, progressForElapsed(GROW_TICKS), EPS);
    }

    @Test
    void pastMatureClampsToOne() {
        // Once the sapling has bloomed, the BE is gone, so this only fires if
        // someone reads progress on a stale BE. Clamp instead of overshooting.
        assertEquals(1f, progressForElapsed(GROW_TICKS * 10L), EPS);
    }

    @Test
    void monotonicIncreaseAcrossSampledTicks() {
        float prev = -1f;
        for (long t = 0; t <= GROW_TICKS; t += 1000) {
            final float p = progressForElapsed(t);
            org.junit.jupiter.api.Assertions.assertTrue(p >= prev,
                    "growth must not regress at elapsed=" + t + " (got " + p + " after " + prev + ")");
            prev = p;
        }
    }
}
