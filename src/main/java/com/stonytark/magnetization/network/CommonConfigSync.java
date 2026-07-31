package com.stonytark.magnetization.network;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends the authoritative COMMON values to connected clients. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class CommonConfigSync {

    private CommonConfigSync() {}

    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player);
    }

    /** Called on the mod event bus when a COMMON file is edited or reloaded. */
    public static void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.COMMON) return;
        final net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }

    private static void send(final ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new CommonConfigSyncPayload(MagConfig.commonSnapshot()));
    }
}
