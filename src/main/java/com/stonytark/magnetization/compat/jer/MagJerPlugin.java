package com.stonytark.magnetization.compat.jer;

import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import jeresources.api.IJERAPI;
import jeresources.api.IWorldGenRegistry;
import jeresources.api.distributions.DistributionTriangular;
import jeresources.api.drop.LootDrop;
import jeresources.api.restrictions.Restriction;
import jeresources.compatibility.api.JERAPI;
import net.minecraft.world.item.ItemStack;

/**
 * Just Enough Resources integration: registers the addon's worldgen ores
 * (magnetite + deepslate variant) so JER's WorldGen tab shows the
 * Y-distribution graph and drops alongside vanilla ores.
 *
 * <p>JER's 1.21.1 NeoForge build (1.6.0.17) ships a broken
 * {@code @JERPlugin} annotation scanner that looks for {@code IJERPlugin}
 * (the interface, not the annotation) in {@code ModFileScanData}, so the
 * standard plugin route never fires. We instead mirror what
 * <a href="https://github.com/Janoeo/JER-Integration">JER Integration</a>
 * does — pull {@code JERAPI.getInstance()} directly during common setup
 * and register against the live registry. The registry queues calls until
 * JER's {@code commit()} runs later in the JEI plugin lifecycle.
 *
 * <p>This class is only loaded when {@code ModList.isLoaded("jeresources")}
 * is true (gated from {@link com.stonytark.magnetization.Magnetization}) —
 * the lazy-class-loading guarantee keeps the JER imports inert when JER
 * isn't on the runtime classpath.
 */
public final class MagJerPlugin {

    /** Per-block chance shown on JER's heatmap. Calibrated to vanilla iron's
     *  band: ~9 attempts × vein 9 spread across ~20k blocks per chunk in the
     *  Y −24…56 layer. JER scales the colour ramp, so the absolute value
     *  matters less than relative ordering vs. neighbouring ores. */
    private static final float CHANCE = 0.004f;

    private MagJerPlugin() {}

    public static void register() {
        final IJERAPI api = JERAPI.getInstance();
        final IWorldGenRegistry wg = api.getWorldGenRegistry();

        wg.register(
                new ItemStack(MagBlocks.MAGNETITE_ORE.get()),
                new DistributionTriangular(-24, 56, CHANCE),
                Restriction.OVERWORLD,
                new LootDrop(MagItems.RAW_MAGNETITE.get(), 1, 1));

        wg.register(
                new ItemStack(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get()),
                new DistributionTriangular(-24, 56, CHANCE),
                Restriction.OVERWORLD,
                new LootDrop(MagItems.RAW_MAGNETITE.get(), 1, 1));
    }
}
