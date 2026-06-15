package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Magnetorheological (MR) fluid block — an iron-particle smart fluid. It flows
 * like a liquid until it sits inside a magnetic field, where it snaps rigid into
 * {@link com.stonytark.magnetization.registry.MagBlocks#HARDENED_MR_FLUID}
 * (walkable — temporary bridges/walkways) and melts back when the field is gone.
 * The field reaction is driven by {@link MrFluidHardenHandler}; this block just
 * registers its source positions so the handler can find the fluid cheaply.
 *
 * <p>(It no longer stiffens on a redstone signal — hardening is field-only.)
 */
public final class MRFluidBlock extends LiquidBlock {

    public MRFluidBlock(final FlowingFluid fluid, final Properties props) {
        super(fluid, props);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && state.getFluidState().isSource()) {
            MrFluidSourceRegistry.add(level, pos);
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MrFluidSourceRegistry.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
