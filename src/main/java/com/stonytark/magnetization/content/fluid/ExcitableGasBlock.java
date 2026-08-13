package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;

/** A placed gas whose light/tint is derived from network excitation. */
public final class ExcitableGasBlock extends LiquidBlock {
    public static final BooleanProperty EXCITED = BooleanProperty.create("excited");
    public static final IntegerProperty EXCITATION_GRACE = IntegerProperty.create("excitation_grace", 0, 3);

    public ExcitableGasBlock(final FlowingFluid fluid, final Properties properties) {
        super(fluid, properties);
        registerDefaultState(defaultBlockState().setValue(EXCITED, false).setValue(EXCITATION_GRACE, 0));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(EXCITED, EXCITATION_GRACE);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server) {
            GasExcitation.invalidate(server);
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final net.minecraft.world.level.block.Block neighbour,
                                   final BlockPos neighbourPos, final boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbour, neighbourPos, movedByPiston);
        if (level instanceof ServerLevel server) {
            GasExcitation.invalidate(server);
        }
        if (!level.isClientSide && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean movedByPiston) {
        if (level instanceof ServerLevel server && !state.is(newState.getBlock())) {
            GasExcitation.invalidate(server);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(final BlockState state, final ServerLevel level, final BlockPos pos,
                        final RandomSource random) {
        GasExcitation.recompute(level, pos);
    }
}
