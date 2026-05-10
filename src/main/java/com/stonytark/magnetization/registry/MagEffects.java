package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.effect.MagnetizedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Magnetization.MOD_ID);

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, Magnetization.MOD_ID);

    public static final DeferredHolder<MobEffect, MagnetizedEffect> MAGNETIZED =
            EFFECTS.register("magnetized", MagnetizedEffect::new);

    /** 60-second Magnetized potion at amplifier 0. NeoForge auto-derives the
     *  splash, lingering, and tipped-arrow variants from this registration. */
    public static final DeferredHolder<Potion, Potion> MAGNETIZED_POTION =
            POTIONS.register("magnetized", () -> new Potion(
                    "magnetized",
                    new MobEffectInstance(MAGNETIZED, 60 * 20, 0)));

    private MagEffects() {}
}
