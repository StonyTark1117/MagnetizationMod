package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-mutating construction diagnostic for the compact Tokamak ring.
 *
 * <p>The controller is the deterministic master at the centre of a horizontal
 * 3x3x1 footprint. Every other cell must contain a Tokamak Coil. Keeping this
 * check separate from the block entity lets the client preview use the same
 * eight required positions as the server formation rule.
 */
public final class TokamakRingPreview {

    private TokamakRingPreview() {}

    public record Preview(boolean valid, BlockPos controller,
                          List<BlockPos> requiredFrame, List<BlockPos> invalidEdges) {}

    public static Preview preview(final BlockGetter level, final BlockPos controller) {
        final List<BlockPos> frame = new ArrayList<>(8);
        final List<BlockPos> invalid = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                final BlockPos p = controller.offset(dx, 0, dz).immutable();
                frame.add(p);
                if (!level.getBlockState(p).is(MagBlocks.TOKAMAK_COIL.get())) invalid.add(p);
            }
        }
        return new Preview(invalid.isEmpty(), controller.immutable(),
                List.copyOf(frame), List.copyOf(invalid));
    }
}
