package com.stonytark.magnetization.content.excavator;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Redstone-powered ferromagnetic mining block. The active face points in the
 * direction set by {@link DirectionalBlock#FACING}; defaults to DOWN, so the
 * block placed against a ceiling rips the floor below it.
 *
 * <p><b>Pull cycle.</b> While powered, scans the column along {@code FACING}
 * for the nearest block tagged {@code #magnetization:ferromagnetic_blocks}.
 * When found, every cell from offset 1 (adjacent to emitter) through the
 * target cell — ferromagnetic ore plus everything on top of it — is taken out
 * of the world and assembled into a single Sable sub-level via
 * {@link SubLevelAssemblyHelper#assembleBlocks}. The standard
 * {@link com.stonytark.magnetization.physics.FieldApplicator} then drags that
 * column-ship toward the emitter, since it sits in the same field as any other
 * Sable contraption. When the ship's centroid arrives within
 * {@link #ARRIVAL_RADIUS} of the emitter, we capture the original block states,
 * drop them as ItemEntities at the emitter, and remove the sub-level.
 *
 * <p><b>One in-flight pull at a time.</b> {@link #activePullShipId} guards
 * against starting a new cycle while the previous column is still en-route —
 * otherwise excavators on STRONG/EXTREME tiers would carpet the world with
 * sub-levels.
 *
 * <p><b>Safety.</b> The scan stops at any block with a BlockEntity (chests,
 * beacons, other emitters) or with negative hardness (bedrock-class). The
 * column refuses assembly if a barrier sits in the path.
 *
 * <p><b>Contraption-mounted excavators</b> ({@code host != null} in
 * {@code tickEmitter}) skip the pull pathway — Sable assembly is a world-level
 * operation and the ship-on-ship semantics aren't worth the complexity here.
 */
public class MagneticExcavatorBlockEntity extends AbstractEmitterBlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/Excavator");

    /** Distance threshold for "the column-ship has arrived at the emitter" — once
     *  the ship's logical pose origin gets this close, we dismantle and drop. */
    private static final double ARRIVAL_RADIUS = 1.5d;

    /** Long.MIN_VALUE-style sentinel: "never pulled before". */
    private long lastPullTick = Long.MIN_VALUE;

    /** UUID of the in-flight column-ship, or null if no pull is active. */
    private @Nullable UUID activePullShipId = null;

    /** Original BlockStates of the column we ripped out, kept around so we can
     *  drop them as items when the ship arrives at the emitter. Order matches
     *  the cells from offset 1 (adjacent to emitter) through the deepest. */
    private List<BlockState> pendingDrops = new ArrayList<>();

    public MagneticExcavatorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_EXCAVATOR.get(), pos, state);
    }

    /** Per-tier cycle interval in ticks. EXTREME mines four times faster than WEAK.
     *  Cycle interval gates *starting* a new pull — once a pull is in flight, we
     *  wait for it to complete regardless of this. */
    private static int cycleIntervalFor(final MagneticStrength tier) {
        return switch (tier) {
            case WEAK     -> 40;
            case MEDIUM   -> 20;
            case STRONG   -> 10;
            case EXTREME  -> 5;
            case NONE     -> Integer.MAX_VALUE;
        };
    }

    /** Hard cap on how many cells one pull cycle may rip out, regardless of
     *  range. Acts as a config-typo backstop. */
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
        // Directional field along the active face. The standard FieldApplicator
        // pulls Sable ships along this axis; that's the same force the column-
        // ship will feel after we assemble it.
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
        if (!isPowered() || host != null) return;

        // First, drive the in-flight column-ship if one exists.
        if (activePullShipId != null) {
            tickActiveShip(server);
            return; // don't start a new cycle while one is already going
        }

        // Otherwise, gate "start a new cycle" on the tier's cadence.
        final MagneticStrength tier = effectiveStrength(MagneticStrength.MEDIUM);
        final long tick = server.getGameTime();
        if (tick - lastPullTick < cycleIntervalFor(tier)) return;
        lastPullTick = tick;
        startPullCycle(server, state, tier);
    }

    /** Track the in-flight column-ship: if it's gone, clear state; if it's
     *  arrived at the emitter, drop the pending blocks and remove it. */
    private void tickActiveShip(final ServerLevel server) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) {
            // Should never happen on a ServerLevel, but defensive.
            activePullShipId = null;
            pendingDrops.clear();
            setChanged();
            return;
        }
        final dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(activePullShipId);
        if (!(sub instanceof ServerSubLevel ship) || ship.getMassTracker().isInvalid()) {
            // Ship vanished (player /shatter, unload, etc.) — drop pending drops at
            // the emitter as a recovery so the player isn't out resources.
            dropPendingAtEmitter(server);
            activePullShipId = null;
            setChanged();
            return;
        }
        final Vec3 emitterCenter = Vec3.atCenterOf(getBlockPos());
        final org.joml.Vector3dc shipPos = ship.logicalPose().position();
        final double dx = emitterCenter.x - shipPos.x();
        final double dy = emitterCenter.y - shipPos.y();
        final double dz = emitterCenter.z - shipPos.z();
        final double dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 <= ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
            dropPendingAtEmitter(server);
            container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
            activePullShipId = null;
            setChanged();
        }
    }

    /** Drop every pending BlockState as an ItemEntity at the cell adjacent to
     *  the emitter (the same spot {@link com.stonytark.magnetization.physics.InventorySink}
     *  watches), then clear the queue. */
    private void dropPendingAtEmitter(final ServerLevel server) {
        if (pendingDrops.isEmpty()) return;
        final Direction facing = getBlockState().getValue(DirectionalBlock.FACING);
        final BlockPos drop = getBlockPos().relative(facing, 1);
        for (final BlockState bs : pendingDrops) {
            Block.dropResources(bs, server, drop);
        }
        pendingDrops.clear();
        server.playSound(null, drop, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.6f, 1.2f);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                drop.getX() + 0.5, drop.getY() + 0.5, drop.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.1);
    }

    private void startPullCycle(final ServerLevel level, final BlockState state, final MagneticStrength tier) {
        final Direction facing = state.getValue(DirectionalBlock.FACING);
        final int rangeBlocks = (int) Math.min(effectiveRange(tier), maxBlocksPerCycle());

        // Find the nearest ferromagnetic block in the column. Stop at any
        // unbreakable / block-entity-bearing cell so we never violate them.
        int targetOffset = -1;
        for (int i = 0; i < rangeBlocks; i++) {
            final BlockPos pos = getBlockPos().relative(facing, i + 1);
            final BlockState bs = level.getBlockState(pos);
            if (bs.isAir()) continue;
            if (isBarrier(level, pos, bs)) return;
            if (bs.is(MagTags.FERROMAGNETIC_BLOCKS)) { targetOffset = i; break; }
        }
        if (targetOffset < 0) return;

        // Snapshot every non-air cell from offset 0 (adjacent) through the target.
        // The column-ship will contain all of them; on arrival we drop their
        // original states as items.
        final List<BlockPos> columnPositions = new ArrayList<>();
        final List<BlockState> columnStates = new ArrayList<>();
        for (int i = 0; i <= targetOffset; i++) {
            final BlockPos pos = getBlockPos().relative(facing, i + 1);
            final BlockState bs = level.getBlockState(pos);
            if (bs.isAir()) continue;
            if (i < targetOffset && isBarrier(level, pos, bs)) return;
            columnPositions.add(pos);
            columnStates.add(bs);
        }
        if (columnPositions.isEmpty()) return;

        // Build the Sable assembly bounds — the column's bbox + 1-block padding.
        final BlockPos anchor = columnPositions.get(columnPositions.size() - 1); // deepest = ferro ore
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos p : columnPositions) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        final BoundingBox3i bounds = new BoundingBox3i(
                minX - 1, minY - 1, minZ - 1,
                maxX + 1, maxY + 1, maxZ + 1);

        try {
            final ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(level, anchor, columnPositions, bounds);
            if (ship.getMassTracker().isInvalid()) {
                // Restore world state if assembly failed (overlap with another ship, etc.).
                for (int i = 0; i < columnPositions.size(); i++) {
                    level.setBlock(columnPositions.get(i), columnStates.get(i), Block.UPDATE_ALL);
                }
                return;
            }
            activePullShipId = ship.getUniqueId();
            pendingDrops = columnStates;
            // FX: scrape sound at the deepest cell + a trail of crit-style sparks
            // along the column to telegraph "this was just ripped out".
            level.playSound(null, anchor, SoundEvents.NETHERITE_BLOCK_BREAK,
                    SoundSource.BLOCKS, 0.7f, 0.5f);
            level.playSound(null, getBlockPos(), SoundEvents.LODESTONE_PLACE,
                    SoundSource.BLOCKS, 0.4f, 1.6f);
            for (final BlockPos p : columnPositions) {
                level.sendParticles(ParticleTypes.CRIT,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                        4, 0.2, 0.2, 0.2, 0.05);
            }
            setChanged();
        } catch (final Throwable t) {
            LOG.error("Excavator assembly failed at {}", getBlockPos().toShortString(), t);
            // World state has already been mutated by Sable's moveBlocks; we can't
            // cleanly restore. Drop the captured states as items at the emitter so
            // the player isn't completely out of resources.
            for (final BlockState bs : columnStates) {
                Block.dropResources(bs, level, getBlockPos().relative(facing, 1));
            }
        }
    }

    /** Scan-stopper: refuse to pull through a block entity (chests, beacons,
     *  other emitters), an unbreakable block (bedrock-class), or anything
     *  explicitly tagged {@code #magnetization:excavator_immune}. */
    private static boolean isBarrier(final ServerLevel level, final BlockPos pos, final BlockState state) {
        if (state.hasBlockEntity()) return true;
        if (state.is(MagTags.EXCAVATOR_IMMUNE)) return true;
        return state.getDestroySpeed(level, pos) < 0;
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("LastPullTick", lastPullTick);
        if (activePullShipId != null) tag.putUUID("ActiveShip", activePullShipId);
        if (!pendingDrops.isEmpty()) {
            final ListTag drops = new ListTag();
            for (final BlockState bs : pendingDrops) {
                drops.add(NbtUtils.writeBlockState(bs));
            }
            tag.put("PendingDrops", drops);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastPullTick = tag.contains("LastPullTick") ? tag.getLong("LastPullTick") : Long.MIN_VALUE;
        activePullShipId = tag.hasUUID("ActiveShip") ? tag.getUUID("ActiveShip") : null;
        pendingDrops = new ArrayList<>();
        if (tag.contains("PendingDrops", Tag.TAG_LIST)) {
            final ListTag list = tag.getList("PendingDrops", Tag.TAG_COMPOUND);
            final var lookup = level == null
                    ? net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty()).lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK)
                    : level.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
            for (int i = 0; i < list.size(); i++) {
                pendingDrops.add(NbtUtils.readBlockState(lookup, list.getCompound(i)));
            }
        }
    }
}
