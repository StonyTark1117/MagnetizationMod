package com.stonytark.magnetization.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GasDetectorHudLayoutTest {
    @Test
    void dangerousReadoutClearsVanillaActionBar() {
        assertTrue(GasDetectorHud.actionBarGap(9) >= 6,
                "Radon warning/readout must not overlap the action-bar summary");
    }
}
