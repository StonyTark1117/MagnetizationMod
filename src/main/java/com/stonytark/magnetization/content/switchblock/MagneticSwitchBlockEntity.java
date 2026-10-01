package com.stonytark.magnetization.content.switchblock;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Tracks the proximity of the nearest sub-level. Recomputes every {@link #PERIOD}
 * ticks (cheap query, but no need to fire every tick), updates a 0–15 signal,
 * and pings neighbors when the value changes.
 *
 * <p>Works both in the open world and mounted on a Sable contraption. On a ship
 * the vanilla block-entity ticker does NOT run, so it also ticks via
 * {@link BlockEntitySubLevelActor#sable$tick}; and because {@code getBlockPos()}
 * is sub-level-LOCAL there, its own position is promoted to world space (via the
 * host pose) before scanning — otherwise the search box sits in empty plot space
 * and the switch "can't detect sub-levels while on a sub-level".
 */
public class MagneticSwitchBlockEntity extends BlockEntity implements BlockEntitySubLevelActor, com.stonytark.magnetization.menu.MachineHudData {

    /** Default scan radius. Server owners override via
     *  {@code MagConfig.MAGNETIC_SWITCH_RANGE}; this is the fallback for
     *  early-load / unit-test contexts. */
    public static final double SCAN_RADIUS = 8.0d;
    private static final int PERIOD = 4;

    private static double scanRadius() {
        try { return com.stonytark.magnetization.config.MagConfig.MAGNETIC_SWITCH_RANGE.get(); }
        catch (final Throwable t) { return SCAN_RADIUS; }
    }

    private int signal = 0;
    private long lastSample = Long.MIN_VALUE;
    private com.stonytark.magnetization.content.docking.DockingState.Mode mode =
            com.stonytark.magnetization.content.docking.DockingState.Mode.PROXIMITY;
    private @Nullable BlockPos anchorPos;
    private com.stonytark.magnetization.content.docking.DockingState docking =
            new com.stonytark.magnetization.content.docking.DockingState();
    private String dockReason = "unlinked";

    public void linkAnchor(BlockPos pos) {
        anchorPos = pos.immutable();
        mode = com.stonytark.magnetization.content.docking.DockingState.Mode.TARGET_PRESENT;
        docking = new com.stonytark.magnetization.content.docking.DockingState();
        configurationChanged();
    }
    public void cycleMode() { mode = mode.next(); configurationChanged(); }
    public com.stonytark.magnetization.content.docking.DockingState.Mode mode() { return mode; }
    public @Nullable BlockPos anchorPos() { return anchorPos; }
    public String dockReason() { return dockReason; }
    private void configurationChanged() {
        lastSample = Long.MIN_VALUE;
        if (level instanceof ServerLevel server) run(server, SableBridge.subLevelOf(this));
        sync();
    }
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
    }
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> lines, boolean sneaking) {
        for (var line : hudLines()) lines.add(net.minecraft.network.chat.Component.literal("    ").append(line));
        return true;
    }
    @Override public java.util.List<net.minecraft.network.chat.Component> hudLines() {
        var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        lines.add(net.minecraft.network.chat.Component.translatable("dock.magnetization.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        lines.add(net.minecraft.network.chat.Component.translatable("dock.magnetization.signal", signal));
        if (mode != com.stonytark.magnetization.content.docking.DockingState.Mode.PROXIMITY) {
            lines.add(net.minecraft.network.chat.Component.translatable("dock.magnetization.reason." + dockReason));
            if (anchorPos != null) lines.add(net.minecraft.network.chat.Component.translatable("dock.magnetization.anchor", anchorPos.toShortString()));
        }
        return lines;
    }
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("DockMode", mode.name());
        if (anchorPos != null) tag.putLong("DockAnchor", anchorPos.asLong());
        if (docking.tracked() != null) tag.putUUID("DockTarget", docking.tracked());
        tag.putBoolean("DockSeen", docking.seen());
        tag.putInt("Signal", signal);
        tag.putString("DockReason", dockReason);
    }
    @Override protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        try { mode = com.stonytark.magnetization.content.docking.DockingState.Mode.valueOf(tag.getString("DockMode")); }
        catch (IllegalArgumentException ignored) { mode = com.stonytark.magnetization.content.docking.DockingState.Mode.PROXIMITY; }
        anchorPos = tag.contains("DockAnchor") ? BlockPos.of(tag.getLong("DockAnchor")) : null;
        docking = new com.stonytark.magnetization.content.docking.DockingState();
        docking.restore(tag.hasUUID("DockTarget") ? tag.getUUID("DockTarget") : null, tag.getBoolean("DockSeen"));
        // A saved settled signal is never trusted on the server before fresh measurements.
        signal = level != null && level.isClientSide ? Math.clamp(tag.getInt("Signal"), 0, 15) : 0;
        dockReason = tag.getString("DockReason");
        lastSample = Long.MIN_VALUE;
    }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider provider) { return saveWithoutMetadata(provider); }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public MagneticSwitchBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.MAGNETIC_SWITCH.get(), pos, state);
    }

    public int signal() {
        return signal;
    }

    /** Vanilla ticker (open world / off-ship). {@code subLevelAt} resolves a host
     *  if this somehow runs on a contraption; normally it returns null here. */
    public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final MagneticSwitchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        be.run(server, SableBridge.subLevelAt(server, pos));
    }

    /** Sable sub-level tick: we're mounted on this ship — scan from its world pose. */
    @Override
    public void sable$tick(final ServerSubLevel subLevel) {
        if (level instanceof ServerLevel server) run(server, subLevel);
    }

    private void run(final ServerLevel server, final @Nullable ServerSubLevel host) {
        long now = server.getGameTime();
        if (lastSample != Long.MIN_VALUE && now - lastSample < PERIOD && now >= lastSample) return;
        lastSample = now;
        String previousReason = dockReason;
        UUID previousTarget = docking.tracked();
        boolean previouslySeen = docking.seen();
        final int next;
        if (com.stonytark.magnetization.config.MagConfig.isBlockDisabled(getBlockState())) next = 0;
        else if (mode == com.stonytark.magnetization.content.docking.DockingState.Mode.PROXIMITY) next = computeSignal(server, host);
        else {
            var sample = com.stonytark.magnetization.content.docking.DockingMeasurements.sample(server, anchorPos);
            var limits = new com.stonytark.magnetization.content.docking.DockingState.Limits(sample.range(),
                    com.stonytark.magnetization.config.MagConfig.DOCK_TOLERANCE.get(),
                    com.stonytark.magnetization.config.MagConfig.DOCK_HYSTERESIS.get(),
                    com.stonytark.magnetization.config.MagConfig.DOCK_SPEED.get(),
                    com.stonytark.magnetization.config.MagConfig.DOCK_SPIN.get(),
                    com.stonytark.magnetization.config.MagConfig.DOCK_DWELL_TICKS.get());
            docking.update(sample.reading(), limits, now);
            dockReason = docking.reason().name().toLowerCase(java.util.Locale.ROOT);
            next = docking.signal(mode, sample.range(), signal);
        }
        if (next != signal) {
            signal = next;
            // Force a comparator/redstone neighbor update.
            server.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
            server.updateNeighbourForOutputSignal(getBlockPos(), getBlockState().getBlock());
            sync();
        } else if (!previousReason.equals(dockReason) || !java.util.Objects.equals(previousTarget, docking.tracked()) || previouslySeen != docking.seen()) sync();
    }

    private int computeSignal(final ServerLevel level, final @Nullable ServerSubLevel host) {
        // On a contraption, getBlockPos()/getCenter() are sub-level-LOCAL plot
        // coordinates. Promote our own centre to world space via the host pose so we
        // search where the ship actually is; off-ship the centre is already world space.
        final Vec3 localCenter = getBlockPos().getCenter();
        final Vec3 origin = host != null
                ? host.logicalPose().transformPosition(localCenter)
                : localCenter;

        final double radius = scanRadius();
        final BoundingBox3d searchBox = new BoundingBox3d(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius
        );
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return 0;

        // Riding a ship: exclude our own craft (and everything joined to it), or the
        // switch would peg at 15 forever detecting the very contraption it sits on.
        final Set<UUID> ownChain = host != null
                ? SableBridge.connectedChainIds(host, level.getGameTime())
                : Set.of();

        double bestDist = Double.MAX_VALUE;
        for (SubLevel sub : container.queryIntersecting(searchBox)) {
            if (ownChain.contains(sub.getUniqueId())) continue;
            final BoundingBox3dc box = sub.boundingBox();
            final double dx = origin.x - clamp(origin.x, box.minX(), box.maxX());
            final double dy = origin.y - clamp(origin.y, box.minY(), box.maxY());
            final double dz = origin.z - clamp(origin.z, box.minZ(), box.maxZ());
            final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < bestDist) bestDist = d;
        }
        if (bestDist == Double.MAX_VALUE) return 0;
        // Linear ramp: 0 at scan radius, 15 at distance 0.
        final double t = Math.max(0.0d, 1.0d - bestDist / radius);
        return Math.min(15, (int) Math.round(t * 15.0d));
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
