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
public class MagnetostrictiveSensorBlockEntity extends BlockEntity {

    private int signal = 0;

    public MagnetostrictiveSensorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETOSTRICTIVE_SENSOR.get(), pos, state);
    }

    public int getSignal() {
        return signal;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MagnetostrictiveSensorBlockEntity be) {
        if (level.isClientSide || (level.getGameTime() % com.stonytark.magnetization.config.MagConfig.sensorInterval()) != 0L) return;

        final double range = com.stonytark.magnetization.config.MagConfig.sensorRange();
        final double moveThreshold = com.stonytark.magnetization.config.MagConfig.sensorMoveThreshold();
        final AABB box = new AABB(pos).inflate(range);
        double best = 0.0;
        for (final LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            // Position delta, NOT getDeltaMovement(): a player's server-side
            // delta-movement stays ~0 (client-authoritative motion), so the
            // sensor would only ever see mobs. xOld/yOld/zOld are updated
            // server-side for players too, giving real per-tick speed.
            final double dx = e.getX() - e.xOld, dy = e.getY() - e.yOld, dz = e.getZ() - e.zOld;
            final double speedSqr = dx * dx + dy * dy + dz * dz;
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
            level.updateNeighborsAt(pos, state.getBlock());
        }
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
