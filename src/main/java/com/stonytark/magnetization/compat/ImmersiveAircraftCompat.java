package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.network.PilotedAircraftImpulsePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** Piloted aircraft simulate movement on their controlling client, including Reloaded subclasses. */
public final class ImmersiveAircraftCompat {
    private ImmersiveAircraftCompat() {}

    public static boolean applyPilotImpulse(final Entity entity, final Vec3 impulse) {
        if (!(entity.getControllingPassenger() instanceof ServerPlayer pilot)
                || !ModList.get().isLoaded("immersive_aircraft") || !Loaded.isAircraft(entity)) return false;
        PacketDistributor.sendToPlayer(pilot, new PilotedAircraftImpulsePayload(entity.getUUID(), impulse.x, impulse.y, impulse.z, com.stonytark.magnetization.config.MagConfig.immersiveAircraftMagneticSpeedLimit()));
        return true;
    }

    private static final class Loaded {
        private static boolean isAircraft(final Entity entity) {
            return entity instanceof immersive_aircraft.entity.VehicleEntity;
        }
    }
}
