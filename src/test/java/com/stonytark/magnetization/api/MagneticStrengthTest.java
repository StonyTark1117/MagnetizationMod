package com.stonytark.magnetization.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Locks the force + range tier values. A typo here ("WEAK.range = 0",
 *  "STRONG.force swapped with WEAK.force") silently breaks gameplay tuning
 *  without any error; the tests pin the numbers so the build catches it. */
class MagneticStrengthTest {

    @Test
    void noneIsZero() {
        assertEquals(0.0d, MagneticStrength.NONE.force(), 1e-9);
        assertEquals(0.0d, MagneticStrength.NONE.range(), 1e-9);
    }

    @Test
    void forceTiersAreStrictlyIncreasing() {
        // Each tier's force should be strictly greater than the previous.
        // Prevents a regression where two tiers collapse to the same value.
        assertTrue(MagneticStrength.WEAK.force()    > MagneticStrength.NONE.force());
        assertTrue(MagneticStrength.MEDIUM.force()  > MagneticStrength.WEAK.force());
        assertTrue(MagneticStrength.STRONG.force()  > MagneticStrength.MEDIUM.force());
        assertTrue(MagneticStrength.EXTREME.force() > MagneticStrength.STRONG.force());
    }

    @Test
    void rangeTiersAreStrictlyIncreasing() {
        assertTrue(MagneticStrength.WEAK.range()    > MagneticStrength.NONE.range());
        assertTrue(MagneticStrength.MEDIUM.range()  > MagneticStrength.WEAK.range());
        assertTrue(MagneticStrength.STRONG.range()  > MagneticStrength.MEDIUM.range());
        assertTrue(MagneticStrength.EXTREME.range() > MagneticStrength.STRONG.range());
    }

    @Test
    void exactTierValues() {
        // Pin the actual numbers used in the field-force balance. If a future
        // refactor moves these, the test forces a deliberate update — no
        // accidental rebalance.
        assertEquals(  200.0d, MagneticStrength.WEAK.force(),    1e-9);
        assertEquals(  800.0d, MagneticStrength.MEDIUM.force(),  1e-9);
        assertEquals( 2400.0d, MagneticStrength.STRONG.force(),  1e-9);
        assertEquals( 8000.0d, MagneticStrength.EXTREME.force(), 1e-9);
        assertEquals(  4.0d, MagneticStrength.WEAK.range(),    1e-9);
        assertEquals(  8.0d, MagneticStrength.MEDIUM.range(),  1e-9);
        assertEquals( 16.0d, MagneticStrength.STRONG.range(),  1e-9);
        assertEquals( 32.0d, MagneticStrength.EXTREME.range(), 1e-9);
    }
}
