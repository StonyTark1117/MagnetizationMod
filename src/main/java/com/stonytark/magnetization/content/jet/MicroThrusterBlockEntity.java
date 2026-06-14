package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagFluids;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
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
public class MicroThrusterBlockEntity extends BlockEntity {

    public static final int TANK_CAPACITY = 8_000;       // 8 buckets of ferrofluid
    private static final int FE_CAPACITY = 400_000;
    private static final int FE_MAX_RECEIVE = 16_000;
    private static final int FE_PER_TICK = 48;
    private static final int FLUID_PER_TICK = 2;
    private static final double THRUST_RANGE = 7.0;
    private static final double MAX_SPEED = 3.6;          // top of the propulsion ladder
    private static final double THRUST_DV = 0.24;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY,
            fs -> fs.getFluid() == MagFluids.FERROFLUID.get());
    private final ReceiveBuffer energy = new ReceiveBuffer(FE_CAPACITY, FE_MAX_RECEIVE);

    public MicroThrusterBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MICRO_THRUSTER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public IFluidHandler fluidHandler() { return tank; }

    /** Try to pour one ferrofluid bucket (1000 mB) into the tank. */
    public boolean fillFromBucket() {
        final FluidStack ferro = new FluidStack(MagFluids.FERROFLUID.get(), 1000);
        if (tank.fill(ferro, IFluidHandler.FluidAction.SIMULATE) < 1000) return false;
        tank.fill(ferro, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        return true;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final MicroThrusterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        final boolean running = be.tank.getFluidAmount() >= FLUID_PER_TICK
                && be.energy.getEnergyStored() >= FE_PER_TICK;
        if (running) {
            be.tank.drain(FLUID_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
            be.energy.drainInternal(FE_PER_TICK);
            be.thrust(server, pos, state);
            be.setChanged();
        }
        if (state.getValue(BlockStateProperties.LIT) != running) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, running), Block.UPDATE_CLIENTS);
        }
    }

    private void thrust(final ServerLevel server, final BlockPos pos, final BlockState state) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return;
        final Direction facing = state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING) : Direction.UP;
        final Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        final double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        final double rangeSqr = THRUST_RANGE * THRUST_RANGE;
        for (final SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship)) continue;
            if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0) continue;
            if (ShipMagneticRegistry.get(server, ship).susceptibility() <= 0.0) continue;
            final Vector3dc p = ship.logicalPose().position();
            final double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            if (dx * dx + dy * dy + dz * dz > rangeSqr) continue;
            final RigidBodyHandle handle = RigidBodyHandle.of(ship);
            if (handle == null || !handle.isValid()) continue;
            final Vector3dc v = handle.getLinearVelocity();
            if (v.x() * dir.x + v.y() * dir.y + v.z() * dir.z >= MAX_SPEED) continue;
            handle.addLinearAndAngularVelocity(
                    new Vector3d(dir.x * THRUST_DV, dir.y * THRUST_DV, dir.z * THRUST_DV), new Vector3d(0, 0, 0));
        }
    }


    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        tank.readFromNBT(registries, tag.getCompound("Tank"));
    }

    private static final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int maxReceive) { super(capacity, maxReceive, 0); }
        void drainInternal(final int amount) { this.energy = Math.max(0, this.energy - amount); }
        void setStored(final int value) { this.energy = Math.max(0, Math.min(capacity, value)); }
    }
}
