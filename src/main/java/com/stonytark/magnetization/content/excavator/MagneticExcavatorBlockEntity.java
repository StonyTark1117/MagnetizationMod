package com.stonytark.magnetization.content.excavator;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.InventorySink;
import com.stonytark.magnetization.physics.SableBridge;
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
 * <p><b>Field model.</b> While powered, the excavator continuously projects a
 * widening cone along {@code FACING} (see {@link #CONE_SLOPE}) and pulls
 * <i>every</i> ferromagnetic block in range simultaneously. Each pulled block
 * becomes its own 1-block Sable sub-level via
 * {@link SubLevelAssemblyHelper#assembleBlocks} the first time it's seen, then
 * gets a per-tick radial impulse along the vector ship → emitter from
 * {@link #tickActiveShips}. Cap on concurrent in-flight ships:
 * {@link #effectiveInFlightCap()}.
 *
 * <p><b>Tunnel through obstructions.</b> Every tick, each pulled ship checks
 * the world cell immediately in front of it (toward the emitter). If that
 * cell is a non-air, non-bedrock, non-ferromagnetic block, it's destroyed:
 * its drops pop as items at that position and the cell becomes air. The
 * pulled ship moves into the freed space, and on the next tick the new
 * adjacent cell is destroyed. Result: each pulled ore visibly drills a
 * tunnel from its vein to the emitter. The in-flight cap therefore tracks
 * only ferromagnetic targets — never intermediate terrain.
 *
 * <p><b>Per-ship lifecycle.</b> Each pulled ship is independent — arrival,
 * stuck detection, and per-ship timeout all happen ship-by-ship. One stalled
 * pull doesn't freeze the rest of the excavator.
 *
 * <p><b>Safety.</b> The scan skips block entities (chests, beacons, other
 * emitters), unbreakable blocks (bedrock), and anything tagged
 * {@code #magnetization:excavator_immune}.
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

    /** Built-in scan depth when the GUI's range slider is at 0 (i.e. "use default").
     *  128 reaches bedrock from a typical sea-level start. Players can dial up to
     *  {@link com.stonytark.magnetization.menu.EmitterMenu#RANGE_MAX} (256) if they
     *  build their excavator high up. The strength tier (WMSE) controls mining
     *  <i>speed</i> + pull force, not depth — those are independent. */
    private static final int DEFAULT_SCAN_DEPTH = 128;

    /** Cone slope. The scan disc at depth {@code d} has radius
     *  {@code max(MIN_CONE_RADIUS, ceil(d × slope))}. 0.25 = ~14° half-angle; combined
     *  with the floor of {@link #MIN_CONE_RADIUS}=2 (5×5 disc) the cone is never
     *  narrower than 2 blocks off-axis even right under the emitter. By mid-depth
     *  the disc widens: depth 16 → radius 4 (9×9), depth 64 → radius 16 (33×33),
     *  depth 128 → radius 32 (65×65). */
    private static final double CONE_SLOPE = 0.25d;

    /** Floor on the cone disc radius. Ensures the scan is at least a 5×5 disc at
     *  every depth so the excavator isn't a 1-block-wide drill for the first
     *  several blocks under itself. */
    private static final int MIN_CONE_RADIUS = 2;

    /** Hard absolute age cap for a pulled ship. If a ship has somehow
     *  been pulled-state for this long without making it home, demote it to
     *  passive so it stops absorbing impulse from this excavator and starts
     *  feeling gravity. The passive timeout then takes over. */
    private static final long PULLED_TIMEOUT_TICKS = 1200L; // 60s @ 20tps

    /** Stuck window for pulled ships: if a pulled ship hasn't reduced its
     *  distance to the emitter by {@link #STUCK_PROGRESS_EPSILON} blocks over
     *  this many ticks, demote it to passive — the excavator gives up on
     *  pulling it. Gravity will then pull it down; it auto-breaks per
     *  {@link #PASSIVE_TIMEOUT_TICKS} and the resulting item is itself
     *  pulled by the field's entity pass, so a future scan cycle may catch
     *  it again from a more favourable position. */
    private static final long PULLED_STUCK_WINDOW_TICKS = 400L; // 20s

    private static final double STUCK_PROGRESS_EPSILON = 0.25d;

    /** Rate at which we rescan the cone for new ferromagnetic blocks to pull
     *  in. Each scan covers up to {@code range × cone-disc} cells, so even at
     *  EXTREME tier a 256-block range can be expensive — limit to once per
     *  this many ticks. Per-ship pull impulses still tick every server tick. */
    private static final long SCAN_INTERVAL_TICKS = 4L;

    /** Hard rate-limit on how many new pulls one scan tick can add. Even when
     *  the per-emitter cap is much higher (e.g. 64), creating that many Sable
     *  sub-levels in a single tick blows out Rapier's per-tick allocator —
     *  we've seen buoyancy crashes ("No center of mass for body!") when 64
     *  rigid bodies entered the world simultaneously. Spreading new pulls
     *  over multiple scan ticks lets each body finish initializing before
     *  the next physics step runs. */
    private static final int PULLS_PER_SCAN_TICK = 4;

    /** Per-ship bookkeeping for the field model. */
    private static final class PulledShip {
        final BlockState capturedState;
        /** World position the sub-level was assembled at. Used to restore the
         *  block back to the world if the ship is culled by Sable for reasons
         *  outside this excavator's control — gives the player back the
         *  ore at its source rather than teleport-dropping at the emitter. */
        final BlockPos bornPos;
        final long bornTick;
        double closestDist;
        long lastProgressTick;
        PulledShip(final BlockState bs, final BlockPos bornPos, final long now, final double dist) {
            this.capturedState = bs;
            this.bornPos = bornPos;
            this.bornTick = now;
            this.closestDist = dist;
            this.lastProgressTick = now;
        }
    }

    /** Every in-flight pulled ship spawned by this excavator. Insertion-ordered
     *  for stable save/load. Non-ferromagnetic terrain is destroyed in place,
     *  never assembled into sub-levels — so the cap only fills with magnetic
     *  targets. */
    private final java.util.LinkedHashMap<UUID, PulledShip> pulledShips = new java.util.LinkedHashMap<>();
    /** Mirror of {@link #pulledShips} keys for {@link #shipFilter()} lookups. */
    private final java.util.HashSet<UUID> activelyPulledShips = new java.util.HashSet<>();

    /** Per-emitter cap on concurrent in-flight ships. 0 = follow the admin
     *  ceiling in {@link MagConfig#EXCAVATOR_MAX_IN_FLIGHT}. Persisted via NBT
     *  and exposed via the GUI's {@link com.stonytark.magnetization.menu.EmitterMenu#CAP_INFLIGHT} bit. */
    private int inFlightCapOverride = 0;

    /** Last tick a cone scan ran. Drives {@link #SCAN_INTERVAL_TICKS} rate-limit. */
    private long lastScanTick = Long.MIN_VALUE;

    /** Remaining destruction budget for the current tick. Reset to
     *  {@link #DESTROYS_PER_TICK_CAP} at the top of {@link #tickActiveShips}
     *  and decremented by every successful {@link #tryDestroyNeighbor} call.
     *  Once 0, {@link #destroyBlockingFace} becomes a no-op for the rest of
     *  the tick — the cone scan still picks up new pulls but no further
     *  terrain mining happens this tick. Spreads work across ships fairly
     *  in iteration order. */
    private transient int destroyBudgetRemaining = 0;
    /** Counter for {@link #BREAK_SOUND_EVERY_N} sampling of break-sound
     *  packets. Modulo-based so it doesn't grow unboundedly. */
    private transient int destroySoundCursor = 0;

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

    /** Internal redstone power slot. Any amount of redstone dust in here keeps
     *  the excavator active — equivalent to an external redstone signal but
     *  immune to the excavator's own pulls destroying its power source. Items
     *  aren't consumed; treat the slot as a built-in lever. Exposed via the
     *  GUI's {@code CAP_REDSTONE_FUEL} bit. */
    private final SimpleContainer redstoneFuelSlot = new SimpleContainer(1) {
        @Override public int getMaxStackSize() { return 64; }
        @Override public void setChanged() {
            super.setChanged();
            MagneticExcavatorBlockEntity.this.setChanged();
            MagneticExcavatorBlockEntity.this.recomputePower();
        }
    };

    /** Tracks the most recent external redstone signal independently of fuel.
     *  Logical {@link #isPowered()} is the OR of this and {@link #hasRedstoneFuel()}.
     *  Persisted so block-state POWERED stays correct across reloads. */
    private boolean externalSignal = false;

    public Container getToolSlot() {
        return toolSlot;
    }

    public Container getRedstoneFuelSlot() {
        return redstoneFuelSlot;
    }

    /** True when the internal redstone slot holds any item in the
     *  {@link MagTags#REDSTONE_FUEL} tag — dust, blocks, torches, levers,
     *  pressure plates, observers, etc. Items aren't consumed; this is a
     *  presence check that the slot has a redstone-source item in it. */
    public boolean hasRedstoneFuel() {
        final ItemStack stack = redstoneFuelSlot.getItem(0);
        return !stack.isEmpty() && stack.is(MagTags.REDSTONE_FUEL);
    }

    /** Logical power = external signal OR fuel present. Overrides the base
     *  external-only flag so the field, tunneling, and arrival code all
     *  treat fuel and external signal as equivalent. */
    @Override
    public boolean isPowered() {
        return super.isPowered() || hasRedstoneFuel();
    }

    /** Drop the redstone-fuel slot's contents into the world. Called from the
     *  block on break so the player doesn't lose redstone they parked here. */
    public void dropRedstoneFuelSlot(final Level level, final BlockPos pos) {
        Containers.dropContents(level, pos, redstoneFuelSlot);
    }

    /** External-signal entry point from {@link MagneticExcavatorBlock#neighborChanged}.
     *  Replaces direct {@link #setPowered} calls so the BE owns power state. */
    public void setExternalSignal(final boolean signal) {
        if (this.externalSignal == signal) return;
        this.externalSignal = signal;
        setChanged();
        recomputePower();
    }

    /** Push logical power (external OR fuel) into both the BE flag and the
     *  block state's {@code POWERED} property so visuals + field logic agree.
     *  Called whenever the external signal or fuel slot changes. */
    private void recomputePower() {
        final boolean logical = externalSignal || hasRedstoneFuel();
        // Update the base externally-tracked flag (drives field activation,
        // sound, cache invalidation). super.isPowered() returns this raw bit.
        setPowered(logical);
        if (level == null) return;
        final BlockState bs = level.getBlockState(getBlockPos());
        if (!(bs.getBlock() instanceof MagneticExcavatorBlock)) return;
        if (bs.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) == logical) return;
        level.setBlock(getBlockPos(),
                bs.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, logical),
                Block.UPDATE_CLIENTS);
    }

    /** Current per-emitter cap override on concurrent in-flight ships. 0 = use
     *  the admin ceiling in {@link MagConfig#EXCAVATOR_MAX_IN_FLIGHT}. */
    public int getInFlightCapOverride() { return inFlightCapOverride; }

    public void setInFlightCapOverride(final int n) {
        final int clamped = Math.max(0, Math.min(adminInFlightCeiling(), n));
        if (this.inFlightCapOverride == clamped) return;
        this.inFlightCapOverride = clamped;
        setChanged();
    }

    /** Effective concurrent-pull cap: GUI override if set, otherwise admin ceiling. */
    public int effectiveInFlightCap() {
        return inFlightCapOverride > 0 ? inFlightCapOverride : adminInFlightCeiling();
    }

    private static int adminInFlightCeiling() {
        try { return MagConfig.EXCAVATOR_MAX_IN_FLIGHT.get(); }
        catch (final Throwable t) { return 16; }
    }

    /** 0..100 percent for the GUI progress bar. In the field model, "progress"
     *  is a soft signal: the closest active pulled ship's normalized progress
     *  toward the emitter. When nothing is in flight we display 100 (ready).
     *  When unpowered we display 0. */
    public int getPullProgressPct() {
        if (!isPowered()) return 0;
        if (level == null) return 0;
        if (activelyPulledShips.isEmpty()) return 100;
        if (!(level instanceof ServerLevel server)) return 0;
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return 0;
        final Vec3 emitterCenter = Vec3.atCenterOf(getBlockPos());
        double bestDist = Double.MAX_VALUE;
        for (final UUID id : activelyPulledShips) {
            final var sub = container.getSubLevel(id);
            if (!(sub instanceof ServerSubLevel ship)) continue;
            final org.joml.Vector3dc shipPos = ship.logicalPose().position();
            final double dx = emitterCenter.x - shipPos.x();
            final double dy = emitterCenter.y - shipPos.y();
            final double dz = emitterCenter.z - shipPos.z();
            final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < bestDist) bestDist = dist;
        }
        if (bestDist == Double.MAX_VALUE) return 100;
        final double range = effectiveRange(effectiveStrength(MagneticStrength.MEDIUM));
        if (range <= 0.5) return 100;
        final double progress = 1.0 - Math.min(1.0, bestDist / range);
        return (int) Math.round(Math.max(0, Math.min(1.0, progress)) * 100);
    }

    /** Drop the tool slot's contents into the world. Called from the block when
     *  the player breaks the excavator so the player doesn't lose their book. */
    public void dropToolSlot(final Level level, final BlockPos pos) {
        Containers.dropContents(level, pos, toolSlot);
    }

    public MagneticExcavatorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_EXCAVATOR.get(), pos, state);
    }

    /**
     * Depth-only default range for the excavator. Strength tier (WMSE) does not
     * affect this — tier controls mining speed + pull force only, leaving the range
     * slider as the single source of truth for "how far down does it reach". When
     * the slider is at 0 we use half of the per-block admin ceiling (so freshly
     * placed excavators ship with a sensible mid-range default), falling back to
     * {@link #DEFAULT_SCAN_DEPTH} if the config isn't loaded yet.
     */
    @Override
    protected double defaultEffectiveRange(final MagneticStrength tier) {
        try { return MagConfig.EXCAVATOR_MAX_RANGE.get() / 2.0d; }
        catch (final Throwable t) { return DEFAULT_SCAN_DEPTH; }
    }

    /** Hard cap on how many cells one cone-scan pass considers, regardless of
     *  range. Acts as a config-typo backstop. */
    private static int maxBlocksPerCycle() {
        try { return MagConfig.EXCAVATOR_MAX_BLOCKS_PER_CYCLE.get(); }
        catch (final Throwable t) { return 32; }
    }

    /** Per-tick acceleration cap for the excavator's manual radial pull.
     *  Deliberately ~10× the global {@link MagConfig#MAX_ACCEL_PER_TICK} (50)
     *  because the excavator's load is unlike a free-ship pull: the ore at the
     *  bottom of the column has to lift the cumulative gravitational weight of
     *  every passive intermediate stacked above it. With the global 50 N cap
     *  on a mass-1 ship, six dirt/stone sub-levels above the ore are enough to
     *  stall it completely (observed in-game: dist stays constant for 30+s).
     *  500 N gives headroom to lift ~50 stacked sub-levels against Sable's
     *  scaled gravity (~4.67 m/s²) with room to spare. */
    private static final double EXCAVATOR_ACCEL_CAP_PER_TICK = 500.0d;

    /** Cap on the ore's "toward-emitter" velocity. Without this, the high
     *  acceleration cap above would let the ore reach ~25+ m/s within a few
     *  ticks (per-tick velocity injection compounds), at which point a single
     *  tick can carry it past the {@link #ARRIVAL_RADIUS}=1.5 window and the
     *  arrival check misses. 12 m/s ≈ 0.6 blocks/tick: traverses 25 blocks in
     *  ~2 seconds, still visibly an animation, never overshoots. */
    private static final double EXCAVATOR_MAX_PULL_SPEED = 12.0d;

    /**
     * Reject every ship from the FieldApplicator's standard pull pass. The
     * stock {@code DIRECTIONAL} shape applies force purely along the facing
     * axis, which is wrong for the excavator — an off-axis ore would be
     * shoved straight along the axis instead of pulled radially toward the
     * emitter. {@link #tickActiveShips} instead manually impulses each
     * actively-pulled ship along the vector ship → emitter, so the pull works
     * regardless of where the cone scan finds the ore.
     *
     * <p>Vanilla entities and dropped items still feel the field via
     * {@link com.stonytark.magnetization.physics.FieldApplicator}'s separate
     * entity pass, which the {@code shipFilter} doesn't gate — that's how
     * dropped items get sucked into the emitter for ingest.
     */
    @Override
    protected @Nullable java.util.function.Predicate<ServerSubLevel> shipFilter() {
        return ship -> false;
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

        // Drive all in-flight ships every tick. Per-ship arrival, stuck detection,
        // and timeout happen ship-by-ship — one bad pull doesn't freeze the others.
        tickActiveShips(server);

        // Rate-limited cone scan to find new ferromagnetic blocks and pull them in.
        // Independent of the per-ship tick: even while ships are in flight we keep
        // scanning so new ores enter the pull pool as space opens up.
        final long tick = server.getGameTime();
        if (lastScanTick == Long.MIN_VALUE || tick - lastScanTick >= SCAN_INTERVAL_TICKS) {
            lastScanTick = tick;
            maybeScanAndPull(server, state);
        }
    }

    /** Per-tick driver: walks every in-flight ship independently.
     *  <ul>
     *    <li>Pulled ships get a radial impulse toward the emitter, an arrival
     *        check, a stuck check, and a per-ship timeout. Each can fail
     *        independently — one stuck pull doesn't freeze the others.</li>
     *    <li>After the impulse, each pulled ship attempts to destroy the
     *        single world block sitting on its toward-emitter face. That
     *        carves the tunnel that lets it (and the chain of ships behind
     *        it from the same vein) make progress without needing physics
     *        rigid bodies for the intermediate terrain.</li>
     *  </ul>
     */
    private void tickActiveShips(final ServerLevel server) {
        if (pulledShips.isEmpty()) return;
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) {
            // Sable container missing — drop everything as recovery and clear state.
            dismantleAllAtEmitter(server);
            return;
        }
        // Reset the per-tick destruction budget. Spread across all in-flight ships
        // in iteration order; ships processed later in a budget-exhausted tick get
        // their turn next tick (LinkedHashMap iteration is stable).
        destroyBudgetRemaining = DESTROYS_PER_TICK_CAP;

        final Vec3 emitterCenter = Vec3.atCenterOf(getBlockPos());
        final ItemStack tool = toolSlot.getItem(0);
        final MagneticStrength tier = effectiveStrength(MagneticStrength.MEDIUM);
        final double pullRange = effectiveRange(tier);
        final double pullForce = tier.force();
        final long now = server.getGameTime();

        final java.util.Iterator<java.util.Map.Entry<UUID, PulledShip>> it = pulledShips.entrySet().iterator();
        while (it.hasNext()) {
            final var entry = it.next();
            final UUID id = entry.getKey();
            final PulledShip rec = entry.getValue();
            final var sub = container.getSubLevel(id);
            if (!(sub instanceof ServerSubLevel ship) || ship.getMassTracker().isInvalid()) {
                // Vanished outside this excavator's control (Sable cull,
                // chunk unload, etc.). Restore at the ship's original spawn
                // position so the ore is back in the world for the next scan.
                restoreBlockAt(server, rec.bornPos, rec.capturedState);
                activelyPulledShips.remove(id);
                it.remove();
                continue;
            }

            final org.joml.Vector3dc shipPos = ship.logicalPose().position();
            final double dx = emitterCenter.x - shipPos.x();
            final double dy = emitterCenter.y - shipPos.y();
            final double dz = emitterCenter.z - shipPos.z();
            final double dist2 = dx * dx + dy * dy + dz * dz;
            if (dist2 <= ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
                dropOneAtEmitter(server, rec.capturedState, tool);
                container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                activelyPulledShips.remove(id);
                it.remove();
                continue;
            }

            final double dist = Math.sqrt(dist2);

            // Progress tracking. If the ship has made it any closer, reset
            // the stuck timer; otherwise once the stuck window expires the
            // pull gives up, the sub-level is removed, and the ore is
            // restored as a world block at its current position so the next
            // scan cycle can re-pull it from there.
            if (dist + STUCK_PROGRESS_EPSILON < rec.closestDist) {
                rec.closestDist = dist;
                rec.lastProgressTick = now;
            } else if (now - rec.lastProgressTick > PULLED_STUCK_WINDOW_TICKS
                    || now - rec.bornTick > PULLED_TIMEOUT_TICKS) {
                if (MagConfig.debugLogging()) {
                    LOG.info("Excavator at {} releasing stuck pulled ship {}: dist={} bornTick={} lastProgress={} now={}",
                            getBlockPos().toShortString(), id.toString().substring(0, 8),
                            String.format("%.2f", dist), rec.bornTick, rec.lastProgressTick, now);
                }
                final BlockPos releaseAt = BlockPos.containing(shipPos.x(), shipPos.y(), shipPos.z());
                container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                restoreBlockAt(server, releaseAt, rec.capturedState);
                activelyPulledShips.remove(id);
                it.remove();
                continue;
            }

            // Manual radial pull: vector from ship → emitter, magnitude scales
            // with the tier force and linearly falls off with distance.
            if (dist > 1.0e-3 && dist <= pullRange) {
                final double falloff = Math.max(0.0d, 1.0d - dist / pullRange);
                double mag = pullForce * falloff;
                final double mass = Math.max(0.0001, ship.getMassTracker().getMass());
                final double cap = EXCAVATOR_ACCEL_CAP_PER_TICK;
                if (cap > 0 && mag / mass > cap) {
                    mag = cap * mass;
                }
                final double invDist = 1.0 / dist;
                final double ux = dx * invDist, uy = dy * invDist, uz = dz * invDist;
                final double vx = ship.latestLinearVelocity.x;
                final double vy = ship.latestLinearVelocity.y;
                final double vz = ship.latestLinearVelocity.z;
                final double towardSpeed = vx * ux + vy * uy + vz * uz;
                if (towardSpeed < EXCAVATOR_MAX_PULL_SPEED) {
                    // Fuse the magnitude into the constructor so we allocate one
                    // Vec3 instead of two (was `new Vec3(...).scale(...)`).
                    final double impulseScale = mag * invDist;
                    final Vec3 impulse = new Vec3(dx * impulseScale, dy * impulseScale, dz * impulseScale);
                    SableBridge.applyWorldImpulse(ship,
                            new Vec3(shipPos.x(), shipPos.y(), shipPos.z()),
                            impulse);
                }

                // Tunnel: destroy the single world block sitting on the
                // toward-emitter face (if any). Non-ferromagnetic intermediates
                // drop natural items at their original position; ferromagnetic
                // blocks are left for the cone scan to pull as their own ship.
                destroyBlockingFace(server, ship, ux, uy, uz, tool);
            }
        }

        if (pulledShips.isEmpty()) {
            activelyPulledShips.clear();
            setChanged();
        }
    }

    /** Threshold below which a toward-emitter unit vector component is treated
     *  as zero for face-destruction. 0.3 ≈ 17.5° — anything more lateral than
     *  that counts as a genuine pull on that axis, and the axis-aligned
     *  neighbor face on that side gets cleared. */
    private static final double FACE_DESTROY_AXIS_SIGNIFICANCE = 0.3d;

    /** Per-emitter ceiling on real block destructions per tick across all in-flight
     *  ships. At cap=64 with 3-axis diagonal destruction the un-throttled rate is
     *  ~192 setBlock calls per tick; combined with neighbor cascades and break-sound
     *  packets, that's enough to wedge the server tick at high pull counts (observed:
     *  4167 ticks behind in &lt;5 min). 16/tick still carves tunnels visibly fast (≈320
     *  cells/sec) while leaving plenty of tick budget for physics + scan + Sable. */
    private static final int DESTROYS_PER_TICK_CAP = 16;

    /** Play 1 break sound per this many actual destructions to keep network packet
     *  rate sane when many ships are tunneling at once. The visual cue (block
     *  disappears) is sufficient on its own; the sound is just flavor. */
    private static final int BREAK_SOUND_EVERY_N = 4;

    /** Destroy the world block(s) on the ship's toward-emitter face. Up to
     *  three axis-aligned neighbors get cleared per tick — one for each axis
     *  with a significant toward-emitter component. Targeting a single
     *  rounded cell (e.g. the diagonal corner at +1,+1,0) leaves the actual
     *  collision faces (+1,0,0) and (0,+1,0) intact, which used to wedge
     *  diagonal pulls in place until gravity randomized the rounded cell
     *  enough to crack the right wall — visible as ores freezing midair
     *  before unsticking. Drops natural loot at each destroyed cell.
     *  Ferromagnetic blocks are skipped so they remain candidates for the
     *  next cone scan; bedrock-class / immune-tagged blocks are skipped
     *  via {@link #isBarrier}. */
    private void destroyBlockingFace(final ServerLevel server, final ServerSubLevel ship,
                                     final double ux, final double uy, final double uz,
                                     final ItemStack tool) {
        if (destroyBudgetRemaining <= 0) return;
        final org.joml.Vector3dc shipPos = ship.logicalPose().position();
        final BlockPos shipCell = BlockPos.containing(shipPos.x(), shipPos.y(), shipPos.z());
        if (Math.abs(ux) > FACE_DESTROY_AXIS_SIGNIFICANCE && destroyBudgetRemaining > 0) {
            tryDestroyNeighbor(server, shipCell.offset(ux > 0 ? 1 : -1, 0, 0), tool);
        }
        if (Math.abs(uy) > FACE_DESTROY_AXIS_SIGNIFICANCE && destroyBudgetRemaining > 0) {
            tryDestroyNeighbor(server, shipCell.offset(0, uy > 0 ? 1 : -1, 0), tool);
        }
        if (Math.abs(uz) > FACE_DESTROY_AXIS_SIGNIFICANCE && destroyBudgetRemaining > 0) {
            tryDestroyNeighbor(server, shipCell.offset(0, 0, uz > 0 ? 1 : -1), tool);
        }
    }

    /** Mine one world cell on behalf of a pulled ship: pops loot at the cell
     *  and clears it to air. No-op for air / ferromagnetic / barrier cells.
     *  Uses {@link Block#UPDATE_CLIENTS} (no neighbor cascade) because the
     *  tunnel doesn't need adjacent redstone re-eval, sand-falling, or shape
     *  recomputes — and skipping those is the single biggest server-tick win
     *  at high pull counts. */
    private void tryDestroyNeighbor(final ServerLevel server, final BlockPos at, final ItemStack tool) {
        final BlockState bs = server.getBlockState(at);
        if (bs.isAir()) return;
        if (bs.is(MagTags.FERROMAGNETIC_BLOCKS)) return;
        if (isBarrier(server, at, bs)) return;
        for (final ItemStack stack : enchantedDropsFor(bs, server, at, tool)) {
            Block.popResource(server, at, stack);
        }
        server.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS);
        // Sample break-sound packets — every Nth destruction this tick — so we
        // don't flood clients when many ships are tunneling at once.
        if ((destroySoundCursor++ % BREAK_SOUND_EVERY_N) == 0) {
            server.playSound(null, at, bs.getSoundType(server, at, null).getBreakSound(),
                    SoundSource.BLOCKS, 0.3f, 1.0f);
        }
        destroyBudgetRemaining--;
    }

    /** Drop one ship's loot at the cell adjacent to the emitter, preferring
     *  direct inventory ingest (hopper / chest / barrel) before falling back to
     *  popping items the {@link InventorySink} would pick up next tick. */
    private void dropOneAtEmitter(final ServerLevel server, final BlockState bs, final ItemStack tool) {
        final Direction facing = getBlockState().getValue(DirectionalBlock.FACING);
        final BlockPos drop = getBlockPos().relative(facing, 1);
        final List<ItemStack> drops = enchantedDropsFor(bs, server, drop, tool);
        for (final ItemStack stack : drops) {
            final ItemStack remainder = InventorySink.tryDirectIngest(server, getBlockPos(), stack);
            if (!remainder.isEmpty()) {
                Block.popResource(server, drop, remainder);
            }
        }
        server.playSound(null, drop, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.4f, 1.2f);
        server.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                drop.getX() + 0.5, drop.getY() + 0.5, drop.getZ() + 0.5,
                4, 0.2, 0.2, 0.2, 0.05);
    }

    /** Put a captured block state back into the world at {@code at}, but only
     *  if that cell is air. If not, the ore is silently lost — by design.
     *  Items are NEVER popped here: the agreed rule is that ferromagnetic
     *  block drops only appear via the emitter delivery path. Used as
     *  the "ship vanished outside our control" recovery so a Sable cull
     *  doesn't teleport items to the emitter or rain items mid-world. */
    private void restoreBlockAt(final ServerLevel level, final BlockPos at, final BlockState bs) {
        if (level.getBlockState(at).isAir()) {
            level.setBlock(at, bs, Block.UPDATE_ALL);
        }
        // else: silently lose. Don't pop items off-path.
    }

    /** Force-dismantle every ship tracked by this excavator and restore each
     *  one's captured block at its spawn position (or silently lose it if
     *  the cell isn't air). Used as the safety fallback when the Sable
     *  container is unavailable — even here we keep the "no off-path item
     *  drops" rule so the player never sees mass-teleport at the emitter. */
    private void dismantleAllAtEmitter(final ServerLevel server) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        for (final var entry : pulledShips.entrySet()) {
            final UUID id = entry.getKey();
            final PulledShip rec = entry.getValue();
            if (container != null) {
                final var sub = container.getSubLevel(id);
                if (sub instanceof ServerSubLevel ship) {
                    container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                }
            }
            restoreBlockAt(server, rec.bornPos, rec.capturedState);
        }
        pulledShips.clear();
        activelyPulledShips.clear();
        setChanged();
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
        final var active = tool.getOrDefault(
                net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        if (!active.isEmpty()) return tool;
        final var stored = tool.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return tool;
        final ItemStack standIn = new ItemStack(net.minecraft.world.item.Items.NETHERITE_PICKAXE);
        standIn.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, stored);
        return standIn;
    }

    /** Continuous field scan. Sweeps the cone, collects every ferromagnetic
     *  block not already being pulled, assembles each as its own pulled
     *  sub-level — until either no candidates remain or the in-flight cap
     *  is hit. Runs every {@link #SCAN_INTERVAL_TICKS} ticks so new ores
     *  get drawn in as space opens up. */
    private void maybeScanAndPull(final ServerLevel level, final BlockState state) {
        final int cap = effectiveInFlightCap();
        if (pulledShips.size() >= cap) return;

        final Direction facing = state.getValue(DirectionalBlock.FACING);
        final int rangeBlocks = (int) Math.min(
                effectiveRange(effectiveStrength(MagneticStrength.MEDIUM)),
                maxBlocksPerCycle());

        // Collect every ferromagnetic cell in the cone, nearest first. We stop
        // pulling once the in-flight cap is hit — the next scan picks up where
        // we left off as ships clear out.
        final List<BlockPos> candidates = findFerromagneticsInCone(level, facing, rangeBlocks);
        if (candidates.isEmpty()) return;

        final long now = level.getGameTime();
        int newPulls = 0;
        for (final BlockPos pos : candidates) {
            if (pulledShips.size() >= cap) break;
            if (newPulls >= PULLS_PER_SCAN_TICK) break;
            final BlockState bs = level.getBlockState(pos);
            if (bs.isAir() || !bs.is(MagTags.FERROMAGNETIC_BLOCKS)) continue;
            if (bs.is(MagTags.EXCAVATOR_IMMUNE)) continue;
            if (tryAssemblePulled(level, pos, bs, now)) newPulls++;
        }
        if (newPulls > 0) {
            damageToolSlot();
            level.playSound(null, getBlockPos(), SoundEvents.LODESTONE_PLACE,
                    SoundSource.BLOCKS, 0.4f, 1.6f);
            if (MagConfig.debugLogging()) {
                LOG.info("Excavator at {} pulled {} new ferromagnetic block(s); now {} in flight / cap {}",
                        getBlockPos().toShortString(), newPulls, pulledShips.size(), cap);
            }
            setChanged();
        }
    }

    /** Assemble a single ferromagnetic cell as a pulled sub-level. Returns true on success. */
    private boolean tryAssemblePulled(final ServerLevel level, final BlockPos pos, final BlockState bs, final long now) {
        final BoundingBox3i bounds = new BoundingBox3i(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        try {
            final ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(
                    level, pos, List.of(pos), bounds);
            if (ship.getMassTracker().isInvalid()) {
                // Mass tracker invalid means Sable couldn't finish initializing
                // the body. Eject it from the container immediately — leaving
                // it stranded was triggering Rapier buoyancy panics on the
                // next physics step ("No center of mass for body!").
                final SubLevelContainer container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                }
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, bs, Block.UPDATE_ALL);
                }
                return false;
            }
            final UUID id = ship.getUniqueId();
            final double dist = Math.sqrt(pos.distSqr(getBlockPos()));
            pulledShips.put(id, new PulledShip(bs, pos, now, dist));
            activelyPulledShips.add(id);
            level.sendParticles(ParticleTypes.CRIT,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.2, 0.2, 0.2, 0.05);
            return true;
        } catch (final Throwable t) {
            LOG.error("Excavator pulled-block assembly failed at {}", pos.toShortString(), t);
            Block.dropResources(bs, level, pos);
            return false;
        }
    }

    /**
     * Cone projection: sweep a widening disc at each depth along the facing
     * axis. Returns every ferromagnetic-blocks-tagged cell in range, sorted
     * nearest-first (Euclidean to emitter). Skips EXCAVATOR_IMMUNE, BE-bearing,
     * and already-tracked positions.
     */
    private List<BlockPos> findFerromagneticsInCone(final ServerLevel level, final Direction facing, final int depthLimit) {
        final BlockPos origin = getBlockPos();
        // Pre-size from the expected upper bound (cone volume ≈ π·slope²·depth³/3
        // ≈ 0.35·depth³ for slope≈0.5). depthLimit²·2 is a generous heuristic
        // that covers most real configurations without ArrayList resize churn.
        final List<BlockPos> out = new ArrayList<>(Math.max(16, depthLimit * depthLimit * 2));
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final Direction.Axis facingAxis = facing.getAxis();
        for (int d = 1; d <= depthLimit; d++) {
            final int radius = Math.max(MIN_CONE_RADIUS, (int) Math.ceil(d * CONE_SLOPE));
            for (int u = -radius; u <= radius; u++) {
                for (int v = -radius; v <= radius; v++) {
                    if (u * u + v * v > radius * radius) continue;
                    final int dx, dy, dz;
                    switch (facingAxis) {
                        case X -> { dx = 0;            dy = u; dz = v; }
                        case Y -> { dx = u;            dy = 0; dz = v; }
                        default -> { dx = u;            dy = v; dz = 0; }
                    }
                    final int sx = facing.getStepX() * d + dx;
                    final int sy = facing.getStepY() * d + dy;
                    final int sz = facing.getStepZ() * d + dz;
                    cursor.set(origin.getX() + sx, origin.getY() + sy, origin.getZ() + sz);
                    final BlockState bs = level.getBlockState(cursor);
                    if (bs.isAir()) continue;
                    if (bs.is(MagTags.EXCAVATOR_IMMUNE)) continue;
                    if (!bs.is(MagTags.FERROMAGNETIC_BLOCKS)) continue;
                    out.add(cursor.immutable());
                }
            }
        }
        out.sort((a, b) -> Double.compare(a.distSqr(origin), b.distSqr(origin)));
        return out;
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
        if (inFlightCapOverride > 0) tag.putInt("InFlightCapOverride", inFlightCapOverride);
        if (lastScanTick != Long.MIN_VALUE) tag.putLong("LastScanTick", lastScanTick);
        if (!pulledShips.isEmpty()) tag.put("PulledShips", saveShipList(pulledShips));
        final ItemStack tool = toolSlot.getItem(0);
        if (!tool.isEmpty()) tag.put("ToolSlot", tool.save(registries));
        final ItemStack fuel = redstoneFuelSlot.getItem(0);
        if (!fuel.isEmpty()) tag.put("RedstoneFuelSlot", fuel.save(registries));
        if (externalSignal) tag.putBoolean("ExternalSignal", true);
    }

    private static ListTag saveShipList(final java.util.LinkedHashMap<UUID, PulledShip> map) {
        final ListTag list = new ListTag();
        for (final var entry : map.entrySet()) {
            final CompoundTag e = new CompoundTag();
            e.putUUID("Id", entry.getKey());
            e.put("State", NbtUtils.writeBlockState(entry.getValue().capturedState));
            e.putLong("BornPos", entry.getValue().bornPos.asLong());
            e.putLong("Born", entry.getValue().bornTick);
            e.putDouble("Closest", entry.getValue().closestDist);
            e.putLong("Progress", entry.getValue().lastProgressTick);
            list.add(e);
        }
        return list;
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inFlightCapOverride = tag.contains("InFlightCapOverride") ? tag.getInt("InFlightCapOverride") : 0;
        lastScanTick = tag.contains("LastScanTick") ? tag.getLong("LastScanTick") : Long.MIN_VALUE;
        pulledShips.clear();
        activelyPulledShips.clear();
        final net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block> lookup =
                registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
        loadShipList(tag, "PulledShips", lookup, pulledShips, true);
        toolSlot.setItem(0, tag.contains("ToolSlot", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("ToolSlot"))
                : ItemStack.EMPTY);
        redstoneFuelSlot.setItem(0, tag.contains("RedstoneFuelSlot", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("RedstoneFuelSlot"))
                : ItemStack.EMPTY);
        externalSignal = tag.getBoolean("ExternalSignal");
    }

    private void loadShipList(final CompoundTag tag, final String key,
                              final net.minecraft.core.HolderLookup<net.minecraft.world.level.block.Block> lookup,
                              final java.util.LinkedHashMap<UUID, PulledShip> out,
                              final boolean addToActivelyPulled) {
        if (!tag.contains(key, Tag.TAG_LIST)) return;
        final ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("Id") || !e.contains("State", Tag.TAG_COMPOUND)) continue;
            final UUID id = e.getUUID("Id");
            final BlockState bs = NbtUtils.readBlockState(lookup, e.getCompound("State"));
            final BlockPos bornPos = e.contains("BornPos", Tag.TAG_LONG)
                    ? BlockPos.of(e.getLong("BornPos"))
                    : getBlockPos(); // legacy save with no born pos — fallback
            final PulledShip rec = new PulledShip(bs, bornPos,
                    e.contains("Born") ? e.getLong("Born") : 0L,
                    e.contains("Closest") ? e.getDouble("Closest") : Double.MAX_VALUE);
            if (e.contains("Progress")) rec.lastProgressTick = e.getLong("Progress");
            out.put(id, rec);
            if (addToActivelyPulled) activelyPulledShips.add(id);
        }
    }

    @Override
    public void fillCrashReportCategory(final net.minecraft.CrashReportCategory category) {
        super.fillCrashReportCategory(category);
        category.setDetail("Magnetization Excavator In-Flight Ships",
                () -> Integer.toString(pulledShips.size()));
        category.setDetail("Magnetization Excavator Actively Pulled",
                () -> Integer.toString(activelyPulledShips.size()));
        category.setDetail("Magnetization Excavator Last Scan Tick",
                () -> Long.toString(lastScanTick));
    }
}
