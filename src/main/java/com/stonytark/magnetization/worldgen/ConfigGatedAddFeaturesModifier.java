package com.stonytark.magnetization.worldgen;

import com.stonytark.magnetization.config.MagConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Variant of {@code neoforge:add_features} that only fires when a named
 * MagConfig flag is true. The biome modifier JSON is always loaded; the gate
 * happens at modify-time so the same datapack works for all config combinations.
 *
 * <p>Reasonable values for {@code flag}: {@code "magnetic_peaks"}, {@code "anomaly_biome"}.
 * Unknown flags default to false (treated as disabled), so a typo can't accidentally
 * inject worldgen.
 */
public record ConfigGatedAddFeaturesModifier(
        String flag,
        HolderSet<Biome> biomes,
        HolderSet<PlacedFeature> features,
        Decoration step
) implements BiomeModifier {

    public static final MapCodec<ConfigGatedAddFeaturesModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            com.mojang.serialization.Codec.STRING.fieldOf("flag").forGetter(ConfigGatedAddFeaturesModifier::flag),
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(ConfigGatedAddFeaturesModifier::biomes),
            RegistryCodecs.homogeneousList(Registries.PLACED_FEATURE).fieldOf("features").forGetter(ConfigGatedAddFeaturesModifier::features),
            Decoration.CODEC.fieldOf("step").forGetter(ConfigGatedAddFeaturesModifier::step)
    ).apply(inst, ConfigGatedAddFeaturesModifier::new));

    @Override
    public void modify(final Holder<Biome> biome, final Phase phase, final ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;
        if (!biomes.contains(biome)) return;
        if (!isFlagEnabled(flag)) return;

        final BiomeGenerationSettingsBuilder gen = builder.getGenerationSettings();
        features.forEach(holder -> gen.addFeature(step, holder));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    private static boolean isFlagEnabled(final String name) {
        try {
            return switch (name) {
                case "anomaly_biome" -> MagConfig.ANOMALY_BIOME_ENABLED.get();
                case "magnetic_gravel_in_vanilla" -> MagConfig.MAGNETIC_GRAVEL_IN_VANILLA_BIOMES.get();
                default -> false;
            };
        } catch (Throwable t) {
            // Config not yet loaded (rare datapack-load-before-config edge case): treat as off.
            return false;
        }
    }
}
