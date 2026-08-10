package com.stonytark.magnetization.content.gas;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.fluid.ExcitableGasBlock;
import com.stonytark.magnetization.content.fluid.GasExcitation;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class GasExciterBlockEntity extends BlockEntity {
    private final Buffer energy = new Buffer(MagConfig.gasExciterCapacity(), MagConfig.gasExciterReceive());
    private long consumedAt = Long.MIN_VALUE;

    public GasExciterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.GAS_EXCITER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public boolean canExcite() { return energy.getEnergyStored() >= MagConfig.gasExciterFePerTick(); }

    public boolean consumeForTick(final long gameTime) {
        if (consumedAt == gameTime) return true;
        final int cost = MagConfig.gasExciterFePerTick();
        if (energy.getEnergyStored() < cost) return false;
        energy.drain(cost);
        consumedAt = gameTime;
        setChanged();
        return true;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final GasExciterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.energy.resize(MagConfig.gasExciterCapacity(), MagConfig.gasExciterReceive());
        for (final Direction direction : Direction.values()) {
            final BlockPos gasPos = pos.relative(direction);
            if (server.getBlockState(gasPos).getBlock() instanceof ExcitableGasBlock) {
                GasExcitation.recompute(server, gasPos);
            }
        }
    }

    @Override protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
    }

    @Override protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
    }

    private final class Buffer extends EnergyStorage {
        Buffer(final int capacity, final int receive) { super(capacity, receive, 0); }
        @Override public int receiveEnergy(final int amount, final boolean simulate) {
            final int accepted = super.receiveEnergy(amount, simulate);
            if (!simulate && accepted > 0) GasExciterBlockEntity.this.setChanged();
            return accepted;
        }
        void drain(final int amount) { energy = Math.max(0, energy - amount); }
        void setStored(final int value) { energy = Math.max(0, Math.min(capacity, value)); }
        void resize(final int capacity, final int receive) {
            this.capacity = Math.max(0, capacity);
            this.maxReceive = Math.max(0, receive);
            energy = Math.min(energy, this.capacity);
        }
    }
}
