package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.compat.coastersmagnetized.MagCoastersMagnetizedCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server-authoritative field-only power state for Coasters: Magnetized visuals. */
public record CoastersMagnetizedPowerPayload(BlockPos pos, boolean powered, boolean reset)
        implements CustomPacketPayload {
    public static final Type<CoastersMagnetizedPowerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "coasters_magnetized_power"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoastersMagnetizedPowerPayload> CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, CoastersMagnetizedPowerPayload::pos,
                    ByteBufCodecs.BOOL, CoastersMagnetizedPowerPayload::powered,
                    ByteBufCodecs.BOOL, CoastersMagnetizedPowerPayload::reset,
                    CoastersMagnetizedPowerPayload::new);

    @Override
    public Type<CoastersMagnetizedPowerPayload> type() {
        return TYPE;
    }

    public static void register(final PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, CoastersMagnetizedPowerPayload::handle);
    }

    private static void handle(final CoastersMagnetizedPowerPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.reset()) MagCoastersMagnetizedCompat.clearClientFieldPower();
            else MagCoastersMagnetizedCompat.updateClientFieldPower(payload.pos(), payload.powered());
        });
    }
}
