package com.stonytark.magnetization.api;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Public tag identifiers other mods can target to opt content into magnetism.
 *
 * <p>Items in {@link #FERROMAGNETIC_ITEMS} are pulled by attractive fields when
 * dropped on the ground. Entities of types in {@link #MAGNETIZABLE_ENTITIES} are
 * pulled directly. Blocks in {@link #MAGNETIC_EMITTER_BLOCKS} are recognized as
 * field sources and may be selectively rendered/processed by client tools.
 */
public final class MagTags {

    public static final TagKey<Item> FERROMAGNETIC_ITEMS =
            TagKey.create(Registries.ITEM, Magnetization.id("ferromagnetic"));

    /** Armor pieces that count as "metal" for player magnetization. Each
     *  worn piece in this tag adds {@link com.stonytark.magnetization.physics.FieldApplicator#PER_ARMOR_SUSCEPTIBILITY}
     *  to the wearer's susceptibility. Other mods can add their own metal
     *  armor by appending to this tag. */
    public static final TagKey<Item> METAL_ARMOR =
            TagKey.create(Registries.ITEM, Magnetization.id("metal_armor"));

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

    public static final TagKey<Block> MAGNETIC_EMITTER_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("magnetic_emitter"));

    /** Block-side counterpart of {@link #FERROMAGNETIC_ITEMS}. The Magnetic
     *  Excavator scans for blocks in this tag and rips them out of the
     *  ground. Other mods can opt their metallic ores in by appending. */
    public static final TagKey<Block> FERROMAGNETIC_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("ferromagnetic_blocks"));

    /** Block-level escape hatch for the Magnetic Excavator: any block in this
     *  tag is treated like bedrock — the excavator will refuse to pull through
     *  it. The bedrock + block-entity safeguards are already wired in code;
     *  this exists so server owners and other mods can extend the immune list
     *  to claim-mod boundaries, valuable spawners, etc. */
    public static final TagKey<Block> EXCAVATOR_IMMUNE =
            TagKey.create(Registries.BLOCK, Magnetization.id("excavator_immune"));

    private MagTags() {}
}
