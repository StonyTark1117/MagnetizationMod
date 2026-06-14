package com.stonytark.magnetization.worldgen;

import com.mojang.datafixers.util.Pair;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
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
 * Warm, mid-humidity, far-inland slot so it doesn't fight {@link AnomalyRegion}
 * for parameter space. Rarity tier driven by
 * {@link MagConfig#PETRIFIED_FOREST_RARITY} — wider spans = encountered more
 * often. Default {@link BiomeRarity#RARE}.
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
        // COMMON config has loaded by the time TerraBlender calls addBiomes at
        // world-create, so both the enabled gate and the rarity lookup resolve
        // to the user's current values here.
        if (!petrifiedForestEnabled()) return;

        final BiomeRarity rarity = rarity();
        final ParameterUtils.ParameterPointListBuilder builder =
                new ParameterUtils.ParameterPointListBuilder()
                        .continentalness(ParameterUtils.Continentalness.FAR_INLAND.parameter())
                        .depth(ParameterUtils.Depth.SURFACE.parameter())
                        .weirdness(ParameterUtils.Weirdness.MID_SLICE_NORMAL_ASCENDING.parameter());

        switch (rarity) {
            case EXTREMELY_RARE -> builder
                    .temperature(ParameterUtils.Temperature.WARM.parameter())
                    .humidity(ParameterUtils.Humidity.DRY.parameter())
                    .erosion(ParameterUtils.Erosion.EROSION_4.parameter());
            case VERY_RARE -> builder
                    .temperature(ParameterUtils.Temperature.span(
                            ParameterUtils.Temperature.NEUTRAL, ParameterUtils.Temperature.WARM))
                    .humidity(ParameterUtils.Humidity.DRY.parameter())
                    .erosion(ParameterUtils.Erosion.span(
                            ParameterUtils.Erosion.EROSION_3, ParameterUtils.Erosion.EROSION_4));
            case RARE -> builder
                    .temperature(ParameterUtils.Temperature.span(
                            ParameterUtils.Temperature.NEUTRAL, ParameterUtils.Temperature.WARM))
                    .humidity(ParameterUtils.Humidity.span(
                            ParameterUtils.Humidity.DRY, ParameterUtils.Humidity.NEUTRAL))
                    .erosion(ParameterUtils.Erosion.span(
                            ParameterUtils.Erosion.EROSION_3, ParameterUtils.Erosion.EROSION_5));
            case COMMON -> builder
                    .temperature(ParameterUtils.Temperature.span(
                            ParameterUtils.Temperature.COOL, ParameterUtils.Temperature.HOT))
                    .humidity(ParameterUtils.Humidity.span(
                            ParameterUtils.Humidity.ARID, ParameterUtils.Humidity.NEUTRAL))
                    .erosion(ParameterUtils.Erosion.span(
                            ParameterUtils.Erosion.EROSION_2, ParameterUtils.Erosion.EROSION_6));
        }

        builder.build().forEach(point -> mapper.accept(Pair.of(point, KEY)));
    }

    private static boolean petrifiedForestEnabled() {
        try { return MagConfig.PETRIFIED_FOREST_ENABLED.get(); }
        catch (final Throwable t) { return false; }
    }

    private static BiomeRarity rarity() {
        try { return MagConfig.PETRIFIED_FOREST_RARITY.get(); }
        catch (final Throwable t) { return BiomeRarity.RARE; }
    }
}
