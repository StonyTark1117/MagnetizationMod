package com.stonytark.magnetization.content.motor;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
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
public class HomopolarMotorBlockEntity extends GeneratingKineticBlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData {

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

    // ── MachineGuiData (shared GUI) ──
    @Override public Container guiInput() { return magnetSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.MOTOR;
    }
    @Override public int guiStat1() { return Math.round(Math.abs(getGeneratedSpeed())); } // RPM
    // No energy bar (it's a generator).

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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) updateGeneratedRotation();
    }

    /** RPM this magnetic material yields, or 0 for a non-magnet. Deliberately
     *  gentle (the motor is the weak machine): 2 RPM per potency point, so the
     *  ladder runs ~6 RPM (hematite ore) up to ~58 RPM (titanomagnetite block). */
    public static float speedFor(final ItemStack stack) {
        final int potency = com.stonytark.magnetization.content.MagneticMaterials.potency(stack);
        return potency <= 0 ? 0f : potency * com.stonytark.magnetization.config.MagConfig.motorRpmPerPotency();
    }

    /** Stress capacity (su) this material provides — scales with potency so a
     *  stronger magnet can drive a bigger Create network. */
    public static float capacityFor(final ItemStack stack) {
        final int potency = com.stonytark.magnetization.content.MagneticMaterials.potency(stack);
        return potency <= 0 ? 0f : potency * com.stonytark.magnetization.config.MagConfig.motorStressPerPotency();
    }

    public static boolean isMagnet(final ItemStack stack) {
        return com.stonytark.magnetization.content.MagneticMaterials.isMagnet(stack);
    }

    @Override
    public float getGeneratedSpeed() {
        final ItemStack m = getMagnet();
        if (m.isEmpty() || !getBlockState().hasProperty(
                com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING)) {
            return 0f;
        }
        // Create generators must sign the speed by their facing.
        return convertToDirection(speedFor(m),
                getBlockState().getValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING));
    }

    /** Kick the kinetic network when the generator (re)loads, like Create's own. */
    @Override
    public void initialize() {
        super.initialize();
        if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed()) {
            updateGeneratedRotation();
        }
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
