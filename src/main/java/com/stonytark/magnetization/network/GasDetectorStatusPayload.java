package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.effect.RadonExposureHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative Radon dose and safety state for the expanded Gas Detector HUD. */
public record GasDetectorStatusPayload(boolean radiationEnabled, int dose, int threshold,
                                       int recoveryPerTick, boolean exposed,
                                       double distanceToSafety) implements CustomPacketPayload {
    public static final Type<GasDetectorStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "gas_detector_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasDetectorStatusPayload> CODEC =
            StreamCodec.of(GasDetectorStatusPayload::encode, GasDetectorStatusPayload::decode);

    private static volatile @Nullable GasDetectorStatusPayload latest;

    public GasDetectorStatusPayload {
        dose = Math.max(0, dose);
        threshold = Math.max(1, threshold);
        recoveryPerTick = Math.max(0, recoveryPerTick);
        distanceToSafety = Math.max(0.0d, distanceToSafety);
    }

    public static GasDetectorStatusPayload from(final LivingEntity entity) {
        final RadonExposureHandler.ExposureSnapshot snapshot = RadonExposureHandler.snapshot(entity);
        return new GasDetectorStatusPayload(snapshot.radiationEnabled(), snapshot.dose(),
                snapshot.threshold(), snapshot.recoveryPerTick(), snapshot.exposed(),
                snapshot.distanceToSafety());
    }

    @Override
    public Type<GasDetectorStatusPayload> type() {
        return TYPE;
    }

    public static void register(final PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, GasDetectorStatusPayload::handle);
    }

    /** Last snapshot received by this physical client; null until the server responds. */
    public static @Nullable GasDetectorStatusPayload latest() {
        return latest;
    }

    public static void clearClientSnapshot() {
        latest = null;
    }

    private static void handle(final GasDetectorStatusPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> latest = payload);
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final GasDetectorStatusPayload payload) {
        buffer.writeBoolean(payload.radiationEnabled());
        buffer.writeVarInt(payload.dose());
        buffer.writeVarInt(payload.threshold());
        buffer.writeVarInt(payload.recoveryPerTick());
        buffer.writeBoolean(payload.exposed());
        buffer.writeDouble(payload.distanceToSafety());
    }

    private static GasDetectorStatusPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new GasDetectorStatusPayload(buffer.readBoolean(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readDouble());
    }
}
