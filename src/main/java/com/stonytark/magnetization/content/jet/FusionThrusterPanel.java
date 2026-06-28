package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure (sub-level-safe) validator for the flat-panel Fusion Thruster multiblock.
 *
 * <p>Shape: a {@code W×H×1} panel standing perpendicular to a shared {@code FACING}.
 * The {@code (W-2)×(H-2)} interior is {@link MagBlocks#FUSION_THRUSTER} (all sharing
 * one FACING); the surrounding 1-block ring (corners included) is
 * {@link MagBlocks#TOKAMAK_COIL}. Thrust is perpendicular to the panel = the
 * interior's FACING. Every read goes through {@link BlockGetter}, so this works
 * unchanged on a moving Sable sub-level (relative offsets, no world transform).
 */
public final class FusionThrusterPanel {

    private FusionThrusterPanel() {}

    /** Validation result. {@code valid} false ⇒ the other fields are meaningless. */
    public record Result(boolean valid, int interiorCount, @Nullable BlockPos master,
                         List<BlockPos> interior, Direction facing) {
        static Result invalid(final Direction facing) {
            return new Result(false, 0, null, List.of(), facing);
        }
    }

    private static boolean isInterior(final BlockGetter level, final BlockPos pos, final Direction facing) {
        final BlockState s = level.getBlockState(pos);
        return s.is(MagBlocks.FUSION_THRUSTER.get())
                && s.hasProperty(DirectionalBlock.FACING)
                && s.getValue(DirectionalBlock.FACING) == facing;
    }

    /**
     * Validate the panel that the interior block at {@code start} (facing {@code facing})
     * belongs to. Flood-fills the connected same-FACING interior, checks it forms a
     * solid 1-deep rect ringed by Tokamak Coils, and resolves the deterministic master.
     */
    public static Result validate(final BlockGetter level, final BlockPos start,
                                  final Direction facing, final int maxEdge) {
        if (!isInterior(level, start, facing)) return Result.invalid(facing);

        // (1) Flood-fill the connected interior over the 4 in-plane neighbours only.
        final Direction[] inPlane = inPlaneDirections(facing);
        final Set<BlockPos> interior = new HashSet<>();
        final Deque<BlockPos> stack = new ArrayDeque<>();
        stack.push(start.immutable());
        final int cap = maxEdge * maxEdge;
        while (!stack.isEmpty()) {
            final BlockPos p = stack.pop();
            if (!interior.add(p)) continue;
            if (interior.size() > cap) return Result.invalid(facing);   // runaway / open shape
            for (final Direction d : inPlane) {
                final BlockPos n = p.relative(d);
                if (!interior.contains(n) && isInterior(level, n, facing)) stack.push(n.immutable());
            }
        }
        if (interior.isEmpty()) return Result.invalid(facing);

        // (2) Bounding box. The facing-axis span must be exactly 1 (a flat panel);
        //     the two in-plane spans are the interior width/height.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos p : interior) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        if (spanOnAxis(facing.getAxis(), maxX - minX, maxY - minY, maxZ - minZ) != 0) {
            return Result.invalid(facing);   // not 1-deep
        }
        // In-plane spans (the two non-facing axes), +1 → interior width/height.
        final int[] inPlaneSpans = inPlaneSpans(facing.getAxis(),
                maxX - minX, maxY - minY, maxZ - minZ);
        final int iw = inPlaneSpans[0] + 1, ih = inPlaneSpans[1] + 1;

        // (3) Solid fill — no holes in the interior rect.
        if (interior.size() != iw * ih) return Result.invalid(facing);

        // (4) Panel size: W = iw+2, H = ih+2 must each be in [3, maxEdge].
        final int w = iw + 2, h = ih + 2;
        if (w < 3 || h < 3 || w > maxEdge || h > maxEdge) return Result.invalid(facing);

        // (5) Perimeter ring — every position in the in-plane-expanded box that is NOT
        //     interior must be a Tokamak Coil (corners included).
        for (int x = minX - (facing.getAxis() == Direction.Axis.X ? 0 : 1);
             x <= maxX + (facing.getAxis() == Direction.Axis.X ? 0 : 1); x++) {
            for (int y = minY - (facing.getAxis() == Direction.Axis.Y ? 0 : 1);
                 y <= maxY + (facing.getAxis() == Direction.Axis.Y ? 0 : 1); y++) {
                for (int z = minZ - (facing.getAxis() == Direction.Axis.Z ? 0 : 1);
                     z <= maxZ + (facing.getAxis() == Direction.Axis.Z ? 0 : 1); z++) {
                    final BlockPos p = new BlockPos(x, y, z);
                    if (interior.contains(p)) continue;
                    if (!level.getBlockState(p).is(MagBlocks.TOKAMAK_COIL.get())) {
                        return Result.invalid(facing);
                    }
                }
            }
        }

        // (6) Master = interior min by (y, then x, then z) — deterministic; every
        //     interior block resolves the same one.
        BlockPos master = null;
        final List<BlockPos> interiorList = new ArrayList<>(interior);
        for (final BlockPos p : interiorList) {
            if (master == null || lessThan(p, master)) master = p;
        }
        return new Result(true, interior.size(), master, interiorList, facing);
    }

    /** Resolve just the master for a panel containing {@code start}, or null if invalid. */
    public static @Nullable BlockPos findMaster(final BlockGetter level, final BlockPos start,
                                                final Direction facing, final int maxEdge) {
        final Result r = validate(level, start, facing, maxEdge);
        return r.valid() ? r.master() : null;
    }

    private static boolean lessThan(final BlockPos a, final BlockPos b) {
        if (a.getY() != b.getY()) return a.getY() < b.getY();
        if (a.getX() != b.getX()) return a.getX() < b.getX();
        return a.getZ() < b.getZ();
    }

    /** The 4 directions perpendicular to {@code facing} (the in-plane neighbours). */
    private static Direction[] inPlaneDirections(final Direction facing) {
        final List<Direction> out = new ArrayList<>(4);
        for (final Direction d : Direction.values()) {
            if (d.getAxis() != facing.getAxis()) out.add(d);
        }
        return out.toArray(new Direction[0]);
    }

    private static int spanOnAxis(final Direction.Axis axis, final int sx, final int sy, final int sz) {
        return switch (axis) { case X -> sx; case Y -> sy; case Z -> sz; };
    }

    /** The two in-plane spans (non-facing axes), in a stable order. */
    private static int[] inPlaneSpans(final Direction.Axis facingAxis,
                                      final int sx, final int sy, final int sz) {
        return switch (facingAxis) {
            case X -> new int[]{sy, sz};
            case Y -> new int[]{sx, sz};
            case Z -> new int[]{sx, sy};
        };
    }
}
