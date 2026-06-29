package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.dampener.DecelerateToHoverHandler;
import com.stonytark.magnetization.content.item.magnetized.MagHoeDowse;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Clears the per-player UUID-keyed transient maps held by item/effect handlers
 * ({@link GrappleTickHandler}, {@link DecelerateToHoverHandler},
 * {@link MagHoeDowse}) at the player-lifecycle transitions where their entity
 * is replaced or leaves. Without this:
 *
 * <ul>
 *   <li>a player who logs out mid-effect leaks an entry permanently (the only
 *       per-tick remover never runs for an absent player), so a long-lived
 *       server's maps grow unbounded;</li>
 *   <li>a player who dies mid-grapple-pull and respawns gets the pull RE-APPLIED
 *       to the new entity — the pull is keyed by the (stable) UUID but the
 *       {@code ServerPlayer} object is new on respawn — yanking them on spawn.</li>
 * </ul>
 *
 * Clearing on logout, respawn/return (PlayerEvent.Clone), dimension change, and
 * server stop covers every replacement/exit path.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class PlayerStateCleanupHandler {

    private PlayerStateCleanupHandler() {}

    private static void clearFor(final Player player) {
        final var id = player.getUUID();
        GrappleTickHandler.clear(id);
        DecelerateToHoverHandler.clear(id);
        MagHoeDowse.clear(id);
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        clearFor(event.getEntity());
    }

    /** Fires on respawn (death) and on return from the End — the player entity is
     *  replaced, so any pull/hover state keyed by UUID must not carry over. */
    @SubscribeEvent
    public static void onClone(final PlayerEvent.Clone event) {
        clearFor(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        clearFor(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        GrappleTickHandler.clearAll();
        DecelerateToHoverHandler.clearAll();
        MagHoeDowse.clearAll();
    }
}
