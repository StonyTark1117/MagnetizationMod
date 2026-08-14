package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.api.MagTags;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Electric noble-gas propulsion for Sable ships. */
public final class IonThrusterBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData, BlockEntitySubLevelActor,
        com.stonytark.magnetization.content.emp.EmpDrainable {

    public record Propellant(double thrust, double speed, int fluidPerTick, int fePerTick, int colour) {}
    public static final Propellant FALLBACK = new Propellant(1.0, 1.0, 2, 100, 0xC8D8FF);

    private final FluidTank tank = new FluidTank(MagConfig.ionThrusterTank(),
            stack -> stack.is(MagTags.ION_THRUSTER_PROPELLANTS)) {
        @Override protected void onContentsChanged() { IonThrusterBlockEntity.this.setChanged(); }
    };
    private final IFluidHandler insertOnly = new com.stonytark.magnetization.content.fluid.InsertOnlyFluidHandler(tank);
    private final ReceiveBuffer energy = new ReceiveBuffer(MagConfig.ionThrusterFeCapacity(), MagConfig.ionThrusterFeReceive());
    private final com.stonytark.magnetization.content.MachineSyncGate syncGate = new com.stonytark.magnetization.content.MachineSyncGate();
    private final SimpleContainer bucketSlot = new SimpleContainer(1) {
        @Override public boolean canPlaceItem(final int slot, final ItemStack stack) { return isPropellantBucket(stack); }
        @Override public void setChanged() { super.setChanged(); IonThrusterBlockEntity.this.setChanged(); }
    };

    public IonThrusterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.ION_THRUSTER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    @Override public void clearEnergyForEmp() { energy.setStored(0); syncEmpEnergyChange(); }
    public IFluidHandler fluidHandler() { return insertOnly; }
    public net.minecraft.world.Container bucketContainer() { return bucketSlot; }
    public int propellantColour() { return tank.isEmpty() ? 0 : profile(tank.getFluid()).colour(); }
    public int propellantProfile() { return profileIndex(tank.getFluid()); }
    @Override public net.minecraft.world.Container guiInput() { return bucketSlot; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.ION_THRUSTER;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return MagConfig.ionThrusterFeCapacity(); }
    @Override public int guiStat1() { return tank.getFluidAmount(); }
    @Override public int guiStat3() { return profileIndex(tank.getFluid()); }
    @Override public int guiStat4() { return MagConfig.ionThrusterTank(); }
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        return level != null && getBlockState().hasProperty(BlockStateProperties.LIT)
                && getBlockState().getValue(BlockStateProperties.LIT)
                ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE;
    }

    public static boolean isPropellantBucket(final ItemStack stack) {
        return fluidForBucket(stack) != null;
    }

    private static @Nullable net.minecraft.world.level.material.Fluid fluidForBucket(final ItemStack stack) {
        if (stack.is(MagItems.HELIUM_BUCKET.get())) return MagFluids.HELIUM.get();
        if (stack.is(MagItems.NEON_BUCKET.get())) return MagFluids.NEON.get();
        if (stack.is(MagItems.ARGON_BUCKET.get())) return MagFluids.ARGON.get();
        if (stack.is(MagItems.KRYPTON_BUCKET.get())) return MagFluids.KRYPTON.get();
        if (stack.is(MagItems.XENON_BUCKET.get())) return MagFluids.XENON.get();
        if (stack.is(MagItems.RADON_BUCKET.get())) return MagFluids.RADON.get();
        return null;
    }

    public boolean fillFromBucket(final ItemStack bucket) {
        final net.minecraft.world.level.material.Fluid fluid = fluidForBucket(bucket);
        if (fluid == null) return false;
        final FluidStack stack = new FluidStack(fluid, 1000);
        if (tank.fill(stack, IFluidHandler.FluidAction.SIMULATE) != 1000) return false;
        tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    public static Propellant profile(final FluidStack stack) {
        if (stack.getFluid() == MagFluids.HELIUM.get()) return configuredProfile(0, 0xFFB38A);
        if (stack.getFluid() == MagFluids.NEON.get()) return configuredProfile(1, 0xFF2A16);
        if (stack.getFluid() == MagFluids.ARGON.get()) return configuredProfile(2, 0xB56CFF);
        if (stack.getFluid() == MagFluids.KRYPTON.get()) return configuredProfile(3, 0xD8FFE6);
        if (stack.getFluid() == MagFluids.XENON.get()) return configuredProfile(4, 0x4FA9FF);
        if (stack.getFluid() == MagFluids.RADON.get()) return configuredProfile(5, 0x6657FF);
        return configuredProfile(6, FALLBACK.colour());
    }

    private static Propellant configuredProfile(final int index, final int colour) {
        return new Propellant(MagConfig.ionThrusterThrustMultiplier(index),
                MagConfig.ionThrusterSpeedMultiplier(index), MagConfig.ionThrusterFluidCost(index),
                MagConfig.ionThrusterFeCost(index), colour);
    }

    private static int profileIndex(final FluidStack stack) {
        if (stack.isEmpty()) return -1;
        if (stack.getFluid() == MagFluids.HELIUM.get()) return 0;
        if (stack.getFluid() == MagFluids.NEON.get()) return 1;
        if (stack.getFluid() == MagFluids.ARGON.get()) return 2;
        if (stack.getFluid() == MagFluids.KRYPTON.get()) return 3;
        if (stack.getFluid() == MagFluids.XENON.get()) return 4;
        if (stack.getFluid() == MagFluids.RADON.get()) return 5;
        return 6;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final IonThrusterBlockEntity be) {
        if (!MagConfig.isBlockDisabled(state) && level instanceof ServerLevel server) {
            be.runEngine(server, SableBridge.subLevelAt(server, pos));
        }
    }

    public static void clientTick(final Level level, final BlockPos pos, final BlockState state,
                                  final IonThrusterBlockEntity be) {
        final int colour = be.propellantColour();
        if (colour != 0) ThrusterPlume.tick(level, pos, state, ThrusterPlume.Style.ION, colour);
    }

    @Override public void sable$tick(final ServerSubLevel subLevel) {
        if (!MagConfig.isBlockDisabled(getBlockState()) && level instanceof ServerLevel server) runEngine(server, subLevel);
    }

    private void runEngine(final ServerLevel server, final @Nullable ServerSubLevel host) {
        tank.setCapacity(MagConfig.ionThrusterTank());
        energy.resize(MagConfig.ionThrusterFeCapacity(), MagConfig.ionThrusterFeReceive());
        final ItemStack input = bucketSlot.getItem(0);
        if (isPropellantBucket(input) && fillFromBucket(input)) bucketSlot.setItem(0, new ItemStack(Items.BUCKET));

        final Propellant propellant = profile(tank.getFluid());
        final boolean firing = host != null && ThrustControl.canRun(server, getBlockPos(),
                !tank.isEmpty() && tank.getFluidAmount() >= propellant.fluidPerTick(),
                energy.getEnergyStored() >= propellant.fePerTick());
        if (firing) {
            final boolean radon = tank.getFluid().getFluid() == MagFluids.RADON.get();
            tank.drain(propellant.fluidPerTick(), IFluidHandler.FluidAction.EXECUTE);
            energy.drainInternal(propellant.fePerTick());
            thrustHost(host, propellant);
            if (radon) exposeRadonExhaust(server, host);
            setChanged();
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != firing) {
            server.setBlock(getBlockPos(), state.setValue(BlockStateProperties.LIT, firing), Block.UPDATE_CLIENTS);
        }
        if (server.getGameTime() % 10L == 0L && syncGate.changed(this, server.registryAccess())) {
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void thrustHost(final ServerSubLevel host, final Propellant propellant) {
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) return;
        final Direction facing = getBlockState().getValue(DirectionalBlock.FACING);
        final Vec3 local = Vec3.atLowerCornerOf(facing.getOpposite().getNormal());
        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;
        final Pose3dc pose = host.logicalPose();
        final Vec3 world = pose.transformNormal(local).normalize();
        final Vector3dc velocity = handle.getLinearVelocity();
        final double speed = velocity.x() * world.x + velocity.y() * world.y + velocity.z() * world.z;
        if (speed >= MagConfig.ionThrusterBaseMaxSpeed() * propellant.speed()) return;
        final double force = MagConfig.ionThrusterBaseThrust() * propellant.thrust() * 20.0
                * host.getMassTracker().getMass();
        final Vector3dc centre = host.getMassTracker().getCenterOfMass();
        SableBridge.applyLocalImpulse(host, new Vector3d(centre.x(), centre.y(), centre.z()),
                new Vector3d(local.x * force, local.y * force, local.z * force));
    }

    private void exposeRadonExhaust(final ServerLevel server, final ServerSubLevel host) {
        if (!MagConfig.radonRadiationEnabled()) return;
        final Vec3 world = host.logicalPose().transformPosition(Vec3.atCenterOf(worldPosition));
        final double radius = MagConfig.radonThrusterExposureRadius();
        for (final net.minecraft.world.entity.LivingEntity living : server.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(world, world).inflate(radius))) {
            // Exposure uses the inflated AABB above, so Chebyshev distance gives
            // the matching shortest clearance to one of that hazard cube's faces.
            final Vec3 position = living.position();
            final double fromCentre = Math.max(Math.abs(position.x - world.x),
                    Math.max(Math.abs(position.y - world.y), Math.abs(position.z - world.z)));
            com.stonytark.magnetization.content.effect.RadonExposureHandler.addExposure(
                    living, 1, Math.max(0.0d, radius - fromCentre));
        }
    }

    @Override protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("Bucket", bucketSlot.createTag(registries));
    }
    @Override protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        bucketSlot.fromTag(tag.getList("Bucket", net.minecraft.nbt.Tag.TAG_COMPOUND), registries);
    }
    @Override public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    private final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int receive) { super(capacity, receive, 0); }
        @Override public int receiveEnergy(final int amount, final boolean simulate) {
            final int accepted = super.receiveEnergy(amount, simulate);
            if (!simulate && accepted > 0) IonThrusterBlockEntity.this.setChanged();
            return accepted;
        }
        void drainInternal(final int amount) { energy = Math.max(0, energy - amount); }
        void setStored(final int value) { energy = Math.max(0, Math.min(capacity, value)); }
        void resize(final int newCapacity, final int receive) {
            capacity = Math.max(0, newCapacity);
            maxReceive = Math.max(0, receive);
            energy = Math.min(energy, capacity);
        }
    }
}
