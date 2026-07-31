package com.stonytark.magnetization.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the analog-redstone force ramp used when an emitter's "Analog Redstone" config
 * toggle is on. The endpoints are the contract — signal 1 is WEAK's force, signal 15 is
 * EXTREME's — and the geometric shape between them is what makes every redstone level an
 * equal proportional step rather than bunching the useful range into the first third of
 * the dial. A change to either would silently rebalance every throttled emitter.
 */
class MagneticStrengthScalingTest {

    @Test
    void endpointsMatchTheTierLadder() {
        assertEquals(MagneticStrength.WEAK.force(),
                MagneticStrength.forceForSignal(1), 1e-9,
                "signal 1 must be exactly WEAK's force");
        assertEquals(MagneticStrength.EXTREME.force(),
                MagneticStrength.forceForSignal(MagneticStrength.MAX_SIGNAL), 1e-9,
                "signal 15 must be exactly EXTREME's force");
    }

    @Test
    void zeroAndNegativeSignalsAreDead() {
        assertEquals(0.0d, MagneticStrength.forceForSignal(0), 1e-9);
        assertEquals(0.0d, MagneticStrength.forceForSignal(-1), 1e-9);
        assertEquals(0.0d, MagneticStrength.forceForSignal(Integer.MIN_VALUE), 1e-9);
    }

    @Test
    void signalsAboveFifteenClampToTheTop() {
        assertEquals(MagneticStrength.EXTREME.force(),
                MagneticStrength.forceForSignal(16), 1e-9);
        assertEquals(MagneticStrength.EXTREME.force(),
                MagneticStrength.forceForSignal(Integer.MAX_VALUE), 1e-9);
    }

    @Test
    void rampIsStrictlyIncreasing() {
        for (int s = 1; s < MagneticStrength.MAX_SIGNAL; s++) {
            assertTrue(MagneticStrength.forceForSignal(s) < MagneticStrength.forceForSignal(s + 1),
                    "force must increase from signal " + s + " to " + (s + 1));
        }
    }

    @Test
    void rampNeverLeavesTheTierRange() {
        for (int s = 1; s <= MagneticStrength.MAX_SIGNAL; s++) {
            final double f = MagneticStrength.forceForSignal(s);
            assertTrue(f >= MagneticStrength.WEAK.force() - 1e-9,
                    "signal " + s + " dropped below WEAK's force");
            assertTrue(f <= MagneticStrength.EXTREME.force() + 1e-9,
                    "signal " + s + " exceeded EXTREME's force");
        }
    }

    @Test
    void rampIsGeometricNotLinear() {
        // The defining property: each step multiplies by a constant ratio. A linear ramp
        // would pass STRONG's force by signal ~5 and leave two thirds of the dial bunched
        // at the top, so this is the assertion that catches a silent switch to linear.
        final double ratio = MagneticStrength.forceForSignal(2) / MagneticStrength.forceForSignal(1);
        for (int s = 1; s < MagneticStrength.MAX_SIGNAL; s++) {
            final double step = MagneticStrength.forceForSignal(s + 1) / MagneticStrength.forceForSignal(s);
            assertEquals(ratio, step, 1e-9, "step ratio drifted at signal " + s);
        }
        // Sanity: the constant ratio is the 14th root of the 40x span (200 -> 8000).
        assertEquals(Math.pow(40.0d, 1.0d / 14.0d), ratio, 1e-9);
    }

    @Test
    void midpointLandsBetweenMediumAndStrong() {
        // Signal 8 is the middle of the dial; a geometric ramp puts it comfortably
        // between MEDIUM and STRONG. A linear ramp would put it above STRONG.
        final double mid = MagneticStrength.forceForSignal(8);
        assertTrue(mid > MagneticStrength.MEDIUM.force(),
                "signal 8 should exceed MEDIUM, was " + mid);
        assertTrue(mid < MagneticStrength.STRONG.force(),
                "signal 8 should stay under STRONG, was " + mid);
    }

    @Test
    void nearestForForceMapsBackOntoTheLadder() {
        assertSame(MagneticStrength.NONE, MagneticStrength.nearestForForce(0.0d));
        assertSame(MagneticStrength.NONE, MagneticStrength.nearestForForce(-5.0d));
        // Below WEAK still reads as WEAK — there is no tier between NONE and WEAK, and a
        // live field must never display as NONE.
        assertSame(MagneticStrength.WEAK, MagneticStrength.nearestForForce(1.0d));
        assertSame(MagneticStrength.WEAK, MagneticStrength.nearestForForce(MagneticStrength.WEAK.force()));
        assertSame(MagneticStrength.WEAK, MagneticStrength.nearestForForce(799.0d));
        assertSame(MagneticStrength.MEDIUM, MagneticStrength.nearestForForce(MagneticStrength.MEDIUM.force()));
        assertSame(MagneticStrength.MEDIUM, MagneticStrength.nearestForForce(2399.0d));
        assertSame(MagneticStrength.STRONG, MagneticStrength.nearestForForce(MagneticStrength.STRONG.force()));
        assertSame(MagneticStrength.STRONG, MagneticStrength.nearestForForce(7999.0d));
        assertSame(MagneticStrength.EXTREME, MagneticStrength.nearestForForce(MagneticStrength.EXTREME.force()));
        assertSame(MagneticStrength.EXTREME, MagneticStrength.nearestForForce(999_999.0d));
    }

    @Test
    void everySignalMapsToALiveDisplayTier() {
        for (int s = 1; s <= MagneticStrength.MAX_SIGNAL; s++) {
            assertTrue(MagneticStrength.nearestForForce(MagneticStrength.forceForSignal(s))
                            != MagneticStrength.NONE,
                    "signal " + s + " produced a field that would display as NONE");
        }
    }
}
