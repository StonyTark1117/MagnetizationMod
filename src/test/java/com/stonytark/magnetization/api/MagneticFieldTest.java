package com.stonytark.magnetization.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagneticFieldTest {

    private static final Vec3 ORIGIN = new Vec3(1.5, 2.5, 3.5);
    private static final Vec3 AXIS = new Vec3(0, 1, 0);

    @Test
    void legacyConstructorSetsCustomRangeToZero() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        assertEquals(0.0d, field.customRange(), 1e-9);
    }

    @Test
    void rangeFallsBackToStrengthRangeWhenCustomZero() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        assertEquals(MagneticStrength.MEDIUM.range(), field.range(), 1e-9);
    }

    @Test
    void rangeUsesCustomRangeWhenPositive() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL, 12.5d);
        assertEquals(12.5d, field.range(), 1e-9);
    }

    @Test
    void nbtRoundtripPreservesAllFields() {
        final MagneticField original = new MagneticField(
                new Vec3(10, -20, 30), new Vec3(0.6, 0.8, 0),
                MagneticPolarity.SOUTH, MagneticStrength.STRONG,
                MagneticField.Shape.CONICAL, 7.25d);

        final MagneticField roundtrip = MagneticField.fromNbt(original.toNbt());

        assertNotNull(roundtrip);
        assertEquals(original.origin().x, roundtrip.origin().x, 1e-9);
        assertEquals(original.origin().y, roundtrip.origin().y, 1e-9);
        assertEquals(original.origin().z, roundtrip.origin().z, 1e-9);
        assertEquals(original.axis().x, roundtrip.axis().x, 1e-9);
        assertEquals(original.axis().y, roundtrip.axis().y, 1e-9);
        assertEquals(original.axis().z, roundtrip.axis().z, 1e-9);
        assertEquals(original.polarity(), roundtrip.polarity());
        assertEquals(original.strength(), roundtrip.strength());
        assertEquals(original.shape(), roundtrip.shape());
        assertEquals(original.customRange(), roundtrip.customRange(), 1e-9);
    }

    @Test
    void toNbtOmitsCustomRangeWhenZero() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.WEAK, MagneticField.Shape.DIRECTIONAL);
        final CompoundTag tag = field.toNbt();
        assertFalse(tag.contains("cr"), "expected 'cr' tag absent when customRange == 0");

        final MagneticField roundtrip = MagneticField.fromNbt(tag);
        assertNotNull(roundtrip);
        assertEquals(0.0d, roundtrip.customRange(), 1e-9);
    }

    @Test
    void forceFallsBackToTheTierWithoutAnOverride() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL);
        assertEquals(MagneticStrength.STRONG.force(), field.force(), 1e-9);
        assertFalse(field.hasForceOverride());
    }

    @Test
    void forceOverrideWinsOverTheTier() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.EXTREME, MagneticField.Shape.OMNIDIRECTIONAL, 0.0d, 555.0d);
        assertEquals(555.0d, field.force(), 1e-9);
        assertTrue(field.hasForceOverride());
        // The tier is untouched, so it keeps driving range and the GUI readout.
        assertEquals(MagneticStrength.EXTREME, field.strength());
        assertEquals(MagneticStrength.EXTREME.range(), field.range(), 1e-9);
    }

    @Test
    void toNbtOmitsForceOverrideWhenZeroAndRoundTripsWhenSet() {
        final MagneticField plain = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.WEAK, MagneticField.Shape.DIRECTIONAL);
        assertFalse(plain.toNbt().contains("fo"), "expected 'fo' tag absent when forceOverride == 0");

        final MagneticField throttled = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.SOUTH,
                MagneticStrength.EXTREME, MagneticField.Shape.CONICAL, 12.5d, 1806.0d);
        final MagneticField roundtrip = MagneticField.fromNbt(throttled.toNbt());
        assertNotNull(roundtrip);
        assertEquals(1806.0d, roundtrip.forceOverride(), 1e-9);
        assertEquals(12.5d, roundtrip.customRange(), 1e-9);
    }

    @Test
    void steppedStrengthScalesTheOverrideProportionally() {
        // Hematite dampening / Halbach boosting step the TIER; without proportional
        // scaling they would silently become range-only for a throttled emitter.
        final MagneticField throttled = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.EXTREME, MagneticField.Shape.OMNIDIRECTIONAL, 0.0d, 4000.0d);
        // EXTREME (8000) -> STRONG (2400) is x0.3, so 4000 N becomes 1200 N.
        final MagneticField damped = throttled.withSteppedStrength(MagneticStrength.STRONG);
        assertEquals(1200.0d, damped.force(), 1e-9);
        assertEquals(MagneticStrength.STRONG, damped.strength());

        // Damping all the way to NONE must silence it outright.
        assertEquals(0.0d, throttled.withSteppedStrength(MagneticStrength.NONE).force(), 1e-9);

        // An un-throttled field just changes tier, exactly as before.
        final MagneticField plain = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.EXTREME, MagneticField.Shape.OMNIDIRECTIONAL);
        assertEquals(MagneticStrength.STRONG.force(),
                plain.withSteppedStrength(MagneticStrength.STRONG).force(), 1e-9);
    }

    @Test
    void withCopiersPreserveCustomRange() {
        // Regression: the four in-tick field rebuilds used to go through the 5-arg
        // constructor and silently dropped customRange, so an emitter with a GUI range
        // override next to an inverter or hematite block lost its range.
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL, 40.0d);
        assertEquals(40.0d, field.withPolarity(MagneticPolarity.SOUTH).customRange(), 1e-9);
        assertEquals(40.0d, field.withStrength(MagneticStrength.WEAK).customRange(), 1e-9);
        assertEquals(40.0d, field.withSteppedStrength(MagneticStrength.WEAK).customRange(), 1e-9);
    }

    @Test
    void fromNbtReturnsNullForNullTag() {
        assertNull(MagneticField.fromNbt(null));
    }

    @Test
    void fromNbtReturnsNullForMalformedTag() {
        final CompoundTag bad = new CompoundTag();
        bad.putDouble("ox", 0); bad.putDouble("oy", 0); bad.putDouble("oz", 0);
        bad.putDouble("ax", 0); bad.putDouble("ay", 1); bad.putDouble("az", 0);
        bad.putString("p", "NOT_A_POLARITY");
        bad.putString("s", "MEDIUM");
        bad.putString("sh", "OMNIDIRECTIONAL");
        assertNull(MagneticField.fromNbt(bad));
    }

    @Test
    void omnidirectionalShapeSurvivesRoundtrip() {
        assertShapeRoundtrips(MagneticField.Shape.OMNIDIRECTIONAL);
    }

    @Test
    void directionalShapeSurvivesRoundtrip() {
        assertShapeRoundtrips(MagneticField.Shape.DIRECTIONAL);
    }

    @Test
    void conicalShapeSurvivesRoundtrip() {
        assertShapeRoundtrips(MagneticField.Shape.CONICAL);
    }

    private static void assertShapeRoundtrips(final MagneticField.Shape shape) {
        final MagneticField original = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, shape);
        final MagneticField roundtrip = MagneticField.fromNbt(original.toNbt());
        assertNotNull(roundtrip);
        assertTrue(roundtrip.shape() == shape, "expected " + shape + ", got " + roundtrip.shape());
    }
}
