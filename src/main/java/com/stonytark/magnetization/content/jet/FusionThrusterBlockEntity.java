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
 * ring + this block tiled inside) that thrusts a Sable craft perpendicular to the
 * panel face. Bigger panel = exponentially more thrust. Burns the fusion fluids
 * (Hydrogen / Deuterium Oxide / Tritium / Helium-3), with Helium-3 the strongest
 * AND longest-running (denser fluids drain slower). Only the deterministic master
 * interior fires; right-clicking any interior opens the master's shared GUI.
 */
public class FusionThrusterBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData, BlockEntitySubLevelActor {

    private static final long RESCAN_INTERVAL = 20L;

    /** This block's fuel tank. Only the panel MASTER's tank is actually used; its
     *  capacity grows with the interior-block count (see {@link #runEngine}), and
     *  every other interior forwards its fluid capability here (see {@link #panelTank}),
     *  so a bigger panel holds proportionally more fuel from one shared tank. */
    private final FluidTank tank = new FluidTank(MagConfig.fusionThrusterTank(),
            fs -> isFusionFluid(fs.getFluid()));
    private final ReceiveBuffer energy = new ReceiveBuffer(
            MagConfig.fusionThrusterFeCapacity(), MagConfig.fusionThrusterFeReceive());

    /** Bucket-input slot — the 4 fusion-fluid buckets auto-drain into the tank. */
    private final SimpleContainer bucketSlot = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final ItemStack st) {
            return isFusionFluidBucket(st);
        }
        @Override public void setChanged() { super.setChanged(); FusionThrusterBlockEntity.this.setChanged(); }
    };

    // Panel-validation cache (re-scanned every RESCAN_INTERVAL ticks).
    private boolean cachedValid;
    private int cachedInterior;
    private @Nullable BlockPos cachedMaster;
    private java.util.List<BlockPos> cachedInteriorList = java.util.List.of();
    private long lastScanTick = Long.MIN_VALUE;
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();
    /** Fractional fuel-consumption accumulator (denser fluids drain < 1 mB/tick). */
    private double fluidAccum;
    private boolean firing;

    public FusionThrusterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.FUSION_THRUSTER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return panelEnergy(); }
    // Insert-only: pipes can fuel the panel but can't siphon unburnt fusion fuel back
    // out. panelTank() resolves the (possibly remote master's) tank, so wrap per call.
    public IFluidHandler fluidHandler() {
        return new com.stonytark.magnetization.content.fluid.InsertOnlyFluidHandler(panelTank());
    }
    public net.minecraft.world.Container bucketContainer() { return bucketSlot; }

    /** The panel's shared fuel tank: the master's tank, so a pipe (or bucket) on
     *  ANY interior fills the one growing tank. Falls back to this block's own tank
     *  when the panel isn't formed, so a lone block still works. */
    private FluidTank panelTank() {
        final FusionThrusterBlockEntity m = panelMaster();
        return m != null ? m.tank : tank;
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

    public boolean isFiring() { return firing; }
    public int interiorCount() { return cachedInterior; }
    public boolean formed() { return cachedValid; }

    // ── MachineGuiData (shared GUI: fluid mB + interior count + FE bar) ──
    @Override public net.minecraft.world.Container guiInput() { return bucketSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.FUSION_THRUSTER;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return MagConfig.fusionThrusterFeCapacity(); }
    @Override public int guiStat1() { return panelTank().getFluidAmount(); }   // shared panel fluid mB
    @Override public int guiStat2() { return cachedInterior; }          // interior count
    // Bar denominator = shared tank × interior count, clamped like MachineScreen did
    // (large tank × big panel overflows int). Computed server-side from server config.
    @Override public int guiStat4() {
        return (int) Math.min(Integer.MAX_VALUE, (long) MagConfig.fusionThrusterTank() * Math.max(1, cachedInterior));
    }
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        if (!cachedValid) return com.stonytark.magnetization.menu.MachineDisplayData.Status.INVALID;
        return firing ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.FORMED;
    }

    static boolean isFusionFluid(final Fluid fluid) {
        return fluid == MagFluids.HYDROGEN.get() || fluid == MagFluids.DEUTERIUM_OXIDE.get()
                || fluid == MagFluids.TRITIUM.get() || fluid == MagFluids.HELIUM_3.get();
    }

    /** Deterministic (client/server-identical) check for the 4 fusion-fluid buckets. */
    public static boolean isFusionFluidBucket(final ItemStack st) {
        return st.is(MagItems.HYDROGEN_BUCKET.get()) || st.is(MagItems.DEUTERIUM_OXIDE_BUCKET.get())
                || st.is(MagItems.TRITIUM_BUCKET.get()) || st.is(MagItems.HELIUM_3_BUCKET.get());
    }

    private static @Nullable Fluid bucketFluid(final ItemStack st) {
        if (st.is(MagItems.HYDROGEN_BUCKET.get())) return MagFluids.HYDROGEN.get();
        if (st.is(MagItems.DEUTERIUM_OXIDE_BUCKET.get())) return MagFluids.DEUTERIUM_OXIDE.get();
        if (st.is(MagItems.TRITIUM_BUCKET.get())) return MagFluids.TRITIUM.get();
        if (st.is(MagItems.HELIUM_3_BUCKET.get())) return MagFluids.HELIUM_3.get();
        return null;
    }

    /** Pour one fusion-fluid bucket (1000 mB) into the shared panel tank if it matches/empty. */
    public boolean fillFromBucket(final ItemStack bucket) {
        final Fluid fluid = bucketFluid(bucket);
        if (fluid == null) return false;
        final FluidTank into = panelTank();
        final FluidStack stack = new FluidStack(fluid, 1000);
        if (into.fill(stack, IFluidHandler.FluidAction.SIMULATE) < 1000) return false;
        into.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    private double fluidMult(final Fluid fluid) {
        if (fluid == MagFluids.HYDROGEN.get()) return MagConfig.fusionThrusterFluidMultHydrogen();
        if (fluid == MagFluids.TRITIUM.get()) return MagConfig.fusionThrusterFluidMultTritium();
        if (fluid == MagFluids.HELIUM_3.get()) return MagConfig.fusionThrusterFluidMultHelium3();
        return MagConfig.fusionThrusterFluidMultDeuteriumOxide();
    }

    private double fluidDensity(final Fluid fluid) {
        if (fluid == MagFluids.HYDROGEN.get()) return MagConfig.fusionThrusterFluidDensityHydrogen();
        if (fluid == MagFluids.TRITIUM.get()) return MagConfig.fusionThrusterFluidDensityTritium();
        if (fluid == MagFluids.HELIUM_3.get()) return MagConfig.fusionThrusterFluidDensityHelium3();
        return MagConfig.fusionThrusterFluidDensityDeuteriumOxide();
    }

    /** Vanilla ticker — thruster in the open world; resolve its host ship (if any). */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final FusionThrusterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.runEngine(server, SableBridge.subLevelAt(server, pos));
    }

    /** Sable sub-level tick — thruster is mounted on this ship. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (level instanceof ServerLevel server) runEngine(server, subLevel);
    }

    private void runEngine(final ServerLevel server, final @Nullable ServerSubLevel host) {
        // Auto-drain a fusion-fluid bucket into the tank (works anywhere).
        final ItemStack in = bucketSlot.getItem(0);
        if (isFusionFluidBucket(in) && fillFromBucket(in)) {
            bucketSlot.setItem(0, new ItemStack(Items.BUCKET));
        }

        // Re-validate the panel on a throttle (cache between scans). The
        // `lastScanTick == MIN_VALUE` guard forces the FIRST scan: subtracting
        // MIN_VALUE would overflow to a negative delta and otherwise never fire.
        if (lastScanTick == Long.MIN_VALUE || server.getGameTime() - lastScanTick >= RESCAN_INTERVAL) {
            final Direction facing = facing();
            final BlockPos prevMaster = cachedMaster;
            final boolean prevValid = cachedValid;
            final java.util.List<BlockPos> prevInterior = cachedInteriorList;
            final FusionThrusterPanel.Result r = FusionThrusterPanel.validate(
                    level, getBlockPos(), facing, MagConfig.fusionThrusterMaxEdge());
            cachedValid = r.valid();
            cachedInterior = r.interiorCount();
            cachedMaster = r.master();
            cachedInteriorList = r.interior();
            lastScanTick = server.getGameTime();
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
        }

        // Only the deterministic master fires + drives the LIT visuals.
        final boolean isMaster = cachedValid && getBlockPos().equals(cachedMaster);
        if (!isMaster) {
            if (firing) firing = false;     // a demoted block goes dark via the master's sweep
            return;
        }

        final int count = Math.max(1, cachedInterior);

        // The panel's fuel capacity scales with its interior-block count, so a bigger
        // panel holds proportionally more fuel (config tank size is now per cell).
        // Compute in long + clamp to Integer.MAX_VALUE: at the config extremes
        // (tank 1,000,000 × up to 62² interior cells) a plain int*int would overflow
        // NEGATIVE, making setCapacity shed the whole tank every tick.
        final int cap = (int) Math.min(Integer.MAX_VALUE,
                (long) MagConfig.fusionThrusterTank() * count);
        if (tank.getCapacity() != cap) {
            tank.setCapacity(cap);
            final int over = tank.getFluidAmount() - cap;   // panel shrank → shed the excess
            if (over > 0) tank.drain(over, IFluidHandler.FluidAction.EXECUTE);
        }

        final int feCost = (int) Math.min(Integer.MAX_VALUE, (long) MagConfig.fusionThrusterFeCostBase()
                + (long) MagConfig.fusionThrusterFeCostPerInterior() * count);
        final int fluidCost = (int) Math.min(Integer.MAX_VALUE, (long) MagConfig.fusionThrusterFluidPerTickBase()
                + (long) MagConfig.fusionThrusterFluidPerTickPerInterior() * count);

        final FluidStack fuel = tank.getFluid();
        final boolean canFire = host != null && cachedValid && !fuel.isEmpty()
                && tank.getFluidAmount() >= 1 && energy.getEnergyStored() >= feCost;

        firing = canFire;
        if (canFire) {
            energy.drainInternal(feCost);
            // Denser/better fluids drain LESS per tick → run far longer per tank.
            fluidAccum += fluidCost / fluidDensity(fuel.getFluid());
            final int whole = (int) fluidAccum;
            if (whole > 0) { tank.drain(whole, IFluidHandler.FluidAction.EXECUTE); fluidAccum -= whole; }
            thrustHost(host, count, fluidMult(fuel.getFluid()));
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

    /** Push the host along the panel face (FACING normal, world-space), exponential
     *  in interior count, scaled by the active fluid's strength multiplier. */
    private void thrustHost(final ServerSubLevel host, final int count, final double fluidMult) {
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) return;
        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;

        final Vec3 dirLocal = Vec3.atLowerCornerOf(facing().getNormal());
        final Pose3dc pose = host.logicalPose();
        final Vec3 dirWorld = pose.transformNormal(new Vec3(dirLocal.x, dirLocal.y, dirLocal.z)).normalize();
        final Vector3dc v = handle.getLinearVelocity();
        if (v.x() * dirWorld.x + v.y() * dirWorld.y + v.z() * dirWorld.z
                >= MagConfig.fusionThrusterMaxSpeed()) return;

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
        tag.put("Bucket", bucketSlot.createTag(registries));
        // Sync the resolved panel state so the client BE (and thus the WTHIT/Jade/TOP
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
        bucketSlot.fromTag(tag.getList("Bucket", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
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
    }
}
