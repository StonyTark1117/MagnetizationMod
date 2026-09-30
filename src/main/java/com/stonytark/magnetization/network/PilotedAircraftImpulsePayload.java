package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.UUID;

/** Server-computed additive impulse; never replaces the pilot's current native velocity. */
public record PilotedAircraftImpulsePayload(UUID vehicle, double x, double y, double z, double speedLimit) implements CustomPacketPayload {
    public static final Type<PilotedAircraftImpulsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "piloted_aircraft_impulse"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PilotedAircraftImpulsePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PilotedAircraftImpulsePayload::vehicle,
            ByteBufCodecs.DOUBLE, PilotedAircraftImpulsePayload::x,
            ByteBufCodecs.DOUBLE, PilotedAircraftImpulsePayload::y,
            ByteBufCodecs.DOUBLE, PilotedAircraftImpulsePayload::z,
            ByteBufCodecs.DOUBLE, PilotedAircraftImpulsePayload::speedLimit, PilotedAircraftImpulsePayload::new);
    @Override public Type<PilotedAircraftImpulsePayload> type() { return TYPE; }
    public static void register(final PayloadRegistrar registrar) {
        // Version 2 adds the server speed limit; reject older four-field decoders at login.
        registrar.versioned("2").playToClient(TYPE, CODEC, PilotedAircraftImpulsePayload::handle);
    }
    private static void handle(final PilotedAircraftImpulsePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final var player = context.player();
            final var craft = player.getVehicle();
            // Ignore packets from a previous ride, dimension or controller. UUID prevents entity-ID reuse.
            if (craft == null || !craft.getUUID().equals(payload.vehicle()) || craft.getControllingPassenger() != player
                    || !Double.isFinite(payload.x()) || !Double.isFinite(payload.y()) || !Double.isFinite(payload.z())
                    || !Double.isFinite(payload.speedLimit()) || payload.speedLimit() <= 0) return;
            craft.setDeltaMovement(com.stonytark.magnetization.compat.AircraftImpulseLimiter.addImpulse(
                    craft.getDeltaMovement(), new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()), payload.speedLimit()));
        });
    }
}
