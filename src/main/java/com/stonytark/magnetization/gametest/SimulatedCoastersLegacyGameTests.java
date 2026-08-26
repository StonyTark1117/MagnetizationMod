package com.stonytark.magnetization.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral coverage against the oldest supported Create: Coasters Simulated 0.1 runtime. */
@GameTestHolder("magnetization_simulatedcoasters_legacy")
@PrefixGameTestTemplate(false)
public final class SimulatedCoastersLegacyGameTests {
    private SimulatedCoastersLegacyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void looseCartRetainsBaseMagneticCompatibility(final GameTestHelper helper) {
        SimulatedCoastersGameTests.looseCoasterCartReactsToFieldsButNotInducer(helper);
    }
}
