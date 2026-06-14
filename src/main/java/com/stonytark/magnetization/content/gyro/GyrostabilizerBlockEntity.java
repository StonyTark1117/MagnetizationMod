package com.stonytark.magnetization.content.gyro;

import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.registry.MagBlockEntities;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Magnetic Gyrostabilizer — mounted on a Sable ship and powered, it magnetically
 * locks the ship's orientation: each tick it cancels the host body's angular
 * velocity, so the craft can still translate (up/down/forward/back) but won't
 * spin, pitch, or roll. Unpowered, it does nothing and the ship rotates freely.
 * Great for running cleanly over repulsor tracks, anchoring a heading, or a crude
 * autopilot on a self-propelled craft.
 */
public class GyrostabilizerBlockEntity extends BlockEntity {

    public GyrostabilizerBlockEntity(final BlockPos pos, final BlockState state) {
        super(MagBlockEntities.GYROSTABILIZER.get(), pos, state);
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final GyrostabilizerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (!state.getValue(BlockStateProperties.POWERED)) return; // off → free rotation
        final ServerSubLevel host = SableBridge.subLevelAt(server, pos);
        if (host == null) return; // not on a contraption
        final RigidBodyHandle handle = RigidBodyHandle.of(host);
        if (handle == null || !handle.isValid()) return;
        final Vector3dc av = handle.getAngularVelocity();
        if (av.x() == 0.0 && av.y() == 0.0 && av.z() == 0.0) return;
        // Cancel this tick's angular velocity → no accumulated spin.
        handle.addLinearAndAngularVelocity(
                new Vector3d(0, 0, 0),
                new Vector3d(-av.x(), -av.y(), -av.z()));
    }
}
