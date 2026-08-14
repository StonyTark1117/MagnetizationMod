package com.stonytark.magnetization.content.railgun;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.menu.MachineMenu;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Railgun emitter (control block at the breech of a rail). Holds the arc state
 * machine for a two-rail accelerator: {@link RailgunHandler} walks its rail,
 * pairs it with a parallel sibling, and — when a ship/entity is in the channel —
 * launches it down the rail with exponential force (the inverse of Lenz braking).
 *
 * <p>Power: redstone OR buffered FE (either-or). A {@link RailgunRemoteItem} in
 * the GUI slot switches the arc to manual mode (hold a target on the rail, then
 * fire it with the bound remote in hand). One pairing covers both sibling rails.
 */
public class RailgunEmitterBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData {

    public static final int ARC_STATE_MASK = 15;
    public static final int MANUAL_MODE_BIT = 16;
    public static final int BREAK_BLOCKS_BIT = 32;
    public static final int AUTO_ASSEMBLE_BIT = 64;

    /** Arc lifecycle. The lower-BlockPos emitter of a pair owns the live state. */
    public enum ArcState { IDLE, HOLDING, LAUNCHING, COOLDOWN }

    private final ReceiveBuffer energy = new ReceiveBuffer(
            MagConfig.railgunFeCapacity(), MagConfig.railgunFeReceive());
    private boolean redstonePowered;
    private boolean energyActiveThisTick;

    private ArcState state = ArcState.IDLE;
    private int launchTicks;
    private int cooldownTicks;
    private boolean manualMode;     // a remote is paired on this arc
    private boolean breakBlocks = true; // per-arc player control; server config remains the global gate
    private boolean autoAssemble;   // turn ordinary blocks staged between the rails into the next projectile
    private boolean fireRequested;  // set by the bound remote; consumed by the handler

    private int railLength;         // cached by the handler each scan
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();

    /** Remote-trigger slot. Inserting a remote binds it (manual mode); empty = auto. */
    private final SimpleContainer remoteSlot = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return st.is(MagItems.RAILGUN_REMOTE.get());
        }
        @Override public void setChanged() {
            super.setChanged();
            RailgunEmitterBlockEntity.this.onRemoteSlotChanged();
            RailgunEmitterBlockEntity.this.setChanged();
        }
    };

    public RailgunEmitterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.RAILGUN_EMITTER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public Container remoteContainer() { return remoteSlot; }

    public ArcState arcState() { return state; }
    public void setArcState(final ArcState s) { if (state != s) { state = s; setChanged(); } }
    public int launchTicks() { return launchTicks; }
    public void setLaunchTicks(final int t) { launchTicks = t; }
    public int cooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(final int t) { cooldownTicks = t; }
    public boolean manualMode() { return manualMode; }
    public void setManualMode(final boolean m) { if (manualMode != m) { manualMode = m; setChanged(); } }
    public boolean breaksBlocks() { return breakBlocks; }
    public void setBreakBlocks(final boolean enabled) {
        if (breakBlocks != enabled) { breakBlocks = enabled; setChanged(); }
    }
    public boolean autoAssemble() { return autoAssemble; }
    public void setAutoAssemble(final boolean enabled) {
        if (autoAssemble != enabled) { autoAssemble = enabled; setChanged(); }
    }
    public int railLength() { return railLength; }
    public void setRailLength(final int l) { if (railLength != l) { railLength = l; setChanged(); } }

    /** Consume the one-shot fire request (set by the bound remote). */
    public boolean consumeFireRequest() {
        if (!fireRequested) return false;
        fireRequested = false;
        return true;
    }
    public void requestFire() { fireRequested = true; setChanged(); }

    public void setRedstonePowered(final boolean p) { if (redstonePowered != p) { redstonePowered = p; setChanged(); } }

    /** Both a redstone signal OR buffered FE arm the emitter (either-or). */
    public boolean isPowered() {
        final boolean redstone = MagConfig.allowRedstonePower() && redstonePowered;
        final boolean fe = MagConfig.allowEnergyPower() && energy.getEnergyStored() > 0;
        return redstone || fe;
    }

    /** Drain FE for one tick of arc work; returns true if it could be paid (or if
     *  redstone covers it for free). */
    public boolean drawPower(final int cost) {
        if (cost <= 0) return true;
        if (energy.getEnergyStored() >= cost) { energy.drainInternal(cost); energyActiveThisTick = true; return true; }
        // Redstone keeps the arc live even with an empty buffer (free power).
        return MagConfig.allowRedstonePower() && redstonePowered;
    }

    private void onRemoteSlotChanged() {
        final ItemStack remote = remoteSlot.getItem(0);
        final boolean present = remote.is(MagItems.RAILGUN_REMOTE.get());
        // Inserting a remote PAIRS the arc: it binds the remote to this emitter and
        // latches manual mode. Crucially, REMOVING the remote does NOT un-pair — the
        // whole point of the manual workflow is to take the bound remote into your
        // hand to fire, so the arc must stay manual (and keep HOLDING a trapped
        // target) while the slot is empty. Un-pairing is an explicit action:
        // sneak-use the bound remote (RailgunRemoteItem), which calls unpair().
        if (present && level != null && !level.isClientSide) {
            setManualMode(true);   // the handler mirrors this to the sibling rail
            // Captures pos + dimension + rail facing/length + any anvil label, so the
            // remote's tooltip can identify the rail without it being loaded.
            RailgunRemoteItem.bind(remote, this, level.dimension());
        }
    }

    /** Explicitly return this arc to automatic mode: clears manual latch and drops a
     *  held target back to IDLE. Invoked by sneak-using the bound remote so a player
     *  who took the remote out of the slot can still un-pair. */
    public void unpair() {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            RailgunHandler.unpairArc(server, getBlockPos());
        } else {
            setManualMode(false);
            if (state == ArcState.HOLDING) setArcState(ArcState.IDLE);
        }
    }

    // ── MachineGuiData (Kind.RAILGUN: rail length + mode/state, FE bar) ──
    @Override public Container guiInput() { return remoteSlot; }
    @Override public MachineMenu.Kind guiKind() { return MachineMenu.Kind.RAILGUN; }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return MagConfig.railgunFeCapacity(); }
    @Override public int guiStat1() { return railLength; }
    /** Pack arc state (bits 0-3), remote mode (bit 4), block breaking (bit 5),
     *  and automatic world-block assembly (bit 6). */
    @Override public int guiStat2() {
        return (manualMode ? MANUAL_MODE_BIT : 0)
                | (breakBlocks ? BREAK_BLOCKS_BIT : 0)
                | (autoAssemble ? AUTO_ASSEMBLE_BIT : 0) | state.ordinal();
    }
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        return switch (state) {
            case HOLDING -> com.stonytark.magnetization.menu.MachineDisplayData.Status.HOLDING;
            case LAUNCHING -> com.stonytark.magnetization.menu.MachineDisplayData.Status.LAUNCHING;
            case COOLDOWN -> com.stonytark.magnetization.menu.MachineDisplayData.Status.COOLDOWN;
            case IDLE -> com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE;
        };
    }

    public static void serverTick(final net.minecraft.world.level.Level level, final BlockPos pos,
                                  final BlockState st, final RailgunEmitterBlockEntity be) {
        be.energyActiveThisTick = false;
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(st)) return;
        be.energy.resize(MagConfig.railgunFeCapacity(), MagConfig.railgunFeReceive());
        // The heavy lifting (rail walk / pairing / acceleration) lives in the
        // RailgunHandler so a single pass covers a whole arc; per-BE tick just
        // decays the cooldown so an unpaired/idle emitter still re-arms.
        if (be.state == ArcState.COOLDOWN && be.cooldownTicks > 0) {
            be.cooldownTicks--;
            if (be.cooldownTicks <= 0) be.setArcState(ArcState.IDLE);
        }
        // Push the live arc state (rail length, mode, arc state, FE) to the client BE
        // so the at-a-glance HUD (WTHIT/Jade/TOP/Create goggles) reads current values — the mutators
        // above only setChanged() (chunk-dirty), which never transmits to clients.
        // Mirrors every sibling machine's serverTick sync.
        if (level instanceof net.minecraft.server.level.ServerLevel server && server.getGameTime() % 10L == 0L
                && be.syncGate.changed(be, server.registryAccess())) {
            server.sendBlockUpdated(pos, st, st, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) RailgunRegistry.register(level, this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            RailgunRegistry.unregister(level, getBlockPos());
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                RailgunHandler.releaseHoldsForEmitter(server, getBlockPos());
            }
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putByte("State", (byte) state.ordinal());
        tag.putInt("Launch", launchTicks);
        tag.putInt("Cooldown", cooldownTicks);
        tag.putBoolean("Manual", manualMode);
        tag.putBoolean("BreakBlocks", breakBlocks);
        tag.putBoolean("AutoAssemble", autoAssemble);
        tag.putBoolean("RedstonePowered", redstonePowered);
        tag.putInt("RailLength", railLength);
        tag.put("Remote", remoteSlot.createTag(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        state = ArcState.values()[Math.min(ArcState.values().length - 1, tag.getByte("State") & 0xFF)];
        launchTicks = tag.getInt("Launch");
        cooldownTicks = tag.getInt("Cooldown");
        manualMode = tag.getBoolean("Manual");
        // Existing worlds predate the per-arc switch and retain the historical
        // block-breaking behaviour until a player explicitly disables it.
        breakBlocks = !tag.contains("BreakBlocks") || tag.getBoolean("BreakBlocks");
        autoAssemble = tag.getBoolean("AutoAssemble");
        redstonePowered = tag.getBoolean("RedstonePowered");
        railLength = tag.getInt("RailLength");
        remoteSlot.fromTag(tag.getList("Remote", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    private static final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int maxReceive) { super(capacity, maxReceive, 0); }
        void drainInternal(final int amount) { this.energy = Math.max(0, this.energy - amount); }
        void setStored(final int value) { this.energy = Math.max(0, Math.min(capacity, value)); }
        void resize(final int capacity, final int maxReceive) {
            this.capacity = Math.max(0, capacity);
            this.maxReceive = Math.max(0, maxReceive);
            this.energy = Math.min(this.energy, this.capacity);
        }
    }
}
