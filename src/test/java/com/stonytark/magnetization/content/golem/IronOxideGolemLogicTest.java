package com.stonytark.magnetization.content.golem;

import com.stonytark.magnetization.api.MagneticPolarity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IronOxideGolemLogicTest {
    @Test
    void polarityRestoreKeepsNorthAndSouthAndRejectsInvalidStates() {
        assertSame(MagneticPolarity.NORTH, IronOxideGolemLogic.restorePolarity("NORTH"));
        assertSame(MagneticPolarity.SOUTH, IronOxideGolemLogic.restorePolarity("SOUTH"));
        assertSame(MagneticPolarity.NORTH, IronOxideGolemLogic.restorePolarity("NONE"));
        assertSame(MagneticPolarity.NORTH, IronOxideGolemLogic.restorePolarity("not-a-polarity"));
    }

    @Test
    void ownerAndTeamPolicyProtectsOnlyTheSpecifiedFriendlyTargets() {
        final UUID source = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        final UUID target = UUID.randomUUID();
        assertTrue(IronOxideGolemLogic.protectsTarget(source, owner, source, false, false));
        assertTrue(IronOxideGolemLogic.protectsTarget(source, owner, owner, false, false));
        assertTrue(IronOxideGolemLogic.protectsTarget(source, owner, target, true, false));
        assertTrue(IronOxideGolemLogic.protectsTarget(source, owner, target, false, true));
        assertFalse(IronOxideGolemLogic.protectsTarget(source, owner, target, false, false));
        assertFalse(IronOxideGolemLogic.protectsTarget(source, null, target, true, true));
    }

    @Test
    void titanCaptureRejectsSelfAndEveryTitanSource() {
        final UUID self = UUID.randomUUID();
        final UUID ordinary = UUID.randomUUID();
        final UUID otherTitan = UUID.randomUUID();
        assertFalse(IronOxideGolemLogic.captureSourceAllowed(self, self, false));
        assertFalse(IronOxideGolemLogic.captureSourceAllowed(self, otherTitan, true));
        assertTrue(IronOxideGolemLogic.captureSourceAllowed(self, ordinary, false));
    }
}
