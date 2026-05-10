package com.stonytark.magnetization.config;

import com.stonytark.magnetization.api.MagneticStrength;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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

    // Per-emitter GUI ceilings. Strings rather than enums so the spec can validate
    // against MagneticStrength's name() values.
    public static final ModConfigSpec.EnumValue<MagneticStrength> ELECTROMAGNET_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue                    ELECTROMAGNET_MAX_RANGE;
    public static final ModConfigSpec.EnumValue<MagneticStrength> ANCHOR_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue                    ANCHOR_MAX_RANGE;
    public static final ModConfigSpec.EnumValue<MagneticStrength> REPULSOR_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue                    REPULSOR_MAX_RANGE;
    public static final ModConfigSpec.EnumValue<MagneticStrength> TRACTOR_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue                    TRACTOR_MAX_RANGE;

    /** Soft-disabled blocks (by registry path). Disabled blocks emit no field, are
     *  hidden from the creative tab, and skip their right-click GUI. Existing
     *  placed instances stay on the map but are inert. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_BLOCKS;
    /** Soft-disabled items (by registry path). Hidden from creative tab; their
     *  effects (grapple, compass, magnetized armor susceptibility) are skipped at runtime. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ITEMS;

    /** Master toggle for the field-applicator and anchor-binding debug logs. Off by default. */
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    /** Global cooldown (ticks) between Magnetic Grapple right-clicks. */
    public static final ModConfigSpec.IntValue GRAPPLE_COOLDOWN_TICKS;
    /** Magnetic Grapple max range, in blocks. */
    public static final ModConfigSpec.IntValue GRAPPLE_MAX_RANGE;
    /** Field Compass scan radius, in blocks. */
    public static final ModConfigSpec.IntValue COMPASS_RANGE;

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

        b.comment("Per-emitter GUI ceilings. The in-game config menu can't dial above these",
                  "values, so server owners can prevent griefer-tier loadouts.")
         .push("guiLimits");

        ELECTROMAGNET_MAX_STRENGTH = b
                .comment("Max strength tier the Electromagnet GUI can select.")
                .defineEnum("electromagnetMaxStrength", MagneticStrength.EXTREME);
        ELECTROMAGNET_MAX_RANGE = b
                .comment("Max range (blocks) the Electromagnet GUI can dial up to.")
                .defineInRange("electromagnetMaxRange", 64, 0, 256);

        ANCHOR_MAX_STRENGTH = b
                .comment("Max strength tier the Magnetic Anchor GUI can select.")
                .defineEnum("anchorMaxStrength", MagneticStrength.EXTREME);
        ANCHOR_MAX_RANGE = b
                .comment("Max range (blocks) the Magnetic Anchor GUI can dial up to.")
                .defineInRange("anchorMaxRange", 64, 0, 256);

        REPULSOR_MAX_STRENGTH = b
                .comment("Max strength tier the Repulsor Coil GUI can select.")
                .defineEnum("repulsorMaxStrength", MagneticStrength.EXTREME);
        REPULSOR_MAX_RANGE = b
                .comment("Max range (blocks) the Repulsor Coil GUI can dial up to.")
                .defineInRange("repulsorMaxRange", 64, 0, 256);

        TRACTOR_MAX_STRENGTH = b
                .comment("Max strength tier the Tractor Beam GUI can select.")
                .defineEnum("tractorMaxStrength", MagneticStrength.EXTREME);
        TRACTOR_MAX_RANGE = b
                .comment("Max range (blocks) the Tractor Beam GUI can dial up to.")
                .defineInRange("tractorMaxRange", 64, 0, 256);

        b.pop();

        b.comment("Disable individual content. Items go in 'disabledItems'; blocks in",
                  "'disabledBlocks' (paths from the magnetization namespace, e.g. 'electromagnet').",
                  "Disabled blocks emit no field, are hidden from the creative tab, and skip",
                  "their right-click GUI. Disabled items are hidden from the creative tab and",
                  "their special effects are skipped (grapple, compass, magnetized-armor pull).",
                  "Existing placed instances stay on the map but are inert.")
         .push("content");

        DISABLED_BLOCKS = b
                .comment("Magnetization-namespaced block paths to disable. Example: [\"repulsor_coil\"].")
                .defineListAllowEmpty("disabledBlocks", List.of(),
                        () -> "", o -> o instanceof String);
        DISABLED_ITEMS = b
                .comment("Magnetization-namespaced item paths to disable. Example: [\"magnetic_grapple\"].")
                .defineListAllowEmpty("disabledItems", List.of(),
                        () -> "", o -> o instanceof String);

        b.pop();

        b.comment("Item / utility tuning.")
         .push("items");

        GRAPPLE_COOLDOWN_TICKS = b
                .comment("Cooldown (game ticks; 20 = 1s) between Magnetic Grapple right-clicks.")
                .defineInRange("grappleCooldownTicks", 20, 0, 600);
        GRAPPLE_MAX_RANGE = b
                .comment("Max range in blocks the Magnetic Grapple will scan for an attractive emitter.")
                .defineInRange("grappleMaxRange", 24, 4, 128);
        COMPASS_RANGE = b
                .comment("Field Compass scan radius in blocks.")
                .defineInRange("compassRange", 16, 4, 128);

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

        b.comment("Diagnostic logging toggles. Off by default in production.")
         .push("debug");

        DEBUG_LOGGING = b
                .comment("Master toggle for FieldApplicator + anchor-binding debug logs.",
                         "Set true while diagnosing emitter behavior; leave false on busy servers.")
                .define("debugLogging", false);

        b.pop();

        SPEC = b.build();
    }

    /** Convenience: lookup whether a block path is in the disabled list. Tolerates
     *  config-not-yet-loaded by returning false. */
    public static boolean isBlockDisabled(final String path) {
        try {
            return DISABLED_BLOCKS.get().contains(path);
        } catch (final Throwable t) {
            return false;
        }
    }

    public static boolean isItemDisabled(final String path) {
        try {
            return DISABLED_ITEMS.get().contains(path);
        } catch (final Throwable t) {
            return false;
        }
    }

    public static boolean debugLogging() {
        try {
            return DEBUG_LOGGING.get();
        } catch (final Throwable t) {
            return false;
        }
    }

    private MagConfig() {}
}
