package com.stonytark.magnetization.menu;

import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.registry.MagItems;
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
    public enum Kind { MOTOR, JET, TOKAMAK, THRUSTER, FUSION_THRUSTER, RAILGUN, ELECTROLYZER,
        /** HUD-only kinds — no GUI menu, surface live status in WTHIT/Jade/TOP/Create goggles. */
        COIL, SAIL }

    public static final int INPUT_X = 80;
    public static final int INPUT_Y = 33;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final Kind kind;
    private final Container input;
    private final WideData energyStored = new WideData();
    private final WideData energyMax = new WideData();
    private final WideData stat1 = new WideData();
    private final WideData stat2 = new WideData();
    private final WideData stat3 = new WideData();
    private final WideData stat4 = new WideData();
    private final DataSlot displayStatus = DataSlot.standalone();

    /** A 32-bit value synced across TWO {@link DataSlot}s (low + high 16 bits). A
     *  vanilla DataSlot is sent as a signed 16-bit short, so a single slot wraps to
     *  garbage for the FE buffers (200k..4M) and large fluid amounts these machines
     *  report. Splitting preserves the exact value (negatives included) over the wire. */
    private static final class WideData {
        private final DataSlot lo = DataSlot.standalone();
        private final DataSlot hi = DataSlot.standalone();
        void set(final int v) { lo.set(v & 0xFFFF); hi.set(v >>> 16); }
        int get() { return (hi.get() << 16) | (lo.get() & 0xFFFF); }
    }

    public MachineMenu(final int id, final Inventory inv, final ContainerLevelAccess access,
                       final BlockPos pos, final Kind kind, final Container input) {
        super(MagMenus.MACHINE.get(), id);
        this.access = access;
        this.pos = pos;
        this.kind = kind;
        this.input = input;
        checkContainerSize(input, 1);

        // Input slot constrained to what this machine accepts. The filter is
        // resolved from the item ID + this menu's kind, so it returns the SAME
        // answer on the client and the server (no dummy-container desync), and
        // the cap is a hard 1 on both sides.
        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(final ItemStack stack) {
                return accepts(stack);
            }
            @Override
            public int getMaxStackSize() {
                return 1;
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
        addDataSlot(energyStored.lo);
        addDataSlot(energyStored.hi);
        addDataSlot(energyMax.lo);
        addDataSlot(energyMax.hi);
        addDataSlot(stat1.lo);
        addDataSlot(stat1.hi);
        addDataSlot(stat2.lo);
        addDataSlot(stat2.hi);
        addDataSlot(stat3.lo);
        addDataSlot(stat3.hi);
        addDataSlot(stat4.lo);
        addDataSlot(stat4.hi);
        addDataSlot(displayStatus);
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
    public int stat3() { return stat3.get(); }
    /** Authoritative fuel/fluid bar denominator, synced from the server's config. */
    public int stat4() { return stat4.get(); }
    public int displayCurrent() { return stat1(); }
    public int displayAuxiliary() { return stat2(); }
    public int displayTier() { return stat3(); }
    public int displayCapacity() { return Math.max(1, stat4()); }
    public MachineDisplayData.Status displayStatus() {
        final MachineDisplayData.Status[] values = MachineDisplayData.Status.values();
        final int code = displayStatus.get();
        return code >= 0 && code < values.length ? values[code] : MachineDisplayData.Status.IDLE;
    }
    public MachineDisplayData displayData() {
        return new MachineDisplayData(energyStored(), energyMax(), displayCurrent(), displayCapacity(),
                displayTier(), displayAuxiliary(), displayStatus());
    }

    private void refresh() {
        access.execute((level, p) -> {
            if (level.getBlockEntity(p) instanceof MachineGuiData d) {
                final MachineDisplayData snapshot = d.displayData();
                energyStored.set(snapshot.energyStored());
                energyMax.set(snapshot.energyCapacity());
                stat1.set(snapshot.current());
                stat2.set(snapshot.auxiliary());
                stat3.set(snapshot.tier());
                stat4.set(snapshot.capacity());
                displayStatus.set(snapshot.statusCode());
            }
        });
    }

    @Override
    public void broadcastChanges() {
        refresh();
        super.broadcastChanges();
    }

    /** What this machine's input slot accepts, by kind. Deterministic from the
     *  item ID so it's identical client-side and server-side. */
    public boolean accepts(final ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case MOTOR, JET -> MagneticMaterials.isMagnet(stack);
            case TOKAMAK -> stack.is(MagItems.DEUTERIUM_CELL.get())
                    || stack.is(MagItems.TRITIUM_CELL.get()) || stack.is(MagItems.HELIUM_3_CELL.get());
            case THRUSTER -> stack.is(MagItems.FERROFLUID_BUCKET.get());
            case FUSION_THRUSTER -> com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.isFusionFluidBucket(stack);
            case RAILGUN -> stack.is(MagItems.RAILGUN_REMOTE.get());
            case ELECTROLYZER -> stack.is(net.minecraft.world.item.Items.WATER_BUCKET);
            case COIL, SAIL -> false;   // HUD-only kinds: no menu, no input slot
        };
    }

    @Override
    public ItemStack quickMoveStack(final Player player, final int index) {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();
        if (index == 0) {
            if (!moveItemStackTo(original, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (accepts(original)) {
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
