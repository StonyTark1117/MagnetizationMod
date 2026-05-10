package com.stonytark.magnetization.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side config for tunable physics constants. Values are read live from the
 * spec, so a config reload propagates immediately without restart.
 */
public final class MagConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ENTITY_VELOCITY_SCALE;
    public static final ModConfigSpec.DoubleValue CONICAL_HALF_ANGLE_COS;
    public static final ModConfigSpec.DoubleValue MAX_ACCEL_PER_TICK;

    public static final ModConfigSpec.BooleanValue MAGNETIC_PEAKS_ENABLED;
    public static final ModConfigSpec.BooleanValue ANOMALY_BIOME_ENABLED;

    static {
        final ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Physics tuning for the Sable sub-level query and entity force application.")
         .push("physics");

        STRENGTH_MULTIPLIER = b
                .comment("Global multiplier applied to every emitter's tier force value.")
                .defineInRange("strengthMultiplier", 1.0d, 0.0d, 100.0d);

        ENTITY_VELOCITY_SCALE = b
                .comment("Scale factor applied when converting impulse units to vanilla entity",
                         "delta-movement (velocity per tick). Default 1/20 = 0.05.")
                .defineInRange("entityVelocityScale", 0.05d, 0.0d, 1.0d);

        CONICAL_HALF_ANGLE_COS = b
                .comment("Cosine of the half-angle of conical emitters (repulsor coil). Default",
                         "cos(45deg) = 0.7071. Larger value = narrower cone.")
                .defineInRange("conicalHalfAngleCos", 0.7071d, 0.0d, 0.999d);

        MAX_ACCEL_PER_TICK = b
                .comment("Per-ship acceleration cap (m/s², Sable's SI units) applied AFTER",
                         "Sable's own F=ma mass scaling. Prevents a STRONG anchor from launching",
                         "a 1-block test ship across the world. 50 m/s² ~= 5 g, well above",
                         "vanilla gravity's ~9.8 m/s². Set to 0 to disable the cap entirely.")
                .defineInRange("maxAccelPerTick", 50.0d, 0.0d, 1000.0d);

        b.pop();

        b.comment("World generation toggles. Note: the base magnetite ore vein generates",
                  "in every overworld biome regardless of these flags — these only control",
                  "the 'flavor' biome modifiers layered on top.")
         .push("worldgen");

        MAGNETIC_PEAKS_ENABLED = b
                .comment("If true, denser magnetite ore veins appear in mountain biomes",
                         "(#minecraft:is_mountain). Adds a flavor pass that surfaces in",
                         "snowy/jagged peaks. Default off — opt-in.")
                .define("magneticPeaksEnabled", false);

        ANOMALY_BIOME_ENABLED = b
                .comment("If true, registers a custom 'magnetic anomaly' biome where field",
                         "compasses spin, ambient particles drift, and emitter strength is",
                         "amplified. Currently a stub — the biome JSON is registered but the",
                         "biome is not yet injected into the overworld terrain. Default off.")
                .define("anomalyBiomeEnabled", false);

        b.pop();

        SPEC = b.build();
    }

    private MagConfig() {}
}
