package com.stonytark.magnetization.content.itemframe;

import com.stonytark.magnetization.registry.MagBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the single item a {@link MagneticItemFrameBlock} displays. Synced to the
 * client (the {@code MagneticItemFrameRenderer} draws it on the plate face).
 */
public class MagneticItemFrameBlockEntity extends BlockEntity {

    private ItemStack displayed = ItemStack.EMPTY;

    public MagneticItemFrameBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_ITEM_FRAME.get(), pos, state);
    }

    public ItemStack getDisplayedItem() {
        return displayed;
    }

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

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!displayed.isEmpty()) {
            tag.put("Item", displayed.save(registries));
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.displayed = tag.contains("Item")
                ? ItemStack.parse(registries, tag.getCompound("Item")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
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
}
