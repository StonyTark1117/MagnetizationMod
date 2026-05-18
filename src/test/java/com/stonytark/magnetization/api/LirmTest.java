package com.stonytark.magnetization.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Decay-curve invariants for the Lightning Induced Remnant Magnetism math.
 *  An off-by-one or wrong-sign here corrupts every magnetized armor piece's
 *  pull strength + every lightning-stamped tool's signature ability multiplier. */
class LirmTest {

    @Test
    void freshStampIsFullStrength() {
        assertEquals(1.0d, Lirm.strengthForElapsed(0), 1e-9);
    }

    @Test
    void clockSkewClampsToFullStrength() {
        // Negative elapsed (a stamp from "the future" after a save-load /
        // server clock reset) should not corrupt to 0 — treat as fresh.
        assertEquals(1.0d, Lirm.strengthForElapsed(-1), 1e-9);
        assertEquals(1.0d, Lirm.strengthForElapsed(-1000L), 1e-9);
    }

    @Test
    void expiredStampIsZeroStrength() {
        // At DURATION_TICKS exactly, the stamp is fully decayed.
        assertEquals(0.0d, Lirm.strengthForElapsed(Lirm.DURATION_TICKS), 1e-9);
        assertEquals(0.0d, Lirm.strengthForElapsed(Lirm.DURATION_TICKS + 1), 1e-9);
        assertEquals(0.0d, Lirm.strengthForElapsed(Long.MAX_VALUE), 1e-9);
    }

    @Test
    void halfwayIsHalfStrength() {
        assertEquals(0.5d, Lirm.strengthForElapsed(Lirm.DURATION_TICKS / 2), 1e-9);
    }

    @Test
    void quarterPointsAreLinear() {
        // Linear decay — 25% elapsed → 75% strength, 75% elapsed → 25% strength.
        assertEquals(0.75d, Lirm.strengthForElapsed(Lirm.DURATION_TICKS / 4),     1e-9);
        assertEquals(0.25d, Lirm.strengthForElapsed(Lirm.DURATION_TICKS * 3 / 4), 1e-9);
    }

    @Test
    void oneTickBeforeExpiryIsAlmostZero() {
        // Sanity check the boundary: 1 tick before DURATION_TICKS is still active,
        // even though it's vanishingly small.
        final double s = Lirm.strengthForElapsed(Lirm.DURATION_TICKS - 1);
        assertTrue(s > 0.0d && s < 0.0001d, "expected ~0 but > 0, got " + s);
    }

    @Test
    void isTemporaryBoundary() {
        // Active for elapsed ∈ [0, DURATION_TICKS); not active at the boundary or after.
        assertTrue(Lirm.isTemporaryForElapsed(0));
        assertTrue(Lirm.isTemporaryForElapsed(Lirm.DURATION_TICKS - 1));
        assertFalse(Lirm.isTemporaryForElapsed(Lirm.DURATION_TICKS));     // exactly at boundary → not temporary
        assertFalse(Lirm.isTemporaryForElapsed(Lirm.DURATION_TICKS + 1));
    }

    @Test
    void isTemporaryRejectsClockSkew() {
        // A negative elapsed (clock skew) returns false — strength clamps to
        // full but isTemporary is the "stamp is actively decaying" flag, which
        // shouldn't be true for unverified-source timestamps.
        assertFalse(Lirm.isTemporaryForElapsed(-1));
        assertFalse(Lirm.isTemporaryForElapsed(-1000L));
    }

    @Test
    void durationIs20Minutes() {
        // Lock the constant. Real-time tuning is 20 minutes (24 000 ticks @ 20 TPS).
        assertEquals(24_000, Lirm.DURATION_TICKS);
    }
}
