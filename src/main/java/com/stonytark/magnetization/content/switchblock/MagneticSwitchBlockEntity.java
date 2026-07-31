package com.stonytark.magnetization.content.switchblock;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Tracks the proximity of the nearest sub-level. Recomputes every {@link #PERIOD}
 * ticks (cheap query, but no need to fire every tick), updates a 0–15 signal,
 * and pings neighbors when the value changes.
 *
 * <p>Works both in the open world and mounted on a Sable contraption. On a ship
 * the vanilla block-entity ticker does NOT run, so it also ticks via
 * {@link BlockEntitySubLevelActor#sable$tick}; and because {@code getBlockPos()}
 * is sub-level-LOCAL there, its own position is promoted to world space (via the
 * host pose) before scanning — otherwise the search box sits in empty plot space
 * and the switch "can't detect sub-levels while on a sub-level".
 */
public class MagneticSwitchBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    /** Default scan radius. Server owners override via
     *  {@code MagConfig.MAGNETIC_SWITCH_RANGE}; this is the fallback for
     *  early-load / unit-test contexts. */
    public static final double SCAN_RADIUS = 8.0d;
    private static final int PERIOD = 4;

    private static double scanRadius() {
        try { return com.stonytark.magnetization.config.MagConfig.MAGNETIC_SWITCH_RANGE.get(); }
        catch (final Throwable t) { return SCAN_RADIUS; }
    }

    private int signal = 0;
    private int phase = 0;

    public MagneticSwitchBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_SWITCH.get(), pos, state);
    }

    public int signal() {
        return signal;
    }

    /** Vanilla ticker (open world / off-ship). {@code subLevelAt} resolves a host
     *  if this somehow runs on a contraption; normally it returns null here. */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final MagneticSwitchBlockEntity be) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(state)) return;
        if (!(level instanceof ServerLevel server)) return;
        be.run(server, SableBridge.subLevelAt(server, pos));
    }

    /** Sable sub-level tick: we're mounted on this ship — scan from its world pose. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (level instanceof ServerLevel server) run(server, subLevel);
    }

    private void run(final ServerLevel server, final @Nullable ServerSubLevel host) {
        if ((phase++ % PERIOD) != 0) return;
        final int next = computeSignal(server, host);
        if (next != signal) {
            signal = next;
            // Force a comparator/redstone neighbor update.
            server.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
        }
    }

    private int computeSignal(final ServerLevel level, final @Nullable ServerSubLevel host) {
        // On a contraption, getBlockPos()/getCenter() are sub-level-LOCAL plot
        // coordinates. Promote our own centre to world space via the host pose so we
        // search where the ship actually is; off-ship the centre is already world space.
        final Vec3 localCenter = getBlockPos().getCenter();
        final Vec3 origin = host != null
                ? host.logicalPose().transformPosition(localCenter)
                : localCenter;

        final double radius = scanRadius();
        final BoundingBox3d searchBox = new BoundingBox3d(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius
        );
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 0;

        // Riding a ship: exclude our own craft (and everything joined to it), or the
        // switch would peg at 15 forever detecting the very contraption it sits on.
        final Set<UUID> ownChain = host != null
                ? SableBridge.connectedChainIds(host, level.getGameTime())
                : Set.of();

        double bestDist = Double.MAX_VALUE;
        for (SubLevel sub : container.queryIntersecting(searchBox)) {
            if (ownChain.contains(sub.getUniqueId())) continue;
            final BoundingBox3dc box = sub.boundingBox();
            final double dx = origin.x - clamp(origin.x, box.minX(), box.maxX());
            final double dy = origin.y - clamp(origin.y, box.minY(), box.maxY());
            final double dz = origin.z - clamp(origin.z, box.minZ(), box.maxZ());
            final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < bestDist) bestDist = d;
        }
        if (bestDist == Double.MAX_VALUE) return 0;
        // Linear ramp: 0 at scan radius, 15 at distance 0.
        final double t = Math.max(0.0d, 1.0d - bestDist / radius);
        return Math.min(15, (int) Math.round(t * 15.0d));
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
