package com.stonytark.magnetization.client;

import com.stonytark.magnetization.worldgen.AnomalyBiome;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Hijacks the {@code minecraft:angle} item property on the vanilla compass so
 * the needle spins wildly whenever the holder is standing inside the Anomaly
 * biome. Outside the anomaly the original property function runs unmodified —
 * lodestone tracking, spawn pointing, off-world wobble all behave as vanilla.
 *
 * <p>Implementation note: vanilla's {@code ItemProperties.bootstrap()} runs
 * during {@code Minecraft.<init>}, so by {@code FMLClientSetupEvent} the
 * compass already has its original angle function registered. We fetch it,
 * cache it, and overwrite the slot with our wrapper. Calling
 * {@code ItemProperties.register} again with the same key replaces the entry,
 * which is exactly what we want.
 */
public final class AnomalyCompassSpin {

    private AnomalyCompassSpin() {}

    public static void install() {
        final ResourceLocation angleId = ResourceLocation.withDefaultNamespace("angle");
        final @Nullable ItemPropertyFunction original =
                ItemProperties.getProperty(Items.COMPASS.getDefaultInstance(), angleId);
        ItemProperties.register(Items.COMPASS, angleId, (stack, level, entity, seed) -> {
            if (entity != null && level != null) {
                if (level.getBiome(entity.blockPosition()).is(AnomalyBiome.KEY)) {
                    // Smooth fast spin with a per-tick jitter ride on top. Compass
                    // model has 32 frames; ~1.6 rotations/sec keeps the needle
                    // visibly chaotic without flickering frame-to-frame.
                    final long t = level.getGameTime();
                    final double base = (t * 0.08d) % 1.0d;
                    final double jitter = (entity.getRandom().nextFloat() - 0.5f) * 0.15d;
                    final double v = base + jitter;
                    return (float) (v - Math.floor(v));
                }
            }
            return original != null ? original.call(stack, level, entity, seed) : 0.0f;
        });
    }
}
