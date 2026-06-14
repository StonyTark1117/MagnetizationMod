package com.stonytark.magnetization.content.sail;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Drives the Alfvén Ribbon Backpack's passive forward boost. While the wearer
 * is gliding with the backpack in the chest slot and the environment has a
 * usable current — daylight high above the surface, or anywhere in the End —
 * it adds a small, capped forward velocity each tick along the look direction,
 * so the player cruises indefinitely without firework rockets.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AlfvenBackpackHandler {

    private static final double BASE_ACCEL = 0.08;       // forward push per tick at the low-altitude floor
    private static final double BASE_MAX_SPEED = 1.4;     // cruise cap along the look vector (×altitude factor)

    private AlfvenBackpackHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (player.level().isClientSide || !player.isFallFlying()) return;
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof AlfvenBackpackItem)) return;

        // The ribbons catch a current in daylight, or anywhere in the End.
        final boolean inEnd = player.level().dimension() == Level.END;
        if (!inEnd && !player.level().isDay()) return;

        // Stronger the higher you fly: 0.7 near sea level → ~2.4 in the high sky.
        final double altFactor = inEnd ? 1.6
                : net.minecraft.util.Mth.clamp(0.7 + (player.getY() - 64) / 150.0, 0.7, 2.4);

        final Vec3 look = player.getLookAngle();
        Vec3 dm = player.getDeltaMovement();

        // Forward thrust toward the look direction, up to an altitude-scaled cap.
        if (dm.dot(look) < BASE_MAX_SPEED * altFactor) {
            dm = dm.add(look.scale(BASE_ACCEL * altFactor));
        }
        // Lift assist: ease the vertical velocity up to a floor so the sail keeps
        // you airborne in daylight — gentle level glide low down, a real climb up high.
        final double minY = -0.04 + 0.035 * altFactor; // altF 0.7 → -0.015 (slow sink); altF 2.4 → +0.044 (climb)
        if (dm.y < minY) {
            dm = new Vec3(dm.x, Math.min(minY, dm.y + 0.06), dm.z);
        }

        player.setDeltaMovement(dm);
        player.hurtMarked = true; // force the server to sync the new velocity to the client
        player.resetFallDistance();
    }
}
