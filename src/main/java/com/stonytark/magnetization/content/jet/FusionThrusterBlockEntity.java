package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagFluids;
import com.stonytark.magnetization.registry.MagItems;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

/**
 * Expandable Fusion Thruster — a flat {@code W×H×1} panel multiblock (Tokamak-Coil
 * ring + this block tiled inside) whose exhaust leaves the panel's FACING side
 * and thrusts a Sable craft in the opposite direction. Bigger panel = exponentially
 * more thrust. Burns the fusion fluids
 * (Hydrogen / Deuterium Oxide / Tritium / Helium-3), with Helium-3 the strongest
 * AND longest-running (denser fluids drain slower). Only the deterministic master
 * interior fires; right-clicking any interior opens the master's shared GUI.
 * Optional water or common-tagged cooling fluid increases thrust/speed and reduces FE/propellant use;
 * an empty coolant tank preserves the original dry performance exactly.
 */
public class FusionThrusterBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData, BlockEntitySubLevelActor {

    private static final long RESCAN_INTERVAL = 20L;

    /** This block's fuel tank. Only the panel MASTER's tank is actually used; its
     *  capacity grows with the interior-block count (see {@link #runEngine}), and
     *  every other interior forwards its fluid capability here (see {@link #panelTank}),
     *  so a bigger panel holds proportionally more fuel from one shared tank. */
    private final FluidTank tank = new FluidTank(MagConfig.fusionThrusterTank(),
            fs -> isFusionFluid(fs.getFluid())) {
        @Override protected void onContentsChanged() { FusionThrusterBlockEntity.this.setChanged(); }
    };
    private final FluidTank coolant = new FluidTank(MagConfig.fusionThrusterCoolantTankPerInterior(),
            fs -> com.stonytark.magnetization.content.fluid.CoolantFluids.isCoolant(fs.getFluid())) {
        @Override protected void onContentsChanged() { FusionThrusterBlockEntity.this.setChanged(); }
    };
    private final ReceiveBuffer energy = new ReceiveBuffer(
            MagConfig.fusionThrusterFeCapacity(), MagConfig.fusionThrusterFeReceive());

    /** Bucket-input slot — fusion-fluid and coolant buckets auto-drain into their tanks. */
    private final SimpleContainer bucketSlot = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return isInputBucket(st);
        }
        @Override public void setChanged() { super.setChanged(); FusionThrusterBlockEntity.this.setChanged(); }
    };

    // Panel-validation cache (re-scanned every RESCAN_INTERVAL ticks).
    private boolean cachedValid;
    private int cachedInterior;
    private @Nullable BlockPos cachedMaster;
    private java.util.List<BlockPos> cachedInteriorList = java.util.List.of();
    private java.util.List<BlockPos> cachedFrameList = java.util.List.of();
    private java.util.List<BlockPos> cachedControlList = java.util.List.of();
    private long lastScanTick = Long.MIN_VALUE;
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();
    /** Fractional fuel-consumption accumulator (denser fluids drain < 1 mB/tick). */
    private double fluidAccum;
    private boolean firing;
    private boolean coolingActive;

    public FusionThrusterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.FUSION_THRUSTER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return panelEnergy(); }

    /** Shared FE capability exposed by a Tokamak Coil in a valid thruster frame. */
    public static @Nullable IEnergyStorage energyBufferFromFrame(final Level level,
                                                                 final BlockPos framePos) {
        final BlockPos masterPos = FusionThrusterPanel.findMasterFromFrame(
                level, framePos, MagConfig.fusionThrusterMaxEdge());
        if (masterPos == null
                || !(level.getBlockEntity(masterPos) instanceof FusionThrusterBlockEntity master)
                || MagConfig.isBlockDisabled(master.getBlockState())) return null;
        // The resolved position is the deterministic master, so use its own buffer
        // directly; capability access need not wait for its throttled panel scan.
        return master.energy;
    }

    /** Shared fuel + coolant input exposed by a Tokamak Coil in a valid frame. */
    public static @Nullable IFluidHandler fluidHandlerFromFrame(final Level level,
                                                                final BlockPos framePos) {
        final BlockPos masterPos = FusionThrusterPanel.findMasterFromFrame(
                level, framePos, MagConfig.fusionThrusterMaxEdge());
        if (masterPos == null
                || !(level.getBlockEntity(masterPos) instanceof FusionThrusterBlockEntity master)
                || MagConfig.isBlockDisabled(master.getBlockState())) return null;
        final FusionThrusterBlockEntity inputMaster = master.inputMaster();
        return new com.stonytark.magnetization.content.fluid.MultiTankInputFluidHandler(
                inputMaster.tank, inputMaster.coolant);
    }
    // Insert-only: pipes can fuel the panel but can't siphon unburnt fusion fuel back
    // out. panelTank() resolves the (possibly remote master's) tank, so wrap per call.
    public IFluidHandler fluidHandler() {
        final FusionThrusterBlockEntity inputMaster = inputMaster();
        return new com.stonytark.magnetization.content.fluid.MultiTankInputFluidHandler(
                inputMaster.tank, inputMaster.coolant);
    }
    public net.minecraft.world.Container bucketContainer() { return bucketSlot; }

    /** The panel's shared fuel tank: the master's tank, so a pipe (or bucket) on
     *  ANY interior fills the one growing tank. Falls back to this block's own tank
     *  when the panel isn't formed, so a lone block still works. */
    private FluidTank panelTank() {
        final FusionThrusterBlockEntity m = panelMaster();
        return m != null ? m.tank : tank;
    }

    private FluidTank panelCoolant() {
        final FusionThrusterBlockEntity m = panelMaster();
        return m != null ? m.coolant : coolant;
    }

    /** The panel's shared FE buffer: the master's buffer, so a cable on ANY interior
     *  feeds the one engine that actually fires (only the master drains FE). Without
     *  this, FE piped to a non-master cell is stranded and the engine never powers
     *  unless the player happens to cable the invisible min-corner master block. */
    private ReceiveBuffer panelEnergy() {
        final FusionThrusterBlockEntity m = panelMaster();
        return m != null ? m.energy : energy;
    }

    /** The resolved master BE when this is a formed non-master interior, else null
     *  (so callers fall back to this block's own tank/buffer). */
    private @Nullable FusionThrusterBlockEntity panelMaster() {
        if (cachedValid && cachedMaster != null && level != null && !getBlockPos().equals(cachedMaster)
                && level.getBlockEntity(cachedMaster) instanceof FusionThrusterBlockEntity m) {
            return m;
        }
        return null;
    }

    /** Resolve fluid input against the live structure, even before its first tick.
     *  Create pipes and frame-coil capabilities may run earlier than the panel
     *  ticker, so relying only on cached formation data would strand fluid in a
     *  non-master or expose only the one-cell capacity on a newly formed panel. */
    private FusionThrusterBlockEntity inputMaster() {
        if (level == null) return this;
        if (cachedValid && cachedMaster != null
                && level.getBlockEntity(cachedMaster) instanceof FusionThrusterBlockEntity master) {
            master.resizeSharedTanks(Math.max(1, cachedInterior));
            return master;
        }
        final FusionThrusterPanel.Result result = FusionThrusterPanel.validate(
                level, getBlockPos(), facing(), MagConfig.fusionThrusterMaxEdge());
        if (result.valid() && result.master() != null
                && level.getBlockEntity(result.master()) instanceof FusionThrusterBlockEntity master) {
            master.resizeSharedTanks(Math.max(1, result.interiorCount()));
            return master;
        }
        resizeSharedTanks(1);
        return this;
    }

    public boolean isFiring() { return firing; }
    public boolean coolingActive() { return panelMaster() != null ? panelMaster().coolingActive : coolingActive; }
    public int coolantStored() { return panelCoolant().getFluidAmount(); }
    public int coolantCapacity() { return panelCoolant().getCapacity(); }
    public int interiorCount() { return cachedInterior; }
    public boolean formed() { return cachedValid; }

    /** Drop cached cable views before an interior block (possibly the master) disappears. */
    void invalidateCachedFrameCapabilities() {
        if (level == null) return;
        for (final BlockPos p : cachedFrameList) level.invalidateCapabilities(p);
    }

    // ── MachineGuiData (shared GUI: fluid mB + interior count + FE bar) ──
    @Override public net.minecraft.world.Container guiInput() { return bucketSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.FUSION_THRUSTER;
    }
    @Override public int guiEnergyStored() { return panelEnergy().getEnergyStored(); }
    @Override public int guiEnergyMax() { return panelEnergy().getMaxEnergyStored(); }
    @Override public int guiStat1() { return panelTank().getFluidAmount(); }   // shared panel fluid mB
    @Override public int guiStat2() { return cachedInterior; }          // interior count
    // Bar denominator = shared tank × interior count, clamped like MachineScreen did
    // (large tank × big panel overflows int). Computed server-side from server config.
    @Override public int guiStat4() {
        return (int) Math.min(Integer.MAX_VALUE, (long) MagConfig.fusionThrusterTank() * Math.max(1, cachedInterior));
    }
    @Override public int guiCoolantStored() { return coolantStored(); }
    @Override public int guiCoolantCapacity() { return coolantCapacity(); }
    @Override public boolean guiCoolingActive() { return coolingActive(); }
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        final BlockState state = getBlockState();
        final boolean visiblyFiring = state.hasProperty(BlockStateProperties.LIT)
                && state.getValue(BlockStateProperties.LIT);
        return displayStatus(cachedValid, visiblyFiring);
    }

    static com.stonytark.magnetization.menu.MachineDisplayData.Status displayStatus(
            final boolean formed, final boolean visiblyFiring) {
        if (!formed) return com.stonytark.magnetization.menu.MachineDisplayData.Status.INVALID;
        return visiblyFiring ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.FORMED;
    }

    static boolean isFusionFluid(final Fluid fluid) {
        return isHydrogen(fluid) || fluid == MagFluids.DEUTERIUM_OXIDE.get()
                || fluid == MagFluids.TRITIUM.get() || fluid == MagFluids.HELIUM_3.get();
    }

    private static boolean isHydrogen(final Fluid fluid) {
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid);
        return !(id != null && id.getNamespace().equals("tfmg") && !MagConfig.tfmgCompatEnabled())
                && fluid.builtInRegistryHolder().is(com.stonytark.magnetization.api.MagTags.HYDROGEN_FLUIDS);
    }

    /** Deterministic (client/server-identical) check for the 4 fusion-fluid buckets. */
    public static boolean isFusionFluidBucket(final ItemStack st) {
        return isCompatibleHydrogenBucket(st)
                || st.is(MagItems.DEUTERIUM_OXIDE_BUCKET.get())
                || st.is(MagItems.TRITIUM_BUCKET.get()) || st.is(MagItems.HELIUM_3_BUCKET.get());
    }

    public static boolean isInputBucket(final ItemStack stack) {
        return com.stonytark.magnetization.content.fluid.CoolantFluids.isCoolantBucket(stack)
                || isFusionFluidBucket(stack);
    }

    private static boolean isCompatibleHydrogenBucket(final ItemStack stack) {
        if (!stack.is(com.stonytark.magnetization.api.MagTags.HYDROGEN_BUCKETS)) return false;
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null || !id.getNamespace().equals("tfmg") || MagConfig.tfmgCompatEnabled();
    }

    private static @Nullable Fluid bucketFluid(final ItemStack st) {
        final java.util.Optional<FluidStack> coolant =
                com.stonytark.magnetization.content.fluid.CoolantFluids.coolantFromBucket(st);
        if (coolant.isPresent()) return coolant.get().getFluid();
        if (isCompatibleHydrogenBucket(st)) {
            final java.util.Optional<FluidStack> contained =
                    net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(st);
            if (contained.isPresent() && isHydrogen(contained.get().getFluid())) {
                return contained.get().getFluid();
            }
        }
        if (st.is(MagItems.DEUTERIUM_OXIDE_BUCKET.get())) return MagFluids.DEUTERIUM_OXIDE.get();
        if (st.is(MagItems.TRITIUM_BUCKET.get())) return MagFluids.TRITIUM.get();
        if (st.is(MagItems.HELIUM_3_BUCKET.get())) return MagFluids.HELIUM_3.get();
        return null;
    }

    /** Pour one fuel or coolant bucket (1000 mB) into the matching shared panel tank. */
    public boolean fillFromBucket(final ItemStack bucket) {
        final Fluid fluid = bucketFluid(bucket);
        if (fluid == null) return false;
        final FusionThrusterBlockEntity inputMaster = inputMaster();
        final FluidTank into = com.stonytark.magnetization.content.fluid.CoolantFluids.isCoolant(fluid)
                ? inputMaster.coolant : inputMaster.tank;
        final FluidStack stack = new FluidStack(fluid, 1000);
        if (into.fill(stack, IFluidHandler.FluidAction.SIMULATE) < 1000) return false;
        into.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        inputMaster.setChanged();
        if (inputMaster != this) setChanged();
        return true;
    }

    private double fluidMult(final Fluid fluid) {
        if (isHydrogen(fluid)) return MagConfig.fusionThrusterFluidMultHydrogen();
        if (fluid == MagFluids.TRITIUM.get()) return MagConfig.fusionThrusterFluidMultTritium();
        if (fluid == MagFluids.HELIUM_3.get()) return MagConfig.fusionThrusterFluidMultHelium3();
        return MagConfig.fusionThrusterFluidMultDeuteriumOxide();
    }

    private double fluidDensity(final Fluid fluid) {
        if (isHydrogen(fluid)) return MagConfig.fusionThrusterFluidDensityHydrogen();
        if (fluid == MagFluids.TRITIUM.get()) return MagConfig.fusionThrusterFluidDensityTritium();
        if (fluid == MagFluids.HELIUM_3.get()) return MagConfig.fusionThrusterFluidDensityHelium3();
        return MagConfig.fusionThrusterFluidDensityDeuteriumOxide();
    }

    /** Vanilla ticker — thruster in the open world; resolve its host ship (if any). */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final FusionThrusterBlockEntity be) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(state)) return;
        if (!(level instanceof ServerLevel server)) return;
        be.runEngine(server, SableBridge.subLevelAt(server, pos));
    }

    public static void clientTick(final Level level, final BlockPos pos, final BlockState state,
                                  final FusionThrusterBlockEntity be) {
        ThrusterPlume.tick(level, pos, state, ThrusterPlume.Style.FUSION,
                be.exhaustColour(), Math.max(1, be.interiorCount()));
    }

    private void resizeSharedTanks(final int interiorCount) {
        final int count = Math.max(1, interiorCount);
        // Compute in long + clamp: tank config × a large panel can exceed int range.
        final int fuelCap = (int) Math.min(Integer.MAX_VALUE,
                (long) MagConfig.fusionThrusterTank() * count);
        if (tank.getCapacity() != fuelCap) {
            tank.setCapacity(fuelCap);
            final int over = tank.getFluidAmount() - fuelCap;
            if (over > 0) tank.drain(over, IFluidHandler.FluidAction.EXECUTE);
        }
        final int coolantCap = (int) Math.min(Integer.MAX_VALUE,
                (long) MagConfig.fusionThrusterCoolantTankPerInterior() * count);
        if (coolant.getCapacity() != coolantCap) {
            coolant.setCapacity(coolantCap);
            final int over = coolant.getFluidAmount() - coolantCap;
            if (over > 0) coolant.drain(over, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** Auto-drain one vanilla/Create-inserted fuel or coolant bucket after panel
     *  resolution, so an input on any interior always reaches the shared master. */
    private void drainInputBucket() {
        final ItemStack in = bucketSlot.getItem(0);
        if (isInputBucket(in) && fillFromBucket(in)) {
            bucketSlot.setItem(0, new ItemStack(Items.BUCKET));
        }
    }

    private int exhaustColour() {
        final Fluid fluid = panelTank().getFluid().getFluid();
        if (isHydrogen(fluid)) return 0x74D9FF;
        if (fluid == MagFluids.TRITIUM.get()) return 0x70FF96;
        if (fluid == MagFluids.HELIUM_3.get()) return 0xFFD36A;
        if (fluid == MagFluids.DEUTERIUM_OXIDE.get()) return 0x829CFF;
        return 0xDDEBFF;
    }

    /** Sable sub-level tick — thruster is mounted on this ship. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(getBlockState())) return;
        if (level instanceof ServerLevel server) runEngine(server, subLevel);
    }

    private void runEngine(final ServerLevel server, final @Nullable ServerSubLevel host) {
        energy.resize(MagConfig.fusionThrusterFeCapacity(), MagConfig.fusionThrusterFeReceive());

        // Re-validate the panel on a throttle (cache between scans). The
        // `lastScanTick == MIN_VALUE` guard forces the FIRST scan: subtracting
        // MIN_VALUE would overflow to a negative delta and otherwise never fire.
        if (lastScanTick == Long.MIN_VALUE || server.getGameTime() - lastScanTick >= RESCAN_INTERVAL) {
            final Direction facing = facing();
            final BlockPos prevMaster = cachedMaster;
            final boolean prevValid = cachedValid;
            final java.util.List<BlockPos> prevInterior = cachedInteriorList;
            final java.util.List<BlockPos> prevFrame = cachedFrameList;
            final FusionThrusterPanel.Result r = FusionThrusterPanel.validate(
                    level, getBlockPos(), facing, MagConfig.fusionThrusterMaxEdge());
            cachedValid = r.valid();
            cachedInterior = r.interiorCount();
            cachedMaster = r.master();
            cachedInteriorList = r.interior();
            cachedFrameList = FusionThrusterPanel.framePositions(r);
            final java.util.ArrayList<BlockPos> control = new java.util.ArrayList<>(cachedInteriorList);
            control.addAll(cachedFrameList);
            cachedControlList = java.util.List.copyOf(control);
            lastScanTick = server.getGameTime();
            if (!prevValid && cachedValid && cachedMaster != null
                    && getBlockPos().equals(cachedMaster)) {
                for (final net.minecraft.server.level.ServerPlayer player : server.players()) {
                    if (player.distanceToSqr(cachedMaster.getX() + 0.5, cachedMaster.getY() + 0.5,
                            cachedMaster.getZ() + 0.5) <= 16.0 * 16.0) {
                        com.stonytark.magnetization.registry.MagTriggers.FUSION_THRUSTER_FORMED
                                .get().trigger(player);
                    }
                }
            }
            // Panel just broke (frame/interior mined): the master's LIT-sweep below no
            // longer runs (it returns early once invalid), so any interior left glowing
            // would be a stranded ghost. Clear LIT on the prior interior set now.
            if (prevValid && !cachedValid && prevInterior != null) {
                for (final BlockPos p : prevInterior) {
                    final BlockState s = level.getBlockState(p);
                    if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) {
                        server.setBlock(p, s.setValue(BlockStateProperties.LIT, false), Block.UPDATE_CLIENTS);
                    }
                }
            }
            // Our forwarded fluid + energy handlers point at the master's tank/buffer;
            // if the master moved, re-resolve so pipes AND cables re-bind to the new
            // shared handlers (invalidateCapabilities clears every cap at this pos).
            if (!java.util.Objects.equals(prevMaster, cachedMaster)) {
                level.invalidateCapabilities(getBlockPos());
            }
            // Tokamak-Coil frame blocks dynamically expose this panel's shared FE
            // buffer. Invalidate both old and new perimeters whenever formation or
            // master ownership changes so cable capability caches cannot retain a
            // stale handler (or a stale null) after the multiblock changes.
            if (prevValid != cachedValid || !java.util.Objects.equals(prevMaster, cachedMaster)
                    || !prevFrame.equals(cachedFrameList)) {
                final java.util.Set<BlockPos> changedFrame = new java.util.HashSet<>(prevFrame);
                changedFrame.addAll(cachedFrameList);
                for (final BlockPos p : changedFrame) level.invalidateCapabilities(p);
            }
            // A non-master HUD resolves the shared buffer through cachedMaster.
            // Publish panel metadata whenever it changes; subsequent live FE updates
            // only need to be sent by the master whose buffer is actually changing.
            if (prevValid != cachedValid || !java.util.Objects.equals(prevMaster, cachedMaster)
                    || prevInterior.size() != cachedInteriorList.size()) {
                server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        // Only the deterministic master fires + drives the LIT visuals.
        final boolean isMaster = cachedValid && getBlockPos().equals(cachedMaster);
        if (!isMaster) {
            resizeSharedTanks(1);
            drainInputBucket();
            if (firing) firing = false;     // a demoted block goes dark via the master's sweep
            coolingActive = false;
            return;
        }

        final int count = Math.max(1, cachedInterior);
        resizeSharedTanks(count);
        drainInputBucket();

        final int coolantCost = (int) Math.min(Integer.MAX_VALUE,
                (long) MagConfig.fusionThrusterCoolantPerTickBase()
                        + (long) MagConfig.fusionThrusterCoolantPerTickPerInterior() * count);
        final boolean coolingAvailable = hasCoolant(coolantCost);
        final OperatingProfile profile = operatingProfile(count, coolingAvailable);

        final FluidStack fuel = tank.getFluid();
        final boolean canFire = host != null && ThrustControl.canRun(server, cachedControlList,
                cachedValid && !fuel.isEmpty() && tank.getFluidAmount() >= 1,
                energy.getEnergyStored() >= profile.feCost());

        firing = canFire;
        coolingActive = canFire && coolingAvailable;
        if (canFire) {
            energy.drainInternal(profile.feCost());
            if (coolingActive && coolantCost > 0) {
                coolant.drain(coolantCost, IFluidHandler.FluidAction.EXECUTE);
            }
            // Denser/better fluids drain LESS per tick → run far longer per tank.
            fluidAccum += profile.nominalFluidCost() / fluidDensity(fuel.getFluid());
            final int whole = (int) fluidAccum;
            if (whole > 0) { tank.drain(whole, IFluidHandler.FluidAction.EXECUTE); fluidAccum -= whole; }
            thrustHost(host, count, fluidMult(fuel.getFluid()) * profile.powerMultiplier(),
                    profile.speedMultiplier());
            setChanged();
        }

        // Drive LIT on every interior (write only on change → no steady-state churn).
        for (final BlockPos p : cachedInteriorList) {
            final BlockState s = level.getBlockState(p);
            if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != canFire) {
                server.setBlock(p, s.setValue(BlockStateProperties.LIT, canFire), Block.UPDATE_CLIENTS);
            }
        }
        if (server.getGameTime() % 10L == 0L && syncGate.changed(this, server.registryAccess())) {
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(DirectionalBlock.FACING) ? s.getValue(DirectionalBlock.FACING) : Direction.NORTH;
    }

    /** Operating costs and bonuses used directly by the engine and by tests. */
    public static OperatingProfile operatingProfile(final int interiorCount, final boolean cooled) {
        final int count = Math.max(1, interiorCount);
        final int baseFe = (int) Math.min(Integer.MAX_VALUE,
                (long) MagConfig.fusionThrusterFeCostBase()
                        + (long) MagConfig.fusionThrusterFeCostPerInterior() * count);
        final int baseFluid = (int) Math.min(Integer.MAX_VALUE,
                (long) MagConfig.fusionThrusterFluidPerTickBase()
                        + (long) MagConfig.fusionThrusterFluidPerTickPerInterior() * count);
        final double efficiency = cooled ? Math.max(1.0d,
                MagConfig.fusionThrusterCooledEfficiencyMultiplier()) : 1.0d;
        final double power = cooled ? Math.max(1.0d,
                MagConfig.fusionThrusterCooledPowerMultiplier()) : 1.0d;
        return new OperatingProfile(
                Math.max(0, (int) Math.ceil(baseFe / efficiency)),
                baseFluid / efficiency, power, power);
    }

    public record OperatingProfile(int feCost, double nominalFluidCost,
                                   double powerMultiplier, double speedMultiplier) {}

    private boolean hasCoolant(final int amount) {
        return !coolant.getFluid().isEmpty() && coolant.getFluidAmount() > 0
                && (amount <= 0 || coolant.getFluidAmount() >= amount);
    }

    /** Push the host opposite the exhaust-facing panel face, exponential in
     *  interior count and scaled by the active fluid's strength multiplier. */
    private void thrustHost(final ServerSubLevel host, final int count, final double fluidMult,
                            final double speedMultiplier) {
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) return;
        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;

        // FACING is the visible exhaust/nozzle side. Reaction thrust moves the
        // ship the other way, matching the Micro Thruster and MHD Jet convention.
        final Vec3 dirLocal = Vec3.atLowerCornerOf(facing().getOpposite().getNormal());
        final Pose3dc pose = host.logicalPose();
        final Vec3 dirWorld = pose.transformNormal(new Vec3(dirLocal.x, dirLocal.y, dirLocal.z)).normalize();
        final Vector3dc v = handle.getLinearVelocity();
        if (v.x() * dirWorld.x + v.y() * dirWorld.y + v.z() * dirWorld.z
                >= MagConfig.fusionThrusterMaxSpeed() * speedMultiplier) return;

        final double mass = host.getMassTracker().getMass();
        final double magnitude = MagConfig.fusionThrusterThrustBase()
                * Math.pow(count, MagConfig.fusionThrusterThrustExponent()) * fluidMult;
        final double force = magnitude * 20.0 * mass;
        final Vector3dc com = host.getMassTracker().getCenterOfMass();
        SableBridge.applyLocalImpulse(host,
                new Vector3d(com.x(), com.y(), com.z()),
                new Vector3d(dirLocal.x * force, dirLocal.y * force, dirLocal.z * force));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("Coolant", coolant.writeToNBT(registries, new CompoundTag()));
        tag.put("Bucket", bucketSlot.createTag(registries));
        tag.putBoolean("Cooling", coolingActive);
        // Sync the resolved panel state so the client BE (and thus the WTHIT/Jade/TOP/Create-goggle
        // HUD) reports the real interior count, formed flag, and master — which the
        // panel-fluid forwarding (panelTank) also needs to resolve on the client.
        tag.putBoolean("Formed", cachedValid);
        tag.putInt("Interior", cachedInterior);
        if (cachedMaster != null) tag.putLong("Master", cachedMaster.asLong());
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        coolant.readFromNBT(registries, tag.getCompound("Coolant"));
        bucketSlot.fromTag(tag.getList("Bucket", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
        coolingActive = tag.getBoolean("Cooling");
        // Restores the synced HUD state on the client; on the server these are
        // recomputed by the next validation pass, so a stale value is harmless.
        cachedValid = tag.getBoolean("Formed");
        cachedInterior = tag.getInt("Interior");
        cachedMaster = tag.contains("Master") ? BlockPos.of(tag.getLong("Master")) : null;
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
