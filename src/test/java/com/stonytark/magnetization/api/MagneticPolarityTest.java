package com.stonytark.magnetization.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Sign-bit-level invariants for {@link MagneticPolarity}. A single typo in
 *  {@code opposite()} would flip the attract/repel sign on every field interaction
 *  in the mod — high blast radius, trivial to guard against. */
class MagneticPolarityTest {

    @Test
    void oppositeIsInvolution() {
        // f(f(x)) == x for NORTH↔SOUTH; NONE is its own opposite.
        assertSame(MagneticPolarity.NORTH, MagneticPolarity.SOUTH.opposite());
        assertSame(MagneticPolarity.SOUTH, MagneticPolarity.NORTH.opposite());
        assertSame(MagneticPolarity.NONE,  MagneticPolarity.NONE.opposite());
    }

    @Test
    void oppositeAppliedTwiceReturnsOriginal() {
        for (final MagneticPolarity p : MagneticPolarity.values()) {
            assertSame(p, p.opposite().opposite(),
                    "opposite(opposite()) should be identity for " + p);
        }
    }

    @Test
    void signEncodesPolarity() {
        // The sign drives attract/repel: NORTH=+1, SOUTH=-1, NONE=0.
        assertEquals(+1, MagneticPolarity.NORTH.sign());
        assertEquals(-1, MagneticPolarity.SOUTH.sign());
        assertEquals( 0, MagneticPolarity.NONE.sign());
    }
}
