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

    private static final double RANGE = 8.0;
    private static final double MOVE_THRESHOLD = 0.02; // (blocks/tick)^2 — sprint/jump, not idle drift
    private static final int INTERVAL = 2;
    private static final int DECAY_PER_STEP = 3;       // signal points shed per scan when quiet

    private int signal = 0;

    public MagnetostrictiveSensorBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETOSTRICTIVE_SENSOR.get(), pos, state);
    }

    public int getSignal() {
        return signal;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MagnetostrictiveSensorBlockEntity be) {
        if (level.isClientSide || (level.getGameTime() % INTERVAL) != 0L) return;

        final AABB box = new AABB(pos).inflate(RANGE);
        double best = 0.0;
        for (final LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            final double speedSqr = e.getDeltaMovement().lengthSqr();
            if (speedSqr < MOVE_THRESHOLD) continue;
            final double dist = Math.sqrt(e.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            final double proximity = Math.max(0.0, 1.0 - dist / RANGE);
            // Faster + closer = stronger reading.
            final double reading = Math.min(1.0, Math.sqrt(speedSqr) * 3.0) * proximity;
            best = Math.max(best, reading);
        }

        final int target = (int) Math.ceil(best * 15.0);
        final int next = target > be.signal ? target : Math.max(0, be.signal - DECAY_PER_STEP);
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
