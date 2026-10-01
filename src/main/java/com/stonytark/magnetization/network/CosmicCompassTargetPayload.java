package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Server-selected AE2 meteorite target; native cores remain visible locally. */
public record CosmicCompassTargetPayload(ResourceLocation dimension, Optional<BlockPos> position,
                                         long expiresAtTick) implements CustomPacketPayload {
    public static final Type<CosmicCompassTargetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "cosmic_compass_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmicCompassTargetPayload> CODEC =
            StreamCodec.of(CosmicCompassTargetPayload::encode, CosmicCompassTargetPayload::decode);

    // Bind to the actual client level, not just its dimension key: two worlds
    // in the same session can have identical dimension IDs and game times.
    private static @Nullable ClientSnapshot latest;

    @Override
    public Type<CosmicCompassTargetPayload> type() { return TYPE; }

    public static void register(final PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, CosmicCompassTargetPayload::handle);
    }

    public static @Nullable BlockPos latestTarget(final Level level, final Vec3 from, final double range) {
        final ClientSnapshot snapshot = latest;
        if (snapshot == null || snapshot.level() != level) return null;
        return snapshot.payload().targetFor(level.dimension().location(), level.getGameTime(), from, range);
    }

    /** Reject expired, out-of-range and other-dimension readings at render time. */
    public @Nullable BlockPos targetFor(final ResourceLocation currentDimension, final long now,
                                        final Vec3 from, final double range) {
        if (!dimension.equals(currentDimension) || now >= expiresAtTick || position.isEmpty()) return null;
        final BlockPos pos = position.get();
        return pos.getCenter().distanceToSqr(from) < range * range ? pos : null;
    }

    public static void clearClientSnapshot() { latest = null; }

    private static void handle(final CosmicCompassTargetPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            final Level level = context.player().level();
            if (level.dimension().location().equals(payload.dimension())) {
                latest = new ClientSnapshot(level, payload);
            }
        });
    }

    private static void encode(final RegistryFriendlyByteBuf buffer, final CosmicCompassTargetPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeOptional(payload.position(), RegistryFriendlyByteBuf::writeBlockPos);
        buffer.writeLong(payload.expiresAtTick());
    }

    private static CosmicCompassTargetPayload decode(final RegistryFriendlyByteBuf buffer) {
        return new CosmicCompassTargetPayload(buffer.readResourceLocation(),
                buffer.readOptional(RegistryFriendlyByteBuf::readBlockPos), buffer.readLong());
    }

    private record ClientSnapshot(Level level, CosmicCompassTargetPayload payload) {}
}
