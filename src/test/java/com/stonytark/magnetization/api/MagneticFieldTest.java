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
