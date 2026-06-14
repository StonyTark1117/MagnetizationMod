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

    private static final double ACCEL = 0.045;          // forward velocity added per tick
    private static final double MAX_GLIDE_SPEED = 1.05;  // cap along the look vector
    private static final int HIGH_ALTITUDE = 120;        // daytime boost needs this height

    private AlfvenBackpackHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(final PlayerTickEvent.Post event) {
        final Player player = event.getEntity();
        if (player.level().isClientSide || !player.isFallFlying()) return;
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof AlfvenBackpackItem)) return;

        final boolean inEnd = player.level().dimension() == Level.END;
        final boolean dayHigh = !inEnd && player.level().isDay() && player.getY() > HIGH_ALTITUDE;
        if (!inEnd && !dayHigh) return;

        final Vec3 look = player.getLookAngle();
        final Vec3 dm = player.getDeltaMovement();
        if (dm.dot(look) >= MAX_GLIDE_SPEED) return; // already cruising at speed
        player.setDeltaMovement(dm.add(look.scale(ACCEL)));
        player.hurtMarked = true; // force the server to sync the new velocity to the client
    }
}
