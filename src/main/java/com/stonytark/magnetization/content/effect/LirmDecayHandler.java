package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Sweeps the player's inventory once every {@link #SWEEP_INTERVAL_TICKS} for items
 * whose LIRM stamp has fully decayed, clearing both the polarity and the LIRM
 * marker. The next-tick {@link Lirm#strength(ItemStack, long)} read will return
 * {@code 1.0} for cleared items because the marker is gone — i.e. they revert
 * to "not magnetic" rather than "permanent magnetic," which is the entire point
 * of temporary magnetism.
 *
 * <p>Notification: the player gets a chat message the first sweep after the decay
 * completes. We don't bother detecting which specific item lost its stamp —
 * that'd require comparing snapshots, and "your gear lost its lightning charge"
 * is enough player-facing info.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class LirmDecayHandler {

    /** How often to walk the inventory looking for expired LIRM stamps.
     *  100 ticks = 5s — granular enough for the player to see the notification,
     *  cheap enough not to matter. */
    private static final int SWEEP_INTERVAL_TICKS = 100;

    private LirmDecayHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() % SWEEP_INTERVAL_TICKS) != 0L) return;

        final long now = level.getGameTime();
        boolean anyCleared = false;

        for (final ItemStack stack : player.getInventory().items) {
            if (clearIfExpired(stack, now)) anyCleared = true;
        }
        for (final ItemStack stack : player.getInventory().armor) {
            if (clearIfExpired(stack, now)) anyCleared = true;
        }
        for (final ItemStack stack : player.getInventory().offhand) {
            if (clearIfExpired(stack, now)) anyCleared = true;
        }

        if (anyCleared) {
            player.displayClientMessage(
                    Component.translatable("lirm.magnetization.faded")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    /** @return true if a previously-stamped item just lost its LIRM. */
    private static boolean clearIfExpired(final ItemStack stack, final long now) {
        if (stack.isEmpty()) return false;
        final Long createdAt = stack.get(MagDataComponents.LIRM_CREATED_AT.get());
        if (createdAt == null) return false;
        if ((now - createdAt) < Lirm.DURATION_TICKS) return false;
        Lirm.clear(stack);
        return true;
    }
}
