package com.stonytark.magnetization.content.sail;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Magnetosphere Solar Sail panel — see {@link SolarSailBlockEntity}. Faces the
 * direction it pushes; build a wide, one-block-deep wall of them on an airship
 * for more thrust. Right-click toggles whether this panel keeps working at night.
 */
public final class SolarSailBlock extends DirectionalBlock implements EntityBlock {

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

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SolarSailBlockEntity sail)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            sail.toggleNightDisabled();
            player.displayClientMessage(Component.translatable(sail.isNightDisabled()
                    ? "message.magnetization.sail_night_off" : "message.magnetization.sail_night_on"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
