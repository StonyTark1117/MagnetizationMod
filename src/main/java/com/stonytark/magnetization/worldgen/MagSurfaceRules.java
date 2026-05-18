package com.stonytark.magnetization.worldgen;

import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Custom surface rules for the two mod-added biomes. Without these, both
 * biomes inherit overworld defaults and look like vanilla plains underground —
 * defeating the "this place feels different" aesthetic.
 *
 * <p><b>Anomaly:</b> magnetite blocks ↔ raw-magnetite-blocks ↔ stone, weighted
 * heavily toward magnetic materials with stone as the binding matrix. Players
 * exploring the biome see a landscape that's visibly half made of the things
 * the rest of the mod cares about.
 *
 * <p><b>Petrified Forest:</b> coarse-dirt surface over dirt — dry, dead-looking,
 * suits the lightning-blasted forest theme. Unchanged.
 *
 * <p>Registered via {@link terrablender.api.SurfaceRuleManager} in
 * {@code Magnetization}'s common-setup so the rules apply to overworld
 * dimensions only and merge cleanly with vanilla.
 */
public final class MagSurfaceRules {

    private MagSurfaceRules() {}

    public static SurfaceRules.RuleSource overworld() {
        // Anomaly palette: a 4-state surface noise picks among
        // magnetite_block, raw_magnetite_block, hematite_block, and stone for
        // the top layer; below that a 2-state noise alternates magnetite ore
        // and stone. The hematite splash is a nod to the antiferromagnetic
        // mineral and the dark-red palette of an irradiated anomaly.
        final SurfaceRules.RuleSource magnetiteBlock =
                SurfaceRules.state(MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState());
        final SurfaceRules.RuleSource rawMagnetiteBlock =
                SurfaceRules.state(MagBlocks.RAW_MAGNETITE_BLOCK.get().defaultBlockState());
        final SurfaceRules.RuleSource hematiteBlock =
                SurfaceRules.state(MagBlocks.HEMATITE_BLOCK.get().defaultBlockState());
        final SurfaceRules.RuleSource maghemiteBlock =
                SurfaceRules.state(MagBlocks.MAGHEMITE_BLOCK.get().defaultBlockState());
        final SurfaceRules.RuleSource rawTitanomagnetiteBlock =
                SurfaceRules.state(MagBlocks.RAW_TITANOMAGNETITE_BLOCK.get().defaultBlockState());
        final SurfaceRules.RuleSource magnetiteOre =
                SurfaceRules.state(MagBlocks.MAGNETITE_ORE.get().defaultBlockState());
        final SurfaceRules.RuleSource stone =
                SurfaceRules.state(Blocks.STONE.defaultBlockState());

        // Surface (visible top): magnetite-heavy with raw-magnetite veins,
        // hematite/maghemite/titanomagnetite splashes. Previously used the
        // SURFACE noise to partition between five materials, but the user
        // reported the biome still looking like normal terrain — most likely
        // a vanilla surface rule fires first when the noise condition isn't
        // strongly positive, leaving grass_block/dirt on top. To force the
        // distinctive look, drop the noise gates on the main bands so EVERY
        // surface block of the biome lands on a magnetic material; only the
        // accent colours (hematite/maghemite/titanomagnetite splashes) remain
        // noise-gated so the surface still varies. raw_magnetite is the
        // default "rest" colour because it reads visually red-orange and is
        // the bulk of the biome's identity.
        final SurfaceRules.RuleSource anomalyTop = SurfaceRules.sequence(
                // High-noise peaks: dense magnetite_block clumps.
                SurfaceRules.ifTrue(
                        SurfaceRules.noiseCondition(net.minecraft.world.level.levelgen.Noises.SURFACE, 0.40, Double.MAX_VALUE),
                        magnetiteBlock),
                // Antiferromagnetic splash — dark red hematite, mid-noise band.
                SurfaceRules.ifTrue(
                        SurfaceRules.noiseCondition(net.minecraft.world.level.levelgen.Noises.SURFACE, 0.10, 0.40),
                        hematiteBlock),
                // Maghemite (rust-orange) splash — low-positive noise band.
                SurfaceRules.ifTrue(
                        SurfaceRules.noiseCondition(net.minecraft.world.level.levelgen.Noises.SURFACE, -0.20, 0.10),
                        maghemiteBlock),
                // Deep-magnetic substrate splash — dark blue-grey
                // titanomagnetite, narrow band on the negative side.
                SurfaceRules.ifTrue(
                        SurfaceRules.noiseCondition(net.minecraft.world.level.levelgen.Noises.SURFACE, -0.50, -0.20),
                        rawTitanomagnetiteBlock),
                // Default "rest" colour: raw_magnetite_block. NO noise gate so
                // any column not painted by the bands above is still magnetic
                // mass — guarantees the biome never falls through to a vanilla
                // grass/dirt/stone surface.
                rawMagnetiteBlock);

        // Sub-surface: ore + raw_magnetite_block (so digging in immediately
        // rewards the player with magnetic mass without exposing vanilla
        // stone). Magnetite ore in the noise-high bands, raw_magnetite_block
        // everywhere else — keeps the column visually consistent with the
        // surface above instead of jarring grey stone underneath.
        final SurfaceRules.RuleSource anomalySub = SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.noiseCondition(net.minecraft.world.level.levelgen.Noises.SURFACE_SECONDARY, 0.0, Double.MAX_VALUE),
                        magnetiteOre),
                rawMagnetiteBlock);

        final SurfaceRules.RuleSource coarseDirt =
                SurfaceRules.state(Blocks.COARSE_DIRT.defaultBlockState());
        final SurfaceRules.RuleSource dirt =
                SurfaceRules.state(Blocks.DIRT.defaultBlockState());

        // NOTE: This rule is the "clean install" path — it works on installs
        // where TerraBlender owns the overworld surface-rule wrap unmolested.
        // In our shipped modpack (Citadel from Alex's Caves also targets
        // NoiseGeneratorSettings.surfaceRule()), TerraBlender's wrap is
        // overwritten and these rules silently no-op at chunk-gen time.
        // The visible surface is therefore handled by
        // {@link com.stonytark.magnetization.worldgen.ChunkSurfaceRepaintHandler}
        // which post-paints the topmost solid block in anomaly + petrified
        // chunks once they finish generating. Keeping this rule registered
        // so users on lighter modpacks (no Citadel) still get correct surfaces
        // without paying the chunk-load repaint cost.
        final SurfaceRules.RuleSource anomalyRule = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, anomalyTop),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, anomalySub)
        );
        final SurfaceRules.RuleSource petrifiedRule = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, coarseDirt),
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dirt)
        );
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(AnomalyBiome.KEY), anomalyRule),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(PetrifiedForestBiome.KEY), petrifiedRule)
        );
    }
}
