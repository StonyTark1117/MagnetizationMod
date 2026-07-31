package com.stonytark.magnetization.content.sail;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Magnetosphere Solar Sail (Alfvén wave sail). Mounted on a Create: Aeronautics
 * craft, each sail panel intercepts the ambient field of the solar wind and
 * gives the ship a small, fuel-free forward push along its facing. Thrust scales
 * automatically with the number of panels (more sail = more push), with altitude
 * (stronger up high), and with daylight: full by day, half by night — or zero at
 * night if the panel's night cut-off is toggled on (right-click).
 */
public class SolarSailBlockEntity extends BlockEntity
        implements BlockEntitySubLevelActor, com.stonytark.magnetization.menu.MachineGuiData {

    private static final double BASE_FORCE = 60.0;          // per-panel force at full strength
    // Cruising-speed ceiling scales with sail area: a bigger sail both accelerates
    // harder (each panel adds force) AND tops out faster.
    private static final double SAIL_SPEED_BASE = 0.7;
    private static final double SAIL_SPEED_PER_PANEL = 0.05;
    private static final double SAIL_SPEED_CAP = 4.0;

    // ── Client-synced HUD state (WTHIT/Jade/TOP/Create goggles) ──
    private int lastPanels = 0;          // panels in the assembled sail
    private boolean lastDay = true;      // day = full thrust, night = reduced/off
    private boolean lastActive = false;  // actually pushing the ship last tick
    private int syncCooldown = 0;
    /** Empty placeholder slot — the sail has no input, but {@link com.stonytark.magnetization.menu.MachineGuiData}
     *  requires a container. Never filled. */
    private final net.minecraft.world.SimpleContainer noInput = new net.minecraft.world.SimpleContainer(1);

    public SolarSailBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.SOLAR_SAIL.get(), pos, state);
    }

    /** Sable drives this when the panel is part of a moving sub-level. */
    @Override
    public void sable$tick(final ServerSubLevel host) {
        if (!(level instanceof ServerLevel server)) return;
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) { recordHud(server, 0, true, false); return; }

        // Day = full; night = server-config fraction (default 0.5, 0 disables).
        final boolean day = server.isDay();
        final double dayFactor = day ? 1.0
                : com.stonytark.magnetization.config.MagConfig.solarSailNightFactor();
        if (dayFactor <= 0.0) { recordHud(server, SailPanelCounter.count(server, host), day, false); return; }
        // More effective the higher you fly; never fully zero at sea level.
        final double altFactor = Mth.clamp(0.3 + (getBlockPos().getY() - 64) / 256.0, 0.3, 1.5);

        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) { recordHud(server, 0, day, false); return; }
        // Bigger sail → higher cruising-speed ceiling (more total panels).
        final int panels = SailPanelCounter.count(server, host);
        final double maxSpeed = Math.min(com.stonytark.magnetization.config.MagConfig.solarSailSpeedCap(), com.stonytark.magnetization.config.MagConfig.solarSailSpeedBase() + com.stonytark.magnetization.config.MagConfig.solarSailSpeedPerPanel() * panels);

        final Direction facing = getBlockState().hasProperty(DirectionalBlock.FACING)
                ? getBlockState().getValue(DirectionalBlock.FACING) : Direction.NORTH;
        // Thrust OUT of the ribboned front face — the ship sails the way the front
        // points, which is opposite the FACING normal (FACING points into the
        // surface the panel was placed against). FACING is in the ship-local frame.
        final Vec3i n = facing.getNormal();
        // Speed ceiling gates the FORWARD component only: project the host's world
        // velocity onto the world-space forward axis, so vertical drift or a lateral
        // knock can't wrongly stall the sail (|v| would always exceed the forward
        // speed). Mirrors the MHD/micro/fusion thrusters.
        final var pose = host.logicalPose();
        final net.minecraft.world.phys.Vec3 dirWorld = pose.transformNormal(
                new net.minecraft.world.phys.Vec3(-n.getX(), -n.getY(), -n.getZ())).normalize();
        final Vector3dc v = handle.getLinearVelocity();
        if (v.x() * dirWorld.x + v.y() * dirWorld.y + v.z() * dirWorld.z >= maxSpeed) { recordHud(server, panels, day, false); return; }
        final double f = com.stonytark.magnetization.config.MagConfig.solarSailForce() * dayFactor * altFactor;
        final Vector3dc com = host.getMassTracker().getCenterOfMass();
        SableBridge.applyLocalImpulse(host,
                new Vector3d(com.x(), com.y(), com.z()),
                new Vector3d(-n.getX() * f, -n.getY() * f, -n.getZ() * f));
        recordHud(server, panels, day, true);
    }

    /** Stash the HUD readouts and throttle a client sync (~every 0.5s, or whenever
     *  the active/day state flips) so WTHIT/Jade/TOP track the sail without spam. */
    private void recordHud(final ServerLevel server, final int panels, final boolean day, final boolean active) {
        final boolean changed = panels != lastPanels || day != lastDay || active != lastActive;
        lastPanels = panels;
        lastDay = day;
        lastActive = active;
        if (changed || --syncCooldown <= 0) {
            syncCooldown = 10;
            server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // ── MachineGuiData (HUD-only: no menu, surfaces in WTHIT/Jade/TOP/Create goggles) ──
    @Override public net.minecraft.world.Container guiInput() { return noInput; }
    @Override public com.stonytark.magnetization.menu.MachineMenu.Kind guiKind() {
        return com.stonytark.magnetization.menu.MachineMenu.Kind.SAIL;
    }

    /** Sail status: panel count, day/night, sailing/idle. No FE (it's fuel-free). */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> hudLines() {
        final java.util.List<net.minecraft.network.chat.Component> out = new java.util.ArrayList<>();
        out.add(net.minecraft.network.chat.Component.translatable(
                "tooltip.magnetization.gui_sail_panels", Math.max(0, lastPanels))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        out.add(net.minecraft.network.chat.Component.translatable(lastDay
                ? "tooltip.magnetization.gui_sail_day" : "tooltip.magnetization.gui_sail_night")
                .withStyle(lastDay ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.BLUE));
        out.add(net.minecraft.network.chat.Component.translatable(lastActive
                ? "tooltip.magnetization.gui_sail_sailing" : "tooltip.magnetization.machine_idle")
                .withStyle(lastActive ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW));
        return out;
    }

    @Override
    public CompoundTag getUpdateTag(final HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Panels", lastPanels);
        tag.putBoolean("Day", lastDay);
        tag.putBoolean("Active", lastActive);
        return tag;
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        lastPanels = tag.getInt("Panels");
        lastDay = tag.getBoolean("Day");
        lastActive = tag.getBoolean("Active");
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(final net.minecraft.network.Connection net,
                            final net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
                            final HolderLookup.Provider registries) {
        final CompoundTag tag = pkt.getTag();
        if (tag != null) handleUpdateTag(tag, registries);
    }
}
