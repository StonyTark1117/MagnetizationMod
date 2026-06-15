package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes ferrofluid <em>creep</em> through the world toward (or away from) nearby
 * magnetic field sources, like real ferrofluid piling onto a magnet:
 *
 * <ul>
 *   <li><b>Plain</b> ferrofluid is drawn toward <em>any</em> field source
 *       (emitter or magnetized-ferrofluid pool), regardless of polarity — slowly.</li>
 *   <li><b>Magnetized</b> ferrofluid is drawn toward an <em>opposing</em> pole and
 *       pushed away from a <em>matching</em> one (like poles repel), and creeps
 *       faster than the plain kind.</li>
 * </ul>
 *
 * <p>Each step relocates one source cell into an adjacent air cell in the best
 * direction. Field sources are read from {@link EmitterRegistry} (powered
 * emitters with a live {@link MagneticField}) plus the magnetized-ferrofluid
 * registry. Scans are bounded by {@link #MAX_CREEP_RADIUS} around each source,
 * so cost stays proportional to active sources, not world size.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FerrofluidCreepHandler {

    /** Magnetized ferrofluid takes a creep step this often (fast). */
    private static final long MAG_INTERVAL = 4L;
    /** Plain ferrofluid takes a creep step this often (3× slower than magnetized). */
    private static final long PLAIN_INTERVAL = 12L;
    /** Cap on the scan box half-width around each source (perf bound). */
    private static final int MAX_CREEP_RADIUS = 5;

    private FerrofluidCreepHandler() {}

    /** One field source the fluid reacts to: where it is and its pole. */
    private record Source(Vec3 origin, MagneticPolarity polarity, int radius) {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final long time = server.getGameTime();
        final boolean moveMag = (time % MAG_INTERVAL) == 0L;
        final boolean movePlain = (time % PLAIN_INTERVAL) == 0L;
        if (!moveMag && !movePlain) return;

        final List<Source> sources = gatherSources(server);
        if (sources.isEmpty()) return;

        // Grow ONE fluid cell of the nearest tendril toward each magnet this tick
        // (the source stays put — the auxiliary fluid creeps out to build a path).
        final Set<BlockPos> placed = new HashSet<>();
        for (final Source s : sources) {
            extendPath(server, s, moveMag, movePlain, placed);
        }
    }

    /** Powered emitters with a live field + every magnetized-ferrofluid source. */
    private static List<Source> gatherSources(final ServerLevel server) {
        final List<Source> sources = new ArrayList<>();
        EmitterRegistry.forEach(server, (lvl, pos) -> {
            if (!(lvl.getBlockEntity(pos) instanceof MagneticFieldSource src)) return;
            final MagneticField f = src.currentField();
            if (f == null) return;
            sources.add(new Source(f.origin(), f.polarity(),
                    Math.min(MAX_CREEP_RADIUS, (int) Math.ceil(f.range()))));
        });
        final Map<BlockPos, MagneticPolarity> mag = MagnetizedFerrofluidRegistry.forLevel(server);
        final int magRadius = Math.min(MAX_CREEP_RADIUS, (int) Math.ceil(MagneticStrength.WEAK.range()));
        for (final Map.Entry<BlockPos, MagneticPolarity> e : mag.entrySet()) {
            sources.add(new Source(Vec3.atCenterOf(e.getKey()), e.getValue(), magRadius));
        }
        return sources;
    }

    /** Distance at which a tendril counts as having reached the magnet (stop growing). */
    private static final double ARRIVE_DIST = 1.7d;

    /** Grow the nearest reacting tendril one cell toward (the magnet) {@code s}.
     *  The fluid's existing blocks stay put; we just place one more fluid cell in
     *  front of the closest tendril, building a path from the fluid to the magnet
     *  — plain toward any field, magnetized toward an opposing pole only. */
    private static void extendPath(final ServerLevel server, final Source s,
                                   final boolean moveMag, final boolean movePlain,
                                   final Set<BlockPos> placed) {
        final int r = s.radius;
        final BlockPos center = BlockPos.containing(s.origin.x, s.origin.y, s.origin.z);
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double globalMin = Double.MAX_VALUE;
        BlockPos bestStep = null;
        double bestStepDist = Double.MAX_VALUE;
        BlockState bestState = null;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!server.isLoaded(m)) continue;
                    final BlockState st = server.getBlockState(m);
                    final boolean plain = st.is(MagBlocks.FERROFLUID_BLOCK.get());
                    final boolean mag = st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get());
                    if (!plain && !mag) continue;
                    final boolean eligible;
                    if (plain) {
                        eligible = movePlain; // drawn to any field source
                    } else if (!moveMag) {
                        eligible = false;
                    } else {
                        final MagneticPolarity pole = st.getValue(MagnetizedFerrofluidBlock.POLARITY);
                        eligible = s.polarity != MagneticPolarity.NONE && pole != MagneticPolarity.NONE
                                && pole != s.polarity; // toward an opposing pole only
                    }
                    if (!eligible) continue;

                    final double d = s.origin.distanceToSqr(Vec3.atCenterOf(m));
                    if (d > (double) r * r) continue;
                    if (d < globalMin) globalMin = d;
                    final BlockPos cell = m.immutable();
                    final BlockPos step = stepTowardMagnet(server, cell, s.origin);
                    if (step == null || placed.contains(step)) continue;
                    final double sd = s.origin.distanceToSqr(Vec3.atCenterOf(step));
                    if (sd < bestStepDist) {
                        bestStepDist = sd;
                        bestStep = step;
                        bestState = plain
                                ? MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState()
                                : MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get().defaultBlockState()
                                        .setValue(MagnetizedFerrofluidBlock.POLARITY,
                                                st.getValue(MagnetizedFerrofluidBlock.POLARITY));
                    }
                }
            }
        }
        // A tendril already reaches the magnet — don't keep growing.
        if (globalMin <= ARRIVE_DIST * ARRIVE_DIST) return;
        if (bestStep != null) {
            server.setBlock(bestStep, bestState, Block.UPDATE_ALL);
            placed.add(bestStep);
        }
    }

    /** The air/replaceable neighbour of {@code cell} that steps closest toward the
     *  magnet origin (must be strictly closer than {@code cell}). Null if none. */
    private static BlockPos stepTowardMagnet(final ServerLevel server, final BlockPos cell, final Vec3 origin) {
        final Vec3 toOrigin = origin.subtract(Vec3.atCenterOf(cell));
        final double here = origin.distanceToSqr(Vec3.atCenterOf(cell));
        BlockPos best = null;
        double bestDot = 0.0; // positive component toward the magnet required
        for (final Direction dr : Direction.values()) {
            final double dot = dr.getStepX() * toOrigin.x + dr.getStepY() * toOrigin.y + dr.getStepZ() * toOrigin.z;
            if (dot <= bestDot) continue;
            final BlockPos np = cell.relative(dr);
            if (origin.distanceToSqr(Vec3.atCenterOf(np)) >= here) continue; // must get closer
            final BlockState ns = server.getBlockState(np);
            if (!ns.isAir() && !(ns.canBeReplaced() && ns.getFluidState().isEmpty())) continue;
            best = np.immutable();
            bestDot = dot;
        }
        return best;
    }
}
