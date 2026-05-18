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
 * pulling from below — placed against a ceiling, the active face points DOWN.
 * While powered, the block projects a widening cone along its active face,
 * continuously pulling every ferromagnetic block it finds; each pulled block
 * tunnels through obstructions on its way to the emitter. Wrench rotates the
 * active face to any of six directions.
 *
 * <p>The block is just plumbing — the BlockEntity does the actual work.
 */
public final class MagneticExcavatorBlock extends DirectionalBlock implements EntityBlock, IWrenchable {

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
        // Strength + range tuning, plus a tool slot for an enchanted tool / book whose
        // enchantments enhance the column's drops (Fortune multiplies, Silk Touch
        // silk-mines), a per-emitter cap on concurrent in-flight pulls, and an internal
        // redstone-power slot so builders can run the excavator without exposing an
        // external redstone source the pulled blocks can destroy.
        final int caps = EmitterMenu.CAP_STRENGTH | EmitterMenu.CAP_RANGE
                | EmitterMenu.CAP_TOOL_SLOT | EmitterMenu.CAP_INFLIGHT
                | EmitterMenu.CAP_REDSTONE_FUEL;
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos, caps,
                Component.translatable("block.magnetization.magnetic_excavator")).openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos,
                            final BlockState newState, final boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Block is genuinely being removed (broken, replaced) — eject any
            // installed enchanted tool and internal redstone power so the
            // player isn't left short.
            if (level.getBlockEntity(pos) instanceof MagneticExcavatorBlockEntity exc) {
                exc.dropToolSlot(level, pos);
                exc.dropRedstoneFuelSlot(level, pos);
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
        applyExternalSignal(state, level, pos);
    }

    @Override
    protected void onPlace(final BlockState state, final Level level, final BlockPos pos,
                           final BlockState oldState, final boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || state.is(oldState.getBlock())) return;
        applyExternalSignal(state, level, pos);
    }

    private static void applyExternalSignal(final BlockState state, final Level level, final BlockPos pos) {
        final boolean nowExternal = level.hasNeighborSignal(pos);
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MagneticExcavatorBlockEntity excavator) {
            // BE owns block-state POWERED: it's the OR of external signal +
            // internal redstone fuel, so the BE has to compute the union and
            // push the result to the world. Just hand it the external bit.
            excavator.setExternalSignal(nowExternal);
            return;
        }
        // No BE yet (placement race): fall back to direct state update so the
        // first frame shows the right visual until the BE attaches.
        if (state.getValue(BlockStateProperties.POWERED) != nowExternal) {
            level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, nowExternal), Block.UPDATE_CLIENTS);
        }
    }
}
