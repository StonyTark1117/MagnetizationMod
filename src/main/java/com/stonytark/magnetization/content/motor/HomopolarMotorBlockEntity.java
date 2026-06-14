package com.stonytark.magnetization.content.motor;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Homopolar Motor / Magnetic Flywheel. A Create kinetic <em>generator</em> that
 * spins from a magnet alone — drop a magnet in its slot and it drives axles,
 * gears and rollers with no fuel or energy. Deliberately weak: the magnet's
 * pull is the only "current", so even the strongest magnet tops out at a gentle
 * RPM. Speed + stress capacity both scale with the installed magnet's strength.
 */
public class HomopolarMotorBlockEntity extends GeneratingKineticBlockEntity {

    private ItemStack magnet = ItemStack.EMPTY;

    public HomopolarMotorBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    public ItemStack getMagnet() {
        return magnet;
    }

    /** Install a magnet (returns the previously-held one). Updates rotation. */
    public ItemStack setMagnet(final ItemStack stack) {
        final ItemStack prev = this.magnet;
        this.magnet = stack;
        setChanged();
        updateGeneratedRotation();
        return prev;
    }

    /** RPM this magnet yields, or 0 for none / a non-magnet. Kept low — it's weak. */
    public static float speedFor(final ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        if (stack.is(MagItems.PERMANENT_MAGNET.get().asItem())) return 32f;
        if (stack.is(MagItems.TEMPORARY_MAGNET.get().asItem())) return 16f;
        if (stack.is(MagItems.MAGNETIC_PLATE.get())) return 8f;
        return 0f;
    }

    /** Stress capacity (su) this magnet provides. */
    public static float capacityFor(final ItemStack stack) {
        if (stack.isEmpty()) return 0f;
        if (stack.is(MagItems.PERMANENT_MAGNET.get().asItem())) return 128f;
        if (stack.is(MagItems.TEMPORARY_MAGNET.get().asItem())) return 64f;
        if (stack.is(MagItems.MAGNETIC_PLATE.get())) return 32f;
        return 0f;
    }

    public static boolean isMagnet(final ItemStack stack) {
        return speedFor(stack) > 0f;
    }

    @Override
    public float getGeneratedSpeed() {
        return getBlockState().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)
                ? speedFor(magnet) : 0f;
    }

    @Override
    public float calculateAddedStressCapacity() {
        final float capacity = capacityFor(magnet);
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!magnet.isEmpty()) tag.put("Magnet", magnet.save(registries));
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        magnet = tag.contains("Magnet") ? ItemStack.parseOptional(registries, tag.getCompound("Magnet")) : ItemStack.EMPTY;
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        final boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.translatable("tooltip.magnetization.motor_magnet",
                        magnet.isEmpty() ? Component.translatable("tooltip.magnetization.motor_no_magnet")
                                         : magnet.getHoverName())
                .withStyle(ChatFormatting.GRAY));
        return added || true;
    }
}
