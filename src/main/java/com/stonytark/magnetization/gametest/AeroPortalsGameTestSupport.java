package com.stonytark.magnetization.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/** Test-only player setup that avoids the embedded mock client's login handshake. */
final class AeroPortalsGameTestSupport {

    private AeroPortalsGameTestSupport() {}

    static ServerPlayer addHeadlessOnlinePlayer(final ServerLevel level, final String name) {
        final ServerPlayer player = new ServerPlayer(
                level.getServer(), level, new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        player.connection = new ServerGamePacketListenerImpl(
                level.getServer(), connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(final Packet<?> packet) {
                // There is no client attached to this GameTest player.
            }
        };
        player.getAdvancements().reload(level.getServer().getAdvancements());
        level.addFreshEntity(player);
        playerList(level).add(player);
        return player;
    }

    static void removeHeadlessOnlinePlayer(final ServerLevel level, final ServerPlayer player) {
        playerList(level).remove(player);
        player.remove(Entity.RemovalReason.DISCARDED);
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> playerList(final ServerLevel level) {
        try {
            final Field field = level.getServer().getPlayerList().getClass().getSuperclass()
                    .getDeclaredField("players");
            field.setAccessible(true);
            return (List<ServerPlayer>) field.get(level.getServer().getPlayerList());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to install a headless AeroPortals test player", exception);
        }
    }
}
