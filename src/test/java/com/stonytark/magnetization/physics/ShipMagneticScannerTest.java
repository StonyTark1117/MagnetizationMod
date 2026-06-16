package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticPolarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Math-only tests for {@link ShipMagneticScanner}. Verify the parity-based
 * polarity rule and the baseline + per-block susceptibility formula. The
 * actual block-walking path can't be exercised here without a live Sable
 * sub-level — that's covered by in-world testing.
 *
 * <p>{@link ShipMagneticScanner#susceptibility} consults {@link MagConfig};
 * the accessors fall back to hardcoded defaults when the SPEC isn't loaded,
 * so these tests verify the default-coefficient math (1.0 baseline + 0.05 per
 * ferrous + 0.15 per magnet, capped at 20.0).
 */
class ShipMagneticScannerTest {

    private static final double EPS = 1.0e-9;

    @Test
    void zeroInvertersNorth() {
        assertEquals(MagneticPolarity.NORTH, ShipMagneticScanner.polarityFromInverterCount(0));
    }

    @Test
    void oneInverterFlipsToSouth() {
        assertEquals(MagneticPolarity.SOUTH, ShipMagneticScanner.polarityFromInverterCount(1));
    }

    @Test
    void twoInvertersCancelToNorth() {
        assertEquals(MagneticPolarity.NORTH, ShipMagneticScanner.polarityFromInverterCount(2));
    }

    @Test
    void parityExtendsToLargerCounts() {
        // User spec: "One = South, Two = North again" — parity, not "any present = SOUTH".
        for (int n = 0; n < 10; n++) {
            final MagneticPolarity expected = (n % 2 == 0) ? MagneticPolarity.NORTH : MagneticPolarity.SOUTH;
            assertEquals(expected, ShipMagneticScanner.polarityFromInverterCount(n),
                    "inverter count " + n);
        }
    }

    @Test
    void baselineSusceptibilityOnly() {
        // No ferrous, no magnets → baseline only.
        assertEquals(1.0, ShipMagneticScanner.susceptibility(0, 0, 0), EPS);
    }

    @Test
    void ferrousBlocksAddLinear() {
        // 1.0 baseline + 20 ferrous × 0.05 = 2.0
        assertEquals(2.0, ShipMagneticScanner.susceptibility(20, 0, 0), EPS);
    }

    @Test
    void magnetsWeighMoreThanFerrous() {
        // Each magnet contributes more than each ferrous block — user's spec
        // says magnets are "ferrous-plus". Defaults: 0.15 vs 0.05.
        final double withTenFerrous = ShipMagneticScanner.susceptibility(10, 0, 0);
        final double withTenMagnets = ShipMagneticScanner.susceptibility(0, 10, 0);
        assertTrue(withTenMagnets > withTenFerrous,
                "10 magnets (" + withTenMagnets + ") should exceed 10 ferrous (" + withTenFerrous + ")");
    }

    @Test
    void cappedAtMax() {
        // 10 000 ferrous would be 1 + 500 = 501× without a cap; default cap is 20×.
        assertEquals(20.0, ShipMagneticScanner.susceptibility(10_000, 0, 0), EPS);
    }

    @Test
    void ferrousAndMagnetsStack() {
        // 1.0 baseline + 10 × 0.05 (ferrous) + 10 × 0.15 (magnets) = 1.0 + 0.5 + 1.5 = 3.0
        assertEquals(3.0, ShipMagneticScanner.susceptibility(10, 10, 0), EPS);
    }
}
