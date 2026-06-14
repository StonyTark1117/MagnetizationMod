package com.stonytark.magnetization.config;

import com.stonytark.magnetization.api.MagneticStrength;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server-side config for tunable physics constants. Values are read live from the
 * spec, so a config reload propagates immediately without restart.
 */
public final class MagConfig {

    /** SERVER-type spec: per-world, server-authoritative, synced server→client.
     *  Holds admin/balance categories (guiLimits, debug, commands) that should
     *  stay under the world owner's control and are NOT editable from the title
     *  screen. */
    public static final ModConfigSpec SPEC;

    /** COMMON-type spec: a single global file ({@code config/magnetization-common.toml})
     *  loaded on both client and server early in mod loading. Holds the
     *  player-facing and worldgen-affecting categories (physics, content, items,
     *  tools, worldgen, lightning, compat). Because it isn't per-world, it can be
     *  edited from the main menu (Mods → Magnetization → Config) BEFORE a world is
     *  created — essential for the worldgen settings (biome rarity/enable) that are
     *  baked in at world generation. On a dedicated server the owner edits the same
     *  file in {@code config/}. */
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.DoubleValue STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ENTITY_VELOCITY_SCALE;
    public static final ModConfigSpec.DoubleValue CONICAL_HALF_ANGLE_COS;
    public static final ModConfigSpec.DoubleValue MAX_ACCEL_PER_TICK;
    public static final ModConfigSpec.DoubleValue SHIP_LINEAR_DRAG;
    public static final ModConfigSpec.DoubleValue SHIP_ANGULAR_DRAG;
    public static final ModConfigSpec.IntValue    SHIP_SAMPLE_STEPS;
    public static final ModConfigSpec.DoubleValue SHIP_BASELINE_SUSCEPTIBILITY;
    public static final ModConfigSpec.DoubleValue SHIP_PER_FERROUS_SUSCEPTIBILITY;
    public static final ModConfigSpec.DoubleValue SHIP_PER_MAGNET_SUSCEPTIBILITY;
    public static final ModConfigSpec.DoubleValue SHIP_MAX_SUSCEPTIBILITY;
    public static final ModConfigSpec.IntValue    SHIP_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue EXCLUDE_CONNECTED_SUBLEVELS;

    public static final ModConfigSpec.BooleanValue ANOMALY_BIOME_ENABLED;
    public static final ModConfigSpec.EnumValue<com.stonytark.magnetization.worldgen.BiomeRarity> ANOMALY_BIOME_RARITY;
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

    /** Master switch for the passive magnetite → maghemite inventory decay
     *  driven by {@code MaghemiteDecayHandler}. Off by default; flip on for
     *  the slow-rusting hoarder behaviour. Named after the source material
     *  (magnetite) since that's the thing that oxidises. */
    /** When false, worn metal armor stops responding to magnetic fields entirely —
     *  players (and armored mobs) are no longer pulled or pushed by ores, emitters
     *  or any field through their gear. Default true (current behaviour). Added for
     *  players who kept getting yanked into ore deposits and hazards by their armor. */
    public static final ModConfigSpec.BooleanValue ARMOR_REACTS_TO_FIELDS;
    /** When true (default), right-clicking an interactible emitter block while
     *  holding a block PLACES that block, and shift-right-click opens the
     *  GUI / flips polarity. When false, the legacy behavior: right-click
     *  interacts and shift-right-click places. Empty hand / non-block items
     *  always interact on right-click regardless. */
    public static final ModConfigSpec.BooleanValue BLOCK_PLACEMENT_FIRST;

    public static final ModConfigSpec.BooleanValue MAGNETITE_OXIDATION_ENABLED;
    /** Ticks a stamped magnetite stack waits before converting in place to
     *  its maghemite equivalent. Only consulted when oxidation is enabled. */
    public static final ModConfigSpec.IntValue     MAGNETITE_OXIDATION_TICKS;

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
    /** Cosmic Compass meteorite-scan radius, in blocks. */
    public static final ModConfigSpec.IntValue COSMIC_COMPASS_RANGE;

    /** Repulsor Gun cone range in blocks. */
    public static final ModConfigSpec.IntValue    REPULSOR_GUN_RANGE;
    /** Cosine of the cone half-angle. Narrower than the Repulsor Coil's 45°
     *  to give the gun precision at distance. */
    public static final ModConfigSpec.DoubleValue REPULSOR_GUN_CONICAL_HALF_ANGLE_COS;
    /** Self-recoil velocity magnitude when aiming at a magnetic emitter block. */
    public static final ModConfigSpec.DoubleValue REPULSOR_GUN_SELF_RECOIL_STRENGTH;
    /** Cooldown (ticks) between Repulsor Gun shots. */
    public static final ModConfigSpec.IntValue    REPULSOR_GUN_COOLDOWN_TICKS;
    /** Base linear impulse the gun applies to a ship per shot (Sable units —
     *  kg·m/s). Resulting velocity change = impulse / ship-mass, so small
     *  ships fly farther than large ones for the same impulse. */
    public static final ModConfigSpec.DoubleValue REPULSOR_GUN_SHIP_IMPULSE;
    /** Per-shot cap on the velocity change a ship can receive (m/s). Prevents
     *  a 1×1 magnetite test cube from launching across the world when the
     *  impulse/mass ratio explodes. */
    public static final ModConfigSpec.DoubleValue REPULSOR_GUN_SHIP_MAX_VELOCITY_DELTA;

    // Per-tool magnetized-effect toggles. Each tool gets a unique signature
    // ability on top of the shared "pull dropped ferromagnetic items" handler.
    public static final ModConfigSpec.BooleanValue LIRM_ENABLED;
    public static final ModConfigSpec.BooleanValue PETRIFIED_FOREST_ENABLED;
    public static final ModConfigSpec.EnumValue<com.stonytark.magnetization.worldgen.BiomeRarity> PETRIFIED_FOREST_RARITY;
    public static final ModConfigSpec.BooleanValue MAGNETIC_GRAVEL_IN_VANILLA_BIOMES;
    public static final ModConfigSpec.IntValue     METEORITE_DECAY_TICKS;
    public static final ModConfigSpec.IntValue     METEORITE_SAPLING_GROW_TICKS;
    public static final ModConfigSpec.IntValue     MAGNETIC_SWITCH_RANGE;
    public static final ModConfigSpec.BooleanValue AE2_METEORITE_HOOK_ENABLED;
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

    /** How our Magnetized status effect coexists with Alex's Caves' Magnetizing
     *  effect when both mods are loaded. Three modes: BOTH (parallel, default
     *  — both effects can be applied independently); OURS_ONLY (when AC's
     *  effect would be applied to an entity, swap it for ours); THEIRS_ONLY
     *  (when ours would be applied, swap it for AC's). Only consulted if
     *  {@code alexscaves} is on the mod list. */
    public enum AlexsCavesPotionMode { BOTH, OURS_ONLY, THEIRS_ONLY }
    public static final ModConfigSpec.EnumValue<AlexsCavesPotionMode> ALEXSCAVES_POTION_MODE;

    /** Whether the vanilla Minecraft compass spins erratically inside the
     *  Magnetic Anomaly biome. Default true — matches the in-mod theming that
     *  any mechanical compass would be scrambled by the field flux. Set false
     *  to leave vanilla compass behaviour untouched (useful when other mods
     *  depend on stable compass readings). Implemented via Mixin into
     *  {@code CompassAngleState.calculate}. */
    public static final ModConfigSpec.BooleanValue ANOMALY_AFFECTS_VANILLA_COMPASS;

    /** Same scramble extended to Nature's Compass when that mod is loaded. */
    public static final ModConfigSpec.BooleanValue ANOMALY_AFFECTS_NATURES_COMPASS;

    /** Same scramble extended to Explorer's Compass when that mod is loaded. */
    public static final ModConfigSpec.BooleanValue ANOMALY_AFFECTS_EXPLORERS_COMPASS;

    /** Admin toggle: redstone signal counts as a valid power source for redstone-
     *  powered emitters (Electromagnet, Anchor, Repulsor, Tractor Beam, Excavator).
     *  Default true. Set false to force players to feed an FE source. */
    public static final ModConfigSpec.BooleanValue ALLOW_REDSTONE_POWER;
    /** Admin toggle: FE/RF energy counts as a valid power source for emitters.
     *  Default true. Set false to disable energy-driven emitters entirely. The
     *  capability is still exposed (NeoForge can't conditionally hide it) but
     *  energy stays buffered and never activates the field. */
    public static final ModConfigSpec.BooleanValue ALLOW_ENERGY_POWER;
    /** Give the Patchouli field manual to each player on their first login.
     *  Default true. Per-player flag is stored in the player's persistent NBT
     *  so reconnecting doesn't re-give the manual. Set false if your server
     *  prefers players craft it from the (cheap) recipes. */
    public static final ModConfigSpec.BooleanValue FIELD_MANUAL_AUTO_GIVE;
    /** Internal FE buffer capacity per emitter, in FE. Default 50_000 (about
     *  5_000 ticks = 250s of operation at the default drain). */
    public static final ModConfigSpec.IntValue EMITTER_ENERGY_CAPACITY;
    /** FE consumed per tick while an emitter is energy-driven. Default 10 FE/tick.
     *  Multiply by 20 for FE/s. */
    public static final ModConfigSpec.IntValue EMITTER_ENERGY_DRAIN_PER_TICK;
    /** Max FE per tick the emitter accepts from external sources. Higher values
     *  refill the buffer faster from heavy cabling. Default 200 FE/tick. */
    public static final ModConfigSpec.IntValue EMITTER_ENERGY_TRANSFER_RATE;

    static {
        // Two builders: `b` accumulates the COMMON (global, main-menu-editable)
        // categories; `sb` accumulates the SERVER (per-world, admin) categories.
        // Each builder's push/pop stack is independent and self-balanced.
        final ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        final ModConfigSpec.Builder sb = new ModConfigSpec.Builder();

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

        SHIP_ANGULAR_DRAG = b
                .comment("Per-tick angular-velocity damping applied to a ship while it is being",
                         "pulled by any magnetic emitter. Counterpart to shipLinearDrag — without",
                         "this, the torque generated by off-center sample forces (see",
                         "shipSampleSteps) could keep a ship spinning indefinitely under sustained",
                         "pull. 0.05 = the ship loses 5% of its current spin every tick a magnet is",
                         "acting on it. Set to 0 to disable angular drag entirely.")
                .translation("magnetization.configuration.physics.shipAngularDrag")
                .defineInRange("shipAngularDrag", 0.05d, 0.0d, 1.0d);

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

        EXCLUDE_CONNECTED_SUBLEVELS = b
                .comment("When an emitter is mounted on a contraption, exclude the ENTIRE assembly",
                         "it belongs to from that emitter's magnetic force — not just the single",
                         "sub-level the emitter physically sits on. In Create: Aeronautics an",
                         "aircraft built with bearings, springs or hinges is several Sable",
                         "sub-levels joined by physics constraints; with this off (1.1.x and",
                         "earlier behaviour) a magnet on the body would yank its own bearing-mounted",
                         "subgroups around, which both looks wrong (the craft pulls on itself) and",
                         "can spike or freeze the physics solver as the magnetic pull fights the",
                         "constraint that holds the parts together. Leave on unless you specifically",
                         "want emitters to act on connected subgroups of their own craft.")
                .translation("magnetization.configuration.physics.excludeConnectedSubLevels")
                .define("excludeConnectedSubLevels", true);

        b.pop();

        sb.comment("Per-emitter GUI ceilings. The in-game config menu can't dial above these",
                  "values, so server owners can prevent griefer-tier loadouts.")
         .translation("magnetization.configuration.guiLimits")
         .push("guiLimits");

        ELECTROMAGNET_MAX_STRENGTH = sb
                .comment("Max strength tier the Electromagnet GUI can select.")
                .translation("magnetization.configuration.guiLimits.electromagnetMaxStrength")
                .defineEnum("electromagnetMaxStrength", MagneticStrength.EXTREME);
        ELECTROMAGNET_MAX_RANGE = sb
                .comment("Max range (blocks) the Electromagnet GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.electromagnetMaxRange")
                .defineInRange("electromagnetMaxRange", 256, 0, 512);

        ANCHOR_MAX_STRENGTH = sb
                .comment("Max strength tier the Magnetic Anchor GUI can select.")
                .translation("magnetization.configuration.guiLimits.anchorMaxStrength")
                .defineEnum("anchorMaxStrength", MagneticStrength.EXTREME);
        ANCHOR_MAX_RANGE = sb
                .comment("Max range (blocks) the Magnetic Anchor GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.anchorMaxRange")
                .defineInRange("anchorMaxRange", 256, 0, 512);

        REPULSOR_MAX_STRENGTH = sb
                .comment("Max strength tier the Repulsor Coil GUI can select.")
                .translation("magnetization.configuration.guiLimits.repulsorMaxStrength")
                .defineEnum("repulsorMaxStrength", MagneticStrength.EXTREME);
        REPULSOR_MAX_RANGE = sb
                .comment("Max range (blocks) the Repulsor Coil GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.repulsorMaxRange")
                .defineInRange("repulsorMaxRange", 256, 0, 512);

        TRACTOR_MAX_STRENGTH = sb
                .comment("Max strength tier the Tractor Beam GUI can select.")
                .translation("magnetization.configuration.guiLimits.tractorMaxStrength")
                .defineEnum("tractorMaxStrength", MagneticStrength.EXTREME);
        TRACTOR_MAX_RANGE = sb
                .comment("Max range (blocks) the Tractor Beam GUI can dial up to.")
                .translation("magnetization.configuration.guiLimits.tractorMaxRange")
                .defineInRange("tractorMaxRange", 256, 0, 512);

        EXCAVATOR_MAX_STRENGTH = sb
                .comment("Max strength tier the Magnetic Excavator GUI can select.",
                         "Strength controls pull force and tunneling speed per in-flight ship.")
                .translation("magnetization.configuration.guiLimits.excavatorMaxStrength")
                .defineEnum("excavatorMaxStrength", MagneticStrength.EXTREME);
        EXCAVATOR_MAX_RANGE = sb
                .comment("Max column depth (blocks) the Magnetic Excavator can rip ores from.",
                         "Default high enough that EXTREME-tier excavators reach bedrock from the",
                         "surface (the runtime applies a 6× multiplier to the tier's nominal range,",
                         "so an EXTREME excavator scans 192 blocks deep with no GUI override).")
                .translation("magnetization.configuration.guiLimits.excavatorMaxRange")
                .defineInRange("excavatorMaxRange", 256, 1, 384);
        EXCAVATOR_MAX_BLOCKS_PER_CYCLE = sb
                .comment("Hard cap on cells the Excavator's cone scan considers per pass,",
                         "regardless of strength + range. Acts as a safety against config",
                         "typos. Bedrock from surface is ~200 blocks; default leaves headroom.")
                .translation("magnetization.configuration.guiLimits.excavatorMaxBlocksPerCycle")
                .defineInRange("excavatorMaxBlocksPerCycle", 256, 1, 512);
        EXCAVATOR_MAX_IN_FLIGHT = sb
                .comment("Max number of ferromagnetic blocks one Magnetic Excavator may pull at",
                         "the same time. Each pulled block becomes a Sable sub-level until it",
                         "reaches the emitter, so this also caps physics-simulation cost. Per-emitter",
                         "GUI control can dial individual excavators down below this admin ceiling.")
                .translation("magnetization.configuration.guiLimits.excavatorMaxInFlight")
                .defineInRange("excavatorMaxInFlight", 16, 1, 64);

        sb.pop();

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

        ARMOR_REACTS_TO_FIELDS = b
                .comment("Whether worn metal armor makes its wearer react to magnetic fields.",
                         "On (default): a player or mob in metal armor is pulled toward / pushed",
                         "away from emitters, magnetic ores and other fields, in proportion to how",
                         "many pieces they wear (magnetized pieces pull harder). Off: armor is",
                         "magnetically inert — the wearer feels no field through their gear. Set",
                         "this off if you keep getting dragged into ore deposits or hazards by your",
                         "own armor. Does NOT affect entities that are magnetizable in their own",
                         "right (e.g. iron golems via the magnetizable_entities tag), magnetized",
                         "armor's other uses, or ferromagnetic item drops.")
                .translation("magnetization.configuration.content.armorReactsToFields")
                .define("armorReactsToFields", true);

        BLOCK_PLACEMENT_FIRST = b
                .comment("How right-clicking our interactible blocks (Electromagnet, Anchor,",
                         "Repulsor, Tractor Beam, Excavator, Permanent/Temporary Magnet) behaves",
                         "while holding a BLOCK.",
                         "On (default): right-click PLACES the held block against ours (like any",
                         "normal block); shift-right-click opens the GUI / flips polarity. This",
                         "fixes the complaint that you couldn't build against our blocks.",
                         "Off (legacy): right-click opens the GUI / flips polarity; you must",
                         "shift-right-click to place a block.",
                         "Either way, an empty hand or a non-block item (tool) always interacts",
                         "on right-click.")
                .translation("magnetization.configuration.content.blockPlacementFirst")
                .define("blockPlacementFirst", true);

        MAGNETITE_OXIDATION_ENABLED = b
                .comment("Master switch for the magnetite → maghemite passive inventory decay.",
                         "Off by default — players found the slow rusting of hoarded magnetite",
                         "more confusing than satisfying. Turn on to re-enable the feature.",
                         "When off, MAGNETITE_OXIDATION_AGE stamps are also skipped so existing",
                         "stacks don't accumulate stale timers.")
                .translation("magnetization.configuration.content.magnetiteOxidationEnabled")
                .define("magnetiteOxidationEnabled", false);

        MAGNETITE_OXIDATION_TICKS = b
                .comment("Ticks a stamped magnetite stack waits before converting to its",
                         "maghemite equivalent. Default 168000 = 1 in-game week (7 days)",
                         "of survival-time per stack. Only consulted when",
                         "magnetiteOxidationEnabled is true.")
                .translation("magnetization.configuration.content.magnetiteOxidationTicks")
                .defineInRange("magnetiteOxidationTicks", 168000, 1200, 2_400_000);

        MAGNETIC_SWITCH_RANGE = b
                .comment("Magnetic Switch ship-detection radius in blocks. Switch outputs a 0–15",
                         "redstone signal whose strength ramps linearly with how close the nearest",
                         "sub-level (ship/contraption) is to the switch within this radius.",
                         "Default 8 blocks. Larger values let one switch cover bigger docking",
                         "bays; smaller values force tighter sensor placement.")
                .translation("magnetization.configuration.content.magneticSwitchRange")
                .defineInRange("magneticSwitchRange", 8, 1, 64);

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
        COSMIC_COMPASS_RANGE = b
                .comment("Cosmic Compass meteorite-scan radius in blocks. Much larger than the",
                         "Field Compass — meteorites are rare worldgen, so a wider arc justifies",
                         "the dedicated item.")
                .translation("magnetization.configuration.items.cosmicCompassRange")
                .defineInRange("cosmicCompassRange", 512, 32, 4096);

        REPULSOR_GUN_RANGE = b
                .comment("Repulsor Gun cone range in blocks. Shorter than the Magnetic Grapple's",
                         "scan because the gun is meant for crowd control and self-launch, not",
                         "long-range traversal.")
                .translation("magnetization.configuration.items.repulsorGunRange")
                .defineInRange("repulsorGunRange", 12, 2, 64);

        REPULSOR_GUN_CONICAL_HALF_ANGLE_COS = b
                .comment("Cosine of the Repulsor Gun's cone half-angle. 0.866 = 30° half-angle",
                         "(60° total). Narrower than the Repulsor Coil's 45° so the gun rewards",
                         "aim at distance.")
                .translation("magnetization.configuration.items.repulsorGunConicalHalfAngleCos")
                .defineInRange("repulsorGunConicalHalfAngleCos", 0.866d, 0.0d, 0.999d);

        REPULSOR_GUN_SELF_RECOIL_STRENGTH = b
                .comment("Self-recoil velocity magnitude (blocks/tick) when aiming at a magnetic",
                         "emitter block. 0.8 = enough to launch a player ~6 blocks back at point",
                         "blank. Falloff linearly to zero at the cone's range. Set to 0 to disable",
                         "self-recoil — gun then only pushes targets, never the holder.")
                .translation("magnetization.configuration.items.repulsorGunSelfRecoilStrength")
                .defineInRange("repulsorGunSelfRecoilStrength", 0.8d, 0.0d, 5.0d);

        REPULSOR_GUN_COOLDOWN_TICKS = b
                .comment("Cooldown (game ticks; 20 = 1s) between Repulsor Gun shots. Prevents",
                         "spam-bouncing exploits while still allowing rapid follow-ups.")
                .translation("magnetization.configuration.items.repulsorGunCooldownTicks")
                .defineInRange("repulsorGunCooldownTicks", 20, 0, 600);

        REPULSOR_GUN_SHIP_IMPULSE = b
                .comment("Base linear impulse (Sable units — kg·m/s) the Repulsor Gun applies",
                         "to a ship per shot. Velocity change = impulse / ship-mass, capped to",
                         "repulsorGunShipMaxVelocityDelta. Default 100000 sizes 1×1 and 3×3",
                         "ships to hit the cap (strong push), 5×5 begins to feel mass scaling",
                         "(noticeable but tough), and 10×10+ barely budges. Bump 5–10× if even",
                         "1×1 ships feel weak; dial back if 5×5+ launches across the map.")
                .translation("magnetization.configuration.items.repulsorGunShipImpulse")
                .defineInRange("repulsorGunShipImpulse", 100_000.0d, 0.0d, 100_000_000.0d);

        REPULSOR_GUN_SHIP_MAX_VELOCITY_DELTA = b
                .comment("Per-shot cap on the velocity change a ship can receive from one Repulsor",
                         "Gun shot. Without this, the impulse/mass ratio would launch tiny test ships",
                         "across the world. Default 64 m/s = a tiny ship launches at terminal velocity.")
                .translation("magnetization.configuration.items.repulsorGunShipMaxVelocityDelta")
                .defineInRange("repulsorGunShipMaxVelocityDelta", 64.0d, 0.0d, 500.0d);

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

        ANOMALY_BIOME_ENABLED = b
                .comment("If true, the anomaly biome generates naturally and its runtime effects",
                         "activate (field-compass spin, vanilla-compass spin, 1.5× emitter",
                         "strength bonus, the random chaos field). With this off the biome",
                         "stays loaded as a resource so /place biome magnetization:anomaly and",
                         "/locate biome magnetization:anomaly still work, but it won't spawn on",
                         "its own and effects are inert. Default off — opt-in.")
                .translation("magnetization.configuration.worldgen.anomalyBiomeEnabled")
                .define("anomalyBiomeEnabled", false);

        ANOMALY_BIOME_RARITY = b
                .comment("How rare the anomaly biome is when generating naturally. Controls the",
                         "width of TerraBlender climate-parameter spans — narrower = fewer chunks",
                         "match. EXTREMELY_RARE (default) pins a single point so you may go",
                         "tens of thousands of blocks without seeing one. VERY_RARE / RARE widen",
                         "the spans incrementally. COMMON is mostly for testing — it overlaps so",
                         "much vanilla parameter space the anomaly turns up every few biomes.")
                .translation("magnetization.configuration.worldgen.anomalyBiomeRarity")
                .defineEnum("anomalyBiomeRarity", com.stonytark.magnetization.worldgen.BiomeRarity.EXTREMELY_RARE);

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
                .comment("If true, the Petrified Forest biome spawns naturally on the overworld",
                         "(a cold/dry inland slot) and its runtime effects activate (lightning-",
                         "storm-driven LIRM magnetization stamping on nearby items). With this",
                         "off the biome JSON loads but doesn't spawn naturally; /place biome",
                         "magnetization:petrified_forest still works. Default off — opt-in.")
                .translation("magnetization.configuration.worldgen.petrifiedForestEnabled")
                .define("petrifiedForestEnabled", false);

        PETRIFIED_FOREST_RARITY = b
                .comment("How rare the petrified forest biome is when generating naturally. See",
                         "anomalyBiomeRarity for what each tier means. Default RARE — encounterable",
                         "on most overworld runs but not in every biome row.")
                .translation("magnetization.configuration.worldgen.petrifiedForestRarity")
                .defineEnum("petrifiedForestRarity", com.stonytark.magnetization.worldgen.BiomeRarity.RARE);

        MAGNETIC_GRAVEL_IN_VANILLA_BIOMES = b
                .comment("If true, sparse patches of magnetic_gravel (the iron-flecked falling",
                         "block from the anomaly biome) appear in vanilla overworld biomes at",
                         "very low frequency (~1-in-24 chunks attempt). Survival players who",
                         "never visit an Anomaly biome can still find a small supply through",
                         "normal world exploration. Default on; flip off if you want the",
                         "block to be strictly anomaly-exclusive.")
                .translation("magnetization.configuration.worldgen.magneticGravelInVanillaBiomes")
                .define("magneticGravelInVanillaBiomes", true);

        METEORITE_DECAY_TICKS = b
                .comment("Ticks between full charge and full decay on a meteorite_core. Default",
                         "12000 = 10 in-game minutes (EXTREME for the first third, STRONG for",
                         "the second, WEAK for the final, then inert). Bump to slow the decay",
                         "loop for long-form survival pacing; drop to make refilling a constant",
                         "chore.")
                .translation("magnetization.configuration.worldgen.meteoriteDecayTicks")
                .defineInRange("meteoriteDecayTicks", 12000, 200, 240000);

        METEORITE_SAPLING_GROW_TICKS = b
                .comment("Ticks before a planted meteorite_sapling matures into a meteorite_core.",
                         "Default 36000 = 30 in-game minutes (chunk must stay loaded the whole",
                         "time). Reduce to make sapling farming faster; raise for stricter",
                         "scarcity.")
                .translation("magnetization.configuration.worldgen.meteoriteSaplingGrowTicks")
                .defineInRange("meteoriteSaplingGrowTicks", 36000, 200, 480000);

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

        sb.comment("Diagnostic logging toggles. Off by default in production.")
         .translation("magnetization.configuration.debug")
         .push("debug");

        DEBUG_LOGGING = sb
                .comment("Master toggle for FieldApplicator + anchor-binding debug logs.",
                         "Set true while diagnosing emitter behavior; leave false on busy servers.")
                .translation("magnetization.configuration.debug.debugLogging")
                .define("debugLogging", false);

        sb.pop();

        sb.comment("Required permission level for each /magnetization command group.",
                  "0 = any player can run, 2 = operator (vanilla default), 3 or 4 =",
                  "higher op tiers. Levels read live from the spec via brigadier's",
                  ".requires() predicate so changes take effect on the next command",
                  "invocation without restart.")
         .translation("magnetization.configuration.commands")
         .push("commands");

        COMMAND_DEBUG_PERMISSION = sb
                .comment("Permission level for /magnetization debug field|forceAt.")
                .translation("magnetization.configuration.commands.debugPermission")
                .defineInRange("debugPermission", 2, 0, 4);
        COMMAND_SPAWN_TEST_PERMISSION = sb
                .comment("Permission level for /magnetization spawn_test_ship|spawn_test_anchor.")
                .translation("magnetization.configuration.commands.spawnTestPermission")
                .defineInRange("spawnTestPermission", 2, 0, 4);
        COMMAND_SHIP_UTIL_PERMISSION = sb
                .comment("Permission level for /magnetization clear_phantoms|shatter_all_ships|push_nearest_ship.",
                         "These mutate world state (destroy sub-levels), so a higher level is",
                         "the sensible default.")
                .translation("magnetization.configuration.commands.shipUtilPermission")
                .defineInRange("shipUtilPermission", 2, 0, 4);
        COMMAND_LIRM_PERMISSION = sb
                .comment("Permission level for /magnetization lirm strike|stamp|inspect|clear|fields.",
                         "Includes lightning-summoning, so keep it op-gated on shared servers.")
                .translation("magnetization.configuration.commands.lirmPermission")
                .defineInRange("lirmPermission", 2, 0, 4);
        COMMAND_TP_PERMISSION = sb
                .comment("Permission level for /magnetization tp anomaly|petrified_forest.",
                         "Default 0 = any player — biome travel is a quality-of-life shortcut,",
                         "not a destructive admin tool. Bump to 2 if you want to lock travel.")
                .translation("magnetization.configuration.commands.tpPermission")
                .defineInRange("tpPermission", 0, 0, 4);

        sb.pop();

        b.comment("Cross-mod compatibility tuning. These only take effect when the",
                  "named mod is installed; otherwise they're inert.")
         .translation("magnetization.configuration.compat")
         .push("compat");

        ALEXSCAVES_POTION_MODE = b
                .comment("How our 'Magnetized' status effect coexists with Alex's Caves'",
                         "'Magnetizing' effect when AC is installed.",
                         "  BOTH       (default) — both effects work independently; an entity can carry both at once.",
                         "  OURS_ONLY  — when AC's Magnetizing would be applied, we swap it for our Magnetized at the same duration/amplifier so only one system drives the pull.",
                         "  THEIRS_ONLY — when our Magnetized would be applied, we swap it for AC's so AC's pull is canonical.",
                         "Pick OURS_ONLY if you find both effects stacking too aggressively, or",
                         "THEIRS_ONLY if you prefer AC's particle/visual treatment.")
                .translation("magnetization.configuration.compat.alexsCavesPotionMode")
                .defineEnum("alexsCavesPotionMode", AlexsCavesPotionMode.BOTH);

        ALLOW_REDSTONE_POWER = b
                .comment("Whether redstone signal activates the addon's redstone-powered emitters",
                         "(Electromagnet, Magnetic Anchor, Repulsor Coil, Tractor Beam, Magnetic Excavator).",
                         "Set false to force players to feed FE/RF energy — useful on hardcore-leaning",
                         "servers where infinite-redstone setups would trivialise the gameplay loop.")
                .translation("magnetization.configuration.compat.allowRedstonePower")
                .define("allowRedstonePower", true);

        ALLOW_ENERGY_POWER = b
                .comment("Whether FE/RF energy activates the addon's redstone-powered emitters.",
                         "Default true. Any mod that provides FE (Create: Crafts & Additions, Mekanism,",
                         "Thermal, Immersive Engineering generators, AE2, etc.) can drive an emitter.",
                         "Set false to disable energy-driven emitters — they fall back to redstone-only.")
                .translation("magnetization.configuration.compat.allowEnergyPower")
                .define("allowEnergyPower", true);

        FIELD_MANUAL_AUTO_GIVE = b
                .comment("Give the Patchouli field manual to each player on their first login.",
                         "Default true. The per-player flag is stored in persistent NBT so",
                         "reconnecting doesn't re-give the manual. Players can also craft it",
                         "from cheap recipes (book + raw_magnetite OR iron ingot OR lodestone).",
                         "Set false on servers that prefer players craft it themselves.")
                .translation("magnetization.configuration.compat.fieldManualAutoGive")
                .define("fieldManualAutoGive", true);

        EMITTER_ENERGY_CAPACITY = b
                .comment("Internal FE buffer capacity per emitter. 50000 ≈ 4 minutes of continuous",
                         "operation at the default drain. Higher = longer ride on a single fill.")
                .translation("magnetization.configuration.compat.emitterEnergyCapacity")
                .defineInRange("emitterEnergyCapacity", 50_000, 1_000, 10_000_000);

        EMITTER_ENERGY_DRAIN_PER_TICK = b
                .comment("FE consumed per tick while an emitter is energy-driven. 10 FE/tick =",
                         "200 FE/s. Set 0 to make energy power free (capability still required",
                         "just for the buffer to exist).")
                .translation("magnetization.configuration.compat.emitterEnergyDrainPerTick")
                .defineInRange("emitterEnergyDrainPerTick", 10, 0, 100_000);

        EMITTER_ENERGY_TRANSFER_RATE = b
                .comment("Max FE per tick the emitter accepts from external sources. Higher =",
                         "refills the buffer faster from heavy cabling. 200 FE/tick is enough for",
                         "20× the default drain — sturdy under load.")
                .translation("magnetization.configuration.compat.emitterEnergyTransferRate")
                .defineInRange("emitterEnergyTransferRate", 200, 1, 1_000_000);

        ANOMALY_AFFECTS_VANILLA_COMPASS = b
                .comment("Whether the vanilla Minecraft compass needle spins erratically when the",
                         "holder is inside the Magnetic Anomaly biome. Default true. Set false to",
                         "leave vanilla compass behaviour untouched — useful when other mods rely",
                         "on stable compass readings (waystone mods, navigation mods).")
                .translation("magnetization.configuration.compat.anomalyAffectsVanillaCompass")
                .define("anomalyAffectsVanillaCompass", true);

        ANOMALY_AFFECTS_NATURES_COMPASS = b
                .comment("Same scramble extended to Nature's Compass (mod ID: naturescompass).",
                         "No effect when that mod isn't loaded. Default true; set false to keep",
                         "biome-search functional inside the anomaly.")
                .translation("magnetization.configuration.compat.anomalyAffectsNaturesCompass")
                .define("anomalyAffectsNaturesCompass", true);

        ANOMALY_AFFECTS_EXPLORERS_COMPASS = b
                .comment("Same scramble extended to Explorer's Compass (mod ID: explorerscompass).",
                         "No effect when that mod isn't loaded. Default true; set false to keep",
                         "structure-search functional inside the anomaly.")
                .translation("magnetization.configuration.compat.anomalyAffectsExplorersCompass")
                .define("anomalyAffectsExplorersCompass", true);

        AE2_METEORITE_HOOK_ENABLED = b
                .comment("When AE2 is installed, scan freshly-loaded chunks for AE2 meteor",
                         "structures and emit a decaying magnetic field at each one (same decay",
                         "curve as a native meteorite_core). Turn off if the AE2 integration is",
                         "causing trouble on a specific AE2 build — disabling is non-destructive,",
                         "previously-registered AE2 meteors simply stop emitting on next reload.",
                         "No effect when AE2 isn't installed.")
                .translation("magnetization.configuration.compat.ae2MeteoriteHookEnabled")
                .define("ae2MeteoriteHookEnabled", true);

        b.pop();

        COMMON_SPEC = b.build();
        SPEC = sb.build();
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

    /** @return whether worn metal armor should respond to magnetic fields. Defaults
     *  to true when the spec isn't loaded yet (matches the declared default). */
    public static boolean armorReactsToFields() {
        try {
            return ARMOR_REACTS_TO_FIELDS.get();
        } catch (final Throwable t) {
            return true;
        }
    }

    /** @return true if holding a block and right-clicking our interactible
     *  blocks should place the block (shift to interact). Defaults true. */
    public static boolean blockPlacementFirst() {
        try {
            return BLOCK_PLACEMENT_FIRST.get();
        } catch (final Throwable t) {
            return true;
        }
    }

    /** @return whether an emitter on a contraption should exclude its whole connected
     *  assembly (not just its host sub-level) from its own force. Defaults to true
     *  when the spec isn't loaded yet (matches the declared default). */
    public static boolean excludeConnectedSubLevels() {
        try {
            return EXCLUDE_CONNECTED_SUBLEVELS.get();
        } catch (final Throwable t) {
            return true;
        }
    }

    private MagConfig() {}
}
