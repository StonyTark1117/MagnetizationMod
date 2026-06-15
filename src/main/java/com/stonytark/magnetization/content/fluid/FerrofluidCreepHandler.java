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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ferrofluid reacts to magnets by growing a path of auxiliary fluid cells from
 * the player's fluid to the field source. Instead of cube-scanning each magnet's
 * (up to 128-block) field, it iterates the small set of ferrofluid SOURCE cells
 * (from {@link FerrofluidSourceRegistry} + {@link MagnetizedFerrofluidRegistry}),
 * tests each against active fields by distance, and advances the nearest one.
 *
 * <ul>
 *   <li><b>Plain</b> grows TOWARD any magnet (any pole), at half the magnetized rate.</li>
 *   <li><b>Magnetized</b> grows TOWARD an opposing pole and AWAY from a matching one.</li>
 * </ul>
 *
 * <p>Grown cells are tracked in {@link FerrofluidCreepRegistry} and recede when no
 * magnet drives them. A magnetized cell's OWN-pole fluid pool does NOT sustain it
 * (it's the cell's own body) — only emitters and opposing-pole pools do — so a
 * magnetized tendril recedes when its (opposing) magnet powers off even though the
 * fluid it grew from is itself a weak magnet.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class FerrofluidCreepHandler {

    private static final long MAG_INTERVAL = 4L;     // magnetized grows this often
    private static final long PLAIN_INTERVAL = 8L;   // plain grows half as often
    private static final long RECEDE_INTERVAL = 8L;  // recede unsupported cells this often
    private static final double ARRIVE_DIST = 1.7d;  // a tendril this close has reached

    private FerrofluidCreepHandler() {}

    /** An active magnet: where it is, its pole, its field reach, and whether it's
     *  a real emitter (vs a magnetized-ferrofluid pool). */
    private record Magnet(Vec3 origin, MagneticPolarity polarity, double range, boolean emitter) {
        boolean covers(final Vec3 p) { return origin.distanceToSqr(p) <= range * range; }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final long time = server.getGameTime();
        final boolean growMag = (time % MAG_INTERVAL) == 0L;
        final boolean growPlain = (time % PLAIN_INTERVAL) == 0L;
        final boolean recede = (time % RECEDE_INTERVAL) == 0L;
        if (!growMag && !growPlain && !recede) return;

        final List<Magnet> magnets = gatherMagnets(server);
        if (recede) recedeUnsupported(server, magnets);
        if (magnets.isEmpty() || (!growMag && !growPlain)) return;

        final List<BlockPos> anchors = gatherAnchors(server); // fluid SOURCE cells
        if (anchors.isEmpty()) return;
        for (final Magnet m : magnets) grow(server, m, anchors, growPlain, growMag);
    }

    /** Real emitters (live field) + magnetized-ferrofluid pools, EXCLUDING creep
     *  cells (so a path doesn't act as its own magnet). */
    private static List<Magnet> gatherMagnets(final ServerLevel server) {
        final List<Magnet> magnets = new ArrayList<>();
        EmitterRegistry.forEach(server, (lvl, pos) -> {
            if (!(lvl.getBlockEntity(pos) instanceof MagneticFieldSource src)) return;
            final MagneticField f = src.currentField();
            if (f == null) return;
            magnets.add(new Magnet(f.origin(), f.polarity(), f.range(), true));
        });
        for (final Map.Entry<BlockPos, MagneticPolarity> e : MagnetizedFerrofluidRegistry.forLevel(server).entrySet()) {
            if (FerrofluidCreepRegistry.contains(server, e.getKey())) continue;
            magnets.add(new Magnet(Vec3.atCenterOf(e.getKey()), e.getValue(), MagneticStrength.WEAK.range(), false));
        }
        return magnets;
    }

    /** Every ferrofluid SOURCE cell (plain + magnetized), pruning registry entries
     *  that are no longer a source (drained / flowed away). */
    private static List<BlockPos> gatherAnchors(final ServerLevel server) {
        final Set<BlockPos> out = new HashSet<>();
        for (final BlockPos p : FerrofluidSourceRegistry.snapshot(server)) {
            final BlockState st = server.getBlockState(p);
            if (isPlain(st) && st.getFluidState().isSource()) out.add(p);
            else FerrofluidSourceRegistry.remove(server, p);
        }
        for (final BlockPos p : MagnetizedFerrofluidRegistry.forLevel(server).keySet()) {
            final BlockState st = server.getBlockState(p);
            if (st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get()) && st.getFluidState().isSource()) out.add(p);
        }
        return new ArrayList<>(out);
    }

    /** Advance the path one cell for magnet {@code m}: the nearest reacting anchor
     *  that can step toward (attract) / away (repel). Plain attracts to any pole;
     *  magnetized attracts to an opposing pole, repels a matching one. */
    private static void grow(final ServerLevel server, final Magnet m, final List<BlockPos> anchors,
                             final boolean growPlain, final boolean growMag) {
        boolean reachedAttract = false;
        BlockPos attractStep = null, repelStep = null;
        double attractStepSq = Double.MAX_VALUE, repelAnchorSq = Double.MAX_VALUE;
        BlockState attractState = null, repelState = null;

        for (final BlockPos a : anchors) {
            final Vec3 ac = Vec3.atCenterOf(a);
            if (!m.covers(ac)) continue;
            final BlockState st = server.getBlockState(a);
            final boolean plain = isPlain(st);
            final boolean mag = st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get());
            if (!plain && !mag) continue;

            boolean attract;
            if (plain) {
                if (!growPlain) continue;
                attract = true;
            } else {
                if (!growMag) continue;
                final MagneticPolarity pole = st.getValue(MagnetizedFerrofluidBlock.POLARITY);
                if (m.polarity == MagneticPolarity.NONE || pole == MagneticPolarity.NONE) continue;
                attract = pole != m.polarity; // opposing → attract, matching → repel
            }

            if (attract) {
                if (m.origin.distanceToSqr(ac) <= ARRIVE_DIST * ARRIVE_DIST) reachedAttract = true;
                final BlockPos step = bestStep(server, a, m.origin, true, st.getBlock());
                if (step != null) {
                    final double sq = m.origin.distanceToSqr(Vec3.atCenterOf(step));
                    if (sq < attractStepSq) { attractStepSq = sq; attractStep = step; attractState = stateFor(st); }
                }
            } else {
                // Flee from the cell CLOSEST to the magnet (the most-repelled edge),
                // so the fluid starts moving away promptly instead of building from
                // the far end.
                final BlockPos step = bestStep(server, a, m.origin, false, st.getBlock());
                if (step != null) {
                    final double anchorSq = m.origin.distanceToSqr(ac);
                    if (anchorSq < repelAnchorSq) { repelAnchorSq = anchorSq; repelStep = step; repelState = stateFor(st); }
                }
            }
        }
        if (!reachedAttract) place(server, attractStep, attractState);
        place(server, repelStep, repelState);
    }

    /** Cap on cells cleared per recede pass (across all tendrils), so a runaway
     *  flood can't stall the tick. */
    private static final int RECEDE_BUDGET = 1024;

    /** Recede tendrils no magnet drives any more. For each unsustained creep cell
     *  we flood-clear the WHOLE connected ferrofluid body it belongs to — creep
     *  cells AND the flowing spread they fed — stopping at the player's ORIGINAL
     *  (non-creep) source pools, which stay. Single-pass removal beats fluid
     *  reflow (removing cells one at a time just lets neighbours flow back in).
     *  Emitters always sustain a cell; a magnetized cell is also sustained by an
     *  opposing-pole pool (what it's attracted to) but never its own-pole pool;
     *  plain by any magnet. */
    private static void recedeUnsupported(final ServerLevel server, final List<Magnet> magnets) {
        // The player's own fluid — source registry entries that aren't creep cells.
        final Set<BlockPos> originals = new HashSet<>();
        for (final BlockPos p : FerrofluidSourceRegistry.snapshot(server)) {
            if (!FerrofluidCreepRegistry.contains(server, p)) originals.add(p);
        }
        for (final BlockPos p : MagnetizedFerrofluidRegistry.forLevel(server).keySet()) {
            if (!FerrofluidCreepRegistry.contains(server, p)) originals.add(p);
        }

        int budget = RECEDE_BUDGET;
        final Set<BlockPos> cleared = new HashSet<>();
        for (final BlockPos start : FerrofluidCreepRegistry.snapshot(server)) {
            if (budget <= 0) break;
            if (cleared.contains(start) || !server.isLoaded(start)) continue;
            final BlockState st = server.getBlockState(start);
            if (!isFerro(st)) { FerrofluidCreepRegistry.remove(server, start); continue; }
            if (sustained(st, Vec3.atCenterOf(start), magnets)) continue;
            budget = floodClear(server, start, originals, cleared, budget);
        }
    }

    /** Whether a ferrofluid cell at {@code cc} (state {@code st}) is still driven. */
    private static boolean sustained(final BlockState st, final Vec3 cc, final List<Magnet> magnets) {
        final boolean plain = isPlain(st);
        final MagneticPolarity pole = plain ? null : st.getValue(MagnetizedFerrofluidBlock.POLARITY);
        for (final Magnet m : magnets) {
            if (!m.covers(cc)) continue;
            if (m.emitter || plain || m.polarity != pole) return true;
        }
        return false;
    }

    /** Flood the connected ferrofluid body from {@code start}, clearing it to air
     *  (and untracking creep cells), but never the player's {@code originals}. */
    private static int floodClear(final ServerLevel server, final BlockPos start,
                                  final Set<BlockPos> originals, final Set<BlockPos> cleared, int budget) {
        final java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        cleared.add(start);
        while (!queue.isEmpty() && budget > 0) {
            final BlockPos p = queue.poll();
            if (originals.contains(p)) continue;          // leave the player's own pour
            if (!isFerro(server.getBlockState(p))) { FerrofluidCreepRegistry.remove(server, p); continue; }
            server.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            FerrofluidCreepRegistry.remove(server, p);
            budget--;
            for (final Direction d : Direction.values()) {
                final BlockPos n = p.relative(d).immutable();
                if (cleared.add(n) && server.isLoaded(n) && isFerro(server.getBlockState(n))) {
                    queue.add(n);
                }
            }
        }
        return budget;
    }

    /** Plain (non-magnetized) field-reactive fluids: ferrofluid + mixed gallium.
     *  Both creep the same way; mixed gallium just keeps its own block/colour. */
    private static boolean isPlain(final BlockState st) {
        return st.is(MagBlocks.FERROFLUID_BLOCK.get()) || st.is(MagBlocks.MIXED_GALLIUM_BLOCK.get());
    }

    private static boolean isFerro(final BlockState st) {
        return isPlain(st) || st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get());
    }

    private static BlockState stateFor(final BlockState frontier) {
        if (frontier.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())) {
            return MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get().defaultBlockState()
                    .setValue(MagnetizedFerrofluidBlock.POLARITY,
                            frontier.getValue(MagnetizedFerrofluidBlock.POLARITY));
        }
        // Plain frontier (ferrofluid or mixed gallium) grows its own block type.
        return frontier.getBlock().defaultBlockState();
    }

    private static void place(final ServerLevel server, final BlockPos at, final BlockState state) {
        if (at == null || state == null) return;
        server.setBlock(at, state, Block.UPDATE_ALL);
        FerrofluidCreepRegistry.add(server, at);
    }

    /** Neighbour of {@code cell} stepping toward (or away from) the magnet — into
     *  air, a replaceable block, or the fluid's OWN flowing tongue (so a buried
     *  source still marches out through its own puddle). Strictly closer (toward)
     *  / farther (away). Null if none. */
    private static BlockPos bestStep(final ServerLevel server, final BlockPos cell, final Vec3 origin,
                                     final boolean toward, final Block fluidBlock) {
        final Vec3 dir = toward ? origin.subtract(Vec3.atCenterOf(cell))
                : Vec3.atCenterOf(cell).subtract(origin);
        final double here = origin.distanceToSqr(Vec3.atCenterOf(cell));
        BlockPos best = null;
        double bestDot = 0.0;
        for (final Direction d : Direction.values()) {
            final double dot = d.getStepX() * dir.x + d.getStepY() * dir.y + d.getStepZ() * dir.z;
            if (dot <= bestDot) continue;
            final BlockPos np = cell.relative(d);
            final double nd = origin.distanceToSqr(Vec3.atCenterOf(np));
            if (toward ? nd >= here : nd <= here) continue;
            final BlockState ns = server.getBlockState(np);
            final boolean ok = ns.isAir()
                    || (ns.is(fluidBlock) && !ns.getFluidState().isSource())   // own flowing tongue
                    || (ns.canBeReplaced() && ns.getFluidState().isEmpty());   // grass etc.
            if (!ok) continue;
            best = np.immutable();
            bestDot = dot;
        }
        return best;
    }
}
