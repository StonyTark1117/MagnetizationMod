package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared query: is a world position inside any active magnetic field? Used by
 * features that "harden in a field" (MR fluid, MR armor, MR fluid golem). A
 * position counts as in-field if it's within the range of any powered emitter's
 * live field, or within a magnetized-ferrofluid pool's weak field.
 */
public final class MagneticFields {

    private MagneticFields() {}

    /** True if {@code pos} is within range of any active field source. */
    public static boolean isInField(final ServerLevel level, final Vec3 pos) {
        final BlockPos target = BlockPos.containing(pos);
        for (final BlockPos emitter : candidatesNear(level, target)) {
            final MagneticField field = fieldAtLoaded(level, emitter);
            if (field != null && field.origin().distanceToSqr(pos) <= field.range() * field.range()) return true;
        }

        final double wr = MagneticStrength.WEAK.range();
        for (final BlockPos p : MagnetizedFerrofluidRegistry.forLevel(level).keySet()) {
            if (Vec3.atCenterOf(p).distanceToSqr(pos) <= wr * wr) return true;
        }
        return false;
    }

    /** Convenience overload for a block position. */
    public static boolean isInField(final ServerLevel level, final BlockPos pos) {
        return isInField(level, Vec3.atCenterOf(pos));
    }

    /**
     * The active emitter field whose range covers {@code pos} and whose origin is
     * nearest to it, or {@code null} if none. Used by gallium's Lorentz current,
     * which needs the field's origin (current direction) and polarity (toward vs
     * away). Magnetized-ferrofluid pools are intentionally ignored here — only a
     * real, directional emitter field drives the gallium current.
     */
    public static @Nullable MagneticField nearestField(final ServerLevel level, final Vec3 pos) {
        MagneticField best = null;
        double bestSq = Double.MAX_VALUE;
        for (final BlockPos emitter : candidatesNear(level, BlockPos.containing(pos))) {
            final MagneticField field = fieldAtLoaded(level, emitter);
            if (field != null) {
                final double distance = field.origin().distanceToSqr(pos);
                if (distance <= field.range() * field.range() && distance < bestSq) {
                    bestSq = distance;
                    best = field;
                }
            }
        }
        return best;
    }

    /** Resolve one tracked source exclusively through already-loaded chunk data. */
    public static @Nullable MagneticField fieldAtLoaded(final ServerLevel level, final BlockPos pos) {
        final BlockState state = LoadedChunkAccess.blockState(level, pos);
        if (state == null) return null;
        final BlockEntity blockEntity = LoadedChunkAccess.blockEntity(level, pos);
        if (blockEntity instanceof MagneticFieldSource source) {
            final MagneticField field = source.currentField();
            if (field != null) return field;
        }
        return com.stonytark.magnetization.compat.ExternalFieldCompat.currentField(level, pos, state);
    }

    private static java.util.Set<BlockPos> candidatesNear(final ServerLevel level, final BlockPos target) {
        // Native emitters can be configured up to 512 blocks; optional-mod
        // adapters currently top out at EXTREME's 32-block range.
        final java.util.Set<BlockPos> candidates = new java.util.HashSet<>(
                EmitterRegistry.snapshotNativeNear(level, target, 512));
        candidates.addAll(EmitterRegistry.snapshotExternalNear(level, target,
                (int) MagneticStrength.EXTREME.range(), Integer.MAX_VALUE));
        return candidates;
    }
}
