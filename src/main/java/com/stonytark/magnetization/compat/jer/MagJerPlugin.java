package com.stonytark.magnetization.compat.jer;

import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import jeresources.api.IJERAPI;
import jeresources.api.IWorldGenRegistry;
import jeresources.api.distributions.DistributionBase;
import jeresources.api.distributions.DistributionCustom;
import jeresources.api.distributions.DistributionHelpers;
import jeresources.api.drop.LootDrop;
import jeresources.api.restrictions.Restriction;
import jeresources.compatibility.api.JERAPI;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

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

    /** Per-block chance shown on JER's heatmap. Calibrated against vanilla
     *  iron — JER normalises the colour ramp, so the absolute number matters
     *  less than the relative ordering against neighbouring ores. */
    private static final float CHANCE = 0.004f;

    /** Mountain-peaks bonus distribution is denser (24 attempts vs. 16) and
     *  biome-restricted to {@code #minecraft:is_mountain}. JER's biome API
     *  needs explicit biomes, so we just bake the higher chance into the
     *  Y 80..200 band and leave the restriction implicit in the chart. */
    private static final float PEAK_CHANCE_MULT = 24f / 16f;

    private MagJerPlugin() {}

    public static void register() {
        final IJERAPI api = JERAPI.getInstance();
        final IWorldGenRegistry wg = api.getWorldGenRegistry();

        // Magnetite: peaks band 81..200 only on the stone variant.
        wg.register(
                new ItemStack(MagBlocks.MAGNETITE_ORE.get()),
                stoneVariantDistribution(),
                Restriction.OVERWORLD,
                new LootDrop(MagItems.RAW_MAGNETITE.get(), 1, 1));
        wg.register(
                new ItemStack(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get()),
                deepslateVariantDistribution(),
                Restriction.OVERWORLD,
                new LootDrop(MagItems.RAW_MAGNETITE.get(), 1, 1));

        // -- Iron-oxide family: simple uniform band, no peaks bonus.
        // Per-ore worldgen counts pulled from the placed_feature JSONs;
        // chance scales with count so JER's heat ramp ranks ores in the
        // order they actually feel rare in-world. --
        registerOreFamily(wg, MagBlocks.MAGHEMITE_ORE.get(), MagBlocks.DEEPSLATE_MAGHEMITE_ORE.get(),
                MagItems.RAW_MAGHEMITE.get(), 40, 120, 14);
        registerOreFamily(wg, MagBlocks.PYRRHOTITE_ORE.get(), MagBlocks.DEEPSLATE_PYRRHOTITE_ORE.get(),
                MagItems.RAW_PYRRHOTITE.get(), -32, 48, 12);
        registerOreFamily(wg, MagBlocks.HEMATITE_ORE.get(), MagBlocks.DEEPSLATE_HEMATITE_ORE.get(),
                MagItems.RAW_HEMATITE.get(), -40, 96, 16);
        registerOreFamily(wg, MagBlocks.TITANOMAGNETITE_ORE.get(), MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE.get(),
                MagItems.RAW_TITANOMAGNETITE.get(), -64, -8, 8);
    }

    /** Reference vanilla-iron worldgen count used to scale {@link #CHANCE}
     *  per ore. JER's per-block chance is unitless, so the absolute number
     *  matters less than the relative ordering against iron. */
    private static final int VANILLA_IRON_COUNT = 90;

    /** Register the stone + deepslate variants of an iron-oxide ore against
     *  the JER worldgen registry. Splits the worldgen Y range at Y=0 to
     *  match the {@code stone_ore_replaceables} vs {@code deepslate_ore_replaceables}
     *  convention used by the configured_feature, so JER shows two entries
     *  with non-overlapping Y bands that reflect how the ore really spawns.
     *
     *  <p>Three cases:
     *  <ul>
     *    <li>{@code yMax < 0} → deepslate band only (no stone entry registered).</li>
     *    <li>{@code yMin >= 0} → stone band only (no deepslate entry registered).</li>
     *    <li>otherwise → split: stone 0..yMax, deepslate yMin..-1.</li>
     *  </ul>
     *  Prior implementation always tried to register both with
     *  {@code Math.max(0, yMin)} / {@code Math.min(-1, yMax)} which produced
     *  degenerate ranges (e.g. titanomagnetite "stone variant at Y=0..0").
     */
    private static void registerOreFamily(final IWorldGenRegistry wg,
                                           final Block stoneOre, final Block deepslateOre,
                                           final Item rawDrop,
                                           final int yMin, final int yMax,
                                           final int worldgenCount) {
        // Scale chance proportionally to vanilla iron, so two ores at the
        // same count read as roughly equally common on JER's heatmap.
        final float chance = (worldgenCount / (float) VANILLA_IRON_COUNT) * 0.008f;

        // Defensive double-registration: always register BOTH stone and
        // deepslate variants. Earlier we skipped a variant when its Y range
        // was empty (entirely above or below Y0); that caused some ores to
        // simply not appear in JER, which players read as "missing entry"
        // rather than "this variant doesn't spawn here." Better to show a
        // narrow / near-zero spawn line than no line at all. JER renders
        // empty distributions cleanly — empty pixels in the heatmap, no
        // crash. Stone variant always gets the Y≥0 portion; deepslate
        // always gets the Y<0 portion.
        final int stoneMin = Math.max(0, yMin);
        final int stoneMax = Math.max(stoneMin, yMax);
        final int deepMin  = Math.min(yMin, -1);
        final int deepMax  = Math.min(-1, yMax);

        final DistributionBase stoneDist = new DistributionCustom(
                DistributionHelpers.getSquareDistribution(stoneMin, stoneMax, stoneMax >= stoneMin && yMax >= 0 ? chance : 0f));
        wg.register(new ItemStack(stoneOre), stoneDist, Restriction.OVERWORLD,
                new LootDrop(rawDrop, 1, 1));

        final DistributionBase deepDist = new DistributionCustom(
                DistributionHelpers.getSquareDistribution(deepMin, deepMax, deepMax >= deepMin && yMin < 0 ? chance : 0f));
        wg.register(new ItemStack(deepslateOre), deepDist, Restriction.OVERWORLD,
                new LootDrop(rawDrop, 1, 1));
    }

    /** Stone variant spawns where the ore_replaceables tag is stone-side (roughly
     *  Y ≥ 0). Baseline placement covers 0..80; the mountain-peaks band adds
     *  81..200 at 1.5× chance (24 vs 16 attempts). Matches the vanilla iron
     *  split convention JER players already recognise. */
    private static DistributionCustom stoneVariantDistribution() {
        final float[] baseline = DistributionHelpers.getSquareDistribution(0, 80, CHANCE);
        final float[] peaks    = DistributionHelpers.getSquareDistribution(81, 200, CHANCE * PEAK_CHANCE_MULT);
        return new DistributionCustom(DistributionHelpers.addDistribution(baseline, peaks));
    }

    /** Deepslate variant spawns where the ore_replaceables tag is deepslate-side
     *  (roughly Y < 0). Only the baseline placement reaches here (peaks band
     *  starts at Y 80). */
    private static DistributionCustom deepslateVariantDistribution() {
        return new DistributionCustom(DistributionHelpers.getSquareDistribution(-48, -1, CHANCE));
    }
}
