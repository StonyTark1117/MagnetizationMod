package com.stonytark.magnetization.menu;

/**
 * A block entity whose effective range can be dialed in-GUI via the shared
 * {@link EmitterMenu} range row (the {@code -}/{@code +} widget). Lets non-emitter
 * machines (e.g. the Magnetostrictive Sensor) reuse the emitter GUI's range
 * control without extending {@code AbstractEmitterBlockEntity}.
 *
 * <p>{@link #getRangeOverride()} returns 0 when no override is set, in which case
 * the GUI shows {@link #defaultRangeBlocks()}. Once the player touches the row the
 * override becomes an explicit value clamped to
 * {@code [minRangeBlocks(), maxRangeBlocks()]}.
 */
public interface RangeConfigurable {

    /** Current dialed range in blocks; 0 means "use {@link #defaultRangeBlocks()}". */
    int getRangeOverride();

    /** Set the dialed range. Implementations clamp to {@code [min, max]}. */
    void setRangeOverride(int blocks);

    /** The range used when no override is set — shown on the GUI label. */
    int defaultRangeBlocks();

    /** Admin ceiling (config-driven) the player can dial up to. */
    int maxRangeBlocks();

    /** Floor the player can dial down to. Defaults to 1 block. */
    default int minRangeBlocks() { return 1; }

    /** Step per +/- click. Defaults to 1 block. */
    default int rangeStep() { return 1; }
}
