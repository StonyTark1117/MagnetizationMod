package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns ambient particles around active emitters. Subscribes to
 * {@link ActiveEmitterScanner}'s shared per-tick walk; the actual particle spawn
 * is throttled to every {@link #SPAWN_INTERVAL_TICKS} ticks per emitter.
 *
 * <p>Class load is forced from {@code MagClientRegistration#onClientSetup}
 * (calling {@link #touch()}). NeoForge 21.1 rejects {@code @EventBusSubscriber}
 * on classes with no {@code @SubscribeEvent} methods, so we don't use that
 * annotation here.
 */
public final class ClientEmitterEffects {

    private static final int SPAWN_INTERVAL_TICKS = 4;
    private static int tickCounter = 0;
    private static boolean wired = false;

    static {
        wire();
    }

    private ClientEmitterEffects() {}

    /** No-op call-site that forces class load so the static {@code wire()} fires. */
    public static void touch() {}

    private static void wire() {
        if (wired) return;
        wired = true;
        ActiveEmitterScanner.onActiveEmitter(ClientEmitterEffects::onActive);
        ActiveEmitterScanner.onScanComplete(() -> tickCounter++);
    }

    private static void onActive(final BlockPos pos, final MagneticField field) {
        if (tickCounter % SPAWN_INTERVAL_TICKS != 0) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        spawnFieldParticles(mc.level, pos, field, ThreadLocalRandom.current());
    }

    private static void spawnFieldParticles(
            final Level level, final BlockPos pos, final MagneticField field, final ThreadLocalRandom rng
    ) {
        final Vec3 origin = pos.getCenter();
        for (int i = 0; i < 2; i++) {
            final Vec3 offset = switch (field.shape()) {
                case OMNIDIRECTIONAL -> new Vec3(
                        rng.nextDouble(-0.5, 0.5),
                        rng.nextDouble(-0.5, 0.5),
                        rng.nextDouble(-0.5, 0.5));
                case DIRECTIONAL, CONICAL -> field.axis().scale(rng.nextDouble(0.2, 1.5))
                        .add(rng.nextDouble(-0.2, 0.2), rng.nextDouble(-0.2, 0.2), rng.nextDouble(-0.2, 0.2));
            };
            final Vec3 spawn = origin.add(offset);
            final SimpleParticleType type = field.polarity() == MagneticPolarity.NORTH
                    ? MagParticles.MAG_NORTH.get() : MagParticles.MAG_SOUTH.get();
            level.addParticle(
                    type,
                    spawn.x, spawn.y, spawn.z,
                    field.axis().x * 0.05, field.axis().y * 0.05, field.axis().z * 0.05);
        }
    }
}
