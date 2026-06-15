package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        final boolean[] found = {false};
        EmitterRegistry.forEach(level, (lvl, p) -> {
            if (found[0]) return;
            if (lvl.getBlockEntity(p) instanceof MagneticFieldSource src) {
                final MagneticField f = src.currentField();
                if (f != null && f.origin().distanceToSqr(pos) <= f.range() * f.range()) {
                    found[0] = true;
                }
            }
        });
        if (found[0]) return true;

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
        final MagneticField[] best = {null};
        final double[] bestSq = {Double.MAX_VALUE};
        EmitterRegistry.forEach(level, (lvl, p) -> {
            if (lvl.getBlockEntity(p) instanceof MagneticFieldSource src) {
                final MagneticField f = src.currentField();
                if (f != null) {
                    final double d = f.origin().distanceToSqr(pos);
                    if (d <= f.range() * f.range() && d < bestSq[0]) {
                        bestSq[0] = d;
                        best[0] = f;
                    }
                }
            }
        });
        return best[0];
    }
}
