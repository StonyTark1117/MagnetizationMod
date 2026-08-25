package com.stonytark.magnetization.compat.coastersmagnetized;

import com.stonytark.magnetization.network.CoastersMagnetizedPowerPayload;
import net.antopfr.coastersmagnetized.magnet.AnchorMode;
import net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Loaded-only implementation; no caller resolves this class without the addon. */
final class MagCoastersMagnetizedLoadedCompat {
    private static final Map<ServerLevel, Map<BlockPos, Boolean>> SERVER_FIELD_POWER = new WeakHashMap<>();
    private static final Set<BlockPos> CLIENT_FIELD_POWER = new HashSet<>();

    private MagCoastersMagnetizedLoadedCompat() {}

    static void wire(final IEventBus eventBus) {
        eventBus.addListener(MagCoastersMagnetizedLoadedCompat::onLevelTick);
        eventBus.addListener(MagCoastersMagnetizedLoadedCompat::onPlayerLoggedIn);
        eventBus.addListener(MagCoastersMagnetizedLoadedCompat::onPlayerChangedDimension);
    }

    static boolean clientFieldPowersAnchor(final BlockPos pos) {
        synchronized (CLIENT_FIELD_POWER) {
            return CLIENT_FIELD_POWER.contains(pos);
        }
    }

    static void updateClientFieldPower(final BlockPos pos, final boolean powered) {
        synchronized (CLIENT_FIELD_POWER) {
            if (powered) CLIENT_FIELD_POWER.add(pos.immutable());
            else CLIENT_FIELD_POWER.remove(pos);
        }
        net.antopfr.coastersmagnetized.client.MagneticVisualRefresh.refresh(pos);
    }

    static void clearClientFieldPower() {
        synchronized (CLIENT_FIELD_POWER) {
            CLIENT_FIELD_POWER.clear();
        }
    }

    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTo(player);
    }

    private static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTo(player);
    }

    private static void syncTo(final ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new CoastersMagnetizedPowerPayload(BlockPos.ZERO, false, true));
        if (!(player.level() instanceof ServerLevel level)) return;
        final Map<BlockPos, Boolean> current;
        synchronized (SERVER_FIELD_POWER) {
            current = new HashMap<>(SERVER_FIELD_POWER.getOrDefault(level, Map.of()));
        }
        current.forEach((pos, powered) -> {
            if (powered) PacketDistributor.sendToPlayer(player,
                    new CoastersMagnetizedPowerPayload(pos, true, false));
        });
    }

    private static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 5L != 0L) return;
        final Set<BlockPos> anchors = new HashSet<>(MagnetizedAnchors.positionsWith(level, AnchorMode.MAGNET));
        anchors.addAll(MagnetizedAnchors.positionsWith(level, AnchorMode.BRAKE));

        final Map<BlockPos, Boolean> previous;
        synchronized (SERVER_FIELD_POWER) {
            previous = SERVER_FIELD_POWER.computeIfAbsent(level, ignored -> new HashMap<>());
        }
        previous.keySet().removeIf(pos -> {
            if (anchors.contains(pos)) return false;
            if (Boolean.TRUE.equals(previous.get(pos))) {
                PacketDistributor.sendToPlayersInDimension(level,
                        new CoastersMagnetizedPowerPayload(pos, false, false));
            }
            return true;
        });
        for (final BlockPos pos : anchors) {
            final boolean powered = MagCoastersMagnetizedCompat.fieldPowersAnchor(level, pos);
            final Boolean old = previous.put(pos.immutable(), powered);
            if (old == null || old != powered) {
                PacketDistributor.sendToPlayersInDimension(level,
                        new CoastersMagnetizedPowerPayload(pos, powered, false));
            }
        }
    }
}
