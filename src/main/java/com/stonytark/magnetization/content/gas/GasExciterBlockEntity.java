package com.stonytark.magnetization.content.gas;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.fluid.GasExcitation;
import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class GasExciterBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.content.emp.EmpDrainable {
    private final Buffer energy = new Buffer(MagConfig.gasExciterCapacity(), MagConfig.gasExciterReceive());
    private long consumedAt = Long.MIN_VALUE;
    private Fluid hudGas = Fluids.EMPTY;
    private boolean hudActive;
    private boolean hudRedstoneDisabled;
    private long lastEnergySyncTick = Long.MIN_VALUE;
    private int lastSyncedEnergy = -1;

    public GasExciterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.GAS_EXCITER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    @Override public void clearEnergyForEmp() { energy.setStored(0); syncEmpEnergyChange(); }
    public boolean canExcite() {
        return !redstoneDisabledNow() && energy.getEnergyStored() >= MagConfig.gasExciterFePerTick();
    }
    public Fluid hudGas() { return hudGas; }
    public boolean hudActive() { return hudActive; }
    public boolean hudRedstoneDisabled() { return hudRedstoneDisabled; }

    /** WTHIT reads this server-synchronized snapshot from the client block entity. */
    public List<Component> hudLines() {
        final Component gas = hudGas == Fluids.EMPTY
                ? Component.translatable("tooltip.magnetization.gas_exciter.no_gas")
                : Component.translatable("tooltip.magnetization.gas_exciter.gas",
                        new FluidStack(hudGas, 1).getHoverName());
        final Component status = hudRedstoneDisabled
                ? Component.translatable("tooltip.magnetization.gas_exciter.off_redstone")
                        .withStyle(ChatFormatting.RED)
                : hudActive
                        ? Component.translatable("tooltip.magnetization.gas_exciter.on")
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatable("tooltip.magnetization.gas_exciter.off")
                                .withStyle(ChatFormatting.YELLOW);
        return List.of(gas.copy().withStyle(ChatFormatting.AQUA), status);
    }

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
        Fluid gas = Fluids.EMPTY;
        for (final Direction direction : Direction.values()) {
            final BlockPos gasPos = pos.relative(direction);
            final Fluid adjacent = GasExcitation.fluidAt(server, gasPos);
            if (adjacent != Fluids.EMPTY) {
                if (gas == Fluids.EMPTY) gas = adjacent;
                GasExcitation.recompute(server, gasPos);
            } else if (server.getBlockEntity(gasPos) instanceof GasVentBlockEntity vent) {
                final BlockPos output = vent.outputPos();
                final Fluid vented = GasExcitation.fluidAt(server, output);
                if (vented != Fluids.EMPTY) {
                    if (gas == Fluids.EMPTY) gas = vented;
                    GasExcitation.recompute(server, output);
                }
            }
        }
        final boolean redstoneDisabled = be.redstoneDisabledNow();
        // A consumption recorded earlier in this game tick must not keep the
        // machine visually/HUD-active if its adjacent gas has since vanished.
        final boolean active = gas != Fluids.EMPTY && !redstoneDisabled
                && be.consumedAt == server.getGameTime();
        be.updateHudState(server, gas, active, redstoneDisabled);
        be.syncEnergyState(server);
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != active) {
            server.setBlock(pos, state.setValue(BlockStateProperties.LIT, active), Block.UPDATE_CLIENTS);
        }
    }

    /** Keep the client-side WTHIT energy bar tied to this exciter's own buffer. */
    private void syncEnergyState(final ServerLevel server) {
        final long now = server.getGameTime();
        final int current = energy.getEnergyStored();
        final boolean changedEnough = lastSyncedEnergy < 0
                || Math.abs(current - lastSyncedEnergy) >= Math.max(1, energy.getMaxEnergyStored() / 100);
        final boolean periodicDue = now - lastEnergySyncTick >= 20L;
        if (!changedEnough && !periodicDue) return;
        lastSyncedEnergy = current;
        lastEnergySyncTick = now;
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private boolean redstoneDisabledNow() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    private void updateHudState(final ServerLevel server, final Fluid gas, final boolean active,
                                final boolean redstoneDisabled) {
        if (hudGas == gas && hudActive == active && hudRedstoneDisabled == redstoneDisabled) return;
        hudGas = gas;
        hudActive = active;
        hudRedstoneDisabled = redstoneDisabled;
        setChanged();
        server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putString("HudGas", BuiltInRegistries.FLUID.getKey(hudGas).toString());
        tag.putBoolean("HudActive", hudActive);
        tag.putBoolean("HudRedstoneDisabled", hudRedstoneDisabled);
    }

    @Override protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        if (tag.contains("HudGas")) {
            hudGas = BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(tag.getString("HudGas")));
        }
        hudActive = tag.getBoolean("HudActive");
        hudRedstoneDisabled = tag.getBoolean("HudRedstoneDisabled");
    }

    @Override public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    private final class Buffer extends EnergyStorage {
        Buffer(final int capacity, final int receive) { super(capacity, receive, 0); }
        @Override public int receiveEnergy(final int amount, final boolean simulate) {
            final int accepted = super.receiveEnergy(amount, simulate);
            if (!simulate && accepted > 0) {
                GasExciterBlockEntity.this.setChanged();
                if (level instanceof ServerLevel server) GasExcitation.invalidate(server);
            }
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
