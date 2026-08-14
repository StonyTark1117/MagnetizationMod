package com.stonytark.magnetization.content.tokamak;

import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tokamak fusion reactor controller. When ringed by a complete square perimeter
 * of Tokamak Coils around a solid Reactor-Core interior (3x3 minimum; larger odd
 * rings are supported) and loaded
 * with deuterium fuel, it fuses and generates a large, steady FE output that it
 * pushes to adjacent machines/cables. Larger rings scale the reactor linearly.
 * Fuel cells and optional coolant are shared across every core; cooling
 * raises generation/output and stretches fuel life without changing dry operation.
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
    private final FluidTank coolant = new FluidTank(
            com.stonytark.magnetization.config.MagConfig.tokamakCoolantTankPerScale(),
            stack -> com.stonytark.magnetization.content.fluid.CoolantFluids.isCoolant(stack.getFluid())) {
        @Override protected void onContentsChanged() { TokamakControllerBlockEntity.this.setChanged(); }
    };
    private double burnAccumulator;
    private boolean coolingActive;
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();

    /** Shared input slot — holds spare fusion cells or drains one coolant bucket at a time. */
    private final net.minecraft.world.SimpleContainer fuelSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return cellTier(st) >= 0
                    || com.stonytark.magnetization.content.fluid.CoolantFluids.isCoolantBucket(st);
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

    public int currentTier() { return displayOwner().currentTier; }

    public TokamakControllerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.TOKAMAK_CONTROLLER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() {
        return displayOwner().energy;
    }

    public net.minecraft.world.Container fuelContainer() {
        return displayOwner().fuelSlot;
    }

    /** Insert-only shared coolant tank exposed through every formed core. */
    public IFluidHandler coolantHandler() {
        return new com.stonytark.magnetization.content.fluid.InsertOnlyFluidHandler(
                displayOwner().coolant);
    }

    public int coolantStored() { return displayOwner().coolant.getFluidAmount(); }
    public int coolantCapacity() { return displayOwner().coolant.getCapacity(); }
    public boolean coolingActive() { return displayOwner().coolingActive; }

    /** Pour one vanilla or common-tagged coolant bucket into the shared tank. */
    public boolean fillCoolantBucket(final ItemStack bucket) {
        final java.util.Optional<FluidStack> coolantBucket =
                com.stonytark.magnetization.content.fluid.CoolantFluids.coolantFromBucket(bucket);
        if (coolantBucket.isEmpty()) return false;
        final FluidTank target = displayOwner().coolant;
        if (target.fill(coolantBucket.get(), IFluidHandler.FluidAction.SIMULATE) < 1000) return false;
        target.fill(coolantBucket.get(), IFluidHandler.FluidAction.EXECUTE);
        displayOwner().setChanged();
        return true;
    }

    net.minecraft.world.Container ownFuelContainer() { return fuelSlot; }

    /** Master buffer exposed through a formed Tokamak-Coil perimeter. */
    public static @Nullable IEnergyStorage energyBufferFromFrame(final Level level,
                                                                 final BlockPos framePos) {
        final BlockPos masterPos = TokamakRingPreview.findController(level, framePos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        if (masterPos == null
                || !(level.getBlockEntity(masterPos) instanceof TokamakControllerBlockEntity master)
                || com.stonytark.magnetization.config.MagConfig.isBlockDisabled(master.getBlockState())) return null;
        final TokamakRingPreview.Preview ring = TokamakRingPreview.previewFromCore(level, masterPos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        return ring != null && ring.valid() && ring.requiredFrame().contains(framePos) ? master.energy : null;
    }

    /** Shared coolant input exposed through a formed Tokamak's perimeter coils. */
    public static @Nullable IFluidHandler coolantHandlerFromFrame(final Level level,
                                                                  final BlockPos framePos) {
        final BlockPos masterPos = TokamakRingPreview.findController(level, framePos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        if (masterPos == null
                || !(level.getBlockEntity(masterPos) instanceof TokamakControllerBlockEntity master)
                || com.stonytark.magnetization.config.MagConfig.isBlockDisabled(master.getBlockState())) return null;
        final TokamakRingPreview.Preview ring = TokamakRingPreview.previewFromCore(level, masterPos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        return ring != null && ring.valid() && ring.requiredFrame().contains(framePos)
                ? new com.stonytark.magnetization.content.fluid.InsertOnlyFluidHandler(master.coolant) : null;
    }

    /** Deterministic formed master for any core in the reactor. */
    public static @Nullable TokamakControllerBlockEntity formedMaster(final Level level,
                                                                       final BlockPos corePos) {
        final TokamakRingPreview.Preview ring = TokamakRingPreview.previewFromCore(level, corePos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        if (ring == null || !ring.valid()) return null;
        return level.getBlockEntity(ring.controller()) instanceof TokamakControllerBlockEntity master
                ? master : null;
    }

    private TokamakControllerBlockEntity displayOwner() {
        if (level != null) {
            final TokamakControllerBlockEntity master = formedMaster(level, getBlockPos());
            if (master != null) return master;
        }
        return this;
    }

    // ── MachineGuiData (shared GUI: fuel runtime + current FE output) ──
    @Override public net.minecraft.world.Container guiInput() { return fuelContainer(); }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.TOKAMAK;
    }
    @Override public int guiEnergyStored() { return displayOwner().energy.getEnergyStored(); }
    @Override public int guiEnergyMax() {
        final TokamakControllerBlockEntity owner = displayOwner();
        return scaled(com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(),
                Math.max(1, owner.formedRingMultiplier));
    }
    @Override public int guiStat1() { return displayOwner().burnTime; }          // ticks; screen shows seconds
    @Override public int guiStat2() { return displayOwner().lastOutput; }        // FE/tick out
    @Override public int guiStat3() { return displayOwner().currentTier; }       // 0=D-D, 1=D-T, 2=D-He³
    @Override public int guiStat4() { return tierBurnTicks(displayOwner().currentTier); }
    @Override public int guiStructureSize() { return displayOwner().formedRingEdge; }
    @Override public int guiStructureScale() { return displayOwner().formedRingMultiplier; }
    @Override public int guiCoolantStored() { return coolantStored(); }
    @Override public int guiCoolantCapacity() { return coolantCapacity(); }
    @Override public boolean guiCoolingActive() { return coolingActive(); }
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        final TokamakControllerBlockEntity owner = displayOwner();
        if (owner.formedRingEdge <= 0) {
            return com.stonytark.magnetization.menu.MachineDisplayData.Status.INVALID;
        }
        return owner.getBlockState().hasProperty(BlockStateProperties.LIT)
                && owner.getBlockState().getValue(BlockStateProperties.LIT)
                ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.FORMED;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final TokamakControllerBlockEntity be) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(state)) return;
        if (!(level instanceof ServerLevel server)) return;
        final ItemStack input = be.fuelSlot.getItem(0);
        if (com.stonytark.magnetization.content.fluid.CoolantFluids.isCoolantBucket(input)
                && be.fillCoolantBucket(input)) {
            be.fuelSlot.setItem(0, new ItemStack(Items.BUCKET));
        }
        if (be.formedRingEdge <= 0 && !TokamakRingPreview.isPotentialMaster(level, pos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge())) return;
        final TokamakRingPreview.Preview ring = TokamakRingPreview.previewFromCore(level, pos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        final boolean formed = ring != null && ring.valid();
        if (!formed) {
            final int previousEdge = be.formedRingEdge;
            be.formedRingEdge = 0;
            be.formedRingMultiplier = 0;
            be.lastOutput = 0;
            be.coolingActive = false;
            be.resizeCoolant(com.stonytark.magnetization.config.MagConfig.tokamakCoolantTankPerScale());
            be.energy.resize(com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(),
                    boosted(com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3(),
                            coolingProfile(true, com.stonytark.magnetization.content.fluid.CoolantFluids
                                    .maximumConfiguredQuality()).powerMultiplier()));
            setLit(level, pos, state, false);
            be.setChanged();
            if (be.syncGate.changed(be, server.registryAccess())) {
                server.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
            }
            if (previousEdge > 0) be.invalidateReactorCapabilities(previousEdge);
            return;
        }

        final int multiplier = Math.max(1, ring.edge() - 2);
        final int previousEdge = be.formedRingEdge;
        be.formedRingEdge = ring.edge();
        be.formedRingMultiplier = multiplier;
        // Only the deterministic center reaches this point. Followers are
        // rejected by the cheap potential-master gate above; their capabilities
        // and displays resolve the center on demand.
        if (!pos.equals(ring.controller())) {
            return;
        }
        if (previousEdge != ring.edge()) invalidateCapabilities(level, ring);

        be.energy.resize(scaled(com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity(), multiplier),
                boosted(scaled(com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3(), multiplier),
                        coolingProfile(true, com.stonytark.magnetization.content.fluid.CoolantFluids
                                .maximumConfiguredQuality()).powerMultiplier()));
        be.resizeCoolant(scaled(com.stonytark.magnetization.config.MagConfig.tokamakCoolantTankPerScale(), multiplier));
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
        final boolean fusing = be.burnTime > 0;
        final int baseCoolantCost = scaled(
                com.stonytark.magnetization.config.MagConfig.tokamakCoolantPerTickPerScale(), multiplier);
        final double coolantQuality = com.stonytark.magnetization.content.fluid.CoolantFluids
                .quality(be.coolant.getFluid().getFluid());
        final int coolantCost = com.stonytark.magnetization.content.fluid.CoolantFluids
                .consumptionForQuality(baseCoolantCost, coolantQuality);
        final boolean cooled = fusing && be.consumeCoolant(coolantCost);
        final CoolingProfile cooling = coolingProfile(cooled, coolantQuality);
        be.coolingActive = cooled;
        if (fusing) {
            be.energy.generate(boosted(scaled(tierGenPerTick(be.currentTier), multiplier),
                    cooling.powerMultiplier()));
            be.burnAccumulator += 1.0d / cooling.fuelEfficiencyMultiplier();
            final int burnSteps = Math.min(be.burnTime, (int) be.burnAccumulator);
            if (burnSteps > 0) {
                be.burnTime -= burnSteps;
                be.burnAccumulator -= burnSteps;
            }
            be.setChanged();
        } else {
            be.burnAccumulator = 0.0d;
        }
        setCoreLit(level, ring.requiredCores(), fusing);
        be.lastOutput = pushEnergy(server, ring.requiredCores(), be.energy,
                boosted(scaled(tierOutputRate(be.currentTier), multiplier), cooling.powerMultiplier()));
        if (server.getGameTime() % 10L == 0L && be.syncGate.changed(be, server.registryAccess())) {
            server.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS); // WTHIT
        }
    }

    /** The largest complete odd-edged Tokamak coil ring up to the configured limit. */
    public static boolean isRingFormed(final Level level, final BlockPos pos) {
        final TokamakRingPreview.Preview ring = TokamakRingPreview.previewFromCore(level, pos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        return ring != null && ring.valid();
    }

    /** The completed ring edge, or 0 while no minimum ring is formed. */
    public static int ringEdge(final Level level, final BlockPos pos) {
        final TokamakRingPreview.Preview preview = TokamakRingPreview.previewFromCore(level, pos,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge());
        return preview != null && preview.valid() ? preview.edge() : 0;
    }

    /** Linear performance scale: 3x3 = 1, 5x5 = 3, 7x7 = 5, etc. */
    public static int ringMultiplier(final Level level, final BlockPos pos) {
        return Math.max(1, ringEdge(level, pos) - 2);
    }

    private static int scaled(final int base, final int multiplier) {
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, (long) Math.max(0, base) * Math.max(1, multiplier)));
    }

    private static int boosted(final int base, final double multiplier) {
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(0.0d, Math.ceil(Math.max(0, base) * Math.max(1.0d, multiplier))));
    }

    /** Runtime profile used by both reactor logic and regression tests. */
    public static CoolingProfile coolingProfile(final boolean cooled) {
        return coolingProfile(cooled, 1.0d);
    }

    public static CoolingProfile coolingProfile(final boolean cooled, final double coolantQuality) {
        final double quality = cooled ? Math.max(0.1d, coolantQuality) : 0.0d;
        return cooled
                ? new CoolingProfile(
                        1.0d + (Math.max(1.0d, com.stonytark.magnetization.config.MagConfig
                                .tokamakCooledPowerMultiplier()) - 1.0d) * quality,
                        1.0d + (Math.max(1.0d, com.stonytark.magnetization.config.MagConfig
                                .tokamakCooledFuelEfficiency()) - 1.0d) * quality)
                : new CoolingProfile(1.0d, 1.0d);
    }

    public record CoolingProfile(double powerMultiplier, double fuelEfficiencyMultiplier) {}

    private boolean consumeCoolant(final int amount) {
        if (coolant.getFluidAmount() <= 0 || coolant.getFluid().isEmpty()) return false;
        if (amount > 0 && coolant.getFluidAmount() < amount) return false;
        if (amount > 0) coolant.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    private void resizeCoolant(final int capacity) {
        final int clamped = Math.max(1, capacity);
        if (coolant.getCapacity() == clamped) return;
        coolant.setCapacity(clamped);
        final int excess = coolant.getFluidAmount() - clamped;
        if (excess > 0) coolant.drain(excess, IFluidHandler.FluidAction.EXECUTE);
    }

    /** Push to neighbours; returns total FE moved this tick (the GUI's output readout). */
    private static int pushEnergy(final ServerLevel level, final List<BlockPos> cores,
                                  final GenBuffer energy, final int outputRate) {
        if (energy.getEnergyStored() <= 0) return 0;
        int pushed = 0;
        final Set<BlockPos> interior = new HashSet<>(cores);
        final Set<BlockPos> visitedTargets = new HashSet<>();
        for (final BlockPos core : cores) {
            for (final Direction dir : Direction.values()) {
                if (pushed >= outputRate || energy.getEnergyStored() <= 0) return pushed;
                final BlockPos targetPos = core.relative(dir);
                if (interior.contains(targetPos) || !visitedTargets.add(targetPos)) continue;
                final IEnergyStorage target = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, targetPos, dir.getOpposite());
                if (target == null || target == energy || !target.canReceive()) continue;
                final int offered = Math.min(outputRate - pushed, energy.getEnergyStored());
                final int accepted = target.receiveEnergy(offered, false);
                if (accepted > 0) { energy.extractEnergy(accepted, false); pushed += accepted; }
            }
        }
        return pushed;
    }

    private static void setCoreLit(final Level level, final List<BlockPos> cores, final boolean lit) {
        for (final BlockPos core : cores) {
            final BlockState state = level.getBlockState(core);
            setLit(level, core, state, lit);
        }
    }

    private static void setLit(final Level level, final BlockPos pos,
                               final BlockState state, final boolean lit) {
        if (state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    void invalidateReactorCapabilities() {
        invalidateReactorCapabilities(formedRingEdge);
    }

    private void invalidateReactorCapabilities(final int edge) {
        if (level == null || edge < 3) return;
        invalidateCapabilities(level, TokamakRingPreview.previewExact(level, getBlockPos(), edge,
                com.stonytark.magnetization.config.MagConfig.tokamakMaxEdge()));
    }

    private static void invalidateCapabilities(final Level level,
                                               final TokamakRingPreview.Preview ring) {
        for (final BlockPos core : ring.requiredCores()) level.invalidateCapabilities(core);
        for (final BlockPos frame : ring.requiredFrame()) level.invalidateCapabilities(frame);
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
        tag.put("Coolant", coolant.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("BurnAccumulator", burnAccumulator);
        tag.putBoolean("Cooling", coolingActive);
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
        coolant.readFromNBT(registries, tag.getCompound("Coolant"));
        burnAccumulator = tag.getDouble("BurnAccumulator");
        coolingActive = tag.getBoolean("Cooling");
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
