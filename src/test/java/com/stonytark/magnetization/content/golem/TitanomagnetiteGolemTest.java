package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TitanomagnetiteGolemTest {
    @Test
    void capturePreservesEveryShapeAndAxisButDropsOverrides() {
        final Vec3 origin = new Vec3(20, 30, 40);
        final Vec3 axis = new Vec3(0.25d, 0.5d, -0.75d);
        for (final MagneticField.Shape shape : MagneticField.Shape.values()) {
            final MagneticField source = new MagneticField(Vec3.ZERO, axis,
                    MagneticPolarity.SOUTH, MagneticStrength.MEDIUM, shape, 91.0d, 1234.0d);
            final MagneticField captured = IronOxideGolemLogic.captureSnapshot(source, origin);
            assertEquals(origin, captured.origin(), shape.name());
            assertEquals(axis, captured.axis(), shape.name());
            assertEquals(MagneticPolarity.SOUTH, captured.polarity(), shape.name());
            assertEquals(MagneticStrength.MEDIUM, captured.strength(), shape.name());
            assertEquals(shape, captured.shape(), shape.name());
            assertEquals(0.0d, captured.customRange(), shape.name());
            assertEquals(0.0d, captured.forceOverride(), shape.name());
            assertEquals(MagneticStrength.MEDIUM.range(), captured.range(), shape.name());
        }
    }

    @Test
    void captureCapsExtremeAtStrongAndUsesTheTierRange() {
        final MagneticField source = new MagneticField(Vec3.ZERO, new Vec3(1, 0, 0),
                MagneticPolarity.NORTH, MagneticStrength.EXTREME,
                MagneticField.Shape.CONICAL, 512.0d, 99999.0d);
        final MagneticField captured = IronOxideGolemLogic.captureSnapshot(source, Vec3.ZERO);
        assertEquals(MagneticStrength.STRONG, captured.strength());
        assertEquals(MagneticStrength.STRONG.range(), captured.range());
        assertEquals(MagneticStrength.STRONG.force(), captured.force());
    }
}
