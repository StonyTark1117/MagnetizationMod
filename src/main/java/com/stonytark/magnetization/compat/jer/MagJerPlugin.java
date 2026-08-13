package com.stonytark.magnetization.compat.jer;

import com.stonytark.magnetization.config.MagConfig;
import jeresources.api.IJERAPI;
import jeresources.api.IWorldGenRegistry;
import jeresources.api.distributions.DistributionBase;
import jeresources.api.distributions.DistributionCustom;
import jeresources.api.distributions.DistributionHelpers;
import jeresources.api.distributions.DistributionUnderWater;
import jeresources.api.drop.LootDrop;
import jeresources.api.restrictions.Restriction;
import jeresources.compatibility.api.JERAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Just Enough Resources adapter for every Magnetization ore and naturally
 * generated resource described by {@link JerWorldgenCatalog}.
 *
 * <p>Registration is invoked explicitly during common setup behind both the
 * JER mod-presence check and Magnetization's compatibility setting. That keeps
 * this optional class unloaded when JER is absent while giving the catalog a
 * deterministic registration point that can be synchronized against data in
 * ordinary unit tests.</p>
 */
public final class MagJerPlugin {
    private MagJerPlugin() {}

    public static void register() {
        if (!MagConfig.justEnoughResourcesCompatEnabled()) return;
        final IJERAPI api = JERAPI.getInstance();
        final IWorldGenRegistry worldgen = api.getWorldGenRegistry();
        final var charts = JerWorldgenCatalog.charts();
        for (final JerWorldgenCatalog.Chart chart : charts) {
            worldgen.register(new ItemStack(requiredItem(chart.display())), distribution(chart),
                    restriction(chart.dimension()), drops(chart));
        }
        org.slf4j.LoggerFactory.getLogger("magnetization/JER")
                .info("Registered {} synchronized worldgen charts with Just Enough Resources", charts.size());
    }

    private static DistributionBase distribution(final JerWorldgenCatalog.Chart chart) {
        if (chart.distribution() == JerWorldgenCatalog.DistributionKind.UNDERWATER) {
            return new DistributionUnderWater(chart.bands().getFirst().frequency());
        }
        float[] combined = null;
        for (final JerWorldgenCatalog.ChartBand band : chart.bands()) {
            final float[] next = chart.distribution() == JerWorldgenCatalog.DistributionKind.SURFACE
                    ? DistributionHelpers.multiplyArray(DistributionHelpers.getOverworldSurface(), band.frequency())
                    : DistributionHelpers.getSquareDistribution(band.minY(), band.maxY(), band.frequency());
            combined = combined == null ? next : DistributionHelpers.addDistribution(combined, next);
        }
        if (combined == null) {
            throw new IllegalStateException("JER chart has no distribution bands: " + chart.source());
        }
        return new DistributionCustom(combined);
    }

    private static Restriction restriction(final JerWorldgenCatalog.Dimension dimension) {
        return dimension == JerWorldgenCatalog.Dimension.END ? Restriction.END : Restriction.OVERWORLD;
    }

    private static LootDrop[] drops(final JerWorldgenCatalog.Chart chart) {
        return chart.drops().stream().map(drop -> new LootDrop(requiredItem(drop.item()),
                        drop.min(), drop.max(), drop.chance()))
                .toArray(LootDrop[]::new);
    }

    private static Item requiredItem(final String path) {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
        final Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalStateException("JER worldgen catalog references missing item " + id);
        }
        return item;
    }
}
