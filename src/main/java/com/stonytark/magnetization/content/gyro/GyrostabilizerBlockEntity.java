package com.stonytark.magnetization.content.gyro;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Magnetic Gyrostabilizer — mounted on a Sable ship and powered (redstone OR
 * FE/RF), it magnetically locks the ship's orientation: each tick it cancels the
 * host body's angular velocity, so the craft can still translate (up/down/
 * forward/back) but won't spin, pitch, or roll. Unpowered, the ship rotates
 * freely.
 *
 * <p>It ticks on the ship via {@link BlockEntitySubLevelActor#sable$tick} (the
 * vanilla block-entity ticker does NOT run inside a Sable sub-level), and falls
 * back to the world ticker off-ship just to keep its powered visual honest.
 */
public class GyrostabilizerBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    private static final int CAPACITY = 50_000;
    private static final int MAX_RECEIVE = 1_000;
    /** FE drained per tick while actively stabilizing on FE power (free on redstone). */
    private static final int FE_PER_TICK = 20;

    private final ReceiveBuffer energy = new ReceiveBuffer(CAPACITY, MAX_RECEIVE);
    /** Synced to clients: actively cancelling a ship's rotation right now. */
    private boolean stabilizing = false;

    public GyrostabilizerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.GYROSTABILIZER.get(), pos, state);
    }

    public IEnergyStorage energyBuffer() { return energy; }
    public boolean isStabilizing() { return stabilizing; }

    /** Vanilla ticker (off-ship / fallback): nothing to stabilize, but resolve a
     *  possible host and keep the powered visual + status honest. */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final GyrostabilizerBlockEntity be) {
        if (level instanceof ServerLevel server) be.run(server, SableBridge.subLevelAt(server, pos));
    }

    /** Sable sub-level tick: we're mounted on this ship — stabilize it. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (level instanceof ServerLevel server) run(server, subLevel);
    }

    private void run(final ServerLevel server, final @Nullable ServerSubLevel host) {
        final boolean redstone = server.hasNeighborSignal(getBlockPos());
        final boolean feReady = energy.getEnergyStored() >= FE_PER_TICK;
        final boolean powered = redstone || feReady;

        boolean stabilizingNow = false;
        if (powered && host != null) {
            final RigidBodyHandle handle = RigidBodyHandle.of(host);
            if (handle != null && handle.isValid()) {
                final Vector3dc av = handle.getAngularVelocity();
                if (av.x() != 0.0 || av.y() != 0.0 || av.z() != 0.0) {
                    // Cancel this tick's angular velocity → no accumulated spin.
                    handle.addLinearAndAngularVelocity(
                            new Vector3d(0, 0, 0), new Vector3d(-av.x(), -av.y(), -av.z()));
                }
                stabilizingNow = true;
                if (!redstone) energy.drainInternal(FE_PER_TICK); // FE only burns when not free-powered by redstone
            }
        }

        // Powered visual (blue sections light up) — drive the POWERED blockstate.
        final BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED) != powered) {
            server.setBlock(getBlockPos(), state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        }
        if (stabilizing != stabilizingNow) {
            stabilizing = stabilizingNow;
            setChanged();
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putBoolean("Stabilizing", stabilizing);
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setStored(tag.getInt("Energy"));
        stabilizing = tag.getBoolean("Stabilizing");
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

    /** Cable-fed buffer: external receive only; the stabilizer drains it internally. */
    private static final class ReceiveBuffer extends EnergyStorage {
        ReceiveBuffer(final int capacity, final int maxReceive) { super(capacity, maxReceive, 0); }
        void drainInternal(final int amount) { this.energy = Math.max(0, this.energy - amount); }
        void setStored(final int value) { this.energy = Math.max(0, Math.min(capacity, value)); }
    }
}
