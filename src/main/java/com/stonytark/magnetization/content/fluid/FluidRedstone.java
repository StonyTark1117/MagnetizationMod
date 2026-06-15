package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Makes the mod's conductive fluids carry a redstone signal like a body of
 * liquid redstone dust — ferrofluid, magnetized ferrofluid, MR fluid, and the
 * Hardened MR Fluid bridge block all conduct. They never <em>react</em> to a
 * signal (no behaviour changes when powered) and are not power sources; they
 * simply relay an external signal across their connected span, attenuating one
 * level per cell exactly like dust, so you can route wiring through pools and
 * walkways for complex machines.
 *
 * <p>Power is stored per cell in the {@link #POWER} blockstate property. When
 * anything adjacent to the network changes, the whole connected component is
 * recomputed in a single pass (mirroring vanilla {@code RedStoneWireBlock}'s
 * network recompute) to avoid the slow "decrement-by-one each tick" artefact a
 * purely local update would produce. A re-entrancy guard keeps our own
 * neighbour notifications from kicking off nested recomputes.
 */
public final class FluidRedstone {

    /** 0–15 conducted signal level for a conductive-fluid cell. */
    public static final IntegerProperty POWER = IntegerProperty.create("signal_power", 0, 15);

    /** Marker for any block that conducts redstone as part of a fluid network. */
    public interface Conductor {}

    /** Cap on a single connected network we will recompute, as a runaway guard. */
    private static final int MAX_NETWORK = 4096;

    private static boolean recomputing = false;

    private FluidRedstone() {}

    public static boolean isConductor(final BlockState state) {
        return state.getBlock() instanceof Conductor;
    }

    /** Stored conducted level (weak power emitted to all sides). */
    public static int signal(final BlockState state) {
        return state.hasProperty(POWER) ? state.getValue(POWER) : 0;
    }

    /**
     * Client visual cue: a conductor carrying a signal lightly drifts a redstone
     * dust particle off its surface, tinted brighter as the carried level rises
     * (the same colour ramp as vanilla redstone wire). Call from {@code animateTick}.
     */
    public static void spawnSignalParticles(final BlockState state, final Level level,
                                            final BlockPos pos, final RandomSource random) {
        final int power = signal(state);
        if (power <= 0 || random.nextInt(6) != 0) return; // keep it light
        final float f = power / 15.0f;
        final float r = f * 0.6f + 0.4f;
        final float g = Math.max(0.0f, f * f * 0.7f - 0.5f);
        final float b = Math.max(0.0f, f * f * 0.6f - 0.7f);
        final DustParticleOptions dust = new DustParticleOptions(new Vector3f(r, g, b), 1.0f);
        final double yTop = state.getFluidState().isEmpty()
                ? 0.95 : Math.max(0.1, state.getFluidState().getOwnHeight());
        level.addParticle(dust,
                pos.getX() + 0.25 + random.nextDouble() * 0.5,
                pos.getY() + yTop,
                pos.getZ() + 0.25 + random.nextDouble() * 0.5,
                0.0, 0.0, 0.0);
    }

    /** Hook for a conductor's {@code neighborChanged}/{@code onPlace}. */
    public static void onNeighborChanged(final Level level, final BlockPos pos, final Block block) {
        if (level.isClientSide || recomputing) return;
        if (!isConductor(level.getBlockState(pos))) return;
        recomputeNetwork(level, pos, block);
    }

    /** Strongest external (non-conductor) signal feeding into a single cell. */
    private static int externalSignal(final Level level, final BlockPos pos) {
        int max = 0;
        for (final Direction d : Direction.values()) {
            final BlockPos np = pos.relative(d);
            if (isConductor(level.getBlockState(np))) continue; // network edges handled separately
            final int s = level.getSignal(np, d);
            if (s > max) max = s;
        }
        return max;
    }

    /**
     * Flood the connected conductor component from {@code start}, solve every
     * cell's conducted level from its external inputs (dust attenuation), write
     * back only the cells that changed, then notify their neighbours so adjacent
     * components (lamps, repeaters, …) re-read the fresh signal.
     */
    private static void recomputeNetwork(final Level level, final BlockPos start, final Block block) {
        recomputing = true;
        try {
            // 1. Collect the connected component + each cell's external input.
            final List<BlockPos> cells = new ArrayList<>();
            final Map<BlockPos, Integer> power = new HashMap<>();
            final Map<BlockPos, Integer> ext = new HashMap<>();
            final Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start.immutable());
            power.put(start.immutable(), 0);
            while (!queue.isEmpty() && cells.size() < MAX_NETWORK) {
                final BlockPos p = queue.poll();
                cells.add(p);
                ext.put(p, externalSignal(level, p));
                for (final Direction d : Direction.values()) {
                    final BlockPos np = p.relative(d).immutable();
                    if (power.containsKey(np)) continue;
                    if (isConductor(level.getBlockState(np))) {
                        power.put(np, 0);
                        queue.add(np);
                    }
                }
            }

            // 2. Solve: power[cell] = max(external[cell], max neighbour power - 1).
            //    Relax repeatedly; values are 0..15 so this converges quickly.
            for (final BlockPos p : cells) power.put(p, ext.get(p));
            boolean changed = true;
            while (changed) {
                changed = false;
                for (final BlockPos p : cells) {
                    int best = ext.get(p);
                    for (final Direction d : Direction.values()) {
                        final BlockPos np = p.relative(d);
                        final Integer pn = power.get(np);
                        if (pn != null && pn - 1 > best) best = pn - 1;
                    }
                    if (best > 15) best = 15;
                    if (best != power.get(p)) { power.put(p, best); changed = true; }
                }
            }

            // 3. Write changed cells (clients only; we notify neighbours ourselves).
            final List<BlockPos> written = new ArrayList<>();
            for (final BlockPos p : cells) {
                final BlockState s = level.getBlockState(p);
                if (!s.hasProperty(POWER)) continue;
                final int want = power.get(p);
                if (s.getValue(POWER) != want) {
                    level.setBlock(p, s.setValue(POWER, want), Block.UPDATE_CLIENTS);
                    written.add(p);
                }
            }
            // 4. Notify every component touching the network so it re-reads us.
            for (final BlockPos p : written) level.updateNeighborsAt(p, block);
        } finally {
            recomputing = false;
        }
    }
}
