package com.stonytark.magnetization.content;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.permanent.PermanentMagnetBlock;
import com.stonytark.magnetization.content.temporary.TemporaryMagnetBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Halbach-array boost. Lining up magnets of the <em>same</em> polarity so their
 * faces touch concentrates the field (a real Halbach array reinforces one side
 * and cancels the other). Here: each face-adjacent magnet that shares this
 * emitter's polarity bumps its strength tier up, clamped to EXTREME — so a row
 * of aligned magnets reads as a single powerful emitter. Mirrors the hematite
 * dampener, which steps the tier the other way.
 */
public final class HalbachArray {

    private static final MagneticStrength[] TIERS = MagneticStrength.values(); // NONE,WEAK,MEDIUM,STRONG,EXTREME
    private static final int MAX_BONUS_STEPS = 2;

    private HalbachArray() {}

    /** Tier after applying the Halbach boost for {@code polarity} at {@code pos}. */
    public static MagneticStrength boostedStrength(final Level level, final BlockPos pos,
                                                   final MagneticPolarity polarity, final MagneticStrength base) {
        if (!com.stonytark.magnetization.config.MagConfig.halbachEnabled()) return base;
        if (polarity == null || polarity == MagneticPolarity.NONE || base == MagneticStrength.NONE) return base;
        int aligned = 0;
        for (final Direction dir : Direction.values()) {
            if (polarityAt(level, pos.relative(dir)) == polarity) aligned++;
        }
        if (aligned <= 0) return base;
        final int steps = Math.min(MAX_BONUS_STEPS, (aligned + 1) / 2); // 1–2 aligned → +1, 3–4 → +2
        final int idx = Math.min(TIERS.length - 1, base.ordinal() + steps);
        return TIERS[idx];
    }

    /** The magnetic polarity a block presents, or null if it isn't a magnet. */
    private static @Nullable MagneticPolarity polarityAt(final Level level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PermanentMagnetBlock) {
            return state.getValue(PermanentMagnetBlock.POLARITY);
        }
        if (state.getBlock() instanceof TemporaryMagnetBlock) {
            return state.getValue(TemporaryMagnetBlock.POLARITY);
        }
        if (level.getBlockEntity(pos) instanceof AbstractEmitterBlockEntity emitter) {
            final MagneticField field = emitter.currentField();
            return field != null ? field.polarity() : null;
        }
        return null;
    }
}
