package com.stonytark.magnetization.content.shaft;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class MagneticShaftBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {
    public enum Status { IDLE, SOURCE, RECEIVING, CONFLICT, OVERSTRESSED }
    private Status status = Status.IDLE;
    private @Nullable BlockPos transmitter;
    private float displayedLoad, displayedCapacity;
    private int displayedRange = 4;
    private boolean restoredReceiver;
    public MagneticShaftBlockEntity(BlockPos pos, BlockState state) { super(MagBlockEntities.MAGNETIC_SHAFT.get(), pos, state); }
    @Override public void onLoad() { super.onLoad(); MagneticShaftNetwork.register(this); }
    @Override public void initialize() {
        if (level != null && !level.isClientSide && restoredReceiver) {
            removeSource();
            restoredReceiver = false;
        }
        super.initialize();
    }
    @Override public void onChunkUnloaded() { MagneticShaftNetwork.unregister(this); super.onChunkUnloaded(); }
    @Override public void remove() { MagneticShaftNetwork.unregister(this); super.remove(); }
    @Override public void sable$tick(ServerSubLevel host) { tick(); }
    @Override public float calculateStressApplied() { return lastStressApplied = 0; }
    @Override public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        super.addPropagationLocations(block, state, neighbours);
        MagneticShaftNetwork.addNeighbours(this, neighbours);
        return neighbours;
    }
    public Status status() { return status; }
    public @Nullable BlockPos transmitter() { return transmitter; }
    public MagneticShaftMaterial material() { return ((MagneticShaftBlock)getBlockState().getBlock()).material(); }
    public int transmissionRange() { return level != null && level.isClientSide ? displayedRange : material().range(); }
    public Vec3 worldCenter() {
        var host = dev.ryanhcode.sable.Sable.HELPER.getContaining(this);
        return host == null ? worldPosition.getCenter() : host.logicalPose().transformPosition(worldPosition.getCenter());
    }
    public void display(Status next, @Nullable BlockPos source) {
        float load = hasNetwork() ? stress : 0;
        float totalCapacity = hasNetwork() ? capacity : 0;
        int range = transmissionRange();
        if (next == status && java.util.Objects.equals(source, transmitter) && load == displayedLoad
                && totalCapacity == displayedCapacity && range == displayedRange) return;
        status = next; transmitter = source; displayedLoad = load; displayedCapacity = totalCapacity; displayedRange = range;
        setChanged(); sendData();
    }
    @Override public boolean addToGoggleTooltip(List<Component> lines, boolean sneaking) {
        super.addToGoggleTooltip(lines, sneaking);
        lines.add(Component.literal("    ").append(Component.translatable("shaft.magnetization.status." + status.name().toLowerCase(java.util.Locale.ROOT))));
        lines.add(Component.translatable("shaft.magnetization.rpm", String.format(java.util.Locale.ROOT, "%.1f", getSpeed())));
        lines.add(Component.translatable("shaft.magnetization.range_material", material().label(), material().strength().name(), transmissionRange()));
        lines.add(Component.translatable("shaft.magnetization.load", Math.round(displayedLoad), Math.round(displayedCapacity)));
        if (transmitter != null) lines.add(Component.translatable("shaft.magnetization.source", transmitter.toShortString()));
        return true;
    }
    @Override protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean client) {
        super.write(tag, provider, client);
        tag.putBoolean("WirelessReceiver", transmitter != null);
        if (transmitter != null) tag.putLong("Transmitter", transmitter.asLong());
        tag.putString("CouplingStatus", status.name());
        tag.putFloat("CouplingLoad", displayedLoad); tag.putFloat("CouplingCapacity", displayedCapacity);
        tag.putInt("CouplingRange", displayedRange);
    }
    @Override protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean client) {
        super.read(tag, provider, client);
        restoredReceiver = !client && tag.getBoolean("WirelessReceiver");
        transmitter = tag.contains("Transmitter") ? BlockPos.of(tag.getLong("Transmitter")) : null;
        try { status = Status.valueOf(tag.getString("CouplingStatus")); } catch (IllegalArgumentException ignored) { status = Status.IDLE; }
        displayedLoad = tag.getFloat("CouplingLoad"); displayedCapacity = tag.getFloat("CouplingCapacity");
        displayedRange = tag.contains("CouplingRange") ? tag.getInt("CouplingRange") : (int)material().strength().range();
    }
}
