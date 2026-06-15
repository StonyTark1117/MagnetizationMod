package com.stonytark.magnetization.content.inducer;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structural Inducer: a powered magnet that grabs the whole structure sitting
 * directly above it and lifts it off the ground as a single Create: Aeronautics
 * physics object. Placed under a building/ruin and powered, it scans a bounded
 * box above itself, glues every solid block in it into one Sable sub-level via
 * {@link SubLevelAssemblyHelper#assembleBlocks}, and applies a steady upward
 * impulse until the structure has cleared the ground — then releases it as a
 * free-floating craft. Gated on clearance ("nothing above" the structure), so
 * it never tries to lift into a ceiling.
 *
 * <p>One capture per power cycle (re-arms when redstone drops and re-applies).
 * Block entities, fluids, unbreakable blocks (bedrock-class), and
 * {@code #magnetization:excavator_immune} are all skipped so the lift can't
 * grief protected/important blocks.
 */
public class StructuralInducerBlockEntity extends AbstractEmitterBlockEntity
        implements com.stonytark.magnetization.content.RedstoneFuelHolder {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/StructuralInducer");

    /** Cross-section half-width of the aim used to find the target (9×9 seek). */
    private static final int SCAN_RADIUS = 4;
    /** Hard cap on captured blocks — keeps one assembly bounded (large building). */
    private static final int MAX_BLOCKS = 4096;
    /** Max distance the structure flood reaches from the seed (chessboard). */
    private static final int MAX_REACH = 24;
    /** Cap on cells the flood may visit (air counts), so an open-air fill ends. */
    private static final int VISIT_CAP = 65_536;
    /** Once the pulled structure's centre is within this many blocks of the
     *  inducer, release it as a free-floating craft (it's been reeled in). */
    private static final double ARRIVAL_DISTANCE = 2.5d;
    /** Base acceleration per tick toward the inducer (m/s²) at the MEDIUM tier;
     *  scaled by the strength buttons via {@link #pullMultiplier()}. Must beat
     *  Sable gravity. */
    private static final double PULL_ACCEL = 16.0d;
    /** Base cap on the structure's pull speed (MEDIUM tier) so it reads as a
     *  smooth tractor beam; scaled by {@link #pullMultiplier()}. */
    private static final double MAX_PULL_SPEED = 6.0d;
    /** Grace ticks after assembly during which an "invalid mass" reading is
     *  treated as the body still initializing, not a cull — so we don't tear a
     *  fresh craft down (and leave a ghost) before Sable finishes setting it up. */
    private static final long INIT_GRACE_TICKS = 10L;
    /** Absolute age cap on a pull before we give up and release it. */
    private static final long PULL_TIMEOUT_TICKS = 600L;
    /** Max world blocks the structure punches through (on its leading faces) per tick. */
    private static final int TUNNEL_BUDGET = 96;

    /** Rescan the cone for new structures this often while powered. */
    private static final long SCAN_INTERVAL = 10L;
    /** Cap on concurrently-reeled structures, so one activation can't spawn a
     *  runaway number of Sable sub-levels. */
    private static final int MAX_STRUCTURES = 8;

    /** External redstone signal, mirrored into block-state POWERED. */
    private boolean externalSignal = false;

    /** Internal redstone-fuel slot (mirrors the excavator): any redstone item
     *  parked here keeps the inducer powered, like a constant signal. */
    private final net.minecraft.world.SimpleContainer redstoneFuelSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public int getMaxStackSize() { return 64; }
        @Override public void setChanged() {
            super.setChanged();
            StructuralInducerBlockEntity.this.setChanged();
        }
    };

    /** Default scan depth when no range override is dialed in. */
    private static final int DEFAULT_DEPTH = 16;

    /** One structure the inducer has assembled and is reeling in. */
    private static final class Lift {
        final long startTick;
        /** Captured blocks in the ship's LOCAL (sub-level) frame, taken at
         *  capture; transformed back through the live pose each tick to follow
         *  the structure's true world cells (rotation included) for punch-through. */
        final java.util.List<Vec3> localCenters;
        /** Captured world states (keyed by ORIGINAL position), to restore if
         *  Sable culls this ship mid-lift. */
        final Map<BlockPos, BlockState> captured;
        Lift(final long startTick, final java.util.List<Vec3> localCenters,
             final Map<BlockPos, BlockState> captured) {
            this.startTick = startTick;
            this.localCenters = localCenters;
            this.captured = captured;
        }
    }

    /** Every structure this inducer made + is reeling in, keyed by ship UUID.
     *  Only these ships are ever driven — never random/foreign craft. Kept across
     *  power cycles so a stop/start resumes reeling them. Insertion-ordered. */
    private final Map<UUID, Lift> lifted = new java.util.LinkedHashMap<>();
    /** Last tick the cone was rescanned for new structures. */
    private long lastScanTick = Long.MIN_VALUE;

    /** Diagnostic state surfaced in goggles when debug.goggleDiagnostics is on. */
    private String lastResult = "idle";

    public StructuralInducerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.STRUCTURAL_INDUCER.get(), pos, state);
    }

    /** Block-driven: external redstone changed. Mirrors into POWERED + base flag. */
    public void setExternalSignal(final boolean signal) {
        if (this.externalSignal == signal) return;
        this.externalSignal = signal;
        setChanged();
        setPowered(signal);
        if (level == null) return;
        final BlockState bs = level.getBlockState(getBlockPos());
        if (bs.hasProperty(BlockStateProperties.POWERED)
                && bs.getValue(BlockStateProperties.POWERED) != signal) {
            level.setBlock(getBlockPos(), bs.setValue(BlockStateProperties.POWERED, signal), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Container getRedstoneFuelSlot() {
        return redstoneFuelSlot;
    }

    /** True when the redstone-fuel slot holds an item tagged REDSTONE_FUEL. */
    public boolean hasRedstoneFuel() {
        final net.minecraft.world.item.ItemStack stack = redstoneFuelSlot.getItem(0);
        return !stack.isEmpty() && stack.is(MagTags.REDSTONE_FUEL);
    }

    /** Powered by external signal/FE (base) OR by parked redstone fuel. */
    @Override
    public boolean isPowered() {
        return super.isPowered() || hasRedstoneFuel();
    }

    /** Drop the fuel slot's contents when the block breaks. */
    public void dropRedstoneFuelSlot(final Level level, final BlockPos pos) {
        net.minecraft.world.Containers.dropContents(level, pos, redstoneFuelSlot);
    }

    /** No standard pull field — the inducer acts only on the structure it grabs. */
    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        return null;
    }

    /** Not a field emitter — don't show the "Inactive" field line; the Status
     *  line from {@link #extraTooltipLines} reports what it's doing instead. */
    @Override
    public boolean showsFieldStatus() {
        return false;
    }

    /** Never feed the standard ship-pull pass; we drive our one ship manually. */
    @Override
    protected @Nullable java.util.function.Predicate<ServerSubLevel> shipFilter() {
        return ship -> false;
    }

    @Override
    protected void tickEmitter(final ServerLevel server, final BlockState state,
                               final @Nullable ServerSubLevel host) {
        super.tickEmitter(server, state, host);
        // Contraption-mounted inducers don't lift — world-space op only.
        if (host != null) return;

        if (!isPowered()) {
            // Power dropped: stop reeling (the ships float free) but KEEP tracking
            // them, so a restart resumes pulling the same ships.
            return;
        }
        // Drive every ship we own toward the inducer, each tick.
        driveAll(server);
        // Periodically rescan the cone and capture any NEW structures (already-
        // assembled ones are no longer world blocks, so they aren't re-found).
        final long now = server.getGameTime();
        if (lastScanTick == Long.MIN_VALUE || now - lastScanTick >= SCAN_INTERVAL) {
            lastScanTick = now;
            captureNewStructures(server);
        }
    }

    /** The direction the inducer grabs + pushes along (its facing; default up). */
    private Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(net.minecraft.world.level.block.DirectionalBlock.FACING)
                ? s.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING) : Direction.UP;
    }

    /** Scan depth, configurable via the emitter range setting (4..48). */
    private int scanDepth() {
        final int r = getRangeOverride();
        return net.minecraft.util.Mth.clamp(r > 0 ? r : DEFAULT_DEPTH, 4, 48);
    }

    /** Reel-in force/speed multiplier from the strength buttons. Mirrors the
     *  excavator's "tier controls pull force" model: WEAK reels slowly, EXTREME
     *  hauls a structure in fast. Defaults to the STRONG tier (no override). */
    private double pullMultiplier() {
        return switch (effectiveStrength(com.stonytark.magnetization.api.MagneticStrength.STRONG)) {
            case WEAK -> 0.5d;
            case MEDIUM -> 1.0d;
            case STRONG -> 1.75d;
            case EXTREME -> 3.0d;
            default -> 1.0d;
        };
    }

    /** Scan the cone and assemble EVERY distinct structure in it (up to the
     *  concurrent cap) into its own tracked ship — so all of them get reeled in,
     *  not just one. Already-assembled structures aren't world blocks, so they're
     *  not re-captured. */
    private void captureNewStructures(final ServerLevel server) {
        if (lifted.size() >= MAX_STRUCTURES) return;
        final Direction grabDir = facing().getOpposite();
        final List<List<BlockPos>> structures = collectAllStructures(server, grabDir);
        if (structures.isEmpty()) return;
        int captured = 0;
        for (final List<BlockPos> positions : structures) {
            if (lifted.size() >= MAX_STRUCTURES) break;
            if (assembleAndTrack(server, positions)) captured++;
        }
        if (captured > 0) {
            server.playSound(null, getBlockPos(), SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    getBlockPos().getX() + 0.5, getBlockPos().getY() + 1.0, getBlockPos().getZ() + 0.5,
                    24, SCAN_RADIUS * 0.4, 1.5, SCAN_RADIUS * 0.4, 0.02);
        }
    }

    /** Assemble one structure's blocks into a tracked ship. Returns true on success. */
    private boolean assembleAndTrack(final ServerLevel server, final List<BlockPos> positions) {
        if (positions.isEmpty()) return false;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        final Map<BlockPos, BlockState> cap = new HashMap<>();
        for (final BlockPos p : positions) {
            cap.put(p, server.getBlockState(p));
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        final BoundingBox3i bounds = new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
        final BlockPos anchor = positions.get(0); // lowest block (list is bottom-up)
        try {
            final ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(server, anchor, positions, bounds);
            if (ship.getMassTracker().isInvalid()) {
                final SubLevelContainer container = SubLevelContainer.getContainer(server);
                if (container != null) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
                restoreCaptured(server, cap);
                setResult(server, "assembly mass invalid (" + positions.size() + " blocks) — reverted");
                return false;
            }
            final var pose0 = ship.logicalPose();
            final java.util.List<Vec3> localCenters = new ArrayList<>(positions.size());
            for (final BlockPos op : positions) {
                localCenters.add(pose0.transformPositionInverse(Vec3.atCenterOf(op)));
            }
            lifted.put(ship.getUniqueId(), new Lift(server.getGameTime(), localCenters, cap));
            setResult(server, "reeling in " + lifted.size() + " structure(s)");
            return true;
        } catch (final Throwable t) {
            setResult(server, "assembly threw: " + t.getClass().getSimpleName());
            LOG.error("Structural Inducer assembly failed at {}", getBlockPos().toShortString(), t);
            return false;
        }
    }

    /** Surface the inducer's live operational status (synced) in goggles + WTHIT,
     *  so it reads as working rather than "inactive" (it has no field of its own). */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> extraTooltipLines(final boolean verbose) {
        final java.util.List<net.minecraft.network.chat.Component> lines =
                new java.util.ArrayList<>(super.extraTooltipLines(verbose));
        lines.add(net.minecraft.network.chat.Component.translatable(
                        "tooltip.magnetization.inducer_status", lastResult)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines;
    }

    /** Reel EVERY tracked ship toward the inducer, releasing each one as a free
     *  craft when it arrives or times out, and pruning any that Sable culled. */
    private void driveAll(final ServerLevel server) {
        if (lifted.isEmpty()) return;
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return;
        final var it = lifted.entrySet().iterator();
        int reeling = 0;
        while (it.hasNext()) {
            final var entry = it.next();
            final Lift lift = entry.getValue();
            final var sub = container.getSubLevel(entry.getKey());
            final boolean present = sub instanceof ServerSubLevel;
            final boolean usable = present && !((ServerSubLevel) sub).getMassTracker().isInvalid();
            if (!usable) {
                // Fresh body may read invalid for a tick or two while Sable inits.
                if (present && server.getGameTime() - lift.startTick <= INIT_GRACE_TICKS) { reeling++; continue; }
                // Cull/unload before arriving — remove the dead sub-level and
                // restore the structure's blocks (no ghost left behind).
                if (sub instanceof ServerSubLevel dead) {
                    container.removeSubLevel(dead, SubLevelRemovalReason.REMOVED);
                }
                restoreCaptured(server, lift.captured);
                it.remove();
                continue;
            }
            if (driveOne(server, (ServerSubLevel) sub, lift)) {
                it.remove(); // arrived / timed out — released as a free craft
            } else {
                reeling++;
            }
        }
        if (reeling > 0) setResult(server, "reeling in " + reeling + " structure(s)");
        else if (lifted.isEmpty()) setResult(server, "idle");
    }

    /** Reel one ship toward the inducer. Returns true once it should be released
     *  (arrived or timed out). */
    private boolean driveOne(final ServerLevel server, final ServerSubLevel ship, final Lift lift) {
        final Vec3 target = Vec3.atCenterOf(getBlockPos());
        final var shipPos = ship.logicalPose().position();
        final double dx = target.x - shipPos.x(), dy = target.y - shipPos.y(), dz = target.z - shipPos.z();
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist <= ARRIVAL_DISTANCE || server.getGameTime() - lift.startTick > PULL_TIMEOUT_TICKS) {
            return true; // reeled in (or timed out) — release as a free-floating craft
        }
        final double inv = 1.0 / Math.max(dist, 0.0001);
        final double ux = dx * inv, uy = dy * inv, uz = dz * inv; // unit vector toward inducer
        final double mult = pullMultiplier();
        final double maxSpeed = MAX_PULL_SPEED * mult;
        final double accel = PULL_ACCEL * mult;
        final var v = ship.latestLinearVelocity;
        final double toward = v.x * ux + v.y * uy + v.z * uz;
        if (toward < maxSpeed) {
            final double mass = Math.max(0.0001, ship.getMassTracker().getMass());
            final Vec3 impulse = new Vec3(ux * accel * mass, uy * accel * mass, uz * accel * mass);
            SableBridge.applyWorldImpulse(ship, new Vec3(shipPos.x(), shipPos.y(), shipPos.z()), impulse);
        }
        // Punch through only the world blocks directly on the leading faces of
        // this structure's OWN blocks (never a wide swath of untouched ground).
        punchThrough(ship, lift.localCenters, ux, uy, uz);
        return false;
    }

    /** Break only the world blocks sitting directly on the leading faces of the
     *  structure's OWN blocks — exactly the cells the moving structure is about
     *  to occupy — so it punches through an obstruction in its path the way the
     *  excavator's pulled blocks do. Never clears a wide radius of untouched
     *  ground. The structure's current world cells are projected from its
     *  captured footprint plus how far the ship has travelled since capture.
     *  Skips the structure's own cells, air, fluids, BEs, immune + unbreakable. */
    private void punchThrough(final ServerSubLevel ship, final java.util.List<Vec3> localCenters,
                              final double ux, final double uy, final double uz) {
        if (localCenters.isEmpty()) return;
        final ServerLevel server = (ServerLevel) level;
        // Project each captured block through the ship's LIVE pose (translation +
        // rotation) to its true current world cell — so we follow the structure
        // exactly instead of drifting off when it tilts.
        final var pose = ship.logicalPose();
        final java.util.Set<BlockPos> cells = new java.util.HashSet<>(localCenters.size() * 2);
        for (final Vec3 lc : localCenters) {
            final Vec3 w = pose.transformPosition(lc);
            cells.add(BlockPos.containing(w.x, w.y, w.z));
        }

        // Leading step(s): the axis directions the structure is actually moving.
        final int sx = Math.abs(ux) > 0.3 ? (ux > 0 ? 1 : -1) : 0;
        final int sy = Math.abs(uy) > 0.3 ? (uy > 0 ? 1 : -1) : 0;
        final int sz = Math.abs(uz) > 0.3 ? (uz > 0 ? 1 : -1) : 0;

        final BlockPos inducer = getBlockPos();
        final Direction grabDir = facing().getOpposite();
        int budget = TUNNEL_BUDGET;
        for (final BlockPos cell : cells) {
            if (budget <= 0) break;
            if (sx != 0) budget = breakAhead(server, cell.offset(sx, 0, 0), cells, inducer, grabDir, budget);
            if (budget > 0 && sy != 0) budget = breakAhead(server, cell.offset(0, sy, 0), cells, inducer, grabDir, budget);
            if (budget > 0 && sz != 0) budget = breakAhead(server, cell.offset(0, 0, sz), cells, inducer, grabDir, budget);
        }
    }

    /** Break one world cell in a structure block's path, if it's a breakable
     *  obstruction (not part of the structure, not air/fluid/BE/immune/unbreakable,
     *  not the inducer, and strictly in front of the inducer — never at or behind
     *  it). Returns the remaining budget. */
    private int breakAhead(final ServerLevel server, final BlockPos at,
                           final java.util.Set<BlockPos> structureCells,
                           final BlockPos inducer, final Direction grabDir, final int budget) {
        if (structureCells.contains(at)) return budget;     // the structure's own block
        if (at.equals(inducer)) return budget;              // never the inducer itself
        if (alongDepth(at, inducer, grabDir) < 1) return budget; // only in front of the inducer
        final BlockState bs = server.getBlockState(at);
        if (bs.isAir()) return budget;
        if (!bs.getFluidState().isEmpty()) return budget;
        if (bs.is(MagTags.EXCAVATOR_IMMUNE)) return budget;
        if (server.getBlockEntity(at) != null) return budget; // don't pulverize chests etc.
        if (bs.getDestroySpeed(server, at) < 0) return budget; // bedrock / unbreakable
        Block.dropResources(bs, server, at);
        server.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS);
        return budget - 1;
    }

    /** Offset perpendicular to the scan axis, for a given (u,v) in the face plane. */
    private static BlockPos cellAt(final BlockPos origin, final Direction facing, final int d,
                                   final int u, final int v, final BlockPos.MutableBlockPos cursor) {
        final int dx, dy, dz;
        switch (facing.getAxis()) {
            case X -> { dx = 0; dy = u; dz = v; }
            case Y -> { dx = u; dy = 0; dz = v; }
            default -> { dx = u; dy = v; dz = 0; }
        }
        cursor.set(origin.getX() + facing.getStepX() * d + dx,
                   origin.getY() + facing.getStepY() * d + dy,
                   origin.getZ() + facing.getStepZ() * d + dz);
        return cursor;
    }

    /** Find EVERY distinct structure in the aimed cone (up to {@link #MAX_STRUCTURES}).
     *
     * <p>Walks the cone for grabbable seeds; each unclaimed seed flood-fills its
     * own connected structure (through built blocks AND air, terrain/fluid/immune
     * bounding it), and its blocks are claimed so a later seed in the same
     * building doesn't start a duplicate. Two buildings that aren't connected
     * within reach come back as separate structures, so all of them get pulled. */
    private List<List<BlockPos>> collectAllStructures(final ServerLevel server, final Direction grabDir) {
        final BlockPos origin = getBlockPos();
        final int depth = scanDepth();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final java.util.Set<BlockPos> claimed = new java.util.HashSet<>();
        final List<List<BlockPos>> structures = new ArrayList<>();

        for (int d = 1; d <= depth && structures.size() < MAX_STRUCTURES; d++) {
            for (int u = -SCAN_RADIUS; u <= SCAN_RADIUS; u++) {
                for (int vv = -SCAN_RADIUS; vv <= SCAN_RADIUS; vv++) {
                    cellAt(origin, grabDir, d, u, vv, cursor);
                    if (!isGrabbable(server, cursor)) continue;
                    final BlockPos seed = cursor.immutable();
                    if (claimed.contains(seed)) continue; // already part of a found structure
                    final List<BlockPos> comp = floodComponent(server, seed, grabDir, depth, claimed);
                    if (!comp.isEmpty()) structures.add(comp);
                    if (structures.size() >= MAX_STRUCTURES) return structures;
                }
            }
        }
        return structures;
    }

    /** Flood one structure from {@code seed} through built blocks + air (terrain,
     *  fluids, immune, unbreakable bound it). Grabbable blocks are added to the
     *  component AND to {@code claimed} so other seeds in the same build skip it.
     *  Returns the component sorted bottom-up (so element 0 is the assembly anchor). */
    private List<BlockPos> floodComponent(final ServerLevel server, final BlockPos seed,
                                          final Direction grabDir, final int depth,
                                          final java.util.Set<BlockPos> claimed) {
        final BlockPos origin = getBlockPos();
        final int reach = net.minecraft.util.Mth.clamp(depth, 4, MAX_REACH);
        final List<BlockPos> out = new ArrayList<>();
        final java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        final java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        while (!queue.isEmpty() && out.size() < MAX_BLOCKS && visited.size() < VISIT_CAP) {
            final BlockPos p = queue.poll();
            if (p.equals(origin)) continue;                    // never grab the inducer itself
            if (alongDepth(p, origin, grabDir) < 0) continue;  // stay in front, don't grab behind
            final boolean grabbable = isGrabbable(server, p);
            final boolean air = server.getBlockState(p).isAir();
            if (!grabbable && !air) continue; // terrain / fluid / immune / bedrock = boundary
            if (grabbable) {
                if (!claimed.add(p)) continue; // already in another structure this scan
                out.add(p);
            }
            for (final Direction dir : Direction.values()) {
                final BlockPos n = p.relative(dir);
                if (chebyshev(n, seed) > reach) continue;
                final BlockPos ni = n.immutable();
                if (visited.add(ni)) queue.add(ni);
            }
        }
        out.sort(java.util.Comparator.comparingInt(BlockPos::getY));
        return out;
    }

    /** Depth of {@code p} along {@code grabDir} from the inducer (negative = behind). */
    private static int alongDepth(final BlockPos p, final BlockPos origin, final Direction grabDir) {
        return (p.getX() - origin.getX()) * grabDir.getStepX()
                + (p.getY() - origin.getY()) * grabDir.getStepY()
                + (p.getZ() - origin.getZ()) * grabDir.getStepZ();
    }

    /** Chebyshev (chessboard) distance — bounds the structure flood from the seed. */
    private static int chebyshev(final BlockPos a, final BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }

    private boolean isGrabbable(final ServerLevel server, final BlockPos pos) {
        final BlockState bs = server.getBlockState(pos);
        if (bs.isAir()) return false;
        if (!bs.getFluidState().isEmpty()) return false;          // skip water/lava/ferrofluid
        if (bs.is(MagTags.EXCAVATOR_IMMUNE)) return false;        // protected/important
        if (isTerrain(bs)) return false;                          // leave the ground behind
        if (bs.getDestroySpeed(server, pos) < 0) return false;     // bedrock / unbreakable
        // NOTE: block entities (chests, beds, etc.) ARE grabbed — they're part of
        // the building and Sable carries their data with the assembled craft.
        return true;
    }

    /** Natural ground/terrain the inducer should never grab (so a building lifts
     *  off cleanly instead of ripping up the land). */
    private static boolean isTerrain(final BlockState bs) {
        return bs.is(net.minecraft.tags.BlockTags.DIRT)
                || bs.is(net.minecraft.tags.BlockTags.SAND)
                || bs.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || bs.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
                || bs.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                || bs.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)
                || bs.is(net.minecraft.world.level.block.Blocks.FARMLAND)
                || bs.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || bs.is(net.minecraft.world.level.block.Blocks.CLAY)
                || bs.is(net.minecraft.world.level.block.Blocks.MUD)
                || bs.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                || bs.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                || bs.is(net.minecraft.world.level.block.Blocks.PODZOL);
    }

    /** Set the operational status and push it to clients (goggle/WTHIT readout). */
    private void setResult(final ServerLevel server, final String result) {
        if (this.lastResult.equals(result)) return;
        this.lastResult = result;
        setChanged();
        markForClientSync(server);
    }

    @Override
    protected void saveAdditional(final net.minecraft.nbt.CompoundTag tag,
                                  final net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("LastResult", lastResult);
        tag.put("RedstoneFuel", redstoneFuelSlot.createTag(registries));
    }

    @Override
    protected void loadAdditional(final net.minecraft.nbt.CompoundTag tag,
                                  final net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LastResult")) lastResult = tag.getString("LastResult");
        redstoneFuelSlot.fromTag(tag.getList("RedstoneFuel", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }

    private void restoreCaptured(final ServerLevel server, final Map<BlockPos, BlockState> captured) {
        for (final Map.Entry<BlockPos, BlockState> e : captured.entrySet()) {
            if (server.getBlockState(e.getKey()).isAir()) {
                server.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            }
        }
    }
}
