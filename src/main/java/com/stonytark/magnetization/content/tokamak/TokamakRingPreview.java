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
            final List<BlockPos> frame = frame(controller, radius);
            final List<BlockPos> invalid = new ArrayList<>();
            for (final BlockPos p : frame) {
                if (!level.getBlockState(p).is(MagBlocks.TOKAMAK_COIL.get())) invalid.add(p);
            }
            if (invalid.isEmpty()) {
                return new Preview(true, controller.immutable(), List.copyOf(frame),
                        List.of(), radius * 2 + 1);
            }
        }

        final List<BlockPos> minimum = frame(controller, 1);
        final List<BlockPos> invalid = minimum.stream()
                .filter(p -> !level.getBlockState(p).is(MagBlocks.TOKAMAK_COIL.get()))
                .toList();
        return new Preview(false, controller.immutable(), List.copyOf(minimum),
                invalid, 3);
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
