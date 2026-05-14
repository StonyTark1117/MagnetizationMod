package com.stonytark.magnetization.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Custom surface rules for the two mod-added biomes. Without these, both
 * biomes inherit overworld defaults and look like vanilla plains underground —
 * defeating the "this place feels different" aesthetic.
 *
 * <p>Anomaly: cobblestone surface over andesite — wind-scoured, rocky,
 * metal-tinged. Suits the magnetic-anomaly flavor.
 *
 * <p>Petrified Forest: coarse-dirt surface over dirt — dry, dead-looking,
 * suits the lightning-blasted forest theme.
 *
 * <p>Registered via {@link terrablender.api.SurfaceRuleManager} in
 * {@code Magnetization}'s common-setup so the rules apply to overworld
 * dimensions only and merge cleanly with vanilla.
 */
public final class MagSurfaceRules {

    private MagSurfaceRules() {}

    public static SurfaceRules.RuleSource overworld() {
        final SurfaceRules.RuleSource cobble =
                SurfaceRules.state(Blocks.COBBLESTONE.defaultBlockState());
        final SurfaceRules.RuleSource andesite =
                SurfaceRules.state(Blocks.ANDESITE.defaultBlockState());
        final SurfaceRules.RuleSource coarseDirt =
                SurfaceRules.state(Blocks.COARSE_DIRT.defaultBlockState());
        final SurfaceRules.RuleSource dirt =
                SurfaceRules.state(Blocks.DIRT.defaultBlockState());

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.isBiome(AnomalyBiome.KEY),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, cobble),
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, andesite)
                        )),
                SurfaceRules.ifTrue(SurfaceRules.isBiome(PetrifiedForestBiome.KEY),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, coarseDirt),
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dirt)
                        ))
        );
    }
}
