package com.stonytark.magnetization.content.sail;

import com.stonytark.magnetization.config.MagConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlfvenBackpackHandlerTest {

    @Test
    void highAltitudeRestrictionDefaultsOff() {
        assertFalse(MagConfig.ALFVEN_HIGH_ALTITUDE_REQUIRED.getDefault());
    }

    @Test
    void unrestrictedDaylightAllowsEveryAltitude() {
        assertTrue(AlfvenBackpackHandler.hasUsableCurrent(false, true, -64.0, false));
        assertTrue(AlfvenBackpackHandler.hasUsableCurrent(false, true, 64.0, false));
        assertTrue(AlfvenBackpackHandler.hasUsableCurrent(false, true, 120.0, false));
    }

    @Test
    void enabledRestrictionRequiresStrictlyAboveY120() {
        assertFalse(AlfvenBackpackHandler.hasUsableCurrent(false, true, 119.99, true));
        assertFalse(AlfvenBackpackHandler.hasUsableCurrent(false, true, 120.0, true));
        assertTrue(AlfvenBackpackHandler.hasUsableCurrent(false, true, 120.01, true));
    }

    @Test
    void endRemainsExemptAndOverworldStillRequiresDaylight() {
        assertTrue(AlfvenBackpackHandler.hasUsableCurrent(true, false, -64.0, true));
        assertFalse(AlfvenBackpackHandler.hasUsableCurrent(false, false, 256.0, false));
        assertFalse(AlfvenBackpackHandler.hasUsableCurrent(false, false, 256.0, true));
    }
}
