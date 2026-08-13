package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import com.stonytark.magnetization.content.gas.GasVentBlockEntity;
import com.stonytark.magnetization.content.gas.ProxyGasCloudBlockEntity;
import com.stonytark.magnetization.physics.PerformanceDiagnostics;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Bounded, loaded-chunk-only flood fill for connected-gas excitation. */
public final class GasExcitation {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ServerLevel, Long2LongOpenHashMap> LAST_GRACE_STEP =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, TickCache> PROCESSED_COMPONENTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private GasExcitation() {}

    /**
     * Invalidate same-tick component memoization after a topology, redstone, or
     * exciter-state mutation. Clearing the level-local set is deliberately coarse:
     * those mutations are rare compared with the many scheduled gas ticks they
     * collapse, and a full clear makes merges and splits unambiguously correct.
     */
    public static void invalidate(final ServerLevel level) {
        final TickCache cache = PROCESSED_COMPONENTS.get(level);
        if (cache != null && cache.gameTime == level.getGameTime()) cache.processed.clear();
    }

    /** Drop all level-scoped state when a dimension unloads. */
    public static void onLevelUnload(final ServerLevel level) {
        PROCESSED_COMPONENTS.remove(level);
        LAST_GRACE_STEP.remove(level);
    }

    public static void recompute(final ServerLevel level, final BlockPos seed) {
        final Fluid gas = fluidAt(level, seed);
        if (gas == Fluids.EMPTY) return;

        final TickCache tickCache = tickCache(level);
        final long seedKey = seed.asLong();
        if (tickCache.processed.contains(seedKey)) {
            PerformanceDiagnostics.recordGasRecompute(level, 0, true);
            return;
        }

        final int cap = Math.max(1, MagConfig.gasExcitationMaxCells());
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet seen = new LongOpenHashSet(Math.min(cap, 4096));
        final LongArrayList cells = new LongArrayList(Math.min(cap, 4096));
        final LongOpenHashSet exciterPositions = new LongOpenHashSet();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        boolean redstone = false;
        queue.enqueue(seedKey);

        while (!queue.isEmpty() && cells.size() < cap) {
            final long packed = queue.dequeueLong();
            if (!seen.add(packed)) continue;
            pos.set(packed);
            if (!level.hasChunkAt(pos) || fluidAt(level, pos) != gas) continue;
            cells.add(packed);
            redstone |= level.hasNeighborSignal(pos);
            for (final Direction direction : DIRECTIONS) {
                next.setWithOffset(pos, direction);
                if (!level.hasChunkAt(next)) continue;
                final Fluid adjacentFluid = fluidAt(level, next);
                if (adjacentFluid == gas) {
                    queue.enqueue(next.asLong());
                    continue;
                }
                final BlockEntity adjacent = level.getBlockEntity(next);
                if (adjacent instanceof GasExciterBlockEntity exciter && exciter.canExcite()) {
                    exciterPositions.add(next.asLong());
                } else if (adjacent instanceof GasVentBlockEntity vent
                        && vent.outputPos().equals(pos)
                        && level.getBlockEntity(vent.attachedExciterPos()) instanceof GasExciterBlockEntity exciter
                        && exciter.canExcite()) {
                    exciterPositions.add(vent.attachedExciterPos().asLong());
                }
            }
        }

        // Mark only the visited portion. If a configured cap truncates a larger
        // component, a scheduled seed outside this portion still gets its own
        // bounded pass instead of being incorrectly suppressed.
        tickCache.processed.addAll(cells);
        PerformanceDiagnostics.recordGasRecompute(level, cells.size(), false);

        boolean excited = redstone;
        if (!excited && !exciterPositions.isEmpty()) {
            long ownerKey = Long.MAX_VALUE;
            for (final long candidate : exciterPositions) ownerKey = Math.min(ownerKey, candidate);
            pos.set(ownerKey);
            if (level.getBlockEntity(pos) instanceof GasExciterBlockEntity exciter) {
                excited = exciter.consumeForTick(level.getGameTime());
            }
        }

        int grace = 0;
        long networkKey = seedKey;
        boolean firstCell = true;
        for (final long packed : cells) {
            if (firstCell || packed < networkKey) {
                networkKey = packed;
                firstCell = false;
            }
            pos.set(packed);
            if (!excited) {
                final BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof ExcitableGasBlock) {
                    grace = Math.max(grace, state.getValue(ExcitableGasBlock.EXCITATION_GRACE));
                } else if (level.getBlockEntity(pos) instanceof ProxyGasCloudBlockEntity cloud) {
                    grace = Math.max(grace, cloud.grace());
                }
            }
        }

        if (!excited && grace > 0) {
            final Long2LongOpenHashMap levelSteps = LAST_GRACE_STEP.computeIfAbsent(level, ignored -> {
                final Long2LongOpenHashMap steps = new Long2LongOpenHashMap();
                steps.defaultReturnValue(Long.MIN_VALUE);
                return steps;
            });
            final long lastStep = levelSteps.put(networkKey, level.getGameTime());
            if (lastStep == level.getGameTime()) {
                scheduleNativeTick(level, seed);
                return;
            }
        } else if (excited || grace == 0) {
            final Long2LongOpenHashMap levelSteps = LAST_GRACE_STEP.get(level);
            if (levelSteps != null) levelSteps.remove(networkKey);
        }

        final boolean visuallyExcited = excited || grace > 0;
        final int nextGrace = excited ? 3 : Math.max(0, grace - 1);
        for (final long packed : cells) {
            pos.set(packed);
            final BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ExcitableGasBlock
                    && (state.getValue(ExcitableGasBlock.EXCITED) != visuallyExcited
                    || state.getValue(ExcitableGasBlock.EXCITATION_GRACE) != nextGrace)) {
                level.setBlock(pos, state.setValue(ExcitableGasBlock.EXCITED, visuallyExcited)
                        .setValue(ExcitableGasBlock.EXCITATION_GRACE, nextGrace), Block.UPDATE_CLIENTS);
            } else if (level.getBlockEntity(pos) instanceof ProxyGasCloudBlockEntity cloud
                    && (cloud.isExcited() != visuallyExcited || cloud.grace() != nextGrace)) {
                cloud.setExcitation(visuallyExcited, nextGrace);
            }
        }
        if (!excited && grace > 0) scheduleNativeTick(level, seed);
    }

    /** Canonical gas identity shared by native fluids and compatibility proxy cells. */
    public static Fluid fluidAt(final ServerLevel level, final BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ProxyGasCloudBlockEntity cloud) return cloud.fluid();
        if (!(level.getBlockState(pos).getBlock() instanceof ExcitableGasBlock)) return Fluids.EMPTY;
        final Fluid fluid = level.getFluidState(pos).getType();
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static TickCache tickCache(final ServerLevel level) {
        final TickCache cache = PROCESSED_COMPONENTS.computeIfAbsent(level, ignored -> new TickCache());
        final long now = level.getGameTime();
        if (cache.gameTime != now) {
            cache.gameTime = now;
            cache.processed.clear();
        }
        return cache;
    }

    private static void scheduleNativeTick(final ServerLevel level, final BlockPos seed) {
        final BlockState state = level.getBlockState(seed);
        if (state.getBlock() instanceof ExcitableGasBlock gasBlock) {
            // scheduleTick de-duplicates equivalent pending entries. Do not use
            // hasScheduledTick here: while the current block tick is executing,
            // some schedulers still report it as pending and would otherwise
            // suppress the next grace-decay step.
            level.scheduleTick(seed, gasBlock, 1);
        }
    }

    private static final class TickCache {
        private long gameTime = Long.MIN_VALUE;
        private final LongOpenHashSet processed = new LongOpenHashSet();
    }
}
