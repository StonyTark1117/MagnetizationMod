package com.stonytark.magnetization.api;

import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Constants + helpers for Lightning Induced Remnant Magnetism — the addon's first
 * implementation of <i>temporary magnetism</i>. A lightning strike stamps a
 * polarity onto a metal piece, and the strength decays linearly from full
 * (at the strike tick) to zero (after {@link #DURATION_TICKS}). Once at zero,
 * the {@link com.stonytark.magnetization.content.effect.LirmDecayHandler}
 * sweeps the polarity + LIRM marker off the item.
 *
 * <p>Items that are permanently magnetized (e.g. stamped via the Electromagnet
 * GUI) never have the {@link MagDataComponents#LIRM_CREATED_AT} component, so
 * {@link #strength(ItemStack, long)} returns {@code 1.0} for them — full,
 * non-decaying strength.
 */
public final class Lirm {

    /** Real-time duration of a fresh LIRM stamp. 20 minutes feels right: long
     *  enough that a lightning-storm windfall is meaningful, short enough that
     *  the player doesn't permanently dodge re-stamping via the Electromagnet GUI. */
    public static final int DURATION_TICKS = 24_000; // 20 minutes

    private Lirm() {}

    /**
     * Current LIRM strength on a stack as a multiplier in [0.0, 1.0].
     * <ul>
     *   <li>{@code 1.0} — no LIRM component (item is either unmagnetized or
     *       permanently magnetized via the Electromagnet GUI).</li>
     *   <li>{@code (0, 1)} — actively decaying; multiplier scales the
     *       contribution this item makes to pulls + signature abilities.</li>
     *   <li>{@code 0.0} — fully decayed; the decay handler will clean it up
     *       on its next sweep.</li>
     * </ul>
     */
    public static double strength(final ItemStack stack, final long currentTick) {
        final Long createdAt = stack.get(MagDataComponents.LIRM_CREATED_AT.get());
        if (createdAt == null) return 1.0d;
        return strengthForElapsed(currentTick - createdAt);
    }

    /** Pure-math decay helper extracted so unit tests can exercise the curve
     *  without needing a real {@link ItemStack} + DataComponents registry. */
    public static double strengthForElapsed(final long elapsed) {
        if (elapsed < 0) return 1.0d; // clock skew / save-load edge
        if (elapsed >= DURATION_TICKS) return 0.0d;
        return 1.0d - (elapsed / (double) DURATION_TICKS);
    }

    /** Convenience: strength scaled into a level-aware call. */
    public static double strength(final ItemStack stack, final Level level) {
        return strength(stack, level.getGameTime());
    }

    /** True iff the item carries an active LIRM stamp (i.e. has the component AND
     *  isn't yet fully decayed). Useful as a "this magnetism is temporary" flag. */
    public static boolean isTemporary(final ItemStack stack, final long currentTick) {
        final Long createdAt = stack.get(MagDataComponents.LIRM_CREATED_AT.get());
        if (createdAt == null) return false;
        return isTemporaryForElapsed(currentTick - createdAt);
    }

    /** Pure-math counterpart to {@link #isTemporary(ItemStack, long)}; returns
     *  true iff a stamp at age {@code elapsed} ticks is still active. */
    public static boolean isTemporaryForElapsed(final long elapsed) {
        return elapsed >= 0 && elapsed < DURATION_TICKS;
    }

    /** Stamp {@code stack} with a fresh LIRM marker at the current game tick. The
     *  caller is responsible for also setting {@link MagDataComponents#ARMOR_POLARITY}
     *  to the actual polarity — this only carries the decay timer. */
    public static void stamp(final ItemStack stack, final long currentTick) {
        stack.set(MagDataComponents.LIRM_CREATED_AT.get(), currentTick);
    }

    /** Remove both the LIRM marker and the paired polarity stamp. Called by the
     *  decay handler when the elapsed time hits {@link #DURATION_TICKS}. */
    public static void clear(final ItemStack stack) {
        stack.remove(MagDataComponents.LIRM_CREATED_AT.get());
        stack.remove(MagDataComponents.ARMOR_POLARITY.get());
    }
}
