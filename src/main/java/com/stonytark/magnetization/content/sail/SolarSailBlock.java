package com.stonytark.magnetization.content.sail;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Magnetosphere Solar Sail panel — see {@link SolarSailBlockEntity}. The ribboned
 * front face is the thrust face; build a wide, one-block-deep wall of them on an
 * airship for more push. Wrench rotates the facing. Night behaviour is a server
 * config ({@code content.solarSailNightFactor}), not a per-panel toggle.
 */
public final class SolarSailBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<SolarSailBlock> CODEC = simpleCodec(SolarSailBlock::new);

    public SolarSailBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new SolarSailBlockEntity(pos, state);
    }
}
