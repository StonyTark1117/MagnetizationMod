package com.stonytark.magnetization.content.golem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MagnetiteGolemTest {
    @Test
    void oxidationTransitionsExactlyAtTheConfiguredDuration() {
        final var before = IronOxideGolemLogic.advanceOxidation(98, false, true, 100);
        assertEquals(99, before.ticks());
        assertFalse(before.oxidized());

        final var boundary = IronOxideGolemLogic.advanceOxidation(before.ticks(), false, true, 100);
        assertEquals(100, boundary.ticks());
        assertTrue(boundary.oxidized());
    }

    @Test
    void disabledAndCompletedOxidationDoNotAdvance() {
        assertEquals(new IronOxideGolemLogic.OxidationProgress(42, false),
                IronOxideGolemLogic.advanceOxidation(42, false, false, 100));
        assertEquals(new IronOxideGolemLogic.OxidationProgress(100, true),
                IronOxideGolemLogic.advanceOxidation(100, true, true, 100));
    }
}
