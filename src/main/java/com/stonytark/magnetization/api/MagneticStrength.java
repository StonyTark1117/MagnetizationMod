package com.stonytark.magnetization.api;

/**
 * Tiered field strength. Each tier carries a force scalar (in newton-equivalents
 * for Sable's impulse units) and an effective range in blocks. The effective range
 * is the radius beyond which the field force is treated as zero.
 */
public enum MagneticStrength {
    NONE(0.0d, 0.0d),
    // Force values in Newtons (Sable's SI unit). Calibrated so that at the
    // tier's effective range the resulting acceleration is comparable to
    // vanilla gravity (~0.4 m/s²/0.04 blocks/tick²) on a 10-block ship; closer
    // emitters with inverse-square falloff hit much harder. The previous
    // values (2/8/24/80) were ~100x too small and produced no visible motion.
    WEAK(200.0d, 4.0d),
    MEDIUM(800.0d, 8.0d),
    STRONG(2400.0d, 16.0d),
    EXTREME(8000.0d, 32.0d);

    private final double force;
    private final double range;

    MagneticStrength(final double force, final double range) {
        this.force = force;
        this.range = range;
    }

    public double force() {
        return force;
    }

    public double range() {
        return range;
    }

    /** Highest analog redstone level, and the signal at which {@link #forceForSignal}
     *  reaches {@link #EXTREME}'s force. */
    public static final int MAX_SIGNAL = 15;

    /**
     * Force in Newtons for an analog redstone level, used by emitters running with an
     * "Analog Redstone" config toggle on. The ramp is anchored to the tier ladder's own
     * endpoints — signal 1 gives {@link #WEAK}'s force and signal 15 gives
     * {@link #EXTREME}'s — so it can never drift away from the tiers as they are retuned.
     *
     * <p>The interpolation is <b>geometric</b>, not linear, because the ladder itself is
     * roughly geometric (200 → 800 → 2400 → 8000 is ×4, ×3, ×3.3). A linear ramp would
     * pass STRONG's force by signal 5 and leave two thirds of the dial bunched in the top
     * band; geometric makes every redstone level an equal proportional step and spreads
     * the four named tiers across the whole range.
     *
     * @param signal 0-15; anything at or below 0 yields 0 (no field), anything above 15
     *               is clamped to the maximum.
     */
    public static double forceForSignal(final int signal) {
        if (signal <= 0) return 0.0d;
        if (signal >= MAX_SIGNAL) return EXTREME.force;
        final double min = WEAK.force;
        final double max = EXTREME.force;
        return min * Math.pow(max / min, (signal - 1) / (double) (MAX_SIGNAL - 1));
    }

    /**
     * The strongest tier whose nominal force does not exceed {@code force} (never below
     * {@link #WEAK} for a positive force). Display-only: the accessibility pip meter and
     * the HUD word describe a tier, so an emitter throttled to 200 N by a weak redstone
     * signal must read as WEAK rather than showing its configured EXTREME.
     */
    public static MagneticStrength nearestForForce(final double force) {
        if (force <= 0.0d) return NONE;
        MagneticStrength best = WEAK;
        for (final MagneticStrength s : values()) {
            if (s == NONE) continue;
            if (s.force <= force) best = s;
        }
        return best;
    }
}
