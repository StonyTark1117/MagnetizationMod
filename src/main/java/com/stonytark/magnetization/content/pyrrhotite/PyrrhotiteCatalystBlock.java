package com.stonytark.magnetization.content.pyrrhotite;

import net.minecraft.world.level.block.Block;

/**
 * Passive heat-bridge block. Doesn't tick or carry state — its only job is
 * to be detectable by nearby {@link PyrrhotiteBlockEntity} instances, which
 * extend their heat scan through any Catalyst block within the Catalyst's
 * own {@link #transmitRadius}. The Catalyst then forwards whatever
 * HEAT_LEVEL it sees on its own 6 axis-aligned neighbours.
 *
 * <p>Three tiers ship: basic (3 blocks), enhanced (5), cosmic (7). All three
 * share this class; the radius is set at construction so each tier registers
 * its own block instance with a distinct {@code transmitRadius()} reading.
 *
 * <p>Chains of Catalysts daisy-chain heat across distance (each one
 * independently transmits to pyrrhotite within its own radius), and mixed
 * tiers stack naturally — the largest radius in range wins.
 *
 * <p>No BE = no per-block memory cost. The scan workload lives in the
 * pyrrhotite reactor, which only ticks while it's an active emitter.
 */
public final class PyrrhotiteCatalystBlock extends Block {

    private final int transmitRadius;

    public PyrrhotiteCatalystBlock(final Properties props, final int transmitRadius) {
        super(props);
        this.transmitRadius = transmitRadius;
    }

    /** Max Chebyshev distance (in blocks) from this Catalyst at which a
     *  pyrrhotite reactor will still pull heat through it. Defaults to 3 for
     *  the basic Catalyst; tiered variants register with larger values. */
    public int transmitRadius() {
        return transmitRadius;
    }
}
