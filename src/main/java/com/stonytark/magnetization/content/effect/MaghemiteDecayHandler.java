package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Sweeps the player's inventory once every {@link #SWEEP_INTERVAL_TICKS} ticks
 * and converts aged magnetite items into their maghemite equivalents — the
 * real-world oxidation of Fe3O4 to γ-Fe2O3 happens slowly in moist air, so a
 * magnetite stash that sits in inventory for a Minecraft day or so quietly
 * weathers into maghemite. Players who use their magnetite stay magnetite-rich;
 * stockpilers end up with a bunch of maghemite (still useful, just different).
 *
 * <p>Stamping policy: items get tagged on first observation rather than on
 * pickup — keeps the handler self-contained without needing a second event
 * subscription. The clock "starts" the first sweep that sees the stack.
 *
 * <p>Decay scope: only the loose forms ({@code raw_magnetite},
 * {@code magnetite_ingot}) convert. Block forms are intentionally exempt — a
 * "your block of magnetite just rusted" surprise would be punishing for
 * builders.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MaghemiteDecayHandler {

    /** Inventory sweep cadence. 200 ticks = 10s — granular enough that the
     *  player sees the conversion message within a beat of the threshold,
     *  cheap enough to be inconsequential at scale. */
    private static final int SWEEP_INTERVAL_TICKS = 200;

    /** Fallback decay window used when the config hasn't loaded yet (early
     *  load, unit tests). Live duration comes from
     *  {@code MagConfig.MAGNETITE_OXIDATION_TICKS}, default 168000 ticks
     *  (1 in-game week). */
    private static final long DEFAULT_DECAY_TICKS = 168000L;

    private static boolean enabled() {
        try { return com.stonytark.magnetization.config.MagConfig.MAGNETITE_OXIDATION_ENABLED.get(); }
        catch (final Throwable t) { return false; }
    }

    private static long decayTicks() {
        try { return com.stonytark.magnetization.config.MagConfig.MAGNETITE_OXIDATION_TICKS.get(); }
        catch (final Throwable t) { return DEFAULT_DECAY_TICKS; }
    }

    private MaghemiteDecayHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if ((level.getGameTime() % SWEEP_INTERVAL_TICKS) != 0L) return;

        final Inventory inv = player.getInventory();
        final long now = level.getGameTime();
        boolean anyConverted = false;

        anyConverted |= sweepList(inv.items, now);
        anyConverted |= sweepList(inv.armor, now);
        anyConverted |= sweepList(inv.offhand, now);

        if (anyConverted) {
            player.displayClientMessage(
                    Component.translatable("maghemite.magnetization.oxidized")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    /** Walks an inventory slot list. Stamps unaged magnetite stacks with the
     *  current tick, and converts already-aged stacks past {@link #DECAY_TICKS}
     *  to their maghemite equivalent. Mutates the underlying list via
     *  {@link java.util.List#set} so the player's inventory updates in place.
     *  Returns true if any conversion happened. */
    private static boolean sweepList(final java.util.List<ItemStack> slots, final long now) {
        ensureMapInitialised();
        final long decay = decayTicks();
        boolean converted = false;
        for (int i = 0; i < slots.size(); i++) {
            final ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            final Item replacement = MAGNETITE_TO_MAGHEMITE.get(stack.getItem());
            if (replacement == null) continue;

            final Long stampedAt = stack.get(MagDataComponents.MAGNETITE_OXIDATION_AGE.get());
            if (stampedAt == null) {
                stack.set(MagDataComponents.MAGNETITE_OXIDATION_AGE.get(), now);
                continue;
            }
            if ((now - stampedAt) < decay) continue;

            // Convert in place, preserving count. New stack has no oxidation
            // tag (maghemite is the terminal state in this model).
            slots.set(i, new ItemStack(replacement, stack.getCount()));
            converted = true;
        }
        return converted;
    }

    /** Source → destination map for in-place oxidation. Built lazily on first
     *  sweep because {@code DeferredItem.get()} requires the registry to have
     *  resolved (post-FMLCommonSetup). */
    private static final Map<Item, Item> MAGNETITE_TO_MAGHEMITE = new HashMap<>();

    private static synchronized void ensureMapInitialised() {
        if (!MAGNETITE_TO_MAGHEMITE.isEmpty()) return;
        MAGNETITE_TO_MAGHEMITE.put(MagItems.RAW_MAGNETITE.get(), MagItems.RAW_MAGHEMITE.get());
        MAGNETITE_TO_MAGHEMITE.put(MagItems.MAGNETITE_INGOT.get(), MagItems.MAGHEMITE_INGOT.get());
    }
}
