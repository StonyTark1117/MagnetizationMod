package com.stonytark.magnetization.content.motor;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Homopolar Motor / Magnetic Flywheel. A Create kinetic <em>generator</em> that
 * spins from a magnet alone — slot a magnet (via the GUI, or quick-insert by
 * right-clicking with one) and it drives axles, gears and rollers with no fuel
 * or energy. Deliberately weak: even the strongest magnet tops out at a gentle
 * RPM. Speed + stress capacity both scale with the installed magnet's strength.
 */
public class HomopolarMotorBlockEntity extends GeneratingKineticBlockEntity {

    /** One-slot magnet inventory, exposed to {@code HomopolarMotorMenu}. */
    private final SimpleContainer magnetSlot = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            onMagnetChanged();
        }
        @Override
        public boolean canPlaceItem(final int slot, final ItemStack stack) {
            return isMagnet(stack);
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    public HomopolarMotorBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    public Container magnetContainer() {
        return magnetSlot;
    }

    public ItemStack getMagnet() {
        return magnetSlot.getItem(0);
    }

    /** Install a magnet (returns the previously-held one). */
    public ItemStack setMagnet(final ItemStack stack) {
        final ItemStack prev = magnetSlot.getItem(0);
        magnetSlot.setItem(0, stack);
        return prev;
    }

    private void onMagnetChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            updateGeneratedRotation();
        }
    }

    /** RPM this magnet yields, or 0 for none / a non-magnet. Kept low — it's weak. */
    public static float speedFor(final ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        if (stack.is(MagItems.PERMANENT_MAGNET.get())) return 32f;
        if (stack.is(MagItems.TEMPORARY_MAGNET.get())) return 16f;
        if (stack.is(MagItems.MAGNETIC_PLATE.get())) return 8f;
        return 0f;
    }

    /** Stress capacity (su) this magnet provides. */
    public static float capacityFor(final ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        if (stack.is(MagItems.PERMANENT_MAGNET.get())) return 128f;
        if (stack.is(MagItems.TEMPORARY_MAGNET.get())) return 64f;
        if (stack.is(MagItems.MAGNETIC_PLATE.get())) return 32f;
        return 0f;
    }

    public static boolean isMagnet(final ItemStack stack) {
        return speedFor(stack) > 0f;
    }

    @Override
    public float getGeneratedSpeed() {
        return getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
                ? speedFor(getMagnet()) : 0f;
    }

    @Override
    public float calculateAddedStressCapacity() {
        final float capacity = capacityFor(getMagnet());
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Magnet", magnetSlot.createTag(registries));
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        magnetSlot.fromTag(tag.getList("Magnet", Tag.TAG_COMPOUND), registries);
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        final boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.translatable("tooltip.magnetization.motor_magnet",
                        getMagnet().isEmpty() ? Component.translatable("tooltip.magnetization.motor_no_magnet")
                                              : getMagnet().getHoverName())
                .withStyle(ChatFormatting.GRAY));
        return added || true;
    }
}
