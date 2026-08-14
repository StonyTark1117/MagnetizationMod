package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticStrength;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class MobileFieldRegistryTest {
    @Test void hematiteStepsStackToCompleteSuppression() {
        assertSame(MagneticStrength.EXTREME, MobileFieldRegistry.stepDown(MagneticStrength.EXTREME, 0));
        assertSame(MagneticStrength.STRONG, MobileFieldRegistry.stepDown(MagneticStrength.EXTREME, 1));
        assertSame(MagneticStrength.MEDIUM, MobileFieldRegistry.stepDown(MagneticStrength.EXTREME, 2));
        assertSame(MagneticStrength.WEAK, MobileFieldRegistry.stepDown(MagneticStrength.EXTREME, 3));
        assertSame(MagneticStrength.NONE, MobileFieldRegistry.stepDown(MagneticStrength.EXTREME, 4));
        assertSame(MagneticStrength.NONE, MobileFieldRegistry.stepDown(MagneticStrength.WEAK, 99));
    }
}
