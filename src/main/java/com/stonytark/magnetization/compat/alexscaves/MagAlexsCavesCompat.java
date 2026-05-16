package com.stonytark.magnetization.compat.alexscaves;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alex's Caves cross-mod glue. AC ships its own "Magnetizing" status effect
 * that's functionally similar to our {@link MagEffects#MAGNETIZED} — pulling
 * the affected entity toward nearby metal. Both effects working in parallel is
 * the default (and the most common preference: "more magnetism is better"),
 * but a server owner can pick {@code OURS_ONLY} or {@code THEIRS_ONLY} to make
 * one mod the canonical source.
 *
 * <p>The swap fires on {@link MobEffectEvent.Added}: when the to-be-replaced
 * effect is freshly applied, we cancel it and apply the substitute at the same
 * duration + amplifier so the player can't tell which mod owns the pull.
 *
 * <p>Wired in {@code Magnetization} only when {@code alexscaves} is loaded.
 */
public final class MagAlexsCavesCompat {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/AlexsCavesCompat");

    /** AC's effect ID. Resolved lazily so a future AC rename only mis-fires once
     *  rather than crashing at mod construction. */
    private static final ResourceLocation AC_MAGNETIZING_ID =
            ResourceLocation.fromNamespaceAndPath("alexscaves", "magnetizing");

    private MagAlexsCavesCompat() {}

    public static void wire(final IEventBus modBus) {
        // Game event, not mod event — listen on NeoForge bus.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MagAlexsCavesCompat::onEffectAdded);
    }

    private static void onEffectAdded(final MobEffectEvent.Added event) {
        final MagConfig.AlexsCavesPotionMode mode;
        try { mode = MagConfig.ALEXSCAVES_POTION_MODE.get(); }
        catch (final Throwable t) { return; } // config not loaded yet
        if (mode == MagConfig.AlexsCavesPotionMode.BOTH) return;

        final LivingEntity living = event.getEntity();
        final MobEffectInstance added = event.getEffectInstance();
        final Holder<MobEffect> addedEffect = added.getEffect();

        final Holder<MobEffect> acMagnetizing = resolveAcMagnetizing();
        if (acMagnetizing == null) return; // AC not actually loaded after all

        if (mode == MagConfig.AlexsCavesPotionMode.OURS_ONLY && sameEffect(addedEffect, acMagnetizing)) {
            // Replace AC's pull with ours at the same duration / amplifier.
            living.removeEffect(acMagnetizing);
            living.addEffect(new MobEffectInstance(
                    MagEffects.MAGNETIZED, added.getDuration(), added.getAmplifier(),
                    added.isAmbient(), added.isVisible(), added.showIcon()));
        } else if (mode == MagConfig.AlexsCavesPotionMode.THEIRS_ONLY && sameEffect(addedEffect, MagEffects.MAGNETIZED)) {
            living.removeEffect(MagEffects.MAGNETIZED);
            living.addEffect(new MobEffectInstance(
                    acMagnetizing, added.getDuration(), added.getAmplifier(),
                    added.isAmbient(), added.isVisible(), added.showIcon()));
        }
    }

    private static boolean sameEffect(final Holder<MobEffect> a, final Holder<MobEffect> b) {
        return a == b || (a.unwrapKey().isPresent() && b.unwrapKey().isPresent()
                && a.unwrapKey().get().equals(b.unwrapKey().get()));
    }

    private static @org.jetbrains.annotations.Nullable Holder<MobEffect> resolveAcMagnetizing() {
        return BuiltInRegistries.MOB_EFFECT.getHolder(AC_MAGNETIZING_ID).orElse(null);
    }
}
