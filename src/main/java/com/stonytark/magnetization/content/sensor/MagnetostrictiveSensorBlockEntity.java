package com.stonytark.magnetization.content.sensor;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Reads nearby motion into an analog redstone signal for
 * {@link MagnetostrictiveSensorBlock}. Every couple ticks it finds the
 * fastest-moving living entity within {@link #RANGE} and maps its speed +
 * proximity to a 0–15 output, which decays a few ticks after movement stops.
 */
public class MagnetostrictiveSensorBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.RangeConfigurable {

    private int signal = 0;
    /** Per-block detection-range override in blocks; 0 = use the config default. */
    private int rangeOverride = 0;

    public MagnetostrictiveSensorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETOSTRICTIVE_SENSOR.get(), pos, state);
    }

    public int getSignal() {
        return signal;
    }

    /** Effective detection radius: the dialed override if set, else the config
     *  default — always clamped to {@code [1, sensorMaxRange]}. */
    public double effectiveRange() {
        final int max = com.stonytark.magnetization.config.MagConfig.sensorMaxRange();
        final double base = rangeOverride > 0
                ? rangeOverride
                : com.stonytark.magnetization.config.MagConfig.sensorRange();
        return Math.max(1.0, Math.min(base, max));
    }

    @Override public int getRangeOverride() { return rangeOverride; }

    @Override public void setRangeOverride(final int blocks) {
        final int max = com.stonytark.magnetization.config.MagConfig.sensorMaxRange();
        this.rangeOverride = blocks <= 0 ? 0 : Math.max(1, Math.min(blocks, max));
        setChanged();
        // Push the new range to clients so the WTHIT tooltip reflects the GUI change
        // immediately (otherwise it lags until the next signal-change block update).
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override public int defaultRangeBlocks() {
        final int max = com.stonytark.magnetization.config.MagConfig.sensorMaxRange();
        return (int) Math.round(Math.max(1.0,
                Math.min(com.stonytark.magnetization.config.MagConfig.sensorRange(), max)));
    }

    @Override public int maxRangeBlocks() {
        return com.stonytark.magnetization.config.MagConfig.sensorMaxRange();
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MagnetostrictiveSensorBlockEntity be) {
        if (level.isClientSide || (level.getGameTime() % com.stonytark.magnetization.config.MagConfig.sensorInterval()) != 0L) return;

        final double range = be.effectiveRange();
        final double moveThreshold = com.stonytark.magnetization.config.MagConfig.sensorMoveThreshold();
        final AABB box = new AABB(pos).inflate(range);
        double best = 0.0;
        for (final LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            // getKnownMovement(), NOT getDeltaMovement(): a player's server-side
            // delta-movement stays ~0 (client-authoritative motion). getKnownMovement
            // is the purpose-built API that returns real movement for players too,
            // and falls back to getDeltaMovement() for mobs.
            final double speedSqr = e.getKnownMovement().lengthSqr();
            if (speedSqr < moveThreshold) continue;
            final double dist = Math.sqrt(e.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            final double proximity = Math.max(0.0, 1.0 - dist / range);
            // Faster + closer = stronger reading.
            final double reading = Math.min(1.0, Math.sqrt(speedSqr) * 3.0) * proximity;
            best = Math.max(best, reading);
        }

        final int target = (int) Math.ceil(best * 15.0);
        final int next = target > be.signal ? target : Math.max(0, be.signal - com.stonytark.magnetization.config.MagConfig.sensorDecayPerStep());
        if (next != be.signal) {
            be.signal = next;
            be.setChanged();
            final boolean powered = next > 0;
            if (state.getValue(BlockStateProperties.POWERED) != powered) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
            }
            // Push the new signal magnitude to clients so the WTHIT tooltip reads
            // the live value, not just on the powered/unpowered transition.
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, state.getBlock());
        }
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Signal", signal);
        tag.putInt("RangeOverride", rangeOverride);
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
        tag.putInt("RangeOverride", rangeOverride);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.signal = tag.getInt("Signal");
        this.rangeOverride = tag.getInt("RangeOverride");
    }
}
