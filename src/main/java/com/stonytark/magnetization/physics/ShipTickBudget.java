package com.stonytark.magnetization.physics;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Per-tick per-ship state for the FieldApplicator pass.
 *
 * <p>One {@link #grant} call per ship per emitter; the budget is shared so three
 * emitters all pulling at the cap can't stack to 3× the cap. Drag piggybacks on
 * the same lookup via {@link #markTouched} — a ship is dragged once per tick
 * regardless of how many emitters touched it.
 *
 * <p>State is keyed by {@link ServerSubLevel} in a {@link WeakHashMap} so
 * shattered sub-levels are reclaimed by GC without an explicit teardown.
 * Entries are stamped with the tick they were created on; reads compare and
 * reset lazily so a stale entry from last tick looks empty.
 */
public final class ShipTickBudget {

    private static final class Entry {
        long tick;
        double appliedAccel;
        boolean dragged;

        Entry(final long tick) {
            this.tick = tick;
            this.appliedAccel = 0.0d;
            this.dragged = false;
        }

        void resetIfStale(final long now) {
            if (this.tick != now) {
                this.tick = now;
                this.appliedAccel = 0.0d;
                this.dragged = false;
            }
        }
    }

    private static final Map<ServerSubLevel, Entry> STATE = new WeakHashMap<>();

    private ShipTickBudget() {}

    /**
     * Ask for permission to apply {@code wantedAccel} m/s² to {@code ship} on
     * tick {@code now}. Returns the granted accel, possibly less than wanted if
     * other emitters this tick have already used up the per-tick cap. The cap
     * may be 0 or negative, in which case all of {@code wantedAccel} is granted
     * (unbounded mode — emitters apply whatever they compute).
     */
    public static synchronized double grant(final ServerSubLevel ship,
                                            final long now,
                                            final double cap,
                                            final double wantedAccel) {
        if (cap <= 0.0d) return wantedAccel;
        if (wantedAccel <= 0.0d) return 0.0d;

        final Entry e = STATE.computeIfAbsent(ship, k -> new Entry(now));
        e.resetIfStale(now);

        final double remaining = Math.max(0.0d, cap - e.appliedAccel);
        if (remaining <= 0.0d) return 0.0d;
        final double granted = Math.min(wantedAccel, remaining);
        e.appliedAccel += granted;
        return granted;
    }

    /**
     * Mark {@code ship} as touched by a magnetic impulse this tick, and return
     * whether this is the first such mark (so drag should be applied exactly
     * once per tick per ship). Subsequent calls in the same tick return false.
     */
    public static synchronized boolean markTouched(final ServerSubLevel ship, final long now) {
        final Entry e = STATE.computeIfAbsent(ship, k -> new Entry(now));
        e.resetIfStale(now);
        if (e.dragged) return false;
        e.dragged = true;
        return true;
    }
}
