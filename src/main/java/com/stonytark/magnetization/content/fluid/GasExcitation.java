package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.content.gas.GasExciterBlockEntity;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
        final BlockState seedState = level.getBlockState(seed);
        if (!(seedState.getBlock() instanceof ExcitableGasBlock gasBlock)) return;

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
            if (state.getBlock() != gasBlock) continue;
            cells.add(pos);
            redstone |= level.hasNeighborSignal(pos);
            for (final Direction direction : Direction.values()) {
                final BlockPos next = pos.relative(direction);
                if (!level.hasChunkAt(next)) continue;
                final BlockState nextState = level.getBlockState(next);
                if (nextState.getBlock() == gasBlock) queue.addLast(next.immutable());
                else if (level.getBlockEntity(next) instanceof GasExciterBlockEntity exciter
                        && exciter.canExcite()) exciterPositions.add(next.immutable());
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
                if (state.getBlock() == gasBlock) grace = Math.max(grace, state.getValue(ExcitableGasBlock.EXCITATION_GRACE));
            }
        }
        final BlockPos networkKey = cells.stream().min(Comparator.comparingLong(BlockPos::asLong)).orElse(seed);
        if (!excited && grace > 0) {
            final java.util.Map<BlockPos, Long> levelSteps = LAST_GRACE_STEP.computeIfAbsent(level,
                    ignored -> new java.util.HashMap<>());
            final Long lastStep = levelSteps.put(networkKey, level.getGameTime());
            if (lastStep != null && lastStep == level.getGameTime()) {
                if (!level.getBlockTicks().hasScheduledTick(seed, gasBlock)) level.scheduleTick(seed, gasBlock, 1);
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
            if (state.getBlock() == gasBlock && (state.getValue(ExcitableGasBlock.EXCITED) != visuallyExcited
                    || state.getValue(ExcitableGasBlock.EXCITATION_GRACE) != nextGrace)) {
                level.setBlock(pos, state.setValue(ExcitableGasBlock.EXCITED, visuallyExcited)
                        .setValue(ExcitableGasBlock.EXCITATION_GRACE, nextGrace), Block.UPDATE_CLIENTS);
            }
        }
        if (!excited && grace > 0 && !level.getBlockTicks().hasScheduledTick(seed, gasBlock)) {
            level.scheduleTick(seed, gasBlock, 1);
        }
    }
}
