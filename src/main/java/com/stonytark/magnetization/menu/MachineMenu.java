package com.stonytark.magnetization.menu;

import com.stonytark.magnetization.registry.MagMenus;
import net.minecraft.core.BlockPos;
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

/**
 * Shared one-input-slot machine menu (motor / MHD jet / tokamak / micro-thruster).
 * The {@code kind} tells {@link com.stonytark.magnetization.client.screen.MachineScreen}
 * how to label the energy bar + two synced stat readouts pulled from the BE's
 * {@link MachineGuiData}.
 */
public final class MachineMenu extends AbstractContainerMenu {

    /** Display flavour — drives slot tooltip + stat labels on the screen. */
    public enum Kind { MOTOR, JET, TOKAMAK, THRUSTER }

    public static final int INPUT_X = 80;
    public static final int INPUT_Y = 33;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Kind kind;
    private final Container input;
    private final DataSlot energyStored = DataSlot.standalone();
    private final DataSlot energyMax = DataSlot.standalone();
    private final DataSlot stat1 = DataSlot.standalone();
    private final DataSlot stat2 = DataSlot.standalone();

    public MachineMenu(final int id, final Inventory inv, final ContainerLevelAccess access,
                       final BlockPos pos, final Kind kind, final Container input) {
        super(MagMenus.MACHINE.get(), id);
        this.access = access;
        this.pos = pos;
        this.kind = kind;
        this.input = input;
        checkContainerSize(input, 1);

        // Constrained input slot: honours the BE container's canPlaceItem filter
        // (magnet / fuel cell / bucket) AND its max-stack-of-1, so shift-click,
        // drag, and hopper insertion all respect the same rule.
        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return input.canPlaceItem(0, stack);
            }
            @Override
            public int getMaxStackSize() {
                return input.getMaxStackSize();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
        addDataSlot(energyStored);
        addDataSlot(energyMax);
        addDataSlot(stat1);
        addDataSlot(stat2);
        refresh();
    }

    public static MachineMenu fromNetwork(final int id, final Inventory inv, final RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final Kind kind = buf.readEnum(Kind.class);
        return new MachineMenu(id, inv, ContainerLevelAccess.NULL, pos, kind, new SimpleContainer(1));
    }

    public static void writeOpen(final RegistryFriendlyByteBuf buf, final BlockPos pos, final Kind kind) {
        buf.writeBlockPos(pos);
        buf.writeEnum(kind);
    }

    public Kind kind() { return kind; }
    public BlockPos pos() { return pos; }
    public int energyStored() { return energyStored.get(); }
    public int energyMax() { return Math.max(1, energyMax.get()); }
    public int stat1() { return stat1.get(); }
    public int stat2() { return stat2.get(); }

    private void refresh() {
        access.execute((level, p) -> {
            if (level.getBlockEntity(p) instanceof MachineGuiData d) {
                energyStored.set(d.guiEnergyStored());
                energyMax.set(d.guiEnergyMax());
                stat1.set(d.guiStat1());
                stat2.set(d.guiStat2());
            }
        });
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();
        if (index == 0) {
            if (!moveItemStackTo(original, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (input.canPlaceItem(0, original)) {
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
        return access.evaluate((level, p) ->
                level.getBlockEntity(p) instanceof MachineGuiData
                        && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0,
                true);
    }
}
