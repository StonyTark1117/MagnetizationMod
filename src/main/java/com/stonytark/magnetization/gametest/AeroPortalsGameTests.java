package com.stonytark.magnetization.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated namespace for tests that require the optional AeroPortals runtime. */
@GameTestHolder("magnetization_aeroportals")
@PrefixGameTestTemplate(false)
public final class AeroPortalsGameTests {

    private AeroPortalsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void retainsMagneticShipState(final GameTestHelper helper) {
        MagGameTests.aeroPortalsRetainsMagneticShipState(helper);
    }
}
