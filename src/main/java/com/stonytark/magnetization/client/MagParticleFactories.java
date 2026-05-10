package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client factories for our custom particles. Each factory wraps a sprite set
 * (the particle JSON descriptor's "textures" array) and constructs a short-lived
 * {@link TextureSheetParticle} with a polarity-tinted color.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class MagParticleFactories {

    private MagParticleFactories() {}

    @SubscribeEvent
    public static void register(final RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(MagParticles.MAG_NORTH.get(), sprites -> new TintedFactory(sprites, 0.78f, 0.22f, 0.31f));
        event.registerSpriteSet(MagParticles.MAG_SOUTH.get(), sprites -> new TintedFactory(sprites, 0.22f, 0.47f, 0.78f));
    }

    private record TintedFactory(SpriteSet sprites, float r, float g, float b) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(
                final SimpleParticleType type, final ClientLevel level,
                final double x, final double y, final double z,
                final double dx, final double dy, final double dz
        ) {
            final TintedParticle p = new TintedParticle(level, x, y, z, dx, dy, dz);
            p.setColor(r, g, b);
            p.pickSprite(sprites);
            return p;
        }
    }

    /**
     * Glow-style sheet particle: short-lived, accelerates along its initial
     * velocity, fades alpha out near the end of life.
     */
    private static final class TintedParticle extends TextureSheetParticle {
        private TintedParticle(final ClientLevel level,
                               final double x, final double y, final double z,
                               final double dx, final double dy, final double dz) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = 12 + this.random.nextInt(6);
            this.quadSize = 0.12f;
            this.gravity = 0.0f;
            this.hasPhysics = false;
        }

        @Override
        public net.minecraft.client.particle.ParticleRenderType getRenderType() {
            return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = Math.max(0f, 1f - (float) this.age / (float) this.lifetime);
        }
    }
}
