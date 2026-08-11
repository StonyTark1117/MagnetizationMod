package com.stonytark.magnetization.menu;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.gas.AirSeparatorBlockEntity;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Dedicated two-slot, five-tank menu for the Air Separator. */
public final class AirSeparatorMenu extends AbstractContainerMenu {
    public static final int ASSIGN_OUTPUT_BASE = 100;
    public static final int SET_INPUT_BASE = 200;
    public static final Direction[] FACE_OPTIONS = {
            Direction.UP, Direction.DOWN, Direction.NORTH,
            Direction.SOUTH, Direction.EAST, Direction.WEST
    };
    public static final int IMAGE_WIDTH = AirSeparatorGuiLayout.IMAGE_WIDTH;
    public static final int IMAGE_HEIGHT = AirSeparatorGuiLayout.IMAGE_HEIGHT;
    public static final int UPGRADE_X = AirSeparatorGuiLayout.UPGRADE_X;
    public static final int UPGRADE_Y = AirSeparatorGuiLayout.UPGRADE_Y;
    public static final int OUTPUT_X = AirSeparatorGuiLayout.OUTPUT_X;
    public static final int OUTPUT_Y = AirSeparatorGuiLayout.OUTPUT_Y;
    public static final int PLAYER_INVENTORY_X = AirSeparatorGuiLayout.PLAYER_INVENTORY_X;
    public static final int PLAYER_INVENTORY_Y = AirSeparatorGuiLayout.PLAYER_INVENTORY_Y;
    public static final int HOTBAR_Y = AirSeparatorGuiLayout.HOTBAR_Y;
    public static final int INVENTORY_LABEL_Y = AirSeparatorGuiLayout.INVENTORY_LABEL_Y;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Container upgrade;
    private final Container output;
    private final WideData[] amounts = wideArray(AirSeparatorBlockEntity.COUNT);
    private final WideData capacity = new WideData();
    private final WideData[] ratesMilli = wideArray(AirSeparatorBlockEntity.COUNT);
    private final DataSlot rpm = DataSlot.standalone();
    private final DataSlot minRpm = DataSlot.standalone();
    private final DataSlot maxRpm = DataSlot.standalone();
    private final DataSlot isotopeProgressPermille = DataSlot.standalone();
    private final DataSlot status = DataSlot.standalone();
    private final DataSlot[] outputFaces = dataArray(AirSeparatorBlockEntity.COUNT);
    private final DataSlot mechanicalFace = DataSlot.standalone();

    public AirSeparatorMenu(final int id, final Inventory inventory, final ContainerLevelAccess access,
                            final BlockPos pos, final Container upgrade, final Container output) {
        super(MagMenus.AIR_SEPARATOR.get(), id);
        this.access = access;
        this.pos = pos;
        this.upgrade = upgrade;
        this.output = output;
        checkContainerSize(upgrade, 1);
        checkContainerSize(output, 1);

        addSlot(new Slot(upgrade, 0, UPGRADE_X, UPGRADE_Y) {
            @Override public boolean mayPlace(final ItemStack stack) {
                return stack.is(MagItems.ISOTOPE_SEPARATION_MODULE.get());
            }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(output, 0, OUTPUT_X, OUTPUT_Y) {
            @Override public boolean mayPlace(final ItemStack stack) { return false; }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        PLAYER_INVENTORY_X + col * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, PLAYER_INVENTORY_X + col * 18, HOTBAR_Y));
        }

        for (final WideData amount : amounts) addWideData(amount);
        addWideData(capacity);
        for (final WideData rate : ratesMilli) addWideData(rate);
        addDataSlot(rpm);
        addDataSlot(minRpm);
        addDataSlot(maxRpm);
        addDataSlot(isotopeProgressPermille);
        addDataSlot(status);
        for (final DataSlot face : outputFaces) addDataSlot(face);
        addDataSlot(mechanicalFace);
        refresh();
    }

    public static AirSeparatorMenu fromNetwork(final int id, final Inventory inventory,
                                               final RegistryFriendlyByteBuf buffer) {
        final BlockPos pos = buffer.readBlockPos();
        return new AirSeparatorMenu(id, inventory, ContainerLevelAccess.NULL, pos,
                new SimpleContainer(1), new SimpleContainer(1));
    }

    public static void writeOpen(final RegistryFriendlyByteBuf buffer, final BlockPos pos) {
        buffer.writeBlockPos(pos);
    }

    public BlockPos pos() { return pos; }
    public int amount(final int gas) { return amounts[gas].get(); }
    public int capacity() { return Math.max(1, capacity.get()); }
    public int totalStored() {
        int total = 0;
        for (final WideData amount : amounts) total += amount.get();
        return total;
    }
    public int rateMilli(final int gas) { return ratesMilli[gas].get(); }
    public int rpm() { return Math.max(0, rpm.get()); }
    public int minRpm() { return Math.max(1, minRpm.get()); }
    public int maxRpm() { return Math.max(minRpm(), maxRpm.get()); }
    public int isotopeProgressPermille() { return Math.max(0, Math.min(1000, isotopeProgressPermille.get())); }
    public AirSeparatorBlockEntity.OperatingStatus status() {
        final var values = AirSeparatorBlockEntity.OperatingStatus.values();
        final int code = status.get();
        return code >= 0 && code < values.length ? values[code]
                : AirSeparatorBlockEntity.OperatingStatus.NEEDS_SPEED;
    }
    public Direction outputFace(final int gas) {
        return Direction.from3DDataValue(outputFaces[gas].get());
    }
    public Direction mechanicalFace() {
        return Direction.from3DDataValue(mechanicalFace.get());
    }
    public int occupantAt(final Direction face) {
        if (face == mechanicalFace()) return -2;
        for (int gas = 0; gas < AirSeparatorBlockEntity.COUNT; gas++) {
            if (outputFace(gas) == face) return gas;
        }
        return -1;
    }
    public static int assignOutputButton(final int gas, final Direction face) {
        return ASSIGN_OUTPUT_BASE + gas * 6 + face.get3DDataValue();
    }
    public static int setInputButton(final Direction face) {
        return SET_INPUT_BASE + face.get3DDataValue();
    }

    private void refresh() {
        access.execute((level, blockPos) -> {
            if (!(level.getBlockEntity(blockPos) instanceof AirSeparatorBlockEntity separator)) return;
            for (int gas = 0; gas < AirSeparatorBlockEntity.COUNT; gas++) {
                amounts[gas].set(separator.tank(gas).getFluidAmount());
                ratesMilli[gas].set(separator.currentRateMilli(gas));
                outputFaces[gas].set(separator.outputFace(gas).get3DDataValue());
            }
            mechanicalFace.set(separator.mechanicalFace().get3DDataValue());
            capacity.set(separator.tank(AirSeparatorBlockEntity.HELIUM).getCapacity());
            rpm.set(separator.currentRpm());
            minRpm.set(MagConfig.airSeparatorMinRpm());
            maxRpm.set(MagConfig.airSeparatorMaxRpm());
            isotopeProgressPermille.set(separator.isotopeProgressPermille());
            status.set(separator.operatingStatus().ordinal());
        });
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(final Player player, final int id) {
        return access.evaluate((level, blockPos) -> {
            if (!(level.getBlockEntity(blockPos) instanceof AirSeparatorBlockEntity separator)) return false;
            final boolean changed;
            if (id >= ASSIGN_OUTPUT_BASE && id < SET_INPUT_BASE) {
                final int selection = id - ASSIGN_OUTPUT_BASE;
                final int gas = selection / 6;
                final int faceId = selection % 6;
                changed = gas >= 0 && gas < AirSeparatorBlockEntity.COUNT
                        && separator.assignOutputFace(gas, Direction.from3DDataValue(faceId));
            } else if (id >= SET_INPUT_BASE && id < SET_INPUT_BASE + 6) {
                changed = separator.setMechanicalFace(
                        Direction.from3DDataValue(id - SET_INPUT_BASE));
            } else {
                return false;
            }
            if (changed) refresh();
            return changed;
        }, false);
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();
        if (index < 2) {
            if (!moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (original.is(MagItems.ISOTOPE_SEPARATION_MODULE.get())) {
            if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(final Player player) {
        return access.evaluate((level, blockPos) ->
                level.getBlockEntity(blockPos) instanceof AirSeparatorBlockEntity
                        && player.distanceToSqr(blockPos.getX() + .5, blockPos.getY() + .5,
                        blockPos.getZ() + .5) <= 64.0, true);
    }

    private void addWideData(final WideData value) {
        addDataSlot(value.lo);
        addDataSlot(value.hi);
    }

    private static WideData[] wideArray(final int size) {
        final WideData[] values = new WideData[size];
        for (int i = 0; i < size; i++) values[i] = new WideData();
        return values;
    }

    private static DataSlot[] dataArray(final int size) {
        final DataSlot[] values = new DataSlot[size];
        for (int i = 0; i < size; i++) values[i] = DataSlot.standalone();
        return values;
    }

    private static final class WideData {
        private final DataSlot lo = DataSlot.standalone();
        private final DataSlot hi = DataSlot.standalone();
        void set(final int value) { lo.set(value & 0xFFFF); hi.set(value >>> 16); }
        int get() { return (hi.get() << 16) | (lo.get() & 0xFFFF); }
    }
}
