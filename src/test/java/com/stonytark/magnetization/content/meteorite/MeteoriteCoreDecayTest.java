package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.api.MagneticStrength;
import org.junit.jupiter.api.Test;

import static com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity.DECAY_TICKS;
import static com.stonytark.magnetization.content.meteorite.MeteoriteCoreBlockEntity.tierForElapsed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Boundary tests for the meteorite-core decay-tier math. An off-by-one in
 *  the boundary conditions would silently shift the duration of each tier —
 *  hard to catch in-world, trivial to catch here. */
class MeteoriteCoreDecayTest {

    @Test
    void freshChargeIsExtreme() {
        assertEquals(MagneticStrength.EXTREME, tierForElapsed(0L));
        assertEquals(MagneticStrength.EXTREME, tierForElapsed(1L));
    }

    @Test
    void boundaryFirstThirdIsStillExtreme() {
        // elapsed < DECAY/3 is EXTREME; the last tick of that range is DECAY/3 - 1.
        assertEquals(MagneticStrength.EXTREME, tierForElapsed(DECAY_TICKS / 3L - 1L));
    }

    @Test
    void exactlyOneThirdElapsedIsStrong() {
        // At elapsed == DECAY/3 we cross out of EXTREME into STRONG.
        assertEquals(MagneticStrength.STRONG, tierForElapsed(DECAY_TICKS / 3L));
    }

    @Test
    void boundaryTwoThirdsIsStillStrong() {
        assertEquals(MagneticStrength.STRONG, tierForElapsed((2L * DECAY_TICKS) / 3L - 1L));
    }

    @Test
    void exactlyTwoThirdsElapsedIsWeak() {
        assertEquals(MagneticStrength.WEAK, tierForElapsed((2L * DECAY_TICKS) / 3L));
    }

    @Test
    void justBeforeFullDecayIsWeak() {
        assertEquals(MagneticStrength.WEAK, tierForElapsed(DECAY_TICKS - 1L));
    }

    @Test
    void exactlyFullDecayIsInert() {
        assertNull(tierForElapsed(DECAY_TICKS));
    }

    @Test
    void wellPastDecayIsInert() {
        assertNull(tierForElapsed(DECAY_TICKS * 5L));
    }
}
