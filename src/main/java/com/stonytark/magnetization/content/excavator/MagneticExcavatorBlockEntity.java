package com.stonytark.magnetization.content.excavator;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Redstone-powered ferromagnetic mining block. Each pull cycle, scans the column
 * along the opposite of {@link DirectionalBlock#FACING} for the nearest block
 * tagged {@code #magnetization:ferromagnetic_blocks}; if found, every cell from
 * the target up to the block adjacent to the emitter shifts one cell toward the
 * emitter, and the cell already adjacent to the emitter pops as an ItemEntity
 * (which the standard {@link com.stonytark.magnetization.physics.InventorySink}
 * vacuums into a connected hopper / ITEM_HANDLER capability if any).
 *
 * <p>Default placement points the active face down — the player puts the block
 * on a ceiling and rips ores up out of the floor. Wrenching rotates the active
 * face to any of the six directions.
 *
 * <p>The cycle interval ladders with the strength tier (so STRONG mines twice
 * as fast as MEDIUM); the column reach uses {@link AbstractEmitterBlockEntity#effectiveRange}
 * exactly like the other emitters' GUI sliders.
 *
 * <p>Safety: blocks with a BlockEntity (chests, beacons, other emitters) and
 * blocks with negative hardness (bedrock, end portal frame, etc.) are skipped —
 * the scan stops at them rather than tearing through. Air cells are walked
 * through transparently.
 */
public class MagneticExcavatorBlockEntity extends AbstractEmitterBlockEntity {

    /** Long.MIN_VALUE-style sentinel: "never pulled before". */
    private long lastPullTick = Long.MIN_VALUE;

    public MagneticExcavatorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_EXCAVATOR.get(), pos, state);
    }

    /** Per-tier cycle interval in ticks. EXTREME mines four times faster than WEAK. */
    private static int cycleIntervalFor(final MagneticStrength tier) {
        return switch (tier) {
            case WEAK     -> 40;
            case MEDIUM   -> 20;
            case STRONG   -> 10;
            case EXTREME  -> 5;
            case NONE     -> Integer.MAX_VALUE;
        };
    }

    /** Hard cap on how many cells one pull cycle may scan/move regardless of
     *  strength tier — protects against config typos and runaway server loads. */
    private static int maxBlocksPerCycle() {
        try { return MagConfig.EXCAVATOR_MAX_BLOCKS_PER_CYCLE.get(); }
        catch (final Throwable t) { return 32; }
    }

    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        if (!isPowered()) return null;
        final Direction facing = state.getValue(DirectionalBlock.FACING);
        final MagneticStrength tier = effectiveStrength(MagneticStrength.MEDIUM);
        final double range = effectiveRange(tier);
        // The visible field is directional along the active face — same shape
        // as the tractor beam — so dropped items in the column experience the
        // standard FieldApplicator pull as a bonus. The shifted-cell mining is
        // a separate pathway invoked from tickEmitter below.
        return new MagneticField(
                Vec3.atCenterOf(getBlockPos()),
                Vec3.atLowerCornerOf(facing.getNormal()),
                effectivePolarity(MagneticPolarity.SOUTH),
                tier,
                MagneticField.Shape.DIRECTIONAL,
                range == tier.range() ? 0.0d : range
        );
    }

    @Override
    protected void tickEmitter(final ServerLevel server, final BlockState state,
                               final @Nullable dev.ryanhcode.sable.sublevel.ServerSubLevel host) {
        super.tickEmitter(server, state, host);
        if (!isPowered() || host != null) return; // mining only runs in the open world
        final MagneticStrength tier = effectiveStrength(MagneticStrength.MEDIUM);
        final long tick = server.getGameTime();
        if (tick - lastPullTick < cycleIntervalFor(tier)) return;
        lastPullTick = tick;
        performPullCycle(server, state, tier);
    }

    private void performPullCycle(final ServerLevel level, final BlockState state, final MagneticStrength tier) {
        final Direction facing = state.getValue(DirectionalBlock.FACING);
        // -FACING is the scan direction (away from the active face); blocks travel
        // back along +scanDir == -(-FACING) == FACING toward the emitter.
        final Direction scanDir = facing;
        final int rangeBlocks = (int) Math.min(effectiveRange(tier), maxBlocksPerCycle());

        // Find the nearest ferromagnetic block in the column. Stop the scan at
        // unbreakable / block-entity-bearing cells so we never violate them.
        int targetOffset = -1;
        for (int i = 0; i < rangeBlocks; i++) {
            final BlockPos pos = getBlockPos().relative(scanDir, i + 1);
            final BlockState bs = level.getBlockState(pos);
            if (bs.isAir()) continue;
            if (isBarrier(level, pos, bs)) return;
            if (bs.is(MagTags.FERROMAGNETIC_BLOCKS)) { targetOffset = i; break; }
        }
        if (targetOffset < 0) return;

        // Snapshot every cell from offset 0 (adjacent to emitter) through targetOffset
        // before any writes — the column shifts in place and we'd re-read overwritten
        // cells otherwise. Refuse the cycle if any cell on the path would be a
        // barrier (block entity or unbreakable in between).
        final BlockState[] path = new BlockState[targetOffset + 1];
        for (int i = 0; i <= targetOffset; i++) {
            final BlockPos pos = getBlockPos().relative(scanDir, i + 1);
            path[i] = level.getBlockState(pos);
            if (i < targetOffset && !path[i].isAir() && isBarrier(level, pos, path[i])) return;
        }

        // Pop the cell already adjacent to the emitter. If it's air there's nothing
        // to drop — the column simply slides into the freed space.
        final BlockPos adjacent = getBlockPos().relative(scanDir, 1);
        if (!path[0].isAir()) {
            Block.dropResources(path[0], level, adjacent);
            level.levelEvent(2001, adjacent, Block.getId(path[0]));
        }
        // Shift each cell from offset 1..targetOffset down to 0..(targetOffset-1).
        for (int i = 0; i < targetOffset; i++) {
            final BlockPos dest = getBlockPos().relative(scanDir, i + 1);
            level.setBlock(dest, path[i + 1], Block.UPDATE_ALL);
        }
        // The deepest cell (where the ore was) becomes air.
        level.setBlock(getBlockPos().relative(scanDir, targetOffset + 1),
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        setChanged();
    }

    /** Scan-stopper: we refuse to pull through a block entity (chests, beacons,
     *  other emitters) or an unbreakable block (bedrock-class). */
    private static boolean isBarrier(final ServerLevel level, final BlockPos pos, final BlockState state) {
        if (state.hasBlockEntity()) return true;
        return state.getDestroySpeed(level, pos) < 0;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("LastPullTick", lastPullTick);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastPullTick = tag.contains("LastPullTick") ? tag.getLong("LastPullTick") : Long.MIN_VALUE;
    }
}
