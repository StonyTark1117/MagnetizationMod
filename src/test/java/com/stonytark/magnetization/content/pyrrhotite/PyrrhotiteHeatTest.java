package com.stonytark.magnetization.content.pyrrhotite;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.stonytark.magnetization.api.MagneticStrength;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Pins the Create-HEAT_LEVEL → MagneticStrength mapping. Create has bumped
 *  its HeatLevel enum in the past; if it adds or renames a level our switch
 *  becomes exhaustiveness-broken and tests fail loudly. */
class PyrrhotiteHeatTest {

    @Test
    void noneEmitsNoField() {
        assertNull(PyrrhotiteBlockEntity.strengthForHeat(BlazeBurnerBlock.HeatLevel.NONE));
    }

    @Test
    void smoulderingIsWeak() {
        assertSame(MagneticStrength.WEAK,
                PyrrhotiteBlockEntity.strengthForHeat(BlazeBurnerBlock.HeatLevel.SMOULDERING));
    }

    @Test
    void fadingIsWeak() {
        // FADING is the cool-down stage between KINDLED and inert; mapping it
        // to WEAK keeps the field-tier readout monotonic as the burner cools.
        assertSame(MagneticStrength.WEAK,
                PyrrhotiteBlockEntity.strengthForHeat(BlazeBurnerBlock.HeatLevel.FADING));
    }

    @Test
    void kindledIsStrong() {
        assertSame(MagneticStrength.STRONG,
                PyrrhotiteBlockEntity.strengthForHeat(BlazeBurnerBlock.HeatLevel.KINDLED));
    }

    @Test
    void seethingIsExtreme() {
        assertSame(MagneticStrength.EXTREME,
                PyrrhotiteBlockEntity.strengthForHeat(BlazeBurnerBlock.HeatLevel.SEETHING));
    }
}
