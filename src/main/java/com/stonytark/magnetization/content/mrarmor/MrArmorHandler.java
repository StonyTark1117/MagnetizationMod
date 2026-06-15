package com.stonytark.magnetization.content.mrarmor;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * MR (Magnetorheological) Liquid Armor. Two regimes:
 *
 * <ul>
 *   <li><b>In a magnetic field</b> — the fluid is held permanently rigid: the
 *       worn pieces stay hardened (refreshed every tick) and shrug off nearly ALL
 *       incoming damage (scales with pieces worn, full set ≈ 95%).</li>
 *   <li><b>Out of a field</b> — it's leather-light and only stiffens on a kinetic
 *       hit (physical/fall/blast), shaving 22.5%/piece off that hit (cap 90%).</li>
 * </ul>
 *
 * MR armor is never pulled by fields (see {@code FieldApplicator}) — it hardens instead.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MrArmorHandler {

    private static final float IMPACT_PER_PIECE = 0.225f;   // out-of-field, kinetic only
    private static final float IMPACT_MAX = 0.9f;
    private static final float FIELD_PER_PIECE = 0.30f;     // in-field, all damage
    private static final float FIELD_MAX = 0.95f;
    private static final long HARDEN_TICKS = 30L;           // rigid-look window after a trigger

    private MrArmorHandler() {}

    /** Keep worn MR armor visibly hardened the whole time the wearer is in a field. */
    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel server)) return;
        if (server.getGameTime() % 5L != 0L) return; // refresh a few times a second
        if (pieces(player) == 0) return;
        if (MagneticFields.isInField(server, player.position())) {
            hardenWorn(player, server.getGameTime() + HARDEN_TICKS);
        }
    }

    /** Same field-hardening for non-player wearers — e.g. a horse in MR barding. */
    @SubscribeEvent
    public static void onEntityTick(final net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) return;
        if (!(living.level() instanceof ServerLevel server)) return;
        if (server.getGameTime() % 5L != 0L) return;
        if (pieces(living) == 0) return;
        if (MagneticFields.isInField(server, living.position())) {
            hardenWorn(living, server.getGameTime() + HARDEN_TICKS);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event) {
        final LivingEntity living = event.getEntity();
        final int pieces = pieces(living);
        if (pieces == 0) return;

        final boolean inField = living.level() instanceof ServerLevel server
                && MagneticFields.isInField(server, living.position());

        if (inField) {
            // Constantly hardened: mitigate nearly all damage. Can't block the
            // unblockable (void / out-of-world / /kill).
            if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
            final float mit = Math.min(FIELD_MAX, FIELD_PER_PIECE * pieces);
            event.setAmount(event.getAmount() * (1.0f - mit));
        } else {
            // Out of a field: only kinetic impacts trigger the snap-rigid response.
            if (!isKinetic(event.getSource())) return;
            final float mit = Math.min(IMPACT_MAX, IMPACT_PER_PIECE * pieces);
            event.setAmount(event.getAmount() * (1.0f - mit));
        }
        hardenWorn(living, living.level().getGameTime() + HARDEN_TICKS);
    }

    /** An MR piece is either a player-worn MR armor item or MR horse barding. */
    private static boolean isMrPiece(final ItemStack stack) {
        return stack.getItem() instanceof MrLiquidArmorItem
                || stack.getItem() instanceof MrFluidHorseArmorItem;
    }

    private static int pieces(final LivingEntity living) {
        int n = 0;
        for (final ItemStack armor : living.getArmorSlots()) {
            if (isMrPiece(armor)) n++;
        }
        return n;
    }

    private static void hardenWorn(final LivingEntity living, final long until) {
        for (final ItemStack armor : living.getArmorSlots()) {
            if (isMrPiece(armor)) {
                armor.set(MagDataComponents.HARDENED_UNTIL.get(), until);
            }
        }
    }

    /** Physical / fall / blast — the impacts the fluid can stiffen against. */
    private static boolean isKinetic(final DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_PROJECTILE)
                || (source.getDirectEntity() instanceof LivingEntity
                    && !source.is(DamageTypeTags.BYPASSES_ARMOR));
    }
}
