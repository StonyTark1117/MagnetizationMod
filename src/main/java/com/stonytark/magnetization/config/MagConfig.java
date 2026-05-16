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
    public static final ModConfigSpec.DoubleValue SHIP_LINEAR_DRAG;
    public static final ModConfigSpec.IntValue    SHIP_SAMPLE_STEPS;
    public static final ModConfigSpec.DoubleValue SHIP_BASELINE_SUSCEPTIBILITY;
    public static final ModConfigSpec.DoubleValue SHIP_PER_FERROUS_SUSCEPTIBILITY;
    public static final ModConfigSpec.DoubleValue SHIP_PER_MAGNET_SUSCEPTIBILITY;
    public static final ModConfigSpec.DoubleValue SHIP_MAX_SUSCEPTIBILITY;
    public static final ModConfigSpec.IntValue    SHIP_SCAN_INTERVAL_TICKS;

    public static final ModConfigSpec.BooleanValue MAGNETIC_PEAKS_ENABLED;
    public static final ModConfigSpec.BooleanValue ANOMALY_BIOME_ENABLED;
    public static final ModConfigSpec.DoubleValue  ANOMALY_CHAOS_STRENGTH;

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
    public static final ModConfigSpec.EnumValue<MagneticStrength> EXCAVATOR_MAX_STRENGTH;
    public static final ModConfigSpec.IntValue                    EXCAVATOR_MAX_RANGE;
    public static final ModConfigSpec.IntValue                    EXCAVATOR_MAX_BLOCKS_PER_CYCLE;
    public static final ModConfigSpec.IntValue                    EXCAVATOR_MAX_IN_FLIGHT;

    /** Soft-disabled blocks (by registry path). Disabled blocks emit no field, are
     *  hidden from the creative tab, and skip their right-click GUI. Existing
     *  placed instances stay on the map but are inert. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_BLOCKS;
    /** Soft-disabled items (by registry path). Hidden from creative tab; their
     *  effects (grapple, compass, magnetized armor susceptibility) are skipped at runtime. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ITEMS;

    /** Master toggle for the field-applicator and anchor-binding debug logs. Off by default. */
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    // Per-command-group permission levels. 0 = any player, 2 = operator (vanilla
    // default), 3/4 = higher op levels. Read live via brigadier .requires(),
    // so admins reload-config can adjust permissions without restarting.
    public static final ModConfigSpec.IntValue COMMAND_DEBUG_PERMISSION;
    public static final ModConfigSpec.IntValue COMMAND_SPAWN_TEST_PERMISSION;
    public static final ModConfigSpec.IntValue COMMAND_SHIP_UTIL_PERMISSION;
    public static final ModConfigSpec.IntValue COMMAND_LIRM_PERMISSION;
    public static final ModConfigSpec.IntValue COMMAND_TP_PERMISSION;

    /** Global cooldown (ticks) between Magnetic Grapple right-clicks. */
    public static final ModConfigSpec.IntValue GRAPPLE_COOLDOWN_TICKS;
    /** Magnetic Grapple max range, in blocks. */
    public static final ModConfigSpec.IntValue GRAPPLE_MAX_RANGE;
    /** Field Compass scan radius, in blocks. */
    public static final ModConfigSpec.IntValue COMPASS_RANGE;

    // Per-tool magnetized-effect toggles. Each tool gets a unique signature
    // ability on top of the shared "pull dropped ferromagnetic items" handler.
    public static final ModConfigSpec.BooleanValue LIRM_ENABLED;
    public static final ModConfigSpec.BooleanValue PETRIFIED_FOREST_ENABLED;
    public static final ModConfigSpec.BooleanValue TOOL_SWORD_YANK_ENABLED;
    public static final ModConfigSpec.BooleanValue TOOL_PICKAXE_ORE_RIP_ENABLED;
    public static final ModConfigSpec.BooleanValue TOOL_AXE_PULSE_ENABLED;
    public static final ModConfigSpec.BooleanValue TOOL_SHOVEL_PAN_ENABLED;
    public static final ModConfigSpec.BooleanValue TOOL_HOE_DOWSE_ENABLED;
    public static final ModConfigSpec.IntValue     TOOL_PICKAXE_RIP_RADIUS;
    public static final ModConfigSpec.IntValue     TOOL_PICKAXE_RIP_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue  TOOL_SHOVEL_PAN_CHANCE;
    public static final ModConfigSpec.IntValue     TOOL_HOE_DOWSE_RADIUS;
    public static final ModConfigSpec.IntValue     TOOL_HOE_DOWSE_COOLDOWN_TICKS;

    static {
        final ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Physics tuning for the Sable sub-level query and entity force application.")
         .translation("magnetization.configuration.physics")
         .push("physics");

        STRENGTH_MULTIPLIER = b
                .comment("Global multiplier applied to every emitter's tier force value.")
                .translation("magnetization.configuration.physics.strengthMultiplier")
                .defineInRange("strengthMultiplier", 1.0d, 0.0d, 100.0d);

        ENTITY_VELOCITY_SCALE = b
                .comment("Scale factor applied when converting impulse units to vanilla entity",
                         "delta-movement (velocity per tick). Default 1/20 = 0.05.")
                .translation("magnetization.configuration.physics.entityVelocityScale")
                .defineInRange("entityVelocityScale", 0.05d, 0.0d, 1.0d);

        CONICAL_HALF_ANGLE_COS = b
                .comment("Cosine of the half-angle of conical emitters (repulsor coil). Default",
                         "cos(45deg) = 0.7071. Larger value = narrower cone.")
                .translation("magnetization.configuration.physics.conicalHalfAngleCos")
                .defineInRange("conicalHalfAngleCos", 0.7071d, 0.0d, 0.999d);

        MAX_ACCEL_PER_TICK = b
                .comment("Per-ship-per-tick acceleration cap (m/s², Sable's SI units), summed",
                         "across every emitter touching the ship that tick. Prevents a STRONG",
                         "anchor — or three of them — from launching a 1-block ship across the",
                         "world. 50 m/s² ~= 5 g, well above vanilla gravity's ~9.8 m/s². Set to",
                         "0 to disable the cap entirely.")
                .translation("magnetization.configuration.physics.maxAccelPerTick")
                .defineInRange("maxAccelPerTick", 50.0d, 0.0d, 1000.0d);

        SHIP_LINEAR_DRAG = b
                .comment("Per-tick linear-velocity damping applied to a ship while it is being",
                         "pulled by any magnetic emitter. 0.02 = the ship loses 2% of its current",
                         "speed every tick a magnet is acting on it, so constant-force pulls",
                         "settle to a terminal velocity instead of accelerating without bound.",
                         "Set to 0 to disable drag entirely (1.0.0 behaviour).")
                .translation("magnetization.configuration.physics.shipLinearDrag")
                .defineInRange("shipLinearDrag", 0.02d, 0.0d, 1.0d);

        SHIP_SAMPLE_STEPS = b
                .comment("How many sample points to take along each axis of a ship's bounding",
                         "box when integrating a magnetic field over it. 1 = single closest-point",
                         "sample (1.0.0 behaviour). 3 = a 3×3×3 grid, so larger ships feel",
                         "varying force across their volume — naturally producing torque from",
                         "non-uniform fields. Higher = smoother but quadratically more work per",
                         "emitter per ship.")
                .translation("magnetization.configuration.physics.shipSampleSteps")
                .defineInRange("shipSampleSteps", 3, 1, 7);

        SHIP_BASELINE_SUSCEPTIBILITY = b
                .comment("Multiplier on external magnetic force a ship feels with NO ferromagnetic",
                         "blocks on board. 1.0 = forces apply at full strength regardless of ship",
                         "composition (1.0.0 behaviour). Lower values mean a pure-stone ship is",
                         "less responsive to magnets, while a ferrous-rich ship can scale up to",
                         "shipMaxSusceptibility.")
                .translation("magnetization.configuration.physics.shipBaselineSusceptibility")
                .defineInRange("shipBaselineSusceptibility", 1.0d, 0.0d, 10.0d);

        SHIP_PER_FERROUS_SUSCEPTIBILITY = b
                .comment("Susceptibility added per ferromagnetic block (#magnetization:ferromagnetic_blocks)",
                         "aboard a ship. 0.05 = 20 iron blocks doubles the ship's responsiveness over",
                         "baseline. Stacks with shipPerMagnetSusceptibility.")
                .translation("magnetization.configuration.physics.shipPerFerrousSusceptibility")
                .defineInRange("shipPerFerrousSusceptibility", 0.05d, 0.0d, 5.0d);

        SHIP_PER_MAGNET_SUSCEPTIBILITY = b
                .comment("Susceptibility added per magnet emitter block (#magnetization:magnetic_emitter)",
                         "aboard a ship. Magnets count as ferrous-plus — their pole does NOT change",
                         "the ship's polarity, but their presence raises the ship's responsiveness",
                         "above what an equivalent count of plain ferrous blocks would.")
                .translation("magnetization.configuration.physics.shipPerMagnetSusceptibility")
                .defineInRange("shipPerMagnetSusceptibility", 0.15d, 0.0d, 5.0d);

        SHIP_MAX_SUSCEPTIBILITY = b
                .comment("Upper cap on a ship's susceptibility multiplier. Prevents a 500-block",
                         "ferromagnetic airship from accelerating at 25× the rate of a small test",
                         "cube. Set high (e.g. 100) to effectively disable.")
                .translation("magnetization.configuration.physics.shipMaxSusceptibility")
                .defineInRange("shipMaxSusceptibility", 20.0d, 1.0d, 100.0d);

        SHIP_SCAN_INTERVAL_TICKS = b
                .comment("How often (ticks) a ship's magnetic state is rescanned. The scan walks",
                         "the contraption's loaded chunks to count ferromagnetic blocks, magnet",
                         "emitters, and polarity inverters. 20 = 1 s. Lower = more responsive to",
                         "block-level changes (e.g. an inverter destroyed by another mod) but more",
                         "CPU. Ships not currently inside any active field never get scanned",
                         "regardless of this interval. Bumped from the 1.0.1 default of 100 to",
                         "20 so polarity flips track block edits within a second.")
                .translation("magnetization.configuration.physics.shipScanIntervalTicks")
                .defineInRange("shipScanIntervalTicks", 20, 1, 6000);

        b.pop();

        b.comment("Per-emitter GUI ceilings. The in-game config menu can't dial above these",
                  "values, so server owners can prevent griefer-tier loadouts.")
         .translation("magnetization.configuration.guiLimits")
         .push("guiLimits");

        ELECTROMAGNET_MAX_STRENGTH = b
                .comment("Max strength tier the Electromagnet GUI can select.")
                .translation("magnetization.configuration.guiLimits.electromagnetMaxStrength")
                .defineEnum("electromagnetMaxStrength", MagneticStrength.EXTREME);
        ELECTROMAGNET_MAX_RANGE = b
                .comment("Max range (blocks) the Electromagnet GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.electromagnetMaxRange")
                .defineInRange("electromagnetMaxRange", 256, 0, 512);

        ANCHOR_MAX_STRENGTH = b
                .comment("Max strength tier the Magnetic Anchor GUI can select.")
                .translation("magnetization.configuration.guiLimits.anchorMaxStrength")
                .defineEnum("anchorMaxStrength", MagneticStrength.EXTREME);
        ANCHOR_MAX_RANGE = b
                .comment("Max range (blocks) the Magnetic Anchor GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.anchorMaxRange")
                .defineInRange("anchorMaxRange", 256, 0, 512);

        REPULSOR_MAX_STRENGTH = b
                .comment("Max strength tier the Repulsor Coil GUI can select.")
                .translation("magnetization.configuration.guiLimits.repulsorMaxStrength")
                .defineEnum("repulsorMaxStrength", MagneticStrength.EXTREME);
        REPULSOR_MAX_RANGE = b
                .comment("Max range (blocks) the Repulsor Coil GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.repulsorMaxRange")
                .defineInRange("repulsorMaxRange", 256, 0, 512);

        TRACTOR_MAX_STRENGTH = b
                .comment("Max strength tier the Tractor Beam GUI can select.")
                .translation("magnetization.configuration.guiLimits.tractorMaxStrength")
                .defineEnum("tractorMaxStrength", MagneticStrength.EXTREME);
        TRACTOR_MAX_RANGE = b
                .comment("Max range (blocks) the Tractor Beam GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.tractorMaxRange")
                .defineInRange("tractorMaxRange", 256, 0, 512);

        EXCAVATOR_MAX_STRENGTH = b
                .comment("Max strength tier the Magnetic Excavator GUI can select.",
                         "Strength controls pull force and tunneling speed per in-flight ship.")
                .translation("magnetization.configuration.guiLimits.excavatorMaxStrength")
                .defineEnum("excavatorMaxStrength", MagneticStrength.EXTREME);
        EXCAVATOR_MAX_RANGE = b
                .comment("Max column depth (blocks) the Magnetic Excavator can rip ores from.",
                         "Default high enough that EXTREME-tier excavators reach bedrock from the",
                         "surface (the runtime applies a 6× multiplier to the tier's nominal range,",
                         "so an EXTREME excavator scans 192 blocks deep with no GUI override).")
                .translation("magnetization.configuration.guiLimits.excavatorMaxRange")
                .defineInRange("excavatorMaxRange", 256, 1, 384);
        EXCAVATOR_MAX_BLOCKS_PER_CYCLE = b
                .comment("Hard cap on cells the Excavator's cone scan considers per pass,",
                         "regardless of strength + range. Acts as a safety against config",
                         "typos. Bedrock from surface is ~200 blocks; default leaves headroom.")
                .translation("magnetization.configuration.guiLimits.excavatorMaxBlocksPerCycle")
                .defineInRange("excavatorMaxBlocksPerCycle", 256, 1, 512);
        EXCAVATOR_MAX_IN_FLIGHT = b
                .comment("Max number of ferromagnetic blocks one Magnetic Excavator may pull at",
                         "the same time. Each pulled block becomes a Sable sub-level until it",
                         "reaches the emitter, so this also caps physics-simulation cost. Per-emitter",
                         "GUI control can dial individual excavators down below this admin ceiling.")
                .translation("magnetization.configuration.guiLimits.excavatorMaxInFlight")
                .defineInRange("excavatorMaxInFlight", 16, 1, 64);

        b.pop();

        b.comment("Disable individual content. Items go in 'disabledItems'; blocks in",
                  "'disabledBlocks' (paths from the magnetization namespace, e.g. 'electromagnet').",
                  "Disabled blocks emit no field, are hidden from the creative tab, and skip",
                  "their right-click GUI. Disabled items are hidden from the creative tab and",
                  "their special effects are skipped (grapple, compass, magnetized-armor pull).",
                  "Existing placed instances stay on the map but are inert.")
         .translation("magnetization.configuration.content")
         .push("content");

        DISABLED_BLOCKS = b
                .comment("Magnetization-namespaced block paths to disable. Example: [\"repulsor_coil\"].")
                .translation("magnetization.configuration.content.disabledBlocks")
                .defineListAllowEmpty("disabledBlocks", List.of(),
                        () -> "", o -> o instanceof String);
        DISABLED_ITEMS = b
                .comment("Magnetization-namespaced item paths to disable. Example: [\"magnetic_grapple\"].")
                .translation("magnetization.configuration.content.disabledItems")
                .defineListAllowEmpty("disabledItems", List.of(),
                        () -> "", o -> o instanceof String);

        b.pop();

        b.comment("Item / utility tuning.")
         .translation("magnetization.configuration.items")
         .push("items");

        GRAPPLE_COOLDOWN_TICKS = b
                .comment("Cooldown (game ticks; 20 = 1s) between Magnetic Grapple right-clicks.")
                .translation("magnetization.configuration.items.grappleCooldownTicks")
                .defineInRange("grappleCooldownTicks", 20, 0, 600);
        GRAPPLE_MAX_RANGE = b
                .comment("Max range in blocks the Magnetic Grapple will scan for an attractive emitter.")
                .translation("magnetization.configuration.items.grappleMaxRange")
                .defineInRange("grappleMaxRange", 24, 4, 128);
        COMPASS_RANGE = b
                .comment("Field Compass scan radius in blocks.")
                .translation("magnetization.configuration.items.compassRange")
                .defineInRange("compassRange", 16, 4, 128);

        b.pop();

        b.comment("Magnetized-tool signature abilities. Each tool in #magnetization:metal_tools",
                  "(or its vanilla tag — #minecraft:swords/pickaxes/axes/shovels/hoes) gets its",
                  "own ability once a polarity is stamped on it via the Electromagnet GUI. The",
                  "shared 'pull dropped items' behavior still runs regardless of these toggles.")
         .translation("magnetization.configuration.tools")
         .push("tools");

        TOOL_SWORD_YANK_ENABLED = b
                .comment("Magnetized swords yank opposite-pole armored targets one step toward the",
                         "attacker on hit (in addition to the existing Magnetized effect tag).")
                .translation("magnetization.configuration.tools.swordYankEnabled")
                .define("swordYankEnabled", true);

        TOOL_PICKAXE_ORE_RIP_ENABLED = b
                .comment("Sneaking with a magnetized pickaxe rips nearby ferromagnetic ore blocks",
                         "out of the world as items, periodically.")
                .translation("magnetization.configuration.tools.pickaxeOreRipEnabled")
                .define("pickaxeOreRipEnabled", true);
        TOOL_PICKAXE_RIP_RADIUS = b
                .comment("Block radius around the player scanned for ferromagnetic ores while",
                         "sneaking with a magnetized pickaxe.")
                .translation("magnetization.configuration.tools.pickaxeRipRadius")
                .defineInRange("pickaxeRipRadius", 4, 1, 16);
        TOOL_PICKAXE_RIP_INTERVAL_TICKS = b
                .comment("Ticks between ore-rip pulses. One block per pulse (capped to prevent griefing).")
                .translation("magnetization.configuration.tools.pickaxeRipIntervalTicks")
                .defineInRange("pickaxeRipIntervalTicks", 20, 4, 200);

        TOOL_AXE_PULSE_ENABLED = b
                .comment("Chopping a log with a magnetized axe sends a brief radial pull on nearby",
                         "ferromagnetic items + entities toward the player.")
                .translation("magnetization.configuration.tools.axePulseEnabled")
                .define("axePulseEnabled", true);

        TOOL_SHOVEL_PAN_ENABLED = b
                .comment("Digging dirt/sand/gravel/clay/soil with a magnetized shovel has a small",
                         "chance to drop trace iron nuggets and rarer raw magnetite.")
                .translation("magnetization.configuration.tools.shovelPanEnabled")
                .define("shovelPanEnabled", true);
        TOOL_SHOVEL_PAN_CHANCE = b
                .comment("Probability per shovel-target block break that a trace metal drops.")
                .translation("magnetization.configuration.tools.shovelPanChance")
                .defineInRange("shovelPanChance", 0.04d, 0.0d, 1.0d);

        TOOL_HOE_DOWSE_ENABLED = b
                .comment("Right-clicking with a magnetized hoe pings ferromagnetic ore blocks in",
                         "range with marker particles — a magnetic metal-detector.")
                .translation("magnetization.configuration.tools.hoeDowseEnabled")
                .define("hoeDowseEnabled", true);
        TOOL_HOE_DOWSE_RADIUS = b
                .comment("Block radius scanned by the hoe dowsing ping.")
                .translation("magnetization.configuration.tools.hoeDowseRadius")
                .defineInRange("hoeDowseRadius", 8, 2, 32);
        TOOL_HOE_DOWSE_COOLDOWN_TICKS = b
                .comment("Cooldown (ticks) between hoe dowsing pings.")
                .translation("magnetization.configuration.tools.hoeDowseCooldownTicks")
                .defineInRange("hoeDowseCooldownTicks", 60, 10, 600);

        b.pop();

        b.comment("World generation toggles. Note: the base magnetite ore vein generates",
                  "in every overworld biome regardless of these flags — these only control",
                  "the 'flavor' biome modifiers layered on top.")
         .translation("magnetization.configuration.worldgen")
         .push("worldgen");

        MAGNETIC_PEAKS_ENABLED = b
                .comment("If true, denser magnetite ore veins appear in mountain biomes",
                         "(#minecraft:is_mountain). Adds a flavor pass that surfaces in",
                         "snowy/jagged peaks. Default off — opt-in.")
                .translation("magnetization.configuration.worldgen.magneticPeaksEnabled")
                .define("magneticPeaksEnabled", false);

        ANOMALY_BIOME_ENABLED = b
                .comment("If true, the anomaly biome generates naturally and its runtime effects",
                         "activate inside it (field-compass spin, vanilla-compass spin, 1.5×",
                         "emitter strength, the random chaos field). With this off the biome",
                         "stays loaded as a resource so /locate biome magnetization:anomaly and",
                         "/magnetization tp anomaly still work, but it won't spawn on its own",
                         "and effects are inert. Default off — opt-in.")
                .translation("magnetization.configuration.worldgen.anomalyBiomeEnabled")
                .define("anomalyBiomeEnabled", false);

        ANOMALY_CHAOS_STRENGTH = b
                .comment("Multiplier applied to the anomaly's chaos field — scales both the",
                         "ship impulses and the player/item velocity injections. 1.0 = default",
                         "(ships peak around half a STRONG emitter at point-blank). Bump higher",
                         "for a more violent biome, or lower (0.1-0.3) for a subtle pull.",
                         "0 disables the chaos field entirely while leaving compass spin and the",
                         "emitter strength bonus intact.")
                .translation("magnetization.configuration.worldgen.anomalyChaosStrength")
                .defineInRange("anomalyChaosStrength", 1.0d, 0.0d, 10.0d);

        PETRIFIED_FOREST_ENABLED = b
                .comment("If true, the Petrified Forest biome registers a TerraBlender region",
                         "so it spawns naturally on the overworld (a cold/dry inland slot).",
                         "The biome JSON itself loads either way — turning this off just blocks",
                         "natural generation; /locate biome magnetization:petrified_forest still",
                         "works. Default off — opt-in.")
                .translation("magnetization.configuration.worldgen.petrifiedForestEnabled")
                .define("petrifiedForestEnabled", false);

        b.pop();

        b.comment("Lightning Induced Remnant Magnetism (LIRM). Real-world phenomenon — a",
                  "lightning strike's transient magnetic field permanently stamps polarity onto",
                  "nearby ferromagnetic material.")
         .translation("magnetization.configuration.lightning")
         .push("lightning");

        LIRM_ENABLED = b
                .comment("If true, every lightning bolt: (1) randomly magnetizes one unstamped",
                         "metal armor/tool piece on the struck entity, and (2) has a high chance",
                         "of converting nearby log blocks to petrified wood. Default true.")
                .translation("magnetization.configuration.lightning.lirmEnabled")
                .define("lirmEnabled", true);

        b.pop();

        b.comment("Diagnostic logging toggles. Off by default in production.")
         .translation("magnetization.configuration.debug")
         .push("debug");

        DEBUG_LOGGING = b
                .comment("Master toggle for FieldApplicator + anchor-binding debug logs.",
                         "Set true while diagnosing emitter behavior; leave false on busy servers.")
                .translation("magnetization.configuration.debug.debugLogging")
                .define("debugLogging", false);

        b.pop();

        b.comment("Required permission level for each /magnetization command group.",
                  "0 = any player can run, 2 = operator (vanilla default), 3 or 4 =",
                  "higher op tiers. Levels read live from the spec via brigadier's",
                  ".requires() predicate so changes take effect on the next command",
                  "invocation without restart.")
         .translation("magnetization.configuration.commands")
         .push("commands");

        COMMAND_DEBUG_PERMISSION = b
                .comment("Permission level for /magnetization debug field|forceAt.")
                .translation("magnetization.configuration.commands.debugPermission")
                .defineInRange("debugPermission", 2, 0, 4);
        COMMAND_SPAWN_TEST_PERMISSION = b
                .comment("Permission level for /magnetization spawn_test_ship|spawn_test_anchor.")
                .translation("magnetization.configuration.commands.spawnTestPermission")
                .defineInRange("spawnTestPermission", 2, 0, 4);
        COMMAND_SHIP_UTIL_PERMISSION = b
                .comment("Permission level for /magnetization clear_phantoms|shatter_all_ships|push_nearest_ship.",
                         "These mutate world state (destroy sub-levels), so a higher level is",
                         "the sensible default.")
                .translation("magnetization.configuration.commands.shipUtilPermission")
                .defineInRange("shipUtilPermission", 2, 0, 4);
        COMMAND_LIRM_PERMISSION = b
                .comment("Permission level for /magnetization lirm strike|stamp|inspect|clear|fields.",
                         "Includes lightning-summoning, so keep it op-gated on shared servers.")
                .translation("magnetization.configuration.commands.lirmPermission")
                .defineInRange("lirmPermission", 2, 0, 4);
        COMMAND_TP_PERMISSION = b
                .comment("Permission level for /magnetization tp anomaly|petrified_forest.",
                         "Default 0 = any player — biome travel is a quality-of-life shortcut,",
                         "not a destructive admin tool. Bump to 2 if you want to lock travel.")
                .translation("magnetization.configuration.commands.tpPermission")
                .defineInRange("tpPermission", 0, 0, 4);

        b.pop();

        SPEC = b.build();
    }

    /** Helper: returns the configured permission level, or a fallback if the
     *  config isn't loaded yet (e.g. during early registration). */
    private static int permissionOr(final ModConfigSpec.IntValue v, final int fallback) {
        try { return v.get(); } catch (final Throwable t) { return fallback; }
    }

    public static int commandDebugPermission()    { return permissionOr(COMMAND_DEBUG_PERMISSION, 2); }
    public static int commandSpawnTestPermission(){ return permissionOr(COMMAND_SPAWN_TEST_PERMISSION, 2); }
    public static int commandShipUtilPermission() { return permissionOr(COMMAND_SHIP_UTIL_PERMISSION, 2); }
    public static int commandLirmPermission()     { return permissionOr(COMMAND_LIRM_PERMISSION, 2); }
    public static int commandTpPermission()       { return permissionOr(COMMAND_TP_PERMISSION, 0); }

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
