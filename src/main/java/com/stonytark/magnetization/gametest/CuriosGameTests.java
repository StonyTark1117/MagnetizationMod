package com.stonytark.magnetization.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated Curios runtime profile for equipment activation and its master gate. */
@GameTestHolder("magnetization_curios")
@PrefixGameTestTemplate(false)
public final class CuriosGameTests {
    private CuriosGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60, batch = "curiosMasterCompat")
    public static void masterGatesRealCurioActivation(final GameTestHelper helper) {
        MagGameTests.curioRepulsorPayloadActivatesRealCharm(helper);
    }
}
