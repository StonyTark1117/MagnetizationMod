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
public class KineticCoilBlockEntity extends BlockEntity
        implements com.stonytark.magnetization.menu.MachineGuiData {

    private static final int CAPACITY = 100_000;
    private static final int OUTPUT_RATE = 4_000;     // FE/tick pushed out
    private static final int FE_PER_EMF = 2_500;      // FE generated per unit EMF
    private static final double RANGE = 4.0;
    private static final double MIN_SPEED = 0.05;     // blocks/tick

    private final GenBuffer energy = new GenBuffer(CAPACITY, OUTPUT_RATE);
    private int signal = 0;
    private int lastSyncedEnergy = -1;
    private int lastGenerated = 0;     // FE generated last tick (HUD readout)
    /** Empty placeholder slot — the coil has no input, but {@link com.stonytark.magnetization.menu.MachineGuiData}
     *  requires a container. Never filled. */
    private final net.minecraft.world.SimpleContainer noInput = new net.minecraft.world.SimpleContainer(1);

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

    // ── MachineGuiData (HUD-only: no menu, surfaces in WTHIT/Jade/TOP/Create goggles) ──
    @Override public net.minecraft.world.Container guiInput() { return noInput; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.COIL;
    }
    @Override public int guiEnergyStored() { return energy.getEnergyStored(); }
    @Override public int guiEnergyMax() { return CAPACITY; }
    @Override public int guiStat1() { return lastGenerated; }   // FE/tick induced
    @Override public com.stonytark.magnetization.menu.MachineDisplayData.Status guiDisplayStatus() {
        return lastGenerated > 0 ? com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE
                : com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE;
    }

    /** Coil status: induced FE/tick + generating/idle. The FE bar is drawn
     *  separately by the energy-bar provider from {@link #guiEnergyStored()}. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> hudLines() {
        final java.util.List<net.minecraft.network.chat.Component> out = new java.util.ArrayList<>();
        final boolean generating = lastGenerated > 0;
        out.add(net.minecraft.network.chat.Component.translatable(
                "tooltip.magnetization.gui_coil_emf", Math.max(0, lastGenerated))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        out.add(net.minecraft.network.chat.Component.translatable(generating
                ? "tooltip.magnetization.machine_active" : "tooltip.magnetization.machine_idle")
                .withStyle(generating ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW));
        return out;
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final KineticCoilBlockEntity be) {
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(state)) return;
        if (!(level instanceof ServerLevel server)) return;
        be.energy.resize(CAPACITY, OUTPUT_RATE);

        final double emf = inducedEmf(server, pos);
        be.lastGenerated = emf > 0.0 ? (int) (emf * FE_PER_EMF) : 0;
        if (be.lastGenerated > 0) {
            be.energy.generate(be.lastGenerated);
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
            // Sable steps physics off-thread; a ship removed mid-tick can make
            // logicalPose()/the rigid-body handle throw. Isolate each ship so one
            // transient failure doesn't crash the BE ticker (mirrors LenzBrakingHandler).
            try {
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
            } catch (final RuntimeException ignored) {
                // ship torn down mid-tick — skip it this pass
            }
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
        void resize(final int capacity, final int maxExtract) {
            this.capacity = Math.max(0, capacity);
            this.maxExtract = Math.max(0, maxExtract);
            this.energy = Math.min(this.energy, this.capacity);
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
        tag.putInt("Generated", lastGenerated);
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
        if (tag.contains("Generated")) this.lastGenerated = tag.getInt("Generated");
    }
}
