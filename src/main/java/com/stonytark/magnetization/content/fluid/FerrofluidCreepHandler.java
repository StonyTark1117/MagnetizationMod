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

        final Set<BlockPos> moved = new HashSet<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (final Source s : sources) {
            final BlockPos center = BlockPos.containing(s.origin.x, s.origin.y, s.origin.z);
            final int r = s.radius;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (!server.isLoaded(cursor)) continue;
                        if (moved.contains(cursor)) continue;
                        tryCreep(server, cursor, s, moveMag, movePlain, moved);
                    }
                }
            }
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

    private static void tryCreep(final ServerLevel server, final BlockPos pos, final Source s,
                                 final boolean moveMag, final boolean movePlain, final Set<BlockPos> moved) {
        final BlockState st = server.getBlockState(pos);
        if (!st.getFluidState().isSource()) return;
        final boolean plain = st.is(MagBlocks.FERROFLUID_BLOCK.get());
        final boolean magnetized = st.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get());
        if (!plain && !magnetized) return;

        final Vec3 cell = Vec3.atCenterOf(pos);
        final Vec3 toOrigin = s.origin.subtract(cell);
        final double dist = toOrigin.length();
        if (dist > s.radius || dist < 0.5) return; // out of range, or at the source

        final boolean attract;
        if (plain) {
            if (!movePlain) return;
            attract = true; // plain is drawn to any field source, regardless of pole
        } else {
            if (!moveMag) return;
            final MagneticPolarity fluidPole = st.getValue(MagnetizedFerrofluidBlock.POLARITY);
            if (s.polarity == MagneticPolarity.NONE || fluidPole == MagneticPolarity.NONE) return;
            attract = fluidPole != s.polarity; // unlike poles attract, like poles repel
        }

        final Vec3 dir = attract ? toOrigin : toOrigin.scale(-1.0);
        final BlockPos target = bestAirNeighbor(server, pos, dir);
        if (target == null) return;

        // Relocate the source cell one step. setBlock fires the magnetized block's
        // onPlace/onRemove, which keep MagnetizedFerrofluidRegistry in sync.
        final BlockState newState = magnetized
                ? MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get().defaultBlockState()
                        .setValue(MagnetizedFerrofluidBlock.POLARITY,
                                st.getValue(MagnetizedFerrofluidBlock.POLARITY))
                : MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState();
        server.setBlock(target, newState, Block.UPDATE_ALL);
        server.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        moved.add(pos.immutable());
        moved.add(target.immutable());
    }

    /** Pick the air neighbour (of 6) whose direction best matches {@code dir}. */
    private static BlockPos bestAirNeighbor(final ServerLevel server, final BlockPos from, final Vec3 dir) {
        BlockPos best = null;
        double bestDot = 0.0; // require a positive component toward the goal
        for (final Direction d : Direction.values()) {
            final double dot = d.getStepX() * dir.x + d.getStepY() * dir.y + d.getStepZ() * dir.z;
            if (dot <= bestDot) continue;
            final BlockPos np = from.relative(d);
            if (!server.getBlockState(np).isAir()) continue; // only flow into open air
            best = np.immutable();
            bestDot = dot;
        }
        return best;
    }
}
