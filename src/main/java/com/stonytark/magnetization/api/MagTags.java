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

    public static final TagKey<EntityType<?>> MAGNETIZABLE_ENTITIES =
            TagKey.create(Registries.ENTITY_TYPE, Magnetization.id("magnetizable"));

    public static final TagKey<Block> MAGNETIC_EMITTER_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("magnetic_emitter"));

    /** Block-side counterpart of {@link #FERROMAGNETIC_ITEMS}. The Magnetic
     *  Excavator scans for blocks in this tag and rips them out of the
     *  ground. Other mods can opt their metallic ores in by appending. */
    public static final TagKey<Block> FERROMAGNETIC_BLOCKS =
            TagKey.create(Registries.BLOCK, Magnetization.id("ferromagnetic_blocks"));

    private MagTags() {}
}
