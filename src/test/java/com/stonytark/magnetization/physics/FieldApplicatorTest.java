package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Math-only tests for {@link FieldApplicator#forceAt}. Verifies sign conventions
 * (NORTH = repel, SOUTH = attract), shape gating, and falloff curves.
 *
 * <p>Vec3 values are compared coarsely via length / dot product rather than
 * component equality so the tests survive small math reorderings.
 */
class FieldApplicatorTest {

    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 AXIS_UP = new Vec3(0, 1, 0);

    @Test
    void omnidirectionalNorthRepels() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);

        final Vec3 sample = new Vec3(2, 0, 0);
        final Vec3 force = FieldApplicator.forceAt(field, sample);

        // Repel = force points outward from origin = same direction as toSample.
        assertTrue(force.dot(sample) > 0, "expected outward force, got " + force);
    }

    @Test
    void omnidirectionalSouthAttracts() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.SOUTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);

        final Vec3 sample = new Vec3(2, 0, 0);
        final Vec3 force = FieldApplicator.forceAt(field, sample);

        // Attract = force points inward = opposite to toSample.
        assertTrue(force.dot(sample) < 0, "expected inward force, got " + force);
    }

    @Test
    void noneProducesZeroForce() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NONE,
                MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL);

        final Vec3 force = FieldApplicator.forceAt(field, new Vec3(1, 1, 1));
        assertEquals(0.0d, force.length(), 1e-9);
    }

    @Test
    void outOfRangeProducesZero() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.SOUTH,
                MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL);
        // WEAK has range = 4
        final Vec3 force = FieldApplicator.forceAt(field, new Vec3(10, 0, 0));
        assertEquals(0.0d, force.length(), 1e-9);
    }

    @Test
    void omnidirectionalFalloffIsInverseSquare() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL);

        final double atTwo = FieldApplicator.forceAt(field, new Vec3(2, 0, 0)).length();
        final double atFour = FieldApplicator.forceAt(field, new Vec3(4, 0, 0)).length();
        // r doubled → magnitude divided by ~4.
        assertEquals(atTwo / atFour, 4.0d, 0.01);
    }

    @Test
    void directionalForceParallelToAxis() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.DIRECTIONAL);

        final Vec3 force = FieldApplicator.forceAt(field, new Vec3(0, 2, 0));
        // For NORTH directional, force should be along +axis (positive Y).
        assertTrue(force.y > 0, "expected +Y force, got " + force);
        // And nearly parallel to axis (no transverse component).
        assertEquals(0.0d, force.x, 1e-9);
        assertEquals(0.0d, force.z, 1e-9);
    }

    @Test
    void directionalNoForceBehindEmitter() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.DIRECTIONAL);

        // Sample is "behind" the emitter (negative axis direction).
        final Vec3 force = FieldApplicator.forceAt(field, new Vec3(0, -2, 0));
        assertEquals(0.0d, force.length(), 1e-9);
    }

    @Test
    void conicalGatesByHalfAngle() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.CONICAL);

        // Inside the 45° cone (straight up).
        final Vec3 inside = FieldApplicator.forceAt(field, new Vec3(0, 2, 0));
        assertTrue(inside.length() > 0, "expected nonzero inside cone");

        // Outside the cone (perpendicular to axis).
        final Vec3 outside = FieldApplicator.forceAt(field, new Vec3(2, 0.1, 0));
        assertEquals(0.0d, outside.length(), 1e-9);
    }

    @Test
    void omnidirectionalForceIsRadiallyOriented() {
        // Force vector should be parallel to the radial direction (no transverse component).
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.SOUTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);

        final Vec3 sample = new Vec3(3, 4, 0);  // 5-block distance
        final Vec3 force = FieldApplicator.forceAt(field, sample);
        final Vec3 radial = sample.normalize();
        // Cross product of two parallel/antiparallel vectors should be ~0
        final Vec3 cross = force.cross(radial);
        assertEquals(0.0d, cross.length(), 1e-6, "force should be radial");
    }

    @Test
    void weakStrengthHasShorterRangeThanStrong() {
        // Both at distance 6, WEAK is out of range (range=4), STRONG is in.
        final MagneticField weak = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.SOUTH,
                MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL);
        final MagneticField strong = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.SOUTH,
                MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL);

        assertEquals(0.0d, FieldApplicator.forceAt(weak, new Vec3(6, 0, 0)).length(), 1e-9);
        assertTrue(FieldApplicator.forceAt(strong, new Vec3(6, 0, 0)).length() > 0);
    }

    @Test
    void zeroDistanceProducesZero() {
        // Sample at field origin: should yield zero (avoid divide-by-zero).
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL);
        assertEquals(0.0d, FieldApplicator.forceAt(field, ORIGIN).length(), 1e-9);
    }

    @Test
    void directionalFalloffIsLinear() {
        // DIRECTIONAL falloff = 1 - distance/range. At range/2, force should be ~half of edge force.
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.DIRECTIONAL);

        final double atOne = FieldApplicator.forceAt(field, new Vec3(0, 1, 0)).length();
        final double atFour = FieldApplicator.forceAt(field, new Vec3(0, 4, 0)).length();
        // range=8 → falloff(1)=7/8, falloff(4)=4/8 → ratio 7/4 = 1.75
        assertEquals(1.75d, atOne / atFour, 0.001);
    }

    @Test
    void conicalForcePointsAlongAxisRegardlessOfSampleOffset() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS_UP, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.CONICAL);

        // Sample inside the cone but offset on X — force should still be along Y axis.
        final Vec3 sample = new Vec3(0.5, 2, 0);
        final Vec3 force = FieldApplicator.forceAt(field, sample);
        assertTrue(force.y > 0);
        assertEquals(0.0d, force.x, 1e-9);
        assertEquals(0.0d, force.z, 1e-9);
    }
}
