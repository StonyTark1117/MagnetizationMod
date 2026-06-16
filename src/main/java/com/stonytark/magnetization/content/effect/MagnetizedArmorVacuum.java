package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.Lirm;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.api.EquippedArmor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Item-vacuum behavior for players wearing magnetized armor. Each tick, scans
 * nearby {@link ItemEntity}s and applies an impulse toward (SOUTH-net) or away
 * from (NORTH-net) the player. Range and strength scale linearly with the
 * count of currently-magnetized armor pieces; a full set (4 pieces) widens
 * the filter from "ferromagnetic only" to "every dropped item".
 *
 * <p><b>Why this lives here, not in {@code FieldApplicator}:</b> the field
 * applicator iterates emitter→entities. A magnetized player isn't an emitter
 * (no {@link com.stonytark.magnetization.api.MagneticFieldSource}); the vacuum
 * is the inverse — entity→entity. Cheaper as a player-tick than promoting
 * every player to a virtual field source on the registry.
 *
 * <p>Players themselves are already pulled by external emitters through
 * {@link com.stonytark.magnetization.physics.FieldApplicator}'s living-entity
 * pass — this class only adds the outbound effect (player magnetism pulls
 * items in), not the inbound (handled there).
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagnetizedArmorVacuum {

    /** Block radius added per magnetized armor piece. With 4 pieces the
     *  effective radius is 8 blocks — generous but not so wide that a
     *  mining player ends up sweeping items from outside their FOV. */
    private static final double RADIUS_PER_PIECE = 2.0d;

    /** Peak per-tick impulse applied to an item right next to the player.
     *  Tuned so a single piece can still measurably tug an item against
     *  gravity (~0.04 m/s²/tick); a full set produces a clear, snappy
     *  vacuum without launching items past the player. */
    private static final double MAX_IMPULSE_PER_TICK = 0.18d;

    /** Distance falloff exponent. 1.5 keeps the tug strong near the player
     *  and falls off cleanly to 0 at {@link #RADIUS_PER_PIECE} × pieces. */
    private static final double FALLOFF_POWER = 1.5d;

    /** Below this LIRM strength a magnetized armor piece is treated as
     *  expired and excluded from the count. Matches the behavior in
     *  {@link com.stonytark.magnetization.physics.FieldApplicator}. */
    private static final double LIRM_DEAD_THRESHOLD = 0.001d;

    private MagnetizedArmorVacuum() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (player.isSpectator() || player.isDeadOrDying()) return;

        // Throttle the item scan: only sweep every N ticks. The per-pull impulse
        // is multiplied by the same N below, so the average force (and feel) is
        // unchanged while we do far fewer entity scans.
        final int interval = com.stonytark.magnetization.config.MagConfig.armorVacuumTicks();
        if (level.getGameTime() % interval != 0L) return;

        // Sum signed polarity contributions of every currently-magnetized
        // armor piece. NORTH = +1, SOUTH = -1. The net sign decides
        // attract vs repel; the absolute piece count drives radius +
        // strength scaling and the full-set filter widening.
        final long now = level.getGameTime();
        int pieces = 0;
        int netPole = 0;
        for (final ItemStack armor : EquippedArmor.all(player)) {
            if (!armor.is(MagTags.METAL_ARMOR)) continue;
            final MagneticPolarity pol = armor.get(MagDataComponents.ARMOR_POLARITY.get());
            if (pol == null || pol == MagneticPolarity.NONE) continue;
            if (Lirm.strength(armor, now) <= LIRM_DEAD_THRESHOLD) continue;
            pieces++;
            netPole += pol.sign();
        }
        if (pieces == 0 || netPole == 0) return;

        // Item entities default to NORTH (see FieldApplicator#polarityOf);
        // opposites attract, so SOUTH-net player pulls items inward.
        final boolean attract = netPole < 0;
        final double radius = pieces * RADIUS_PER_PIECE;
        final boolean fullSet = pieces >= 4;

        final Vec3 origin = player.position().add(0, player.getBbHeight() * 0.5d, 0);
        final AABB box = AABB.ofSize(origin, 2 * radius, 2 * radius, 2 * radius);
        // Filter during the chunk-section traversal rather than materialising every
        // item entity in the box and filtering after. Skip the player's own
        // freshly-dropped items (40-tick pickup delay) so a Q-toss isn't yanked
        // straight back; a partial set only acts on ferromagnetic items so the
        // player isn't sweeping up grass and bones (a full set vacuums everything).
        final List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                item -> !item.hasPickUpDelay()
                        && (fullSet || item.getItem().is(MagTags.FERROMAGNETIC_ITEMS)));
        for (final ItemEntity item : nearby) {
            final Vec3 delta = item.position().subtract(origin);
            final double dist = delta.length();
            if (dist < 0.1d || dist > radius) continue;

            // Linear-ish falloff so the impulse is strong near the player
            // and disappears at the edge of the radius.
            final double falloff = Math.pow(1.0d - dist / radius, FALLOFF_POWER);
            final double pieceScale = Math.min(1.0d, pieces / 4.0d);
            final double mag = MAX_IMPULSE_PER_TICK * interval * falloff * (0.25d + 0.75d * pieceScale);
            final Vec3 unit = delta.scale(1.0d / dist);
            final Vec3 impulse = attract ? unit.scale(-mag) : unit.scale(mag);
            item.setDeltaMovement(item.getDeltaMovement().add(impulse));
            item.hurtMarked = true;
        }
    }
}
