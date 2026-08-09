package com.stonytark.magnetization.content.tokamak;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Superconducting confinement coil — the structural ring of the tokamak. The
 * {@link TokamakControllerBlockEntity} scans for a complete ring of these around
 * itself to form the reactor. A coil only exposes FE when it belongs to a valid
 * Fusion Thruster frame; otherwise it remains passive.
 */
public final class TokamakCoilBlock extends Block {

    public TokamakCoilBlock(final Properties props) {
        super(props);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(state.getBlock())) level.invalidateCapabilities(pos);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean moving) {
        super.onRemove(state, level, pos, newState, moving);
        if (!newState.is(state.getBlock())) level.invalidateCapabilities(pos);
    }
}
