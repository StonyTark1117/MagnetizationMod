package com.stonytark.magnetization.content.itemframe;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Holds the single item a {@link MagneticItemFrameBlock} displays. When fed
 * redstone or FE it magnetically spins the item (direction toggled by
 * right-click / wrench). Synced to the client, which draws + spins the item.
 */
public class MagneticItemFrameBlockEntity extends BlockEntity {

    /** Spin modes, cycled by right-click: turn left / turn right / tumble up /
     *  tumble down. Two axes (yaw for left-right, pitch for up-down) × two signs. */
    public static final int SPIN_LEFT = 0, SPIN_RIGHT = 1, SPIN_UP = 2, SPIN_DOWN = 3;

    private ItemStack displayed = ItemStack.EMPTY;
    private final ReceiveBuffer energy = new ReceiveBuffer(
            com.stonytark.magnetization.config.MagConfig.itemFrameFeCapacity(), 200);
    /** Synced: is the item currently spinning. */
    private boolean spinning = false;
    /** Synced spin mode (one of {@code SPIN_*}). */
    private int spinMode = SPIN_LEFT;

    public MagneticItemFrameBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_ITEM_FRAME.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public ItemStack getDisplayedItem() { return displayed; }
    public boolean isSpinning() { return spinning; }
    public int spinMode() { return spinMode; }

    public void setDisplayedItem(final ItemStack stack) {
        this.displayed = stack;
        setChanged();
        sync();
    }

    public ItemStack removeDisplayedItem() {
        final ItemStack out = displayed;
        this.displayed = ItemStack.EMPTY;
        setChanged();
        sync();
        return out;
    }

    /** Cycle the spin mode left → right → up → down → … (right-click / wrench). */
    public void cycleSpin() {
        spinMode = (spinMode + 1) % 4;
        setChanged();
        sync();
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MagneticItemFrameBlockEntity be) {
        if (!(level instanceof ServerLevel)) return;
        boolean powered = level.hasNeighborSignal(pos);
        final int fePerTick = com.stonytark.magnetization.config.MagConfig.itemFrameFePerTick();
        if (!powered && fePerTick > 0 && be.energy.getEnergyStored() >= fePerTick) {
            be.energy.drainInternal(fePerTick);
            powered = true;
        }
        // Only spin a held item.
        final boolean now = powered && !be.displayed.isEmpty();
        if (now != be.spinning) {
            be.spinning = now;
            be.setChanged();
            be.sync();
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!displayed.isEmpty()) tag.put("Item", displayed.save(registries));
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putBoolean("Spinning", spinning);
        tag.putInt("SpinMode", spinMode);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.displayed = tag.contains("Item")
                ? ItemStack.parse(registries, tag.getCompound("Item")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        energy.setStored(tag.getInt("Energy"));
        spinning = tag.getBoolean("Spinning");
        if (tag.contains("SpinMode")) {
            spinMode = Math.floorMod(tag.getInt("SpinMode"), 4);
        } else if (tag.contains("SpinDir")) { // legacy: -1 was "right", else "left"
            spinMode = tag.getInt("SpinDir") < 0 ? SPIN_RIGHT : SPIN_LEFT;
        } else {
            spinMode = SPIN_LEFT;
        }
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int maxReceive) { super(capacity, maxReceive, 0); }
        void drainInternal(final int amount) { this.energy = Math.max(0, this.energy - amount); }
        void setStored(final int value) { this.energy = Math.max(0, Math.min(capacity, value)); }
    }
}
