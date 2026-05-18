package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagParticles;
import com.stonytark.magnetization.api.EquippedArmor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Client-side visual feedback for magnetized armor and tools — emits a tiny
 * polarity-tinted particle on each entity that's carrying magnetized gear.
 * Without this, multiplayer can't tell magnetized players from regular ones
 * (the tooltip is the only indicator, and you can't read tooltips on other
 * players' equipment from a distance).
 *
 * <p>Throttled to once every {@link #INTERVAL} ticks so the chat doesn't
 * become a particle storm in a server full of magnetized players. Iterates
 * loaded entities client-side; works only for entities the client knows
 * about.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class MagnetizedAuraEffect {

    /** Tick interval between aura particle emissions. 8 ticks ≈ 2.5 Hz. */
    private static final int INTERVAL = 8;
    /** Per-piece offset from the entity's center used as the spawn position
     *  (small jitter; the particles drift from there). */
    private static final double JITTER = 0.4d;

    private MagnetizedAuraEffect() {}

    @SubscribeEvent
    public static void onClientTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level)) return;
        if (level.getGameTime() % INTERVAL != 0) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (final var entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            int northCount = 0, southCount = 0;
            for (final ItemStack stack : EquippedArmor.all(living)) {
                final MagneticPolarity p = stack.is(MagTags.METAL_ARMOR)
                        ? stack.get(MagDataComponents.ARMOR_POLARITY.get()) : null;
                if (p == MagneticPolarity.NORTH) northCount++;
                else if (p == MagneticPolarity.SOUTH) southCount++;
            }
            if (living instanceof Player player) {
                final ItemStack main = player.getMainHandItem();
                final ItemStack off = player.getOffhandItem();
                for (final ItemStack stack : new ItemStack[] { main, off }) {
                    if (!stack.is(MagTags.METAL_TOOLS)) continue;
                    final MagneticPolarity p = stack.get(MagDataComponents.ARMOR_POLARITY.get());
                    if (p == MagneticPolarity.NORTH) northCount++;
                    else if (p == MagneticPolarity.SOUTH) southCount++;
                }
            }
            if (northCount == 0 && southCount == 0) continue;
            spawnAura(level, living, northCount, southCount);
        }
    }

    private static void spawnAura(final net.minecraft.world.level.Level level, final LivingEntity entity,
                                   final int northCount, final int southCount) {
        final double cx = entity.getX();
        final double cy = entity.getY() + entity.getBbHeight() * 0.6;
        final double cz = entity.getZ();
        final var rng = entity.getRandom();
        final ParticleOptions north = MagParticles.MAG_NORTH.get();
        final ParticleOptions south = MagParticles.MAG_SOUTH.get();
        for (int i = 0; i < northCount; i++) {
            level.addParticle(north,
                    cx + (rng.nextDouble() - 0.5) * 2 * JITTER,
                    cy + (rng.nextDouble() - 0.5) * 2 * JITTER,
                    cz + (rng.nextDouble() - 0.5) * 2 * JITTER,
                    0, 0.01, 0);
        }
        for (int i = 0; i < southCount; i++) {
            level.addParticle(south,
                    cx + (rng.nextDouble() - 0.5) * 2 * JITTER,
                    cy + (rng.nextDouble() - 0.5) * 2 * JITTER,
                    cz + (rng.nextDouble() - 0.5) * 2 * JITTER,
                    0, 0.01, 0);
        }
    }
}
