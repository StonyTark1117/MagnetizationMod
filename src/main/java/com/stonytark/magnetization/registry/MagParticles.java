package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagParticles {

    public static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Magnetization.MOD_ID);

    /** North-pole field-line particle: tints red. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MAG_NORTH =
            REGISTER.register("mag_north", () -> new SimpleParticleType(false));

    /** South-pole field-line particle: tints blue. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MAG_SOUTH =
            REGISTER.register("mag_south", () -> new SimpleParticleType(false));

    private MagParticles() {}
}
