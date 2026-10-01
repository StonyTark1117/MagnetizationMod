package com.stonytark.magnetization.content.docking;

import java.util.UUID;

/** Server-owned docking state. Uses elapsed game ticks, never ticker invocation count. */
public final class DockingState {
    public enum Mode { PROXIMITY, TARGET_PRESENT, SETTLED, TARGET_LOST, ANALOG_DISTANCE;
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public enum Reason { UNLINKED, ANCHOR_UNAVAILABLE, NO_TARGET, TARGET_UNAVAILABLE, OUT_OF_RANGE,
        TOO_FAR, MOVING, SPINNING, SETTLING, SETTLED }
    public record Limits(double range, double tolerance, double hysteresis, double speed,
                         double spin, int dwellTicks) {}
    public record Reading(UUID target, double distance, double speed, double spin, Reason unavailable) {
        public static Reading missing(Reason reason) {
            return new Reading(null, Double.POSITIVE_INFINITY, 0, 0, reason);
        }
    }
    private UUID tracked;
    private boolean seen, present, settled;
    private long stableSince = -1;
    private double distance = Double.POSITIVE_INFINITY;
    private Reason reason = Reason.UNLINKED;

    public void update(Reading reading, Limits limits, long tick) {
        distance = reading.distance();
        if (reading.target() != null && !reading.target().equals(tracked)) {
            tracked = reading.target();
            seen = present = settled = false;
            stableSince = -1;
        }
        if (reading.unavailable() != null) {
            present = settled = false;
            stableSince = -1;
            reason = reading.unavailable();
            return;
        }
        present = distance <= limits.range() + (present ? limits.hysteresis() : 0);
        if (!present) { reset(Reason.OUT_OF_RANGE); return; }
        seen = true;
        final double margin = settled ? limits.hysteresis() : 0;
        if (distance > limits.tolerance() + margin) { reset(Reason.TOO_FAR); return; }
        if (reading.speed() > limits.speed() * (settled ? 1.5 : 1)) { reset(Reason.MOVING); return; }
        if (reading.spin() > limits.spin() * (settled ? 1.5 : 1)) { reset(Reason.SPINNING); return; }
        if (stableSince < 0 || tick < stableSince) stableSince = tick;
        settled = tick - stableSince >= limits.dwellTicks();
        reason = settled ? Reason.SETTLED : Reason.SETTLING;
    }
    private void reset(Reason why) { settled = false; stableSince = -1; reason = why; }
    public int signal(Mode mode, double range, int previous) {
        return switch (mode) {
            case PROXIMITY -> 0; // legacy proximity scan belongs to the switch
            case TARGET_PRESENT -> present ? 15 : 0;
            case SETTLED -> settled ? 15 : 0;
            case TARGET_LOST -> seen && !present ? 15 : 0;
            case ANALOG_DISTANCE -> {
                if (!present) yield 0;
                double raw = Math.clamp(15 * (1 - distance / range), 0, 15);
                // Schmitt threshold about each integer output prevents rounding chatter.
                yield Math.abs(raw - previous) < 0.75 ? previous : (int) Math.round(raw);
            }
        };
    }
    public Reason reason() { return reason; }
    public UUID tracked() { return tracked; }
    public boolean seen() { return seen; }
    public double distance() { return distance; }
    public void restore(UUID id, boolean wasSeen) { tracked = id; seen = wasSeen; }
}
