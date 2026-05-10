package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.worldgen.ConfigGatedAddFeaturesModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the addon's custom BiomeModifier codec types. Datapack JSONs in
 * {@code data/magnetization/biome_modifier/} reference these by id.
 */
public final class MagBiomeModifiers {

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Magnetization.MOD_ID);

    public static final Supplier<MapCodec<ConfigGatedAddFeaturesModifier>> CONFIG_GATED_ADD_FEATURES =
            REGISTER.register("config_gated_add_features", () -> ConfigGatedAddFeaturesModifier.CODEC);

    private MagBiomeModifiers() {}
}
