package com.stonytark.magnetization.content.docking;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static com.stonytark.magnetization.content.docking.DockingState.*;

class DockingStateTest {
    private final UUID ship = UUID.randomUUID();
    private final Limits limits = new Limits(8, 1.5, .5, .1, .1, 20);
    private Reading reading(double distance, double speed, double spin) {
        return new Reading(ship, distance, speed, spin, null);
    }
    @Test void settledRequiresContinuousDwellAndDistance() {
        var state = new DockingState();
        state.update(reading(4, 0, 0), limits, 0);
        state.update(reading(4, 0, 0), limits, 100);
        assertEquals(Reason.TOO_FAR, state.reason());
        assertEquals(0, state.signal(Mode.SETTLED, 8, 0));
        state.update(reading(1, 0, 0), limits, 104);
        state.update(reading(1, 0, 0), limits, 120);
        assertEquals(Reason.SETTLING, state.reason());
        state.update(reading(1, .2, 0), limits, 124);
        assertEquals(Reason.MOVING, state.reason());
        state.update(reading(1, 0, 0), limits, 128);
        state.update(reading(1, 0, 0), limits, 148);
        assertEquals(15, state.signal(Mode.SETTLED, 8, 0));
        state.update(reading(1.8, .12, .12), limits, 152);
        assertEquals(Reason.SETTLED, state.reason());
        state.update(reading(2.1, 0, 0), limits, 156);
        assertEquals(Reason.TOO_FAR, state.reason());
    }
    @Test void presenceAndAnalogSignalsHaveHysteresis() {
        var state = new DockingState();
        state.update(reading(7.9, 0, 0), limits, 0);
        state.update(reading(8.3, 0, 0), limits, 4);
        assertEquals(15, state.signal(Mode.TARGET_PRESENT, 8, 0));
        state.update(reading(8.6, 0, 0), limits, 8);
        assertEquals(15, state.signal(Mode.TARGET_LOST, 8, 0));
        state.update(reading(8.3, 0, 0), limits, 12);
        assertEquals(0, state.signal(Mode.TARGET_PRESENT, 8, 0));
        state.update(reading(4, 0, 0), limits, 16);
        assertEquals(8, state.signal(Mode.ANALOG_DISTANCE, 8, 0));
        state.update(reading(4.01, 0, 0), limits, 20);
        assertEquals(8, state.signal(Mode.ANALOG_DISTANCE, 8, 8));
    }
    @Test void lostNeverFiresBeforeSeeingTargetAndSurvivesReload() {
        var state = new DockingState();
        state.update(Reading.missing(Reason.NO_TARGET), limits, 0);
        assertEquals(0, state.signal(Mode.TARGET_LOST, 8, 0));
        state.update(reading(1, 0, 0), limits, 4);
        var restored = new DockingState();
        restored.restore(state.tracked(), state.seen());
        restored.update(Reading.missing(Reason.TARGET_UNAVAILABLE), limits, 8);
        assertEquals(15, restored.signal(Mode.TARGET_LOST, 8, 0));
        assertEquals(0, restored.signal(Mode.SETTLED, 8, 0));
    }
    @Test void replacementTargetMustBeSeenBeforeReportingLost() {
        var state = new DockingState();
        state.update(reading(1,0,0), limits, 0);
        state.update(new Reading(UUID.randomUUID(), Double.POSITIVE_INFINITY, 0, 0, Reason.TARGET_UNAVAILABLE), limits, 4);
        assertFalse(state.seen());
        assertEquals(0, state.signal(Mode.TARGET_LOST, 8, 0));
    }
    @Test void replacementTargetAndSpinResetDwell() {
        var state = new DockingState();
        state.update(reading(1, 0, 0), limits, 0);
        state.update(reading(1, 0, .2), limits, 24);
        assertEquals(Reason.SPINNING, state.reason());
        state.update(reading(1, 0, 0), limits, 28);
        state.update(new Reading(UUID.randomUUID(), 1, 0, 0, null), limits, 48);
        assertEquals(Reason.SETTLING, state.reason());
    }
}
