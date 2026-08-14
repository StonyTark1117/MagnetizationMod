package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Client request for the server-owned exposure snapshot shown by a held Gas Detector. */
public record GasDetectorStatusRequestPayload() implements CustomPacketPayload {
    public static final Type<GasDetectorStatusRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "gas_detector_status_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GasDetectorStatusRequestPayload> CODEC =
            StreamCodec.of((buffer, payload) -> {}, buffer -> new GasDetectorStatusRequestPayload());

    @Override
    public Type<GasDetectorStatusRequestPayload> type() {
        return TYPE;
    }

    public static void register(final PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, CODEC, GasDetectorStatusRequestPayload::handle);
    }

    private static void handle(final GasDetectorStatusRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && holdingDetector(player)) {
                PacketDistributor.sendToPlayer(player, GasDetectorStatusPayload.from(player));
            }
        });
    }

    private static boolean holdingDetector(final ServerPlayer player) {
        return isEnabledDetector(player.getMainHandItem()) || isEnabledDetector(player.getOffhandItem());
    }

    private static boolean isEnabledDetector(final net.minecraft.world.item.ItemStack stack) {
        return stack.is(MagItems.GAS_DETECTOR.get()) && !MagConfig.isItemDisabled(stack);
    }
}
