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

    private MrArmorHandler() {}

    /** Keep worn MR armor visibly hardened the whole time the wearer is in a field. */
    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel server)) return;
        if (server.getGameTime() % com.stonytark.magnetization.config.MagConfig.mrArmorRefreshTicks() != 0L) return;
        final boolean inField = MagneticFields.isInField(server, player.position());
        if (inField) {
            final long until = server.getGameTime() + com.stonytark.magnetization.config.MagConfig.mrArmorHardenTicks();
            if (pieces(player) > 0) hardenWorn(player, until);
            // An MR-fluid tool held in hand also hardens while in a field — same as
            // the armor — so a wielded tool reads rigid, not just on use.
            hardenHeldTool(player.getMainHandItem(), until);
            hardenHeldTool(player.getOffhandItem(), until);
        }
    }

    /** Stamp the hardened window onto a held MR-fluid tool (no-op for anything else). */
    private static void hardenHeldTool(final ItemStack stack, final long until) {
        if (stack.getItem() instanceof com.stonytark.magnetization.content.mrtools.MrFluidTools.Marker) {
            stack.set(MagDataComponents.HARDENED_UNTIL.get(), until);
        }
    }

    /** Same field-hardening for non-player wearers — e.g. a horse in MR barding. */
    @SubscribeEvent
    public static void onEntityTick(final net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) return;
        if (!(living.level() instanceof ServerLevel server)) return;
        if (server.getGameTime() % com.stonytark.magnetization.config.MagConfig.mrArmorRefreshTicks() != 0L) return;
        if (pieces(living) == 0) return;
        if (MagneticFields.isInField(server, living.position())) {
            hardenWorn(living, server.getGameTime() + com.stonytark.magnetization.config.MagConfig.mrArmorHardenTicks());
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
            final float mit = Math.min(com.stonytark.magnetization.config.MagConfig.mrArmorFieldMax(),
                    com.stonytark.magnetization.config.MagConfig.mrArmorFieldPerPiece() * pieces);
            event.setAmount(event.getAmount() * (1.0f - mit));
        } else {
            // Out of a field: only kinetic impacts trigger the snap-rigid response.
            if (!isKinetic(event.getSource())) return;
            final float mit = Math.min(com.stonytark.magnetization.config.MagConfig.mrArmorImpactMax(),
                    com.stonytark.magnetization.config.MagConfig.mrArmorImpactPerPiece() * pieces);
            event.setAmount(event.getAmount() * (1.0f - mit));
        }
        hardenWorn(living, living.level().getGameTime() + com.stonytark.magnetization.config.MagConfig.mrArmorHardenTicks());
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
        // getArmorSlots() omits the BODY slot — where horse barding (and wolf/llama
        // body armor) lives — so count it explicitly.
        if (isMrPiece(living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY))) n++;
        return n;
    }

    private static void hardenWorn(final LivingEntity living, final long until) {
        for (final ItemStack armor : living.getArmorSlots()) {
            if (isMrPiece(armor)) {
                armor.set(MagDataComponents.HARDENED_UNTIL.get(), until);
            }
        }
        final ItemStack body = living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.BODY);
        if (isMrPiece(body)) {
            body.set(MagDataComponents.HARDENED_UNTIL.get(), until);
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
