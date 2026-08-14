package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Non-mutating validator and construction diagnostic for the Tokamak.
 *
 * <p>An odd-edged horizontal square has a one-block Tokamak-Coil perimeter and
 * a <em>solid</em> Tokamak Reactor Core interior. The unique center core is the
 * deterministic master; every other core forwards fuel, FE, GUI, and HUD state
 * to it. Thus 3x3 uses one core, 5x5 uses nine, 7x7 uses twenty-five, and so on.
 */
public final class TokamakRingPreview {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private TokamakRingPreview() {}

    public record Preview(boolean valid, BlockPos controller,
                          List<BlockPos> requiredFrame, List<BlockPos> invalidEdges,
                          List<BlockPos> requiredCores, List<BlockPos> invalidCores,
                          int edge) {
        public int coilCount() { return requiredFrame.size(); }
        public int coreCount() { return requiredCores.size(); }
    }

    public static Preview preview(final BlockGetter level, final BlockPos controller) {
        return preview(level, controller,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
    }

    /** Largest complete reactor centered on {@code controller}. */
    public static Preview preview(final BlockGetter level, final BlockPos controller,
                                  final int maxEdge) {
        final int maxRadius = (normalizedMaxEdge(maxEdge) - 1) / 2;
        for (int radius = maxRadius; radius >= 1; radius--) {
            final Preview candidate = inspect(level, controller, radius);
            if (candidate.valid()) return candidate;
        }
        return inspect(level, controller, 1);
    }

    /** Inspect one explicitly selected odd-edged footprint. */
    public static Preview previewExact(final BlockGetter level, final BlockPos controller,
                                       final int requestedEdge, final int maxEdge) {
        final int limit = normalizedMaxEdge(maxEdge);
        int edge = Math.max(3, Math.min(limit, requestedEdge));
        if (edge % 2 == 0) edge--;
        return inspect(level, controller, (edge - 1) / 2);
    }

    /**
     * Derive the intended footprint from a connected Reactor-Core interior.
     * A complete odd square is not required: if its bounding box is already an
     * odd square, missing interior cores remain visible in {@code invalidCores}.
     */
    public static @Nullable Preview previewFromCore(final BlockGetter level, final BlockPos start,
                                                    final int maxEdge) {
        if (!isCore(level, start)) return null;
        final int maxInterior = normalizedMaxEdge(maxEdge) - 2;
        final int cap = maxInterior * maxInterior;
        final Set<BlockPos> connected = new HashSet<>();
        final ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(start.immutable());
        while (!open.isEmpty()) {
            final BlockPos pos = open.removeFirst();
            if (!connected.add(pos) || connected.size() > cap) continue;
            for (final Direction direction : HORIZONTAL) {
                final BlockPos next = pos.relative(direction);
                if (!connected.contains(next) && isCore(level, next)) open.addLast(next.immutable());
            }
        }
        if (connected.size() > cap) return null;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos pos : connected) {
            if (pos.getY() != start.getY()) return null;
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }
        final int width = maxX - minX + 1;
        final int depth = maxZ - minZ + 1;
        if (width != depth || width < 1 || width > maxInterior || width % 2 == 0) return null;
        final BlockPos center = new BlockPos((minX + maxX) / 2, start.getY(), (minZ + maxZ) / 2);
        return inspect(level, center, (width + 1) / 2);
    }

    /** Builder-facing view for a core. Prefer its connected core footprint; if
     * only the center exists, an explicitly started outer coil ring can select
     * the expansion target. */
    public static Preview constructionPreview(final BlockGetter level, final BlockPos core,
                                               final int maxEdge) {
        final Preview fromCore = previewFromCore(level, core, maxEdge);
        if (fromCore != null && (fromCore.edge() > 3 || fromCore.valid())) return fromCore;
        final BlockPos center = fromCore == null ? core : fromCore.controller();
        final int maxRadius = (normalizedMaxEdge(maxEdge) - 1) / 2;
        for (int radius = maxRadius; radius > 1; radius--) {
            final Preview candidate = inspect(level, center, radius);
            if (candidate.invalidEdges().size() < candidate.requiredFrame().size()) return candidate;
        }
        return fromCore == null ? inspect(level, core, 1) : fromCore;
    }

    /** Resolve the deterministic center master from any formed/diagnosable core
     * or perimeter coil, including outer corners. */
    public static @Nullable BlockPos findController(final BlockGetter level, final BlockPos hit,
                                                    final int maxEdge) {
        if (isCore(level, hit)) {
            final Preview own = previewFromCore(level, hit, maxEdge);
            return own == null ? hit.immutable() : own.controller();
        }
        if (!level.getBlockState(hit).is(MagBlocks.TOKAMAK_COIL.get())) return null;

        Preview best = null;
        int bestPresent = -1;
        // A perimeter side touches an interior core cardinally; a corner touches
        // one diagonally, hence the 3x3 horizontal neighbourhood.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final Preview candidate = previewFromCore(level, hit.offset(dx, 0, dz), maxEdge);
                if (candidate == null || !candidate.requiredFrame().contains(hit)) continue;
                final int present = candidate.coilCount() - candidate.invalidEdges().size()
                        + candidate.coreCount() - candidate.invalidCores().size();
                if (present > bestPresent) { best = candidate; bestPresent = present; }
            }
        }
        // A legacy hollow expansion has no core adjacent to its outer frame.
        // Scan possible centered footprints so goggles can point out the missing
        // interior instead of failing to recognize the attempted reactor.
        if (best == null) {
            final int maxRadius = (normalizedMaxEdge(maxEdge) - 1) / 2;
            for (int dx = -maxRadius; dx <= maxRadius; dx++) {
                for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                    final int radius = Math.max(Math.abs(dx), Math.abs(dz));
                    if (radius < 1 || radius > maxRadius) continue;
                    final BlockPos candidateCenter = hit.offset(dx, 0, dz);
                    if (!isCore(level, candidateCenter)) continue;
                    final Preview candidate = inspect(level, candidateCenter, radius);
                    if (!candidate.requiredFrame().contains(hit)) continue;
                    final int present = candidate.coilCount() - candidate.invalidEdges().size()
                            + candidate.coreCount() - candidate.invalidCores().size();
                    if (present > bestPresent) { best = candidate; bestPresent = present; }
                }
            }
        }
        return best == null ? null : best.controller();
    }

    /** Cheap server-tick gate: only the center of a solid odd square has equal
     * contiguous core reach in all four horizontal directions. Followers can
     * therefore skip the expensive full validation; the remembered master
     * continues validating after a block is removed. */
    public static boolean isPotentialMaster(final BlockGetter level, final BlockPos pos,
                                            final int maxEdge) {
        if (!isCore(level, pos)) return false;
        final int limit = normalizedMaxEdge(maxEdge) - 2;
        final int west = coreReach(level, pos, Direction.WEST, limit);
        return west == coreReach(level, pos, Direction.EAST, limit)
                && west == coreReach(level, pos, Direction.NORTH, limit)
                && west == coreReach(level, pos, Direction.SOUTH, limit);
    }

    private static int coreReach(final BlockGetter level, final BlockPos pos,
                                 final Direction direction, final int limit) {
        int distance = 0;
        while (distance < limit && isCore(level, pos.relative(direction, distance + 1))) distance++;
        return distance;
    }

    private static int normalizedMaxEdge(final int maxEdge) {
        final int configured = Math.max(3, maxEdge);
        return configured % 2 == 0 ? configured - 1 : configured;
    }

    private static boolean isCore(final BlockGetter level, final BlockPos pos) {
        return level.getBlockState(pos).is(MagBlocks.TOKAMAK_CONTROLLER.get());
    }

    private static Preview inspect(final BlockGetter level, final BlockPos controller,
                                   final int radius) {
        final List<BlockPos> frame = frame(controller, radius);
        final List<BlockPos> cores = cores(controller, radius);
        final List<BlockPos> invalidEdges = frame.stream()
                .filter(pos -> !level.getBlockState(pos).is(MagBlocks.TOKAMAK_COIL.get()))
                .toList();
        final List<BlockPos> invalidCores = cores.stream()
                .filter(pos -> !isCore(level, pos))
                .toList();
        return new Preview(invalidEdges.isEmpty() && invalidCores.isEmpty(), controller.immutable(),
                List.copyOf(frame), List.copyOf(invalidEdges), List.copyOf(cores),
                List.copyOf(invalidCores), radius * 2 + 1);
    }

    private static List<BlockPos> frame(final BlockPos controller, final int radius) {
        final List<BlockPos> frame = new ArrayList<>(radius * 8);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                frame.add(controller.offset(dx, 0, dz).immutable());
            }
        }
        return frame;
    }

    private static List<BlockPos> cores(final BlockPos controller, final int radius) {
        final int inner = radius - 1;
        final List<BlockPos> cores = new ArrayList<>((inner * 2 + 1) * (inner * 2 + 1));
        for (int dx = -inner; dx <= inner; dx++) {
            for (int dz = -inner; dz <= inner; dz++) {
                cores.add(controller.offset(dx, 0, dz).immutable());
            }
        }
        return cores;
    }
}
