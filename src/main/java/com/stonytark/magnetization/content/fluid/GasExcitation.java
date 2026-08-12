package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import com.stonytark.magnetization.content.gas.GasVentBlockEntity;
import com.stonytark.magnetization.content.gas.ProxyGasCloudBlockEntity;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, loaded-chunk-only flood fill for connected-gas excitation. */
public final class GasExcitation {
    private static final java.util.Map<ServerLevel, java.util.Map<BlockPos, Long>> LAST_GRACE_STEP =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private GasExcitation() {}

    public static void recompute(final ServerLevel level, final BlockPos seed) {
        final Fluid gas = fluidAt(level, seed);
        if (gas == Fluids.EMPTY) return;

        final int cap = Math.max(1, MagConfig.gasExcitationMaxCells());
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        final List<BlockPos> cells = new ArrayList<>();
        final Set<BlockPos> exciterPositions = new HashSet<>();
        boolean redstone = false;
        queue.add(seed.immutable());

        while (!queue.isEmpty() && cells.size() < cap) {
            final BlockPos pos = queue.removeFirst();
            if (!seen.add(pos) || !level.hasChunkAt(pos)) continue;
            final BlockState state = level.getBlockState(pos);
            if (fluidAt(level, pos) != gas) continue;
            cells.add(pos);
            redstone |= level.hasNeighborSignal(pos);
            for (final Direction direction : Direction.values()) {
                final BlockPos next = pos.relative(direction);
                if (!level.hasChunkAt(next)) continue;
                if (fluidAt(level, next) == gas) queue.addLast(next.immutable());
                else if (level.getBlockEntity(next) instanceof GasExciterBlockEntity exciter
                        && exciter.canExcite()) exciterPositions.add(next.immutable());
                else if (level.getBlockEntity(next) instanceof GasVentBlockEntity vent
                        && vent.outputPos().equals(pos)
                        && level.getBlockEntity(vent.attachedExciterPos()) instanceof GasExciterBlockEntity exciter
                        && exciter.canExcite()) exciterPositions.add(vent.attachedExciterPos().immutable());
            }
        }

        boolean excited = redstone;
        if (!excited && !exciterPositions.isEmpty()) {
            final BlockPos owner = exciterPositions.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
            if (level.getBlockEntity(owner) instanceof GasExciterBlockEntity exciter) {
                excited = exciter.consumeForTick(level.getGameTime());
            }
        }

        int grace = 0;
        if (!excited) {
            for (final BlockPos pos : cells) {
                final BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof ExcitableGasBlock) {
                    grace = Math.max(grace, state.getValue(ExcitableGasBlock.EXCITATION_GRACE));
                } else if (level.getBlockEntity(pos) instanceof ProxyGasCloudBlockEntity cloud) {
                    grace = Math.max(grace, cloud.grace());
                }
            }
        }
        final BlockPos networkKey = cells.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(seed);
        if (!excited && grace > 0) {
            final java.util.Map<BlockPos, Long> levelSteps = LAST_GRACE_STEP.computeIfAbsent(level,
                    ignored -> new java.util.HashMap<>());
            final Long lastStep = levelSteps.put(networkKey, level.getGameTime());
            if (lastStep != null && lastStep == level.getGameTime()) {
                scheduleNativeTick(level, seed);
                return;
            }
        } else if (excited || grace == 0) {
            final java.util.Map<BlockPos, Long> levelSteps = LAST_GRACE_STEP.get(level);
            if (levelSteps != null) levelSteps.remove(networkKey);
        }
        final boolean visuallyExcited = excited || grace > 0;
        final int nextGrace = excited ? 3 : Math.max(0, grace - 1);
        for (final BlockPos pos : cells) {
            final BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ExcitableGasBlock && (state.getValue(ExcitableGasBlock.EXCITED) != visuallyExcited
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
}
