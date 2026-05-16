package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression guard for the 1.0.1 "magnets stuck on rotating contraption" bug.
 *
 * <p>The fix replaced a buggy re-query of the host SubLevel inside
 * {@link SableBridge#promoteToWorldSpace} with the caller-supplied pose. These
 * tests construct {@link Pose3d}s with known rotations / translations and assert
 * that both the field origin and the directional axis follow the pose.
 *
 * <p>Tolerance is {@value #EPS} because we're chaining floating-point quaternion
 * rotations; component equality without slack would fail on rounding.
 */
class SableBridgePromoteTest {

    private static final double EPS = 1.0e-9;

    private static Pose3d pose(final Vector3d position, final Quaterniond orientation) {
        return new Pose3d(position, orientation, new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
    }

    private static MagneticField field(final Vec3 origin, final Vec3 axis) {
        return new MagneticField(
                origin, axis,
                MagneticPolarity.NORTH, MagneticStrength.MEDIUM,
                MagneticField.Shape.DIRECTIONAL,
                12.0);
    }

    @Test
    void identityPoseReturnsFieldUnchanged() {
        final MagneticField local = field(new Vec3(0.5, 0.5, 0.5), new Vec3(0, 1, 0));
        final MagneticField world = SableBridge.promoteToWorldSpace(pose(new Vector3d(), new Quaterniond()), local);

        assertEquals(0.5, world.origin().x, EPS);
        assertEquals(0.5, world.origin().y, EPS);
        assertEquals(0.5, world.origin().z, EPS);
        assertEquals(0.0, world.axis().x, EPS);
        assertEquals(1.0, world.axis().y, EPS);
        assertEquals(0.0, world.axis().z, EPS);
    }

    @Test
    void translatedPoseTranslatesOrigin() {
        final MagneticField local = field(new Vec3(0.5, 0.5, 0.5), new Vec3(0, 1, 0));
        final MagneticField world = SableBridge.promoteToWorldSpace(
                pose(new Vector3d(100, 64, -200), new Quaterniond()), local);

        assertEquals(100.5, world.origin().x, EPS);
        assertEquals(64.5, world.origin().y, EPS);
        assertEquals(-199.5, world.origin().z, EPS);
        // No rotation → axis unchanged.
        assertEquals(1.0, world.axis().y, EPS);
    }

    @Test
    void ninetyDegreeYawRotatesNorthAxisToWest() {
        // Repulsor placed pointing NORTH (-Z) on a ship that has yawed 90° (around +Y)
        // should now project force toward WEST (-X) in world space.
        final Quaterniond yaw90 = new Quaterniond(new AxisAngle4d(Math.PI / 2.0, 0, 1, 0));
        final MagneticField local = field(new Vec3(0, 0, 0), new Vec3(0, 0, -1));
        final MagneticField world = SableBridge.promoteToWorldSpace(pose(new Vector3d(), yaw90), local);

        assertEquals(-1.0, world.axis().x, EPS);
        assertEquals(0.0, world.axis().y, EPS);
        assertEquals(0.0, world.axis().z, EPS);
    }

    @Test
    void ninetyDegreePitchRotatesUpAxisToSouth() {
        // Permanent magnet's axis (+Y) on a ship that pitched nose-down 90° around
        // +X — the local-up axis should now point +Z in world space.
        final Quaterniond pitch90 = new Quaterniond(new AxisAngle4d(Math.PI / 2.0, 1, 0, 0));
        final MagneticField local = field(new Vec3(0, 0, 0), new Vec3(0, 1, 0));
        final MagneticField world = SableBridge.promoteToWorldSpace(pose(new Vector3d(), pitch90), local);

        assertEquals(0.0, world.axis().x, EPS);
        assertEquals(0.0, world.axis().y, EPS);
        assertEquals(1.0, world.axis().z, EPS);
    }

    @Test
    void rotationAlsoMovesOriginAroundShipCenter() {
        // The bug also stranded the field origin in local space — without promotion,
        // the field would attract to the spot where the ship was assembled rather
        // than where the magnet currently sits in the world.
        final Quaterniond yaw180 = new Quaterniond(new AxisAngle4d(Math.PI, 0, 1, 0));
        final MagneticField local = field(new Vec3(5, 0, 0), new Vec3(0, 1, 0));
        final MagneticField world = SableBridge.promoteToWorldSpace(
                pose(new Vector3d(10, 64, 0), yaw180), local);

        // Local (5, 0, 0) rotated 180° around Y → (-5, 0, 0), then translated by (10, 64, 0).
        assertEquals(5.0, world.origin().x, EPS);
        assertEquals(64.0, world.origin().y, EPS);
        assertEquals(0.0, world.origin().z, EPS);
    }

    @Test
    void preservesCustomRange() {
        // Earlier signature dropped customRange when constructing the world-space
        // field, so a player-tuned range silently reverted to the strength tier's
        // default the moment the ship moved.
        final MagneticField local = field(new Vec3(0, 0, 0), new Vec3(0, 1, 0));
        final MagneticField world = SableBridge.promoteToWorldSpace(
                pose(new Vector3d(), new Quaterniond()), local);

        assertEquals(12.0, world.customRange(), EPS);
        assertEquals(12.0, world.range(), EPS);
    }

    @Test
    void preservesPolarityStrengthAndShape() {
        final MagneticField local = new MagneticField(
                new Vec3(0, 0, 0), new Vec3(0, 1, 0),
                MagneticPolarity.SOUTH, MagneticStrength.STRONG,
                MagneticField.Shape.CONICAL,
                0.0);
        final MagneticField world = SableBridge.promoteToWorldSpace(
                pose(new Vector3d(), new Quaterniond()), local);

        assertEquals(MagneticPolarity.SOUTH, world.polarity());
        assertEquals(MagneticStrength.STRONG, world.strength());
        assertEquals(MagneticField.Shape.CONICAL, world.shape());
    }
}
