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
}
