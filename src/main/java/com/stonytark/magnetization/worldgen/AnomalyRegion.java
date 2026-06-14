package com.stonytark.magnetization.worldgen;

import com.mojang.datafixers.util.Pair;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

/**
 * TerraBlender overworld region that injects the {@code magnetization:anomaly}
 * biome. The biome's climate identity is cold + arid + heavily eroded inland.
 * How wide a slice of that climate parameter space the biome claims is driven
 * by {@link MagConfig#ANOMALY_BIOME_RARITY} at world-create time — narrower
 * spans mean fewer chunks match, so the biome is rarer.
 *
 * <p>Without TerraBlender on the classpath the {@code anomaly.json} biome
 * still loads but only spawns via {@code /place biome} / {@code /locate}.
 */
public final class AnomalyRegion extends Region {

    /** TerraBlender region weight constant. Not user-facing — actual rarity
     *  is governed by the rarity-driven parameter spans in {@link #addBiomes}. */
    public static final int WEIGHT = 1;

    public AnomalyRegion() {
        super(Magnetization.id("anomaly_region"), RegionType.OVERWORLD, WEIGHT);
    }

    @Override
    public void addBiomes(
            final Registry<Biome> registry,
            final Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper
    ) {
        // COMMON config has loaded by the time TerraBlender calls addBiomes
        // at world-create, so both the enabled gate and the rarity lookup
        // resolve to the user's current values here.
        if (!AnomalyBiome.enabled()) return;

        final BiomeRarity rarity = rarity();
        final ParameterUtils.ParameterPointListBuilder builder =
                new ParameterUtils.ParameterPointListBuilder()
                        .continentalness(ParameterUtils.Continentalness.FAR_INLAND.parameter())
                        .depth(ParameterUtils.Depth.SURFACE.parameter())
                        .weirdness(ParameterUtils.Weirdness.PEAK_NORMAL.parameter());

        switch (rarity) {
            case EXTREMELY_RARE -> builder
                    .temperature(ParameterUtils.Temperature.ICY.parameter())
                    .humidity(ParameterUtils.Humidity.ARID.parameter())
                    .erosion(ParameterUtils.Erosion.EROSION_1.parameter());
            case VERY_RARE -> builder
                    .temperature(ParameterUtils.Temperature.ICY.parameter())
                    .humidity(ParameterUtils.Humidity.span(
                            ParameterUtils.Humidity.ARID, ParameterUtils.Humidity.DRY))
                    .erosion(ParameterUtils.Erosion.span(
                            ParameterUtils.Erosion.EROSION_0, ParameterUtils.Erosion.EROSION_1));
            case RARE -> builder
                    .temperature(ParameterUtils.Temperature.span(
                            ParameterUtils.Temperature.ICY, ParameterUtils.Temperature.COOL))
                    .humidity(ParameterUtils.Humidity.span(
                            ParameterUtils.Humidity.ARID, ParameterUtils.Humidity.DRY))
                    .erosion(ParameterUtils.Erosion.span(
                            ParameterUtils.Erosion.EROSION_0, ParameterUtils.Erosion.EROSION_2));
            case COMMON -> builder
                    .temperature(ParameterUtils.Temperature.span(
                            ParameterUtils.Temperature.ICY, ParameterUtils.Temperature.NEUTRAL))
                    .humidity(ParameterUtils.Humidity.span(
                            ParameterUtils.Humidity.ARID, ParameterUtils.Humidity.NEUTRAL))
                    .erosion(ParameterUtils.Erosion.span(
                            ParameterUtils.Erosion.EROSION_0, ParameterUtils.Erosion.EROSION_4));
        }

        builder.build().forEach(point -> mapper.accept(Pair.of(point, AnomalyBiome.KEY)));
    }

    private static BiomeRarity rarity() {
        try { return MagConfig.ANOMALY_BIOME_RARITY.get(); }
        catch (final Throwable t) { return BiomeRarity.EXTREMELY_RARE; }
    }
}
