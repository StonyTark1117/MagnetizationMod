package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server-authoritative snapshot of the otherwise-local COMMON config. */
public record CommonConfigSyncPayload(CompoundTag values) implements CustomPacketPayload {

    public static final Type<CommonConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "common_config_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CommonConfigSyncPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG,
                    CommonConfigSyncPayload::values, CommonConfigSyncPayload::new);

    @Override
    public Type<CommonConfigSyncPayload> type() { return TYPE; }

    public static void register(final PayloadRegistrar registrar) {
        registrar.playToClient(TYPE, CODEC, CommonConfigSyncPayload::handle);
    }

    private static void handle(final CommonConfigSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> MagConfig.applyClientSnapshot(payload.values()));
    }
}
