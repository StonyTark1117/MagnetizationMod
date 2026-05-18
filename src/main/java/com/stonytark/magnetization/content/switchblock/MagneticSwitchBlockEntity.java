package com.stonytark.magnetization.content.switchblock;

import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tracks the proximity of the nearest sub-level. Recomputes every {@link #PERIOD}
 * ticks (cheap query, but no need to fire every tick), updates a 0–15 signal,
 * and pings neighbors when the value changes.
 */
public class MagneticSwitchBlockEntity extends BlockEntity {

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

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final MagneticSwitchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if ((be.phase++ % PERIOD) != 0) return;

        final int next = be.computeSignal(server);
        if (next != be.signal) {
            be.signal = next;
            // Force a comparator/redstone neighbor update.
            server.updateNeighborsAt(pos, state.getBlock());
        }
    }

    private int computeSignal(final ServerLevel level) {
        final var origin = getBlockPos().getCenter();
        final double radius = scanRadius();
        final BoundingBox3d searchBox = new BoundingBox3d(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius
        );
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 0;
        double bestDist = Double.MAX_VALUE;
        for (SubLevel sub : container.queryIntersecting(searchBox)) {
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
