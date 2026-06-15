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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ferrofluid creeps toward (or away from) nearby magnets by growing a path of
 * auxiliary fluid cells — the player's poured fluid stays put.
 *
 * <ul>
 *   <li><b>Plain</b> ferrofluid grows a path TOWARD any field source (any pole).</li>
 *   <li><b>Magnetized</b> ferrofluid grows TOWARD an opposing pole and AWAY from a
 *       matching one (a path trying to leave the field), and reacts faster.</li>
 * </ul>
 *
 * <p>Each grown cell is tracked in {@link FerrofluidCreepRegistry}; when no magnet
 * drives a cell any more (the emitter powered off / was removed) the path recedes
 * back to normal. Magnets are powered emitters with a live field plus the player's
 * own magnetized-ferrofluid <em>source</em> blocks (creep-added cells don't count
 * as magnets, so paths recede and don't self-sustain).
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FerrofluidCreepHandler {

    private static final long MAG_INTERVAL = 4L;    // magnetized grows this often
    private static final long PLAIN_INTERVAL = 12L; // plain grows this often (slower)
    private static final long RECEDE_INTERVAL = 8L; // recede unsupported cells this often
    /** Scan bound per magnet — ferrofluid reacts out to (about) the field's reach,
     *  capped so the per-tick cube scan stays affordable. */
    private static final int MAX_CREEP_RADIUS = 16;
    private static final double ARRIVE_DIST = 1.7d; // a tendril this close has reached

    private FerrofluidCreepHandler() {}

    /** One field source the fluid reacts to: where it is and its pole. */
    private record Source(Vec3 origin, MagneticPolarity polarity, int radius) {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final long time = server.getGameTime();
        final boolean growMag = (time % MAG_INTERVAL) == 0L;
        final boolean growPlain = (time % PLAIN_INTERVAL) == 0L;
        final boolean recede = (time % RECEDE_INTERVAL) == 0L;
        if (!growMag && !growPlain && !recede) return;

        final List<Source> sources = gatherSources(server);

        if (recede) recedeUnsupported(server, sources);
        if (sources.isEmpty()) return;

        for (final Source s : sources) {
            if (growPlain || growMag) growAttract(server, s, growPlain, growMag);
            if (growMag) growRepel(server, s);
        }
    }

    /** Powered emitters with a live field + the player's magnetized-ferrofluid
     *  SOURCE blocks. Creep-added cells are excluded so they don't act as magnets
     *  (which would stop paths ever receding). */
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
            if (FerrofluidCreepRegistry.contains(server, e.getKey())) continue; // not a creep cell
            sources.add(new Source(Vec3.atCenterOf(e.getKey()), e.getValue(), magRadius));
        }
        return sources;
    }

    /** Grow the path one cell toward {@code s} from the eligible source cell whose
     *  step toward the magnet lands closest to it. Plain reacts to any pole;
     *  magnetized only to an opposing pole. Stops once a tendril has arrived. */
    private static void growAttract(final ServerLevel server, final Source s,
                                    final boolean growPlain, final boolean growMag) {
        final int r = s.radius;
        final BlockPos c = BlockPos.containing(s.origin.x, s.origin.y, s.origin.z);
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double minSourceSq = Double.MAX_VALUE; // reached gate — SOURCE cells only
        BlockPos bestStep = null;
        double bestStepDist = Double.MAX_VALUE;
        BlockState bestState = null;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            if (!server.isLoaded(m)) continue;
            final BlockState st = server.getBlockState(m);
            if (st.getFluidState().isEmpty()) continue; // grow from source OR flowing edge
            final boolean plain = st.is(MagBlocks.FERROFLUID_BLOCK.get());
            final boolean mag = st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get());
            if (plain ? !growPlain : mag ? !growMag : true) continue;
            if (mag) {
                final MagneticPolarity pole = st.getValue(MagnetizedFerrofluidBlock.POLARITY);
                if (s.polarity == MagneticPolarity.NONE || pole == MagneticPolarity.NONE
                        || pole == s.polarity) continue; // matching/none → not attracted
            }
            final double d = s.origin.distanceToSqr(Vec3.atCenterOf(m));
            if (d > (double) r * r) continue;
            // Only SOURCE cells count toward "reached" — a flowing edge spreading
            // near the magnet must NOT false-flag arrival (that killed plain creep).
            if (st.getFluidState().isSource() && d < minSourceSq) minSourceSq = d;
            final BlockPos step = bestStep(server, m.immutable(), s.origin, true);
            if (step == null) continue;
            final double sd = s.origin.distanceToSqr(Vec3.atCenterOf(step));
            if (sd < bestStepDist) {
                bestStepDist = sd;
                bestStep = step;
                bestState = stateFor(st);
            }
        }
        if (minSourceSq <= ARRIVE_DIST * ARRIVE_DIST) return; // a tendril already reached
        place(server, bestStep, bestState);
    }

    /** Grow a path one cell AWAY from {@code s} (a path trying to leave the field)
     *  from the magnetized matching-pole source cell whose away-step lands farthest
     *  from the magnet, bounded by the scan radius. */
    private static void growRepel(final ServerLevel server, final Source s) {
        final int r = s.radius;
        final BlockPos c = BlockPos.containing(s.origin.x, s.origin.y, s.origin.z);
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos bestStep = null;
        double bestStepDist = -1.0;
        BlockState bestState = null;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            if (!server.isLoaded(m)) continue;
            final BlockState st = server.getBlockState(m);
            if (st.getFluidState().isEmpty() || !st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())) continue;
            final MagneticPolarity pole = st.getValue(MagnetizedFerrofluidBlock.POLARITY);
            if (s.polarity == MagneticPolarity.NONE || pole != s.polarity) continue; // only like poles repel
            final double d = s.origin.distanceToSqr(Vec3.atCenterOf(m));
            if (d > (double) r * r) continue;
            final BlockPos step = bestStep(server, m.immutable(), s.origin, false);
            if (step == null) continue;
            final double sd = s.origin.distanceToSqr(Vec3.atCenterOf(step));
            if (sd <= (double) r * r && sd > bestStepDist) {
                bestStepDist = sd;
                bestStep = step;
                bestState = stateFor(st);
            }
        }
        place(server, bestStep, bestState);
    }

    /** Remove creep cells no magnet drives any more, so the path recedes to normal. */
    private static void recedeUnsupported(final ServerLevel server, final List<Source> sources) {
        for (final BlockPos pos : FerrofluidCreepRegistry.snapshot(server)) {
            if (!server.isLoaded(pos)) continue;
            final BlockState st = server.getBlockState(pos);
            if (!st.is(MagBlocks.FERROFLUID_BLOCK.get()) && !st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())) {
                FerrofluidCreepRegistry.remove(server, pos); // no longer our fluid — drop tracking
                continue;
            }
            boolean driven = false;
            final Vec3 cc = Vec3.atCenterOf(pos);
            for (final Source s : sources) {
                if (s.origin.distanceToSqr(cc) <= (double) s.radius * s.radius) { driven = true; break; }
            }
            if (!driven) {
                server.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                FerrofluidCreepRegistry.remove(server, pos);
            }
        }
    }

    private static BlockState stateFor(final BlockState frontier) {
        if (frontier.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())) {
            return MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get().defaultBlockState()
                    .setValue(MagnetizedFerrofluidBlock.POLARITY,
                            frontier.getValue(MagnetizedFerrofluidBlock.POLARITY));
        }
        return MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState();
    }

    private static void place(final ServerLevel server, final BlockPos at, final BlockState state) {
        if (at == null || state == null) return;
        server.setBlock(at, state, Block.UPDATE_ALL);
        FerrofluidCreepRegistry.add(server, at);
    }

    /** The air/replaceable neighbour of {@code cell} stepping toward (or away from)
     *  the magnet origin — strictly closer (toward) / farther (away). Null if none. */
    private static BlockPos bestStep(final ServerLevel server, final BlockPos cell,
                                     final Vec3 origin, final boolean toward) {
        final Vec3 dir = toward ? origin.subtract(Vec3.atCenterOf(cell))
                : Vec3.atCenterOf(cell).subtract(origin);
        final double here = origin.distanceToSqr(Vec3.atCenterOf(cell));
        BlockPos best = null;
        double bestDot = 0.0; // require a positive component in the chosen direction
        for (final Direction d : Direction.values()) {
            final double dot = d.getStepX() * dir.x + d.getStepY() * dir.y + d.getStepZ() * dir.z;
            if (dot <= bestDot) continue;
            final BlockPos np = cell.relative(d);
            final double nd = origin.distanceToSqr(Vec3.atCenterOf(np));
            if (toward ? nd >= here : nd <= here) continue; // must make progress
            final BlockState ns = server.getBlockState(np);
            if (!ns.isAir() && !(ns.canBeReplaced() && ns.getFluidState().isEmpty())) continue;
            best = np.immutable();
            bestDot = dot;
        }
        return best;
    }
}
