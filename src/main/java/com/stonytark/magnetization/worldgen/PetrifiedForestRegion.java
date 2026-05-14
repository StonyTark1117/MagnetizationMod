package com.stonytark.magnetization.worldgen;

import com.mojang.datafixers.util.Pair;
import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

/**
 * TerraBlender region for the {@code magnetization:petrified_forest} biome.
 * Sits in a slightly different climate slot from {@link AnomalyRegion} (warmer,
 * mid-humidity, far-inland) so the two custom biomes don't fight for the same
 * parameter space. Gated by {@link com.stonytark.magnetization.config.MagConfig#PETRIFIED_FOREST_ENABLED}
 * at registration time.
 */
public final class PetrifiedForestRegion extends Region {

    public static final int WEIGHT = 2;
    public static final ResourceKey<Biome> KEY =
            ResourceKey.create(Registries.BIOME, Magnetization.id("petrified_forest"));

    public PetrifiedForestRegion() {
        super(Magnetization.id("petrified_forest_region"), RegionType.OVERWORLD, WEIGHT);
    }

    @Override
    public void addBiomes(
            final Registry<Biome> registry,
            final Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper
    ) {
        new ParameterUtils.ParameterPointListBuilder()
                .temperature(ParameterUtils.Temperature.span(
                        ParameterUtils.Temperature.NEUTRAL, ParameterUtils.Temperature.WARM))
                .humidity(ParameterUtils.Humidity.span(
                        ParameterUtils.Humidity.DRY, ParameterUtils.Humidity.NEUTRAL))
                .continentalness(ParameterUtils.Continentalness.FAR_INLAND.parameter())
                .erosion(ParameterUtils.Erosion.span(
                        ParameterUtils.Erosion.EROSION_3, ParameterUtils.Erosion.EROSION_5))
                .depth(ParameterUtils.Depth.SURFACE.parameter())
                .weirdness(ParameterUtils.Weirdness.MID_SLICE_NORMAL_ASCENDING.parameter())
                .build()
                .forEach(point -> mapper.accept(Pair.of(point, KEY)));
    }
}
