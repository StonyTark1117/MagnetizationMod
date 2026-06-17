package com.stonytark.magnetization.content.sensor;

import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.menu.EmitterMenuProvider;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 * Magnetostrictive movement sensor — a buried magnetic perimeter. Nearby motion
 * (a player or mob running/jumping within range) flexes the magnetostrictive
 * core, which it reads out as an analog redstone signal. Detects through blocks,
 * so it makes an invisible tripwire.
 */
public final class MagnetostrictiveSensorBlock extends Block implements EntityBlock {

    public MagnetostrictiveSensorBlock(final Properties props) {
        super(props);
        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new MagnetostrictiveSensorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            final Level level, final BlockState state, final BlockEntityType<T> type) {
        if (level.isClientSide || type != MagBlockEntities.MAGNETOSTRICTIVE_SENSOR.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<MagnetostrictiveSensorBlockEntity>)
                MagnetostrictiveSensorBlockEntity::serverTick;
    }

    @Override
    protected InteractionResult useWithoutItem(
            final BlockState state, final Level level, final BlockPos pos,
            final Player player, final BlockHitResult hit
    ) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // Reuse the shared emitter GUI with only the range row — lets the player
        // dial detection radius in-world, clamped to the admin sensorMaxRange.
        new EmitterMenuProvider(ContainerLevelAccess.create(level, pos), pos,
                EmitterMenu.CAP_RANGE,
                Component.translatable("block.magnetization.magnetostrictive_sensor")).openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isSignalSource(final BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction dir) {
        return level.getBlockEntity(pos) instanceof MagnetostrictiveSensorBlockEntity be ? be.getSignal() : 0;
    }

    @Override
    protected int getDirectSignal(final BlockState state, final BlockGetter level, final BlockPos pos, final Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}
