package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagFluids;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ferrofluid Micro-Thruster (capillary spike jet) — the mod's <b>strongest</b>
 * propulsion. Ions fired from a magnetically-spiked ferrofluid bed: it burns
 * raw ferrofluid (held in an internal tank, topped up with ferrofluid buckets)
 * plus FE electricity to push a magnetic Sable craft along its facing harder and
 * faster than anything else. Its stored ferrofluid + FE show automatically in
 * WTHIT/Jade/TOP via the registered fluid + energy capabilities.
 */
public class MicroThrusterBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData, BlockEntitySubLevelActor {

    public static final int TANK_CAPACITY = 8_000;       // 8 buckets of ferrofluid
    private static final int FE_CAPACITY = 400_000;
    private static final int FE_MAX_RECEIVE = 16_000;
    private static final int FE_PER_TICK = 48;
    private static final int FLUID_PER_TICK = 2;
    // Top of the propulsion ladder — the strongest engine in the mod.
    private static final double MAX_SPEED = 5.0;          // 100 b/s cruising ceiling
    private static final double THRUST_DV = 1.0;          // 20 b/s^2 acceleration

    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            fs -> fs.getFluid() == MagFluids.FERROFLUID.get());
    private final ReceiveBuffer energy = new ReceiveBuffer(FE_CAPACITY, FE_MAX_RECEIVE);
    /** Bucket-input slot — ferrofluid buckets are auto-drained into the tank. */
    private final net.minecraft.world.SimpleContainer bucketSlot = new net.minecraft.world.SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int s, final net.minecraft.world.item.ItemStack st) {
            return st.is(com.stonytark.magnetization.registry.MagItems.FERROFLUID_BUCKET.get());
        }
        @Override public void setChanged() { super.setChanged(); MicroThrusterBlockEntity.this.setChanged(); }
    };

    public MicroThrusterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MICRO_THRUSTER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public IFluidHandler fluidHandler() { return tank; }
    public net.minecraft.world.Container bucketContainer() { return bucketSlot; }

    // ── MachineGuiData (shared GUI: ferrofluid mB + FE bar) ──
    @Override public net.minecraft.world.Container guiInput() { return bucketSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.THRUSTER;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return FE_CAPACITY; }
    @Override public int guiStat1() { return tank.getFluidAmount(); }

    /** Try to pour one ferrofluid bucket (1000 mB) into the tank. */
    public boolean fillFromBucket() {
        final FluidStack ferro = new FluidStack(MagFluids.FERROFLUID.get(), 1000);
        if (tank.fill(ferro, IFluidHandler.FluidAction.SIMULATE) < 1000) return false;
        tank.fill(ferro, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    /** Vanilla ticker: thruster in the open world (or defensively a contraption
     *  still world-ticked) — resolve its host ship. */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MicroThrusterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.runEngine(server, SableBridge.subLevelAt(server, pos));
    }

    /** Sable sub-level tick: thruster is mounted on this ship — thrust it. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (level instanceof ServerLevel server) runEngine(server, subLevel);
    }

    private void runEngine(final ServerLevel server, final @Nullable ServerSubLevel host) {
        // Auto-drain a ferrofluid bucket into the tank (works anywhere).
        final net.minecraft.world.item.ItemStack in = bucketSlot.getItem(0);
        if (in.is(com.stonytark.magnetization.registry.MagItems.FERROFLUID_BUCKET.get()) && fillFromBucket()) {
            bucketSlot.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET));
        }
        final boolean firing = host != null
                && tank.getFluidAmount() >= FLUID_PER_TICK
                && energy.getEnergyStored() >= FE_PER_TICK;
        if (firing) {
            tank.drain(FLUID_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
            energy.drainInternal(FE_PER_TICK);
            thrustHost(host);
            setChanged();
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != firing) {
            server.setBlock(getBlockPos(), state.setValue(BlockStateProperties.LIT, firing), Block.UPDATE_CLIENTS);
        }
        if (server.getGameTime() % 10L == 0L) {
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); // WTHIT
        }
    }

    /** Push the host ship along the thruster's facing (world-space), capped at
     *  MAX_SPEED, applied at the centre of mass (pure forward thrust, no spin).
     *  Force is mass-scaled so acceleration is consistent across ship sizes. */
    private void thrustHost(final ServerSubLevel host) {
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) return;
        final Direction facing = getBlockState().hasProperty(DirectionalBlock.FACING)
                ? getBlockState().getValue(DirectionalBlock.FACING) : Direction.UP;
        // Thrust OUT of the nozzle — opposite the FACING normal — so the ship is
        // pushed the way the thruster visually points (exhaust the other way).
        final Vec3 dirLocal = Vec3.atLowerCornerOf(facing.getOpposite().getNormal());

        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;
        final Pose3dc pose = host.logicalPose();
        final Vec3 dirWorld = pose.transformNormal(new Vec3(dirLocal.x, dirLocal.y, dirLocal.z)).normalize();
        final Vector3dc v = handle.getLinearVelocity();
        if (v.x() * dirWorld.x + v.y() * dirWorld.y + v.z() * dirWorld.z >= MAX_SPEED) return;

        final double mass = host.getMassTracker().getMass();
        final double force = THRUST_DV * 20.0 * mass;
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
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        bucketSlot.fromTag(tag.getList("Bucket", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
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
