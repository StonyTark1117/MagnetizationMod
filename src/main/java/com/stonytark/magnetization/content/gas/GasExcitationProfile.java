package com.stonytark.magnetization.content.gas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Datapack description of a foreign gas that can be vented into a proxy cloud. */
public record GasExcitationProfile(List<ResourceLocation> fluids, Buoyancy buoyancy,
                                   int dormantArgb, int excitedArgb) {
    private static final Codec<Integer> ARGB = Codec.STRING.<Integer>comapFlatMap(value -> {
        final String normalized = value.startsWith("#") ? value.substring(1) : value;
        if (!normalized.matches("[0-9a-fA-F]{8}")) {
            return DataResult.error(() -> "Expected an eight-digit ARGB colour, got " + value);
        }
        return DataResult.success((int) Long.parseLong(normalized, 16));
    }, value -> String.format("%08X", value));

    public static final Codec<GasExcitationProfile> CODEC = RecordCodecBuilder.<GasExcitationProfile>create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("fluids").forGetter((GasExcitationProfile p) -> p.fluids()),
            Buoyancy.CODEC.fieldOf("buoyancy").forGetter((GasExcitationProfile p) -> p.buoyancy()),
            ARGB.fieldOf("dormant_argb").forGetter((GasExcitationProfile p) -> p.dormantArgb()),
            ARGB.fieldOf("excited_argb").forGetter((GasExcitationProfile p) -> p.excitedArgb())
    ).apply(instance, GasExcitationProfile::new)).validate(profile -> profile.fluids().isEmpty()
            ? DataResult.error(() -> "Gas excitation profile must name at least one fluid")
            : DataResult.success(profile));

    public enum Buoyancy {
        RISE, SINK, NEUTRAL;

        public static final Codec<Buoyancy> CODEC = Codec.STRING.<Buoyancy>comapFlatMap(value -> {
            try {
                return DataResult.success(valueOf(value.toUpperCase(java.util.Locale.ROOT)));
            } catch (final IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown gas buoyancy " + value + "; expected rise, sink, or neutral");
            }
        }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    }
}
