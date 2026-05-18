package com.stonytark.magnetization.content.hematite;

import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storage block for hematite (α-Fe2O3) — antiferromagnetic iron oxide whose
 * lattice naturally cancels out applied magnetic fields. Placed adjacent to
 * an emitter (any of 6 faces) it dampens that emitter's strength tier by one
 * step per block. Stacking is additive: two hematite faces drop a {@code
 * STRONG} emitter to {@code WEAK}; three or more clamp at {@code WEAK}.
 *
 * <p>The block is a passive marker — no BE, no ticker. Detection happens in
 * {@link com.stonytark.magnetization.content.AbstractEmitterBlockEntity#tickEmitter}
 * via {@link #dampenedStrength(BlockGetter, BlockPos, MagneticStrength)}.
 *
 * <p>Hematite also contributes to whole-ship polarity calculations the same
 * way the Polarity Inverter does, so place/break invalidates the
 * {@link ShipMagneticRegistry} cache to keep ship-mounted setups responsive.
 */
public final class HematiteBlock extends Block {

    public HematiteBlock(final Properties props) {
        super(props);
    }

    /** Walk the 6 axis-aligned neighbours of {@code pos}; for each adjacent
     *  hematite block, step the strength tier down by one. {@code WEAK} is the
     *  floor (further hematite blocks have no additional effect). */
    public static MagneticStrength dampenedStrength(final BlockGetter level,
                                                     final BlockPos pos,
                                                     final MagneticStrength base) {
        int count = 0;
        for (final Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof HematiteBlock) count++;
        }
        return stepDown(base, count);
    }

    /** Pure tier-arithmetic helper. Extracted so the ordinal-clamp behaviour
     *  ({@code WEAK} as the floor, additive stacking, no-op at zero) can be
     *  regression-tested without a {@link BlockGetter}. Public so tests in a
     *  different package can call it directly. */
    public static MagneticStrength stepDown(final MagneticStrength base, final int count) {
        if (count <= 0) return base;
        final MagneticStrength[] values = MagneticStrength.values();
        final int dampened = Math.max(0, base.ordinal() - count);
        return values[dampened];
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel server) ShipMagneticRegistry.invalidateAll(server);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            ShipMagneticRegistry.invalidateAll(server);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
