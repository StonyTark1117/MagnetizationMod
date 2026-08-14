package com.stonytark.magnetization.content.jet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Client-side exhaust shared by the ship engines. A block-entity ticker drives
 * this instead of {@code animateTick}: random display ticks are too sparse to
 * read as a continuous plume, especially while the camera follows a moving ship.
 */
final class ThrusterPlume {

    private static final int COOLED_FUSION_COLOUR = 0xB8F4FF;

    enum Style {
        MHD(2, 1.15d, 0.075d, 0.16d, 1),
        MICRO(3, 1.45d, 0.12d, 0.20d, 1),
        ION(3, 1.90d, 0.065d, 0.24d, 1),
        FUSION(2, 1.65d, 0.14d, 0.22d, 3);

        final int samples;
        final double length;
        final double radius;
        final double speed;
        final int tickDivisor;

        Style(final int samples, final double length, final double radius,
              final double speed, final int tickDivisor) {
            this.samples = samples;
            this.length = length;
            this.radius = radius;
            this.speed = speed;
            this.tickDivisor = tickDivisor;
        }
    }

    private ThrusterPlume() {}

    static void tick(final Level level, final BlockPos pos, final BlockState state,
                     final Style style, final int colour) {
        tick(level, pos, state, style, colour, 1);
    }

    static void tick(final Level level, final BlockPos pos, final BlockState state,
                     final Style style, final int colour, final int nozzleCount) {
        if (!level.isClientSide || !state.hasProperty(BlockStateProperties.LIT)
                || !state.getValue(BlockStateProperties.LIT)
                || !state.hasProperty(DirectionalBlock.FACING)) return;

        final RandomSource random = level.random;
        // A large Fusion panel has one nozzle per interior cell. Sampling each
        // cell keeps the whole face alive while capping the expected panel-wide
        // dust load at roughly 24 particles/tick even at extreme config sizes.
        final int divisor = samplingDivisor(style, nozzleCount);
        if (divisor > 1 && random.nextInt(divisor) != 0) return;

        final Direction exhaust = state.getValue(DirectionalBlock.FACING);
        final Vector3f rgb = colourVector(colour);
        for (int i = 0; i < style.samples; i++) {
            final double reach = random.nextDouble() * style.length;
            final double width = style.radius * (0.35d + reach / style.length);
            final double lateralA = (random.nextDouble() * 2.0d - 1.0d) * width;
            final double lateralB = (random.nextDouble() * 2.0d - 1.0d) * width;
            final Vec3 point = plumePoint(pos, exhaust, reach, lateralA, lateralB);
            final Vec3 velocity = plumeVelocity(exhaust, style.speed,
                    lateralA * 0.08d, lateralB * 0.08d);

            level.addParticle(new DustParticleOptions(rgb, particleScale(style)),
                    point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
            addAccent(level, style, random, point, velocity);
        }
    }

    static int samplingDivisor(final Style style, final int nozzleCount) {
        if (style != Style.FUSION) return style.tickDivisor;
        final long panelSamples = (long) Math.max(1, nozzleCount) * style.samples;
        final long capped = Math.max(style.tickDivisor, (panelSamples + 23L) / 24L);
        return (int) Math.min(Integer.MAX_VALUE, capped);
    }

    /**
     * Adds a pale coolant-mist sheath around an already-running Fusion plume.
     * The caller gates this on synchronized cooling state; the normal LIT guard
     * here ensures a delayed BE update can never leave steam on an idle panel.
     */
    static void tickCooledFusion(final Level level, final BlockPos pos, final BlockState state,
                                 final int nozzleCount) {
        if (!level.isClientSide || !state.hasProperty(BlockStateProperties.LIT)
                || !state.getValue(BlockStateProperties.LIT)
                || !state.hasProperty(DirectionalBlock.FACING)) return;

        final RandomSource random = level.random;
        final int divisor = cooledSamplingDivisor(nozzleCount);
        if (divisor > 1 && random.nextInt(divisor) != 0) return;

        final Direction exhaust = state.getValue(DirectionalBlock.FACING);
        final double reach = random.nextDouble() * 0.72d;
        final double angle = random.nextDouble() * Math.PI * 2.0d;
        final double radius = 0.15d + reach * 0.16d;
        final double lateralA = Math.cos(angle) * radius;
        final double lateralB = Math.sin(angle) * radius;
        final Vec3 point = plumePoint(pos, exhaust, reach, lateralA, lateralB);
        final Vec3 velocity = plumeVelocity(exhaust, 0.11d,
                lateralA * 0.16d, lateralB * 0.16d);

        level.addParticle(new DustParticleOptions(colourVector(COOLED_FUSION_COLOUR), 0.82f),
                point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        add(level, ParticleTypes.CLOUD, point, velocity.scale(0.52d));
        if (random.nextInt(5) == 0) {
            final Vec3 nozzle = plumePoint(pos, exhaust, 0.04d,
                    lateralA * 0.65d, lateralB * 0.65d);
            add(level, ParticleTypes.SPLASH, nozzle, velocity.scale(0.35d));
        }
    }

    /** Keep the additive cooled layer to roughly twelve sampled cells/tick. */
    static int cooledSamplingDivisor(final int nozzleCount) {
        final long count = Math.max(1, nozzleCount);
        final long capped = Math.max(4L, (count + 11L) / 12L);
        return (int) Math.min(Integer.MAX_VALUE, capped);
    }

    private static float particleScale(final Style style) {
        return switch (style) {
            case ION -> 0.72f;
            case MHD -> 0.82f;
            case MICRO -> 0.94f;
            case FUSION -> 1.08f;
        };
    }

    private static void addAccent(final Level level, final Style style, final RandomSource random,
                                  final Vec3 point, final Vec3 velocity) {
        switch (style) {
            case MHD -> {
                if (random.nextInt(3) == 0) add(level, ParticleTypes.ELECTRIC_SPARK, point, velocity.scale(1.15d));
            }
            case MICRO -> {
                if (random.nextInt(4) == 0) add(level, ParticleTypes.SMALL_FLAME, point, velocity.scale(0.75d));
                else if (random.nextInt(5) == 0) add(level, ParticleTypes.SMOKE, point, velocity.scale(0.55d));
            }
            case ION -> {
                if (random.nextInt(4) == 0) add(level, ParticleTypes.ELECTRIC_SPARK, point, velocity.scale(1.25d));
            }
            case FUSION -> {
                if (random.nextInt(4) == 0) add(level, ParticleTypes.END_ROD, point, velocity.scale(0.85d));
                else if (random.nextInt(6) == 0) add(level, ParticleTypes.SMOKE, point, velocity.scale(0.45d));
            }
        }
    }

    private static void add(final Level level, final net.minecraft.core.particles.ParticleOptions particle,
                            final Vec3 point, final Vec3 velocity) {
        level.addParticle(particle, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
    }

    /** Point in the exhaust cone; package-visible for geometry regression tests. */
    static Vec3 plumePoint(final BlockPos pos, final Direction exhaust, final double reach,
                           final double lateralA, final double lateralB) {
        final Vec3 direction = Vec3.atLowerCornerOf(exhaust.getNormal());
        final Vec3 tangentA = tangentA(exhaust);
        final Vec3 tangentB = direction.cross(tangentA);
        return Vec3.atCenterOf(pos).add(direction.scale(0.56d + reach))
                .add(tangentA.scale(lateralA)).add(tangentB.scale(lateralB));
    }

    /** Directional velocity with spread constrained to the nozzle plane. */
    static Vec3 plumeVelocity(final Direction exhaust, final double speed,
                              final double lateralA, final double lateralB) {
        final Vec3 direction = Vec3.atLowerCornerOf(exhaust.getNormal());
        final Vec3 tangentA = tangentA(exhaust);
        final Vec3 tangentB = direction.cross(tangentA);
        return direction.scale(speed).add(tangentA.scale(lateralA)).add(tangentB.scale(lateralB));
    }

    private static Vec3 tangentA(final Direction exhaust) {
        return exhaust.getAxis() == Direction.Axis.Y ? new Vec3(1.0d, 0.0d, 0.0d)
                : new Vec3(0.0d, 1.0d, 0.0d);
    }

    static Vector3f colourVector(final int colour) {
        return new Vector3f(((colour >> 16) & 255) / 255.0f,
                ((colour >> 8) & 255) / 255.0f, (colour & 255) / 255.0f);
    }
}
