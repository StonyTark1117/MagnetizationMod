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
import net.minecraft.server.level.ServerLevel;
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
public class StructuralInducerBlockEntity extends AbstractEmitterBlockEntity {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/StructuralInducer");

    /** Footprint half-width above the inducer (radius 4 → 9×9 column). */
    private static final int SCAN_RADIUS = 4;
    /** How tall a structure the inducer reaches above itself. */
    private static final int SCAN_HEIGHT = 16;
    /** Hard cap on captured blocks — keeps one assembly bounded. */
    private static final int MAX_BLOCKS = 512;
    /** Blocks of clearance the structure must rise before it's released. */
    private static final double LIFT_HEIGHT = 8.0d;
    /** Upward acceleration per tick (m/s²); must exceed Sable's scaled gravity. */
    private static final double LIFT_ACCEL = 16.0d;
    /** Cap on the structure's climb speed so the lift reads as an animation. */
    private static final double MAX_LIFT_SPEED = 6.0d;
    /** Absolute age cap on a lift before we give up and release it. */
    private static final long LIFT_TIMEOUT_TICKS = 600L;

    /** External redstone signal, mirrored into block-state POWERED. */
    private boolean externalSignal = false;
    /** True until a capture fires; re-armed on a fresh power cycle. */
    private boolean armed = true;

    /** The in-flight lifted structure, if any. */
    private @Nullable UUID liftedShipId = null;
    private double liftOriginY = 0.0d;
    private long liftStartTick = 0L;
    /** Captured world states, for restoring if Sable culls the ship mid-lift. */
    private final Map<BlockPos, BlockState> captured = new HashMap<>();

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

    /** No standard pull field — the inducer acts only on the structure it grabs. */
    @Override
    protected @Nullable MagneticField computeField(final BlockState state) {
        return null;
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
            // Power dropped: stop lifting (the ship floats free) and re-arm.
            liftedShipId = null;
            captured.clear();
            armed = true;
            return;
        }
        if (liftedShipId != null) {
            driveLift(server);
        } else if (armed) {
            armed = false;
            tryCapture(server);
        }
    }

    /** Scan the box above, gate on clearance, assemble into one lifting ship. */
    private void tryCapture(final ServerLevel server) {
        final List<BlockPos> positions = collectStructure(server);
        if (positions.isEmpty()) return;
        if (!hasClearanceAbove(server, positions)) {
            // Capped by a ceiling — can't lift cleanly. Buzz and bail.
            server.playSound(null, getBlockPos(), SoundEvents.LODESTONE_BREAK, SoundSource.BLOCKS, 0.5f, 0.7f);
            return;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        captured.clear();
        for (final BlockPos p : positions) {
            captured.put(p, server.getBlockState(p));
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
                restoreCaptured(server);
                captured.clear();
                return;
            }
            liftedShipId = ship.getUniqueId();
            liftOriginY = ship.logicalPose().position().y();
            liftStartTick = server.getGameTime();
            server.playSound(null, getBlockPos(), SoundEvents.LODESTONE_PLACE, SoundSource.BLOCKS, 0.8f, 0.8f);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    getBlockPos().getX() + 0.5, getBlockPos().getY() + 1.0, getBlockPos().getZ() + 0.5,
                    24, SCAN_RADIUS * 0.4, 1.5, SCAN_RADIUS * 0.4, 0.02);
        } catch (final Throwable t) {
            LOG.error("Structural Inducer assembly failed at {}", getBlockPos().toShortString(), t);
            captured.clear();
        }
    }

    /** Drive the captured ship upward until it clears the ground, then release. */
    private void driveLift(final ServerLevel server) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) { liftedShipId = null; captured.clear(); return; }
        if (!(container.getSubLevel(liftedShipId) instanceof ServerSubLevel ship)
                || ship.getMassTracker().isInvalid()) {
            // Ship vanished (cull/unload) before lifting — put the blocks back.
            restoreCaptured(server);
            liftedShipId = null;
            captured.clear();
            return;
        }
        final var shipPos = ship.logicalPose().position();
        final double risen = shipPos.y() - liftOriginY;
        if (risen >= LIFT_HEIGHT || server.getGameTime() - liftStartTick > LIFT_TIMEOUT_TICKS) {
            // Cleared the ground — let it float free.
            liftedShipId = null;
            captured.clear();
            return;
        }
        if (ship.latestLinearVelocity.y < MAX_LIFT_SPEED) {
            final double mass = Math.max(0.0001, ship.getMassTracker().getMass());
            final Vec3 impulse = new Vec3(0.0d, LIFT_ACCEL * mass, 0.0d);
            SableBridge.applyWorldImpulse(ship, new Vec3(shipPos.x(), shipPos.y(), shipPos.z()), impulse);
        }
    }

    /** Solid blocks in the box directly above the inducer, bottom-up, capped. */
    private List<BlockPos> collectStructure(final ServerLevel server) {
        final BlockPos origin = getBlockPos();
        final List<BlockPos> out = new ArrayList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= SCAN_HEIGHT && out.size() < MAX_BLOCKS; dy++) {
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!isGrabbable(server, cursor)) continue;
                    out.add(cursor.immutable());
                    if (out.size() >= MAX_BLOCKS) return out;
                }
            }
        }
        return out;
    }

    private boolean isGrabbable(final ServerLevel server, final BlockPos pos) {
        final BlockState bs = server.getBlockState(pos);
        if (bs.isAir()) return false;
        if (!bs.getFluidState().isEmpty()) return false;          // skip water/lava/ferrofluid
        if (bs.is(MagTags.EXCAVATOR_IMMUNE)) return false;        // protected/important
        if (server.getBlockEntity(pos) != null) return false;     // chests, emitters, etc.
        if (bs.getDestroySpeed(server, pos) < 0) return false;     // bedrock / unbreakable
        return true;
    }

    /** True if the layer directly above the captured structure's top is all air. */
    private boolean hasClearanceAbove(final ServerLevel server, final List<BlockPos> positions) {
        int maxY = Integer.MIN_VALUE;
        for (final BlockPos p : positions) maxY = Math.max(maxY, p.getY());
        final BlockPos origin = getBlockPos();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                cursor.set(origin.getX() + dx, maxY + 1, origin.getZ() + dz);
                final BlockState bs = server.getBlockState(cursor);
                if (!bs.isAir() && bs.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }

    private void restoreCaptured(final ServerLevel server) {
        for (final Map.Entry<BlockPos, BlockState> e : captured.entrySet()) {
            if (server.getBlockState(e.getKey()).isAir()) {
                server.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            }
        }
    }
}
