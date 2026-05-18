package com.stonytark.magnetization.api;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** NBT round-trip for the ship snapshot that's BE→client synced for the
 *  goggle / Jade / WTHIT / TOP tooltips. If a field is dropped or coerced
 *  wrong here, ships show the wrong polarity or susceptibility in HUDs. */
class ShipMagneticStateTest {

    @Test
    void defaultMatchesIntendedFallback() {
        // 1.0.0 fallback for ships that haven't been scanned yet.
        assertSame(MagneticPolarity.NORTH, ShipMagneticState.DEFAULT.polarity());
        assertEquals(1.0d, ShipMagneticState.DEFAULT.susceptibility(), 1e-9);
        assertEquals(0, ShipMagneticState.DEFAULT.ferrousBlockCount());
        assertEquals(0, ShipMagneticState.DEFAULT.magnetBlockCount());
        assertEquals(0, ShipMagneticState.DEFAULT.inverterBlockCount());
    }

    @Test
    void nbtRoundTripSouthHeavy() {
        final ShipMagneticState before = new ShipMagneticState(
                MagneticPolarity.SOUTH, 3.275d, 24, 5, 1);
        final CompoundTag tag = before.toNbt();
        final ShipMagneticState after = ShipMagneticState.fromNbt(tag);

        assertEquals(before, after);
    }

    @Test
    void nbtRoundTripNorthFreshShip() {
        // Counts at zero (just-assembled ship) survive the round trip.
        final ShipMagneticState before = new ShipMagneticState(
                MagneticPolarity.NORTH, 1.0d, 0, 0, 0);
        final ShipMagneticState after = ShipMagneticState.fromNbt(before.toNbt());

        assertEquals(before, after);
    }

    @Test
    void nbtRoundTripExtremeSusceptibility() {
        // Cap-edge (shipMaxSusceptibility default 20.0) survives serialization.
        final ShipMagneticState before = new ShipMagneticState(
                MagneticPolarity.NORTH, 20.0d, 400, 50, 0);
        final ShipMagneticState after = ShipMagneticState.fromNbt(before.toNbt());

        assertEquals(before, after);
    }

    @Test
    void fromNbtReturnsNullForMalformed() {
        final CompoundTag malformed = new CompoundTag();
        malformed.putString("p", "PURPLE"); // not a valid MagneticPolarity
        assertNull(ShipMagneticState.fromNbt(malformed));
    }

    @Test
    void fromNbtReturnsNullForNullInput() {
        assertNull(ShipMagneticState.fromNbt(null));
    }
}
