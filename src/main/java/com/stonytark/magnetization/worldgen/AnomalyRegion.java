package com.stonytark.magnetization.worldgen;

import com.mojang.datafixers.util.Pair;
import com.stonytark.magnetization.Magnetization;
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
 * biome at low weight. The region is registered in {@link Magnetization}'s
 * constructor; without TerraBlender on the classpath the {@code anomaly.json}
 * biome still loads but only spawns via {@code /place biome} / {@code /locate}.
 *
 * <p>Climate slot: cold + arid + heavily eroded inland. This biases the biome
 * toward windswept-cold-ish strips at higher continentalness, which fits the
 * "stray anomaly" flavor — appears here and there inside dry mountainous
 * stretches, not as a continuous biome chunk.
 */
public final class AnomalyRegion extends Region {

    /** Weight relative to the vanilla overworld region (which has weight 10).
     *  At weight=2, the anomaly's parameter slots win the multi-noise lottery
     *  about 1/6 of the time within their climate band — already small, and
     *  the band itself is narrow, so the biome ends up genuinely rare. */
    public static final int WEIGHT = 2;

    public AnomalyRegion() {
        super(Magnetization.id("anomaly_region"), RegionType.OVERWORLD, WEIGHT);
    }

    @Override
    public void addBiomes(
            final Registry<Biome> registry,
            final Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper
    ) {
        new ParameterUtils.ParameterPointListBuilder()
                .temperature(ParameterUtils.Temperature.span(
                        ParameterUtils.Temperature.ICY, ParameterUtils.Temperature.COOL))
                .humidity(ParameterUtils.Humidity.span(
                        ParameterUtils.Humidity.ARID, ParameterUtils.Humidity.DRY))
                .continentalness(ParameterUtils.Continentalness.span(
                        ParameterUtils.Continentalness.MID_INLAND, ParameterUtils.Continentalness.FAR_INLAND))
                .erosion(ParameterUtils.Erosion.span(
                        ParameterUtils.Erosion.EROSION_0, ParameterUtils.Erosion.EROSION_2))
                .depth(ParameterUtils.Depth.SURFACE.parameter())
                .weirdness(ParameterUtils.Weirdness.PEAK_NORMAL.parameter())
                .build()
                .forEach(point -> mapper.accept(Pair.of(point, AnomalyBiome.KEY)));
    }
}
