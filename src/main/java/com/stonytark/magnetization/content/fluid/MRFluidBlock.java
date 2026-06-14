package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Magnetorheological (MR) fluid block. Iron-particle smart fluid: when a redstone
 * signal is applied (the {@code POWERED} state) it stiffens to a near-solid —
 * gaining a full collision box so entities walk on it like a block — and reverts
 * to a flowing liquid when the signal drops. Makes magnetically/redstone-toggled
 * bridges, gates and dams.
 */
public final class MRFluidBlock extends LiquidBlock {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public MRFluidBlock(final FlowingFluid fluid, final Properties props) {
        super(fluid, props);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level,
                                           final BlockPos pos, final CollisionContext ctx) {
        return state.getValue(POWERED) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) updateSolid(state, level, pos);
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston) {
        if (!level.isClientSide) updateSolid(state, level, pos);
    }

    private static void updateSolid(final BlockState state, final Level level, final BlockPos pos) {
        final boolean now = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != now) {
            level.setBlock(pos, state.setValue(POWERED, now), Block.UPDATE_ALL);
        }
    }
}
