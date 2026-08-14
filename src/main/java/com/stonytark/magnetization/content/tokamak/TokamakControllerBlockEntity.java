package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * Tokamak fusion reactor controller. When ringed by a complete square perimeter
 * of Tokamak Coils (3x3 minimum; larger odd rings are supported) and loaded
 * with deuterium fuel, it fuses and generates a large, steady FE output that it
 * pushes to adjacent machines/cables. Larger rings scale the reactor linearly.
 * Fuel is loaded by right-clicking with a Deuterium Cell.
 */
public class TokamakControllerBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData {

    private final GenBuffer energy = new GenBuffer(
            com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(),
            // Extract cap = the most any tier can output (He-3) so per-tier limiting
            // in pushEnergy isn't clamped by the buffer's own maxExtract.
            com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3());
    private int burnTime = 0;
    private int currentTier = 0;   // 0 = D-D, 1 = D-T, 2 = D-He³ — set when a cell is consumed
    private int lastOutput = 0; // FE actually pushed to neighbours last tick (GUI readout)
    private int formedRingEdge = 0;
    private int formedRingMultiplier = 0;
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();

    /** Fuel slot — holds spare fusion cells (D-D / D-T / D-He³), auto-fed into the burn. */
    private final net.minecraft.world.SimpleContainer fuelSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return cellTier(st) >= 0;
        }
        @Override public void setChanged() { super.setChanged(); TokamakControllerBlockEntity.this.setChanged(); }
    };

    /** Fusion-cell tier: 0 = Deuterium, 1 = Tritium, 2 = Helium-3, -1 = not a cell. */
    private static int cellTier(final ItemStack st) {
        if (st.is(com.stonytark.magnetization.registry.MagItems.DEUTERIUM_CELL.get())) return 0;
        if (st.is(com.stonytark.magnetization.registry.MagItems.TRITIUM_CELL.get())) return 1;
        if (st.is(com.stonytark.magnetization.registry.MagItems.HELIUM_3_CELL.get())) return 2;
        return -1;
    }

    private static int tierGenPerTick(final int tier) {
        return switch (tier) {
            case 1 -> com.stonytark.magnetization.config.MagConfig.tokamakGenPerTickTritium();
            case 2 -> com.stonytark.magnetization.config.MagConfig.tokamakGenPerTickHelium3();
            default -> com.stonytark.magnetization.config.MagConfig.tokamakGenPerTick();
        };
    }

    private static int tierBurnTicks(final int tier) {
        return switch (tier) {
            case 1 -> com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksTritium();
            case 2 -> com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksHelium3();
            default -> com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksPerCell();
        };
    }

    private static int tierOutputRate(final int tier) {
        return switch (tier) {
            case 1 -> com.stonytark.magnetization.config.MagConfig.tokamakOutputRateTritium();
            case 2 -> com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3();
            default -> com.stonytark.magnetization.config.MagConfig.tokamakOutputRate();
        };
    }

    public int currentTier() { return currentTier; }

    public TokamakControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TOKAMAK_CONTROLLER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() {
        return energy;
    }

    public net.minecraft.world.Container fuelContainer() {
        return fuelSlot;
    }

    // ── MachineGuiData (shared GUI: fuel runtime + current FE output) ──
    @Override public net.minecraft.world.Container guiInput() { return fuelSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.TOKAMAK;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() {
        return scaled(com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(),
                Math.max(1, formedRingMultiplier));
    }
    @Override public int guiStat1() { return burnTime; }          // ticks; screen shows seconds
    @Override public int guiStat2() { return lastOutput; }        // FE/tick out
    @Override public int guiStat3() { return currentTier; }       // 0=D-D, 1=D-T, 2=D-He³
    @Override public int guiStat4() { return tierBurnTicks(currentTier); }  // bar denominator (server config)
    @Override public int guiStructureSize() { return formedRingEdge; }
    @Override public int guiStructureScale() { return formedRingMultiplier; }
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        if (formedRingEdge <= 0) {
            return com.stonytark.magnetization.menu.MachineDisplayData.Status.INVALID;
        }
        return level != null && getBlockState().hasProperty(BlockStateProperties.LIT)
                && getBlockState().getValue(BlockStateProperties.LIT)
                ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.FORMED;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final TokamakControllerBlockEntity be) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(state)) return;
        if (!(level instanceof ServerLevel server)) return;
        // Resolve formation once per tick. Besides avoiding two full perimeter
        // scans, this keeps capacity, generation, output, and LIT state tied to
        // the exact same authoritative ring snapshot.
        final TokamakRingPreview.Preview ring = TokamakRingPreview.preview(level, pos);
        final boolean formed = ring.valid();
        final int multiplier = formed ? Math.max(1, ring.edge() - 2) : 1;
        be.formedRingEdge = formed ? ring.edge() : 0;
        be.formedRingMultiplier = formed ? multiplier : 0;
        be.energy.resize(scaled(com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(), multiplier),
                scaled(com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3(), multiplier));
        // Auto-feed: load one cell when the burn is empty, recording its tier so
        // gen/output use that tier's rates (mixing tiers cleanly, one cell at a time).
        if (be.burnTime <= 0) {
            final ItemStack cell = be.fuelSlot.getItem(0);
            final int tier = cellTier(cell);
            if (tier >= 0) {
                cell.shrink(1);
                be.fuelSlot.setItem(0, cell);
                be.currentTier = tier;
                be.burnTime += tierBurnTicks(tier);
                be.setChanged();
            }
        }
        final boolean fusing = be.burnTime > 0 && formed;
        if (fusing) {
            be.energy.generate(scaled(tierGenPerTick(be.currentTier), multiplier));
            be.burnTime--;
            be.setChanged();
        }
        if (state.getValue(BlockStateProperties.LIT) != fusing) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, fusing), Block.UPDATE_CLIENTS);
        }
        be.lastOutput = pushEnergy(server, pos, be.energy,
                scaled(tierOutputRate(be.currentTier), multiplier));
        if (server.getGameTime() % 10L == 0L && be.syncGate.changed(be, server.registryAccess())) {
            server.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS); // WTHIT
        }
    }

    /** The largest complete odd-edged Tokamak coil ring up to the configured limit. */
    public static boolean isRingFormed(final Level level, final BlockPos pos) {
        return TokamakRingPreview.preview(level, pos).valid();
    }

    /** The completed ring edge, or 0 while no minimum ring is formed. */
    public static int ringEdge(final Level level, final BlockPos pos) {
        final TokamakRingPreview.Preview preview = TokamakRingPreview.preview(level, pos);
        return preview.valid() ? preview.edge() : 0;
    }

    /** Linear performance scale: 3x3 = 1, 5x5 = 3, 7x7 = 5, etc. */
    public static int ringMultiplier(final Level level, final BlockPos pos) {
        return Math.max(1, ringEdge(level, pos) - 2);
    }

    private int ringMultiplier() {
        return level == null ? 1 : ringMultiplier(level, getBlockPos());
    }

    private static int scaled(final int base, final int multiplier) {
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, (long) Math.max(0, base) * Math.max(1, multiplier)));
    }

    /** Push to neighbours; returns total FE moved this tick (the GUI's output readout). */
    private static int pushEnergy(final ServerLevel level, final BlockPos pos, final GenBuffer energy, final int outputRate) {
        if (energy.getEnergyStored() <= 0) return 0;
        int pushed = 0;
        for (final Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) break;
            final IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            final int offered = Math.min(outputRate - pushed, energy.getEnergyStored());
            final int accepted = target.receiveEnergy(offered, false);
            if (accepted > 0) { energy.extractEnergy(accepted, false); pushed += accepted; }
        }
        return pushed;
    }

    private static final class GenBuffer extends EnergyStorage {
        GenBuffer(final int capacity, final int maxExtract) {
            super(capacity, 0, maxExtract);
        }
        void resize(final int capacity, final int maxExtract) {
            this.capacity = Math.max(0, capacity);
            this.maxExtract = Math.max(0, maxExtract);
            this.energy = Math.min(this.energy, this.capacity);
        }
        void generate(final int amount) {
            this.energy = Math.min(this.capacity, this.energy + amount);
        }
        /** Set the stored amount directly (clamped). Used on load/sync — `generate`
         *  ADDS, so using it for the client update tag would re-add the synced value
         *  every packet and peg the client buffer at full (wrong HUD FE bar). */
        void set(final int amount) {
            this.energy = Math.max(0, Math.min(this.capacity, amount));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Burn", burnTime);
        tag.putInt("Tier", currentTier);
        tag.putInt("LastOutput", lastOutput); // synced via getUpdateTag → WTHIT/GUI output readout
        tag.putInt("RingEdge", formedRingEdge);
        tag.putInt("RingMultiplier", formedRingMultiplier);
        tag.put("Fuel", fuelSlot.createTag(registries));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.set(tag.getInt("Energy"));
        burnTime = tag.getInt("Burn");
        currentTier = tag.getInt("Tier");
        lastOutput = tag.getInt("LastOutput");
        formedRingEdge = tag.getInt("RingEdge");
        formedRingMultiplier = tag.getInt("RingMultiplier");
        fuelSlot.fromTag(tag.getList("Fuel", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
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
}
