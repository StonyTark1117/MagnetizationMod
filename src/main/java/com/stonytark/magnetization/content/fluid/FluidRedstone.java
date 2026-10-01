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

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the mod's conductive fluids carry a redstone signal like a body of
 * liquid redstone dust — ferrofluid, magnetized ferrofluid, MR fluid, and the
 * Hardened MR Fluid bridge block all conduct. Conductors are not power sources;
 * they relay an external signal across their connected span, attenuating one
 * level per cell exactly like dust. MR Fluid additionally uses the conducted
 * value to harden while powered, restoring its redstone-controlled bridge mode.
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

    private static final Direction[] DIRECTIONS = Direction.values();
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

    /** Read-only discovery snapshot. Discarded before another notification can
     * recompute; signal methods still receive the actual level and live BEs. */
    private static final class ReadCache {
        private final Level level;
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<BlockState> states =
                new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private ReadCache(final Level level) { this.level = level; }
        private BlockState state(final BlockPos pos) {
            final long key = pos.asLong();
            BlockState state = states.get(key);
            if (state == null) { state = level.getBlockState(pos); states.put(key, state); }
            return state;
        }
        // SignalGetter#getSignal, including NeoForge's weak-power hook. Reusing
        // block states avoids repeatedly looking up the same solid neighbors.
        private int signal(final BlockState state, final BlockPos pos, final Direction direction) {
            final int weak = state.getSignal(level, pos, direction);
            if (!state.shouldCheckWeakPower(level, pos, direction)) return weak;
            int direct = 0;
            for (final Direction d : DIRECTIONS) {
                final BlockPos neighbor = pos.relative(d);
                direct = Math.max(direct, state(neighbor).getDirectSignal(level, neighbor, d));
                if (direct >= 15) break;
            }
            return Math.max(weak, direct);
        }
        private int externalSignal(final BlockPos pos) {
            int max = 0;
            for (final Direction d : DIRECTIONS) {
                final BlockPos neighbor = pos.relative(d);
                final BlockState state = state(neighbor);
                if (!isConductor(state)) max = Math.max(max, signal(state, neighbor, d));
            }
            return max;
        }
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
            // Retain the original breadth-first discovery order and exact cap.
            // Queue entries beyond the cap have zero power, just as before.
            final it.unimi.dsi.fastutil.longs.LongArrayList queue = new it.unimi.dsi.fastutil.longs.LongArrayList();
            final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap indices = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
            indices.defaultReturnValue(-1);
            final List<BlockPos> cells = new ArrayList<>();
            final it.unimi.dsi.fastutil.ints.IntArrayList external = new it.unimi.dsi.fastutil.ints.IntArrayList();
            final it.unimi.dsi.fastutil.ints.IntArrayList edges = new it.unimi.dsi.fastutil.ints.IntArrayList();
            final ReadCache reads = new ReadCache(level);
            final long seed = start.asLong();
            queue.add(seed);
            indices.put(seed, 0);
            for (int cursor = 0; cursor < queue.size() && cursor < MAX_NETWORK; cursor++) {
                final BlockPos p = BlockPos.of(queue.getLong(cursor));
                cells.add(p);
                external.add(reads.externalSignal(p));
                for (final Direction d : DIRECTIONS) {
                    final BlockPos np = p.relative(d);
                    final long key = np.asLong();
                    int index = indices.get(key);
                    if (index < 0 && isConductor(reads.state(np))) {
                        index = queue.size();
                        indices.put(key, index);
                        queue.add(key);
                    }
                    edges.add(index);
                }
            }
            final int[] power = FluidSignalSolver.solve(external.toIntArray(), edges.toIntArray());

            // 3. Write changed cells (clients only; we notify neighbours ourselves).
            final List<BlockPos> written = new ArrayList<>();
            for (int index = 0; index < cells.size(); index++) {
                final BlockPos p = cells.get(index);
                final BlockState s = level.getBlockState(p);
                if (!s.hasProperty(POWER)) continue;
                final int want = power[index];
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
