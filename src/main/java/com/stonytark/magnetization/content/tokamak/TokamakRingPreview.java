package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-mutating construction diagnostic for the compact Tokamak ring.
 *
 * <p>The controller is the deterministic master at the centre of an odd-edged
 * horizontal footprint. The perimeter of the largest complete square ring up
 * to the configured limit must contain Tokamak Coils. A 3x3 ring remains the
 * minimum valid reactor, while 5x5, 7x7, and larger rings can scale output.
 */
public final class TokamakRingPreview {

    private TokamakRingPreview() {}

    public record Preview(boolean valid, BlockPos controller,
                          List<BlockPos> requiredFrame, List<BlockPos> invalidEdges,
                          int edge) {
        public int coilCount() { return requiredFrame.size(); }
    }

    public static Preview preview(final BlockGetter level, final BlockPos controller) {
        return preview(level, controller,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
    }

    public static Preview preview(final BlockGetter level, final BlockPos controller,
                                  final int maxEdge) {
        final int maxRadius = Math.max(1, (Math.max(3, maxEdge) - 1) / 2);
        // Prefer the largest complete ring. This means a reactor remains
        // operational at its smaller completed size while an outer ring is
        // still being built.
        for (int radius = maxRadius; radius >= 1; radius--) {
            final Preview candidate = inspect(level, controller, radius);
            if (candidate.valid()) return candidate;
        }

        return inspect(level, controller, 1);
    }

    /** Inspect one explicitly selected odd-edged ring. This is used by the
     * construction overlay when a player looks at an outer coil: diagnostics
     * must describe that ring, even while it is incomplete. */
    public static Preview previewExact(final BlockGetter level, final BlockPos controller,
                                       final int requestedEdge, final int maxEdge) {
        final int limit = normalizedMaxEdge(maxEdge);
        int edge = Math.max(3, Math.min(limit, requestedEdge));
        if (edge % 2 == 0) edge--;
        return inspect(level, controller, (edge - 1) / 2);
    }

    /** Construction-oriented view used while looking at the controller. A
     * started outer perimeter takes precedence over an already-formed inner
     * ring so goggles show the expansion the player is currently building. */
    public static Preview constructionPreview(final BlockGetter level, final BlockPos controller,
                                               final int maxEdge) {
        final Preview formed = preview(level, controller, maxEdge);
        final int formedRadius = formed.valid() ? (formed.edge() - 1) / 2 : 0;
        final int maxRadius = (normalizedMaxEdge(maxEdge) - 1) / 2;
        for (int radius = maxRadius; radius > Math.max(1, formedRadius); radius--) {
            final Preview candidate = inspect(level, controller, radius);
            if (candidate.invalidEdges().size() < candidate.requiredFrame().size()) return candidate;
        }
        return formed;
    }

    /** Resolve the nearest centered controller for a controller or perimeter
     * coil hit. Unlike the old one-block lookup, this covers every configured
     * expanded-ring radius. */
    public static BlockPos findController(final BlockGetter level, final BlockPos hit,
                                          final int maxEdge) {
        if (level.getBlockState(hit).is(MagBlocks.TOKAMAK_CONTROLLER.get())) return hit;
        if (!level.getBlockState(hit).is(MagBlocks.TOKAMAK_COIL.get())) return null;
        final int maxRadius = (normalizedMaxEdge(maxEdge) - 1) / 2;
        BlockPos nearest = null;
        int nearestRadius = Integer.MAX_VALUE;
        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                final int radius = Math.max(Math.abs(dx), Math.abs(dz));
                if (radius < 1 || radius >= nearestRadius) continue;
                final BlockPos candidate = hit.offset(dx, 0, dz);
                if (level.getBlockState(candidate).is(MagBlocks.TOKAMAK_CONTROLLER.get())) {
                    nearest = candidate.immutable();
                    nearestRadius = radius;
                }
            }
        }
        return nearest;
    }

    private static int normalizedMaxEdge(final int maxEdge) {
        final int configured = Math.max(3, maxEdge);
        return configured % 2 == 0 ? configured - 1 : configured;
    }

    private static Preview inspect(final BlockGetter level, final BlockPos controller,
                                   final int radius) {
        final List<BlockPos> frame = frame(controller, radius);
        final List<BlockPos> invalid = new ArrayList<>();
        for (final BlockPos p : frame) {
            if (!level.getBlockState(p).is(MagBlocks.TOKAMAK_COIL.get())) invalid.add(p);
        }
        return new Preview(invalid.isEmpty(), controller.immutable(), List.copyOf(frame),
                List.copyOf(invalid), radius * 2 + 1);
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
}
