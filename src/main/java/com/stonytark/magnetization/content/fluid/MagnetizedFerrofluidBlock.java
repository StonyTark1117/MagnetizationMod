package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.api.MagneticPolarity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Magnetized ferrofluid — a fully flowing fluid (it pours and spreads like the
 * plain kind) that a player has charged with a polarity, so it acts as a weak
 * magnetic field source. The pole is held in the {@code POLARITY} blockstate
 * (set at placement from the bucket's stamp). Only <em>source</em> blocks emit
 * a field; they register into {@link MagnetizedFerrofluidRegistry} so
 * {@link MagnetizedFerrofluidFieldHandler} can drive them without a block
 * entity. Plain (unmagnetized) ferrofluid emits nothing and is field-immune —
 * which is why the Anomaly biome's pools sit still instead of draining away.
 */
public final class MagnetizedFerrofluidBlock extends LiquidBlock {

    public static final EnumProperty<MagneticPolarity> POLARITY =
            EnumProperty.create("polarity", MagneticPolarity.class, MagneticPolarity.NORTH, MagneticPolarity.SOUTH);

    public MagnetizedFerrofluidBlock(final FlowingFluid fluid, final Properties props) {
        super(fluid, props);
        registerDefaultState(defaultBlockState().setValue(POLARITY, MagneticPolarity.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POLARITY);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Track source blocks only — flowing tongues don't emit. The field
        // handler prunes any entry that stops being a magnetized source, so
        // a flow that drains this cell heals the registry on its own.
        if (!level.isClientSide && state.getFluidState().isSource()) {
            MagnetizedFerrofluidRegistry.add(level, pos, state.getValue(POLARITY));
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MagnetizedFerrofluidRegistry.remove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
