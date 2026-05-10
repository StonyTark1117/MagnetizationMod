package com.stonytark.magnetization.content.excavator;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.InventorySink;
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
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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

    /** Hard ceiling on how long a single pull cycle can run before we
     *  force-dismantle. Covers chunk-unload + Sable-edge-case scenarios where
     *  the ship can't move toward the emitter and would otherwise block all
     *  future pulls forever. 30 seconds at 20 tps. */
    private static final long PULL_TIMEOUT_TICKS = 600L;

    /** Long.MIN_VALUE-style sentinel: "never pulled before". */
    private long lastPullTick = Long.MIN_VALUE;
    /** Tick when the in-flight pull cycle started. Long.MIN_VALUE = no pull. */
    private long pullStartTick = Long.MIN_VALUE;

    /** UUID of the in-flight column-ship, or null if no pull is active. */
    private @Nullable UUID activePullShipId = null;

    /** Original BlockStates of the column we ripped out, kept around so we can
     *  drop them as items when the ship arrives at the emitter. Order matches
     *  the cells from offset 1 (adjacent to emitter) through the deepest. */
    private List<BlockState> pendingDrops = new ArrayList<>();

    /** Single-slot container holding an enchanted tool / book that influences
     *  the column's drops — Fortune multiplies, Silk Touch silk-mines, etc.
     *  Persists across reloads via NBT and dumps to the ground on block break.
     *  The slot is exposed via the GUI's {@code CAP_TOOL_SLOT} bit. */
    private final SimpleContainer toolSlot = new SimpleContainer(1) {
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {
            super.setChanged();
            MagneticExcavatorBlockEntity.this.setChanged();
        }
    };

    public Container getToolSlot() {
        return toolSlot;
    }

    /** 0..100 percent for the GUI progress bar. While a pull is in flight the
     *  value reflects ship-arrival progress (0 = just spawned, 100 = at the
     *  emitter). When idle, the value reflects cycle cooldown progress
     *  (0 = just fired, 100 = ready to fire). Returns 0 when not powered. */
    public int getPullProgressPct() {
        if (!isPowered()) return 0;
        if (level == null) return 0;
        if (activePullShipId != null) {
            if (!(level instanceof ServerLevel server)) return 0;
            final SubLevelContainer container = SubLevelContainer.getContainer(server);
            if (container == null) return 0;
            final dev.ryanhcode.sable.sublevel.SubLevel sub = container.getSubLevel(activePullShipId);
            if (!(sub instanceof ServerSubLevel ship)) return 0;
            final Vec3 emitterCenter = Vec3.atCenterOf(getBlockPos());
            final org.joml.Vector3dc shipPos = ship.logicalPose().position();
            final double dx = emitterCenter.x - shipPos.x();
            final double dy = emitterCenter.y - shipPos.y();
            final double dz = emitterCenter.z - shipPos.z();
            final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // Map [range..0] → [0..100]. Saturate at the ends.
            final double range = effectiveRange(effectiveStrength(MagneticStrength.MEDIUM));
            if (range <= 0.5) return 100;
            final double progress = 1.0 - Math.min(1.0, dist / range);
            return (int) Math.round(Math.max(0, Math.min(1.0, progress)) * 100);
        }
        if (lastPullTick == Long.MIN_VALUE) return 100; // never fired = ready
        final long now = level.getGameTime();
        final int interval = cycleIntervalFor(effectiveStrength(MagneticStrength.MEDIUM));
        if (interval <= 0) return 100;
        final long elapsed = now - lastPullTick;
        return (int) Math.round(Math.max(0, Math.min(1.0, (double) elapsed / interval)) * 100);
    }

    /** Drop the tool slot's contents into the world. Called from the block when
     *  the player breaks the excavator so the player doesn't lose their book. */
    public void dropToolSlot(final Level level, final BlockPos pos) {
        Containers.dropContents(level, pos, toolSlot);
    }

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
     *  arrived at the emitter, drop the pending blocks and remove it. Also
     *  enforces {@link #PULL_TIMEOUT_TICKS} — if the ship can't move toward
     *  the emitter for any reason (chunk unload, Sable edge case), the pull
     *  is force-dismantled so the excavator isn't blocked forever. */
    private void tickActiveShip(final ServerLevel server) {
        // Force-dismantle on timeout. Drop pending at emitter as recovery.
        if (pullStartTick != Long.MIN_VALUE
                && server.getGameTime() - pullStartTick > PULL_TIMEOUT_TICKS) {
            LOG.warn("Excavator at {} hit pull timeout; force-dismantling ship {}",
                    getBlockPos().toShortString(), activePullShipId);
            final SubLevelContainer cont = SubLevelContainer.getContainer(server);
            if (cont != null) {
                final var sub = cont.getSubLevel(activePullShipId);
                if (sub instanceof ServerSubLevel ship) {
                    cont.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                }
            }
            dropPendingAtEmitter(server);
            activePullShipId = null;
            pullStartTick = Long.MIN_VALUE;
            setChanged();
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) {
            // Should never happen on a ServerLevel, but defensive.
            activePullShipId = null;
            pullStartTick = Long.MIN_VALUE;
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
            pullStartTick = Long.MIN_VALUE;
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
            pullStartTick = Long.MIN_VALUE;
            setChanged();
        }
    }

    /** Drop every pending BlockState's loot at the cell adjacent to the emitter.
     *  Tries to push each stack directly into an adjacent inventory (hopper /
     *  chest / barrel / etc.) first, falling back to spawning ItemEntities that
     *  the polling {@link InventorySink} would otherwise pick up next tick.
     *
     *  <p>If the BE's tool slot has an enchanted item or book in it, its
     *  enchantments are passed to the loot context — Fortune multiplies ore
     *  drops, Silk Touch silk-mines, etc. Damageable tools take 1 durability
     *  per pull cycle (see {@link #damageToolSlot}); books are immune. */
    private void dropPendingAtEmitter(final ServerLevel server) {
        if (pendingDrops.isEmpty()) return;
        final Direction facing = getBlockState().getValue(DirectionalBlock.FACING);
        final BlockPos drop = getBlockPos().relative(facing, 1);
        final ItemStack tool = toolSlot.getItem(0);
        for (final BlockState bs : pendingDrops) {
            final List<ItemStack> drops = enchantedDropsFor(bs, server, drop, tool);
            for (final ItemStack stack : drops) {
                final ItemStack remainder =
                        InventorySink.tryDirectIngest(server, getBlockPos(), stack);
                if (!remainder.isEmpty()) {
                    Block.popResource(server, drop, remainder);
                }
            }
        }
        pendingDrops.clear();
        server.playSound(null, drop, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.6f, 1.2f);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                drop.getX() + 0.5, drop.getY() + 0.5, drop.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.1);
    }

    /** Compute a block's drop list using the loot context, threading through
     *  {@code tool}'s enchantments if any are present. When tool is empty we
     *  fall back to the simpler {@code Block.getDrops(state, level, pos, null)}
     *  path that was used before.
     *
     *  <p>Special case: enchanted books store enchantments in
     *  {@link net.minecraft.core.component.DataComponents#STORED_ENCHANTMENTS}
     *  rather than {@code ENCHANTMENTS}, so the loot system would read zero
     *  enchantments off them. To make Fortune-on-a-book work, we synthesize a
     *  stand-in ItemStack with those enchantments active — a netherite pickaxe
     *  has the right tool/mining tag for ore drops, and the loot context only
     *  reads enchantments off the stack so the item type doesn't otherwise
     *  matter for drop resolution. */
    private static List<ItemStack> enchantedDropsFor(
            final BlockState state, final ServerLevel server, final BlockPos pos,
            final ItemStack tool
    ) {
        if (tool.isEmpty()) return Block.getDrops(state, server, pos, null);
        final ItemStack effectiveTool = translateBookEnchantments(tool);
        final LootParams.Builder params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, effectiveTool)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, null);
        return state.getDrops(params);
    }

    /** If {@code tool} is an enchanted book (stored enchantments only, no
     *  active ones), return a netherite-pickaxe stand-in stack with the same
     *  enchantments stamped active so the loot system actually picks them up.
     *  Otherwise return the input unchanged. */
    private static ItemStack translateBookEnchantments(final ItemStack tool) {
        final var active = tool.getEnchantments();
        if (active != null && !active.isEmpty()) return tool;
        final var stored = tool.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return tool;
        final ItemStack standIn = new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
        standIn.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, stored);
        return standIn;
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
        // Anchor is the cell *immediately* adjacent to the emitter (always
        // offset 1, regardless of whether that cell is in columnPositions —
        // it might be air-skipped). Sable uses the anchor as the ship's pose
        // origin; the column extends away from the emitter from there. When
        // FieldApplicator pulls the ship toward the emitter, the pose origin
        // (leading edge) reaches ARRIVAL_RADIUS exactly when the column
        // visually meets the magnet. An earlier version anchored at the deep
        // end (the ferro ore), which made the dismantle fire only after the
        // entire column had passed *through* the emitter cell.
        final BlockPos anchor = getBlockPos().relative(facing, 1);
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
            pullStartTick = level.getGameTime();
            pendingDrops = columnStates;
            // Tool wear: damageable tools take 1 durability per cycle. Enchanted
            // books are immune (no damage component) so a Fortune-book setup
            // never wears out — but a Fortune-pickaxe slowly does, encouraging
            // players to repair it occasionally.
            damageToolSlot();
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

    /** Hurt the installed tool by 1 durability, clearing the slot if the tool
     *  breaks. No-op for items that aren't damageable (enchanted books). */
    private void damageToolSlot() {
        final ItemStack tool = toolSlot.getItem(0);
        if (tool.isEmpty() || !tool.isDamageableItem()) return;
        final int newDamage = tool.getDamageValue() + 1;
        if (newDamage >= tool.getMaxDamage()) {
            toolSlot.setItem(0, ItemStack.EMPTY);
        } else {
            tool.setDamageValue(newDamage);
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
        if (pullStartTick != Long.MIN_VALUE) tag.putLong("PullStartTick", pullStartTick);
        if (activePullShipId != null) tag.putUUID("ActiveShip", activePullShipId);
        if (!pendingDrops.isEmpty()) {
            final ListTag drops = new ListTag();
            for (final BlockState bs : pendingDrops) {
                drops.add(NbtUtils.writeBlockState(bs));
            }
            tag.put("PendingDrops", drops);
        }
        final ItemStack tool = toolSlot.getItem(0);
        if (!tool.isEmpty()) tag.put("ToolSlot", tool.save(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastPullTick = tag.contains("LastPullTick") ? tag.getLong("LastPullTick") : Long.MIN_VALUE;
        pullStartTick = tag.contains("PullStartTick") ? tag.getLong("PullStartTick") : Long.MIN_VALUE;
        activePullShipId = tag.hasUUID("ActiveShip") ? tag.getUUID("ActiveShip") : null;
        pendingDrops = new ArrayList<>();
        if (tag.contains("PendingDrops", Tag.TAG_LIST)) {
            final ListTag list = tag.getList("PendingDrops", Tag.TAG_COMPOUND);
            // Prefer the registries parameter that NeoForge hands us — it's
            // always populated during BE deserialization. Fall back to the
            // level's holderLookup once the BE is attached. The previous
            // empty-stream fallback would throw on lookupOrThrow when level
            // happened to be null at load time.
            final net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block> lookup =
                    registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
            for (int i = 0; i < list.size(); i++) {
                pendingDrops.add(NbtUtils.readBlockState(lookup, list.getCompound(i)));
            }
        }
        toolSlot.setItem(0, tag.contains("ToolSlot", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("ToolSlot"))
                : ItemStack.EMPTY);
    }
}
