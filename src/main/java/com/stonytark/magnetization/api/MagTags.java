package com.stonytark.magnetization.api;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Public tag identifiers other mods can target to opt content into magnetism.
 *
 * <p>Items in {@link #FERROMAGNETIC_ITEMS} are pulled by attractive fields when
 * dropped on the ground. Entities of types in {@link #MAGNETIZABLE_ENTITIES} are
 * pulled directly. Blocks in {@link #MAGNETIC_EMITTER_BLOCKS} are recognized as
 * field sources and may be selectively rendered/processed by client tools.
 */
public final class MagTags {

    /** Common hydrogen identity shared with processing/technology mods. Both
     * source and flowing variants belong here; consumers should test this tag
     * instead of hard-coding a registry ID. */
    public static final TagKey<Fluid> HYDROGEN_FLUIDS =
            TagKey.create(Registries.FLUID,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "hydrogen"));

    /** Filled hydrogen containers. TFMG already publishes its Hydrogen Tank to
     * this common tag; Magnetization contributes its bucket as the counterpart. */
    public static final TagKey<Item> HYDROGEN_BUCKETS =
            TagKey.create(Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "buckets/hydrogen"));

    /** Dedicated cross-mod coolants. Vanilla water is accepted separately;
     * TFMG publishes its Cooling Fluid source/flowing variants to this tag. */
    public static final TagKey<Fluid> COOLING_FLUIDS =
            TagKey.create(Registries.FLUID,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "cooling_fluid"));

    /** Full-bucket containers corresponding to {@link #COOLING_FLUIDS}. */
    public static final TagKey<Item> COOLING_FLUID_BUCKETS =
            TagKey.create(Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "buckets/cooling_fluid"));

    public static final TagKey<Item> FERROMAGNETIC_ITEMS =
            TagKey.create(Registries.ITEM, Magnetization.id("ferromagnetic"));

    /** Foreign magnet items that may drive magnet-slot machines. Magnetization's
     *  own materials retain their form/type potency ladder; members of this tag
     *  receive the server-configured external potency. */
    public static final TagKey<Item> MACHINE_MAGNETS =
            TagKey.create(Registries.ITEM, Magnetization.id("machine_magnets"));

    /** Armor pieces that count as "metal" for player magnetization. Each
     *  worn piece in this tag adds {@link com.stonytark.magnetization.physics.FieldApplicator#PER_ARMOR_SUSCEPTIBILITY}
     *  to the wearer's susceptibility. Other mods can add their own metal
     *  armor by appending to this tag. */
    public static final TagKey<Item> METAL_ARMOR =
            TagKey.create(Registries.ITEM, Magnetization.id("metal_armor"));

    /** Diamagnetic items — repelled by BOTH magnetic poles. Dropped in a field
     *  they hover above the source instead of being pulled in (bismuth /
     *  pyrolytic carbon). */
    public static final TagKey<Item> DIAMAGNETIC_ITEMS =
            TagKey.create(Registries.ITEM, Magnetization.id("diamagnetic"));

    /** Tools and weapons that can be magnetized via the electromagnet GUI.
     *  When a magnetized tool from this tag is held or worn, dropped
     *  ferromagnetic items within a small radius get pulled toward the
     *  holder — like a personal item magnet. Other mods can opt their
     *  metal tools in by appending to this tag. */
    public static final TagKey<Item> METAL_TOOLS =
            TagKey.create(Registries.ITEM, Magnetization.id("metal_tools"));

    /** Items the Magnetic Excavator (and any other emitter exposing the
     *  redstone-fuel slot) accepts as a self-contained power source.
     *  Presence-only — items in the slot are never consumed. The default
     *  tag covers obvious redstone sources (dust, block, torch, lever,
     *  observer, daylight detector, target, etc.); datapacks can extend
     *  the list however they like. */
    public static final TagKey<Item> REDSTONE_FUEL =
            TagKey.create(Registries.ITEM, Magnetization.id("redstone_fuel"));

    public static final TagKey<EntityType<?>> MAGNETIZABLE_ENTITIES =
            TagKey.create(Registries.ENTITY_TYPE, Magnetization.id("magnetizable"));

    /** Cross-mod opt-out tag we honor: any entity whose type is in
     *  {@code magnetizing:unmoveable_by_magnets} (from the Magnetizing mod) is
     *  skipped by our field application, even if armor/tag-membership would
     *  otherwise qualify. Lets server owners maintain a single "do not move"
     *  list across both mods. The tag may not exist when Magnetizing isn't
     *  loaded; that's fine — vanilla tag lookup on a missing tag is empty. */
    public static final TagKey<EntityType<?>> MAGNETIZING_UNMOVEABLE =
            TagKey.create(Registries.ENTITY_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("magnetizing", "unmoveable_by_magnets"));

    public static final TagKey<Block> MAGNETIC_EMITTER_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("magnetic_emitter"));

    /** Block-side counterpart of {@link #FERROMAGNETIC_ITEMS}. The Magnetic
     *  Excavator scans for blocks in this tag and rips them out of the
     *  ground. Other mods can opt their metallic ores in by appending. */
    public static final TagKey<Block> FERROMAGNETIC_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("ferromagnetic_blocks"));

    /** Blocks whose contents or casing must not make a ship magnetically
     *  susceptible. Checked before material tags by the ship scanner; intended
     *  for fuel, fluid, and gas containers where the stored substance is not a
     *  structural magnetic material. */
    public static final TagKey<Block> MAGNETIC_SUSCEPTIBILITY_EXCLUDED =
            TagKey.create(Registries.BLOCK, Magnetization.id("magnetic_susceptibility_excluded"));

    /** Blocks that make a Sable ship diamagnetic — repelled by BOTH poles of any
     *  field (reacts to positive + negative the same way). */
    public static final TagKey<Block> DIAMAGNETIC_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("diamagnetic_blocks"));

    /** Ores that can rarely drop raw gallium as a byproduct (gallium occurs with
     *  zinc/aluminium in nature). Create's zinc ore by default; TFMG bauxite is
     *  opted in via a {@code required:false} entry, so it's a soft dependency. */
    public static final TagKey<Block> GALLIUM_BEARING_ORES =
            TagKey.create(Registries.BLOCK, Magnetization.id("gallium_bearing_ores"));

    /** Metallic / magnetic ore blocks the Ore Dowsing Compass can point to and
     *  tune onto. Our ferrous ores plus vanilla metal ores; other mods extend
     *  by appending. Also gates the anvil-tuning (only ores in this tag tune the
     *  compass). */
    public static final TagKey<Block> METALLIC_ORES =
            TagKey.create(Registries.BLOCK, Magnetization.id("metallic_ores"));

    /**
     * Ore-like blocks that Ore Excavation may treat as one material vein when
     * that optional mod is installed.  This is deliberately narrower than the
     * general ferromagnetic tag: storage blocks and machine casings should not
     * become veinminer targets just because they are magnetic.
     */
    public static final TagKey<Block> ORE_EXCAVATION_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("ore_excavation"));

    /** Magnetic blocks that, placed next to an anvil, dampen it — the magnetic
     *  field steadies the impact, so the anvil doesn't degrade and its clang is
     *  deadened. Curated to our magnet blocks; extend by appending. */
    public static final TagKey<Block> ANVIL_DAMPENERS =
            TagKey.create(Registries.BLOCK, Magnetization.id("anvil_dampeners"));

    /** Non-ferrous CONDUCTIVE blocks (copper, aluminium, …) that brake a moving
     *  magnetic ship via induced eddy currents (the Lenz effect). Must be
     *  conductive but NOT ferromagnetic — these don't attract, they drag. */
    public static final TagKey<Block> EDDY_CONDUCTORS =
            TagKey.create(Registries.BLOCK, Magnetization.id("eddy_conductors"));

    /** Conductive fluids accepted by the MHD Jet. Built-in fluids have dedicated
     *  conductivity settings; other members use the tagged-fluid fallback. */
    public static final TagKey<Fluid> MHD_WORKING_FLUIDS =
            TagKey.create(Registries.FLUID, Magnetization.id("mhd_working_fluids"));

    /** Gases accepted as propellant by the Ion Thruster. The six built-in
     * noble gases have tuned profiles; datapack-added fluids use the neutral
     * fallback profile so compatibility does not require a code hook. */
    public static final TagKey<Fluid> ION_THRUSTER_PROPELLANTS =
            TagKey.create(Registries.FLUID, Magnetization.id("ion_thruster_propellants"));

    /** Conductive metal blocks that work as Railgun rails (Lorentz-force track).
     *  Mirrors {@link #EDDY_CONDUCTORS} but kept separate so rail material is
     *  tunable independently of Lenz braking. Rails are destruction-immune. */
    public static final TagKey<Block> RAILGUN_RAILS =
            TagKey.create(Registries.BLOCK, Magnetization.id("railgun_rails"));

    /** Our magnetic-metal anvils — self-dampened (quiet use-clang) and given a
     *  per-metal break chance by {@code AnvilDampenerHandler}. */
    public static final TagKey<Block> DAMPENED_ANVILS =
            TagKey.create(Registries.BLOCK, Magnetization.id("dampened_anvils"));

    /** Block-level escape hatch for the Magnetic Excavator: any block in this
     *  tag is treated like bedrock — the excavator will refuse to pull through
     *  it. The bedrock + block-entity safeguards are already wired in code;
     *  this exists so server owners and other mods can extend the immune list
     *  to claim-mod boundaries, valuable spawners, etc. */
    public static final TagKey<Block> EXCAVATOR_IMMUNE =
            TagKey.create(Registries.BLOCK, Magnetization.id("excavator_immune"));

    /** Damage-source types treated as "lightning-flavoured" for LIRM stamping.
     *  Includes {@code minecraft:lightning_bolt} plus a curated set of modded
     *  lightning attacks (Iron's Spells, Cataclysm, Alex's Caves, Twilight
     *  Forest). Datapacks can extend without code changes. */
    public static final TagKey<DamageType> LIGHTNING_SOURCES =
            TagKey.create(Registries.DAMAGE_TYPE, Magnetization.id("lightning_sources"));

    private MagTags() {}
}
