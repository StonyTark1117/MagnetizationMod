package com.stonytark.magnetization.content.sensor;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Emits a fresh random redstone strength every couple ticks while a magnet is
 * adjacent (the Barkhausen jitter), 0 otherwise. See {@link BarkhausenBlock}.
 */
public class BarkhausenBlockEntity extends BlockEntity {

    private static final int INTERVAL = 2;

    private int signal = 0;

    public BarkhausenBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.BARKHAUSEN.get(), pos, state);
    }

    public int getSignal() {
        return signal;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final BarkhausenBlockEntity be) {
        if (level.isClientSide || (level.getGameTime() % INTERVAL) != 0L) return;

        final int next = hasAdjacentMagnet(level, pos) ? level.random.nextInt(16) : 0;
        if (next != be.signal) {
            be.signal = next;
            be.setChanged();
            final boolean powered = next > 0;
            if (state.getValue(BlockStateProperties.POWERED) != powered) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
            }
            // Push the new signal magnitude to clients so the WTHIT tooltip reads the
            // live jitter value, not just the powered/unpowered transition.
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    private static boolean hasAdjacentMagnet(final Level level, final BlockPos pos) {
        for (final Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(MagTags.ANVIL_DAMPENERS)) return true;
        }
        return false;
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Signal", signal);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Signal", signal);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.signal = tag.getInt("Signal");
    }
}
