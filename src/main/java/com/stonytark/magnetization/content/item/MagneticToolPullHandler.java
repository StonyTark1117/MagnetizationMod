package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
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
 * Personal item magnet: a magnetized tool/weapon held in either hand or worn
 * on the player attracts dropped {@link ItemEntity}s tagged
 * {@code #magnetization:ferromagnetic} toward the player. Range is tied to
 * the polarity strength so players can stack tools for stronger pull.
 *
 * <p>This is intentionally separate from {@link com.stonytark.magnetization.physics.FieldApplicator}
 * — emitters apply force at a world position; this applies force toward the
 * <i>holder</i>. Like-pole tool vs. north-default item entities means SOUTH
 * tools attract, NORTH tools repel.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagneticToolPullHandler {

    /** Pull radius (blocks) per magnetized tool the player carries. */
    private static final double RADIUS_PER_TOOL = 4.0d;
    /** Per-tick velocity boost applied to each pulled item entity, scaled by inverse distance. */
    private static final double PULL_VELOCITY = 0.08d;
    /** Upper bound on per-tick velocity injected — prevents tunneling at very close range. */
    private static final double MAX_PER_TICK = 0.6d;

    private MagneticToolPullHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Sum the polarity contributions of every magnetized metal tool the
        // player currently carries — main hand, off-hand, every armor slot
        // (some quivers/utility belts park tools there). Each tool counts
        // as one "magnet": +1 NORTH, -1 SOUTH, 0 if not magnetized or not in
        // the metal_tools tag.
        int netPolarity = 0;
        int magnetCount = 0;
        for (final ItemStack stack : iterMagnetCarriers(player)) {
            if (!stack.is(MagTags.METAL_TOOLS)) continue;
            final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
            if (pol == null || pol == MagneticPolarity.NONE) continue;
            netPolarity += pol.sign();
            magnetCount++;
        }
        if (magnetCount == 0 || netPolarity == 0) return;

        final double radius = RADIUS_PER_TOOL * magnetCount;
        final Vec3 playerPos = player.position().add(0, player.getBbHeight() * 0.5d, 0);
        final AABB box = AABB.ofSize(playerPos, 2 * radius, 2 * radius, 2 * radius);
        final List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> !e.getItem().isEmpty()
                        && e.getItem().is(MagTags.FERROMAGNETIC_ITEMS)
                        && e.distanceToSqr(playerPos) <= radius * radius);

        // SOUTH net polarity attracts (item entities default to NORTH);
        // NORTH net polarity repels.
        final double sign = netPolarity > 0 ? -1.0d : 1.0d;

        for (final ItemEntity item : nearby) {
            final Vec3 toward = playerPos.subtract(item.position()).normalize();
            final double distance = item.distanceTo(player);
            // Inverse-distance falloff with a soft floor so close items don't NaN.
            final double scale = Math.min(MAX_PER_TICK,
                    PULL_VELOCITY * Math.max(1.0d, distance) / Math.max(1.0d, distance * distance));
            item.setDeltaMovement(item.getDeltaMovement().add(toward.scale(sign * scale)));
            item.hasImpulse = true;
        }
    }

    /** Iterate the slots a "carried" tool can sit in: both hands and all armor. */
    private static Iterable<ItemStack> iterMagnetCarriers(final ServerPlayer player) {
        final List<ItemStack> out = new java.util.ArrayList<>(6);
        out.add(player.getMainHandItem());
        out.add(player.getOffhandItem());
        EquippedArmor.all(player).forEach(out::add);
        return out;
    }
}
