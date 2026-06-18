package com.stonytark.magnetization.content.induction;

import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.registry.MagBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.joml.Vector3dc;

/**
 * Kinetic induction coil — Faraday's law. A magnetic Sable ship moving through
 * the coil induces an EMF proportional to its speed: the coil generates FE
 * (pushed to adjacent machines/cables) and emits an analog redstone pulse while
 * the magnet passes. No fuel — pure kinetic-to-electric.
 */
public class KineticCoilBlockEntity extends BlockEntity {

    private static final int CAPACITY = 100_000;
    private static final int OUTPUT_RATE = 4_000;     // FE/tick pushed out
    private static final int FE_PER_EMF = 2_500;      // FE generated per unit EMF
    private static final double RANGE = 4.0;
    private static final double MIN_SPEED = 0.05;     // blocks/tick

    private final GenBuffer energy = new GenBuffer(CAPACITY, OUTPUT_RATE);
    private int signal = 0;
    private int lastSyncedEnergy = -1;

    public KineticCoilBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.KINETIC_COIL.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() {
        return energy;
    }

    public int getSignal() {
        return signal;
    }

    /** Live FE in the buffer (synced to client for WTHIT). */
    public int getEnergyStored() {
        return energy.getEnergyStored();
    }

    /** Buffer capacity (constant; exposed for the WTHIT readout). */
    public int getEnergyCapacity() {
        return CAPACITY;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final KineticCoilBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;

        final double emf = inducedEmf(server, pos);
        if (emf > 0.0) {
            be.energy.generate((int) (emf * FE_PER_EMF));
        }
        // Redstone tracks the live EMF (instant pulse while a magnet passes).
        final int sig = (int) Math.ceil(Math.min(1.0, emf * 1.5) * 15.0);
        boolean sync = false;
        if (sig != be.signal) {
            be.signal = sig;
            be.setChanged();
            final boolean powered = sig > 0;
            if (state.getValue(BlockStateProperties.POWERED) != powered) {
                level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
            }
            level.updateNeighborsAt(pos, state.getBlock());
            sync = true;
        }
        pushEnergy(server, pos, be.energy);
        // Throttled FE sync so the WTHIT readout tracks the buffer without spamming
        // a packet every tick while energy drains/charges.
        if (!sync && (server.getGameTime() % 20L) == 0L && be.energy.getEnergyStored() != be.lastSyncedEnergy) {
            sync = true;
        }
        if (sync) {
            be.lastSyncedEnergy = be.energy.getEnergyStored();
            level.sendBlockUpdated(pos, state, level.getBlockState(pos), Block.UPDATE_CLIENTS);
        }
    }

    /** Strongest induced EMF (speed × susceptibility) from any magnetic ship in range. */
    private static double inducedEmf(final ServerLevel server, final BlockPos pos) {
        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container == null) return 0.0;
        final double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double best = 0.0;
        for (final SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel ship)) continue;
            if (ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0) continue;
            final double susc = ShipMagneticRegistry.get(server, ship).susceptibility();
            if (susc <= 0.0) continue;
            final Vector3dc p = ship.logicalPose().position();
            final double dx = p.x() - cx, dy = p.y() - cy, dz = p.z() - cz;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            final RigidBodyHandle handle = RigidBodyHandle.of(ship);
            if (handle == null || !handle.isValid()) continue;
            final Vector3dc v = handle.getLinearVelocity();
            final double speed = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
            if (speed < MIN_SPEED) continue;
            best = Math.max(best, speed * susc);
        }
        return best;
    }

    private static void pushEnergy(final ServerLevel level, final BlockPos pos, final GenBuffer energy) {
        if (energy.getEnergyStored() <= 0) return;
        for (final Direction dir : Direction.values()) {
            if (energy.getEnergyStored() <= 0) break;
            final IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            final int sim = target.receiveEnergy(Math.min(OUTPUT_RATE, energy.getEnergyStored()), true);
            if (sim <= 0) continue;
            final int moved = target.receiveEnergy(energy.extractEnergy(sim, false), false);
            // (extractEnergy already removed `sim`; receiveEnergy(moved) returns moved == sim)
            if (moved < sim) energy.generate(sim - moved); // put back any unaccepted remainder
        }
    }

    private static final class GenBuffer extends EnergyStorage {
        GenBuffer(final int capacity, final int maxExtract) {
            super(capacity, 0, maxExtract);
        }
        void generate(final int amount) {
            this.energy = Math.min(this.capacity, this.energy + amount);
        }
        void set(final int amount) {
            this.energy = Math.max(0, Math.min(this.capacity, amount));
        }
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Signal", signal);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Signal", signal);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.set(tag.getInt("Energy")); // set, not accumulate — handles repeated client update tags
        this.signal = tag.getInt("Signal");
    }
}
