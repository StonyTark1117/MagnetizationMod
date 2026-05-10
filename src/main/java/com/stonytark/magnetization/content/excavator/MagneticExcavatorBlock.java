package com.stonytark.magnetization.content.excavator;

import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.menu.EmitterMenuProvider;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Magnetic Excavator: the directional ferromagnetic mining block. Defaults to
 * pulling from below — placed against a ceiling, the active face points DOWN,
 * and the column under it ascends one cell per pull cycle until exhausted.
 * Wrench rotates the active face to any of six directions.
 *
 * <p>The block is just plumbing — the BlockEntity does the actual work.
 */
public class MagneticExcavatorBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<MagneticExcavatorBlock> CODEC = simpleCodec(MagneticExcavatorBlock::new);

    public MagneticExcavatorBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.DOWN)
                .setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        // Pointing the active face away from the surface the player clicks against.
        // Clicking the ceiling of a tunnel → face = DOWN (default); clicking a wall
        // → face = the wall's outward normal opposite. Same convention as the repulsor.
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(BlockStateProperties.POWERED, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MagneticExcavatorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type
    ) {
        if (level.isClientSide || type != MagBlockEntities.MAGNETIC_EXCAVATOR.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MagneticExcavatorBlockEntity>)
                MagneticExcavatorBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // Strength + range tuning for the column reach, plus a tool slot for an
        // enchanted tool / book whose enchantments enhance the column's drops
        // (Fortune multiplies, Silk Touch silk-mines).
        final int caps = EmitterMenu.CAP_STRENGTH | EmitterMenu.CAP_RANGE | EmitterMenu.CAP_TOOL_SLOT;
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos, caps,
                Component.translatable("block.magnetization.magnetic_excavator")).openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Block is genuinely being removed (broken, replaced) — eject any
            // installed enchanted tool so the player isn't left short.
            if (level.getBlockEntity(pos) instanceof MagneticExcavatorBlockEntity exc) {
                exc.dropToolSlot(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void neighborChanged(
            final BlockState state, final Level level, final BlockPos pos,
            final Block neighborBlock, final BlockPos neighborPos, final boolean movedByPiston
    ) {
        if (level.isClientSide) return;
        final boolean nowPowered = level.hasNeighborSignal(pos);
        if (state.getValue(BlockStateProperties.POWERED) != nowPowered) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, nowPowered), Block.UPDATE_CLIENTS);
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MagneticExcavatorBlockEntity excavator) {
            excavator.setPowered(nowPowered);
        }
    }
}
