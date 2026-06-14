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
public class SolarSailBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {

    private static final double BASE_FORCE = 20.0;     // per-panel force at full strength
    private static final double MAX_SAIL_SPEED = 1.6;  // terminal cruising speed
    private static final double NIGHT_FACTOR = 0.5;

    public SolarSailBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.SOLAR_SAIL.get(), pos, state);
    }

    /** Sable drives this when the panel is part of a moving sub-level. */
    @Override
    public void sable$tick(final ServerSubLevel host) {
        if (!(level instanceof ServerLevel server)) return;
        if (host.getMassTracker().isInvalid() || host.getMassTracker().getMass() <= 0.0) return;

        // Day = full; night = server-config fraction (default 0.5, 0 disables).
        final double dayFactor = server.isDay() ? 1.0
                : com.stonytark.magnetization.config.MagConfig.solarSailNightFactor();
        if (dayFactor <= 0.0) return;
        // More effective the higher you fly; never fully zero at sea level.
        final double altFactor = Mth.clamp(0.3 + (getBlockPos().getY() - 64) / 256.0, 0.3, 1.5);

        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;
        final Vector3dc v = handle.getLinearVelocity();
        if (Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z()) >= MAX_SAIL_SPEED) return;

        final Direction facing = getBlockState().hasProperty(DirectionalBlock.FACING)
                ? getBlockState().getValue(DirectionalBlock.FACING) : Direction.NORTH;
        // Thrust OUT of the ribboned front face — the ship sails the way the front
        // points, which is opposite the FACING normal (FACING points into the
        // surface the panel was placed against). FACING is in the ship-local frame.
        final Vec3i n = facing.getNormal();
        final double f = BASE_FORCE * dayFactor * altFactor;
        final Vector3dc com = host.getMassTracker().getCenterOfMass();
        SableBridge.applyLocalImpulse(host,
                new Vector3d(com.x(), com.y(), com.z()),
                new Vector3d(-n.getX() * f, -n.getY() * f, -n.getZ() * f));
    }
}
