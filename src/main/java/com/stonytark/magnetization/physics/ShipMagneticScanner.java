package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.ShipMagneticState;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.inverter.PolarityInverterBlock;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * Counts the blocks that contribute to a ship's magnetic state and folds them
 * into a {@link ShipMagneticState}. Section-aware iteration: empty/all-air
 * sections are skipped via {@link LevelChunkSection#hasOnlyAir()} and the
 * remaining sections are palette-iterated, so even a 100-block-thick ship
 * inside a large allocated plot scans in microseconds.
 *
 * <p>What gets counted:
 * <ul>
 *   <li>any block in {@link MagTags#FERROMAGNETIC_BLOCKS} → ferrous count</li>
 *   <li>any block in {@link MagTags#MAGNETIC_EMITTER_BLOCKS} → magnet count
 *       (also counts toward the susceptibility budget, like ferrous-plus)</li>
 *   <li>any {@link PolarityInverterBlock} → inverter count (parity → polarity)</li>
 * </ul>
 *
 * <p>The polarity rule is parity-based, matching the user's spec: 0/2/4
 * inverters = NORTH (no flip), 1/3/5 = SOUTH. A single inverter flips; a
 * second cancels it; etc.
 */
public final class ShipMagneticScanner {

    private ShipMagneticScanner() {}

    public static ShipMagneticState scan(final ServerSubLevel subLevel) {
        int ferrous = 0;
        int magnets = 0;
        int inverters = 0;

        for (final PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
            final LevelChunk chunk = holder.getChunk();
            for (final LevelChunkSection section : chunk.getSections()) {
                if (section == null || section.hasOnlyAir()) continue;
                final PalettedContainer<BlockState> states = section.getStates();
                for (int dx = 0; dx < 16; dx++) {
                    for (int dy = 0; dy < 16; dy++) {
                        for (int dz = 0; dz < 16; dz++) {
                            final BlockState state = states.get(dx, dy, dz);
                            if (state.isAir()) continue;
                            if (state.getBlock() instanceof PolarityInverterBlock) {
                                inverters++;
                                continue;
                            }
                            if (state.is(MagTags.MAGNETIC_EMITTER_BLOCKS)) {
                                magnets++;
                                continue;
                            }
                            if (state.is(MagTags.FERROMAGNETIC_BLOCKS)) {
                                ferrous++;
                            }
                        }
                    }
                }
            }
        }

        return new ShipMagneticState(
                polarityFromInverterCount(inverters),
                susceptibility(ferrous, magnets),
                ferrous, magnets, inverters);
    }

    /** XOR-style: each inverter on board flips the ship's polarity once. Even
     *  count = NORTH (cancels out), odd = SOUTH. Lets a player toggle by
     *  placing/removing a single inverter, or stack multiples and use a paired
     *  inverter to fine-tune. */
    static MagneticPolarity polarityFromInverterCount(final int inverters) {
        return (inverters & 1) == 1 ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH;
    }

    /** baseline + ferrous*k_f + magnet*k_m, clamped to the configured max. */
    static double susceptibility(final int ferrous, final int magnets) {
        final double baseline = configDouble(MagConfig.SHIP_BASELINE_SUSCEPTIBILITY, 1.0d);
        final double kFerrous = configDouble(MagConfig.SHIP_PER_FERROUS_SUSCEPTIBILITY, 0.05d);
        final double kMagnet  = configDouble(MagConfig.SHIP_PER_MAGNET_SUSCEPTIBILITY, 0.15d);
        final double cap      = configDouble(MagConfig.SHIP_MAX_SUSCEPTIBILITY, 20.0d);
        final double raw = baseline + ferrous * kFerrous + magnets * kMagnet;
        return Math.min(raw, cap);
    }

    private static double configDouble(final net.neoforged.neoforge.common.ModConfigSpec.DoubleValue v,
                                       final double fallback) {
        try { return v.get(); } catch (final Throwable t) { return fallback; }
    }
}
