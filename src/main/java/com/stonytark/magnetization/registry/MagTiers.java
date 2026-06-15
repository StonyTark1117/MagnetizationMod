package com.stonytark.magnetization.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * Tool tier for magnetite gear. Stats are deliberately close to vanilla iron —
 * magnetite is real-world iron oxide so it reads as iron-equivalent — with
 * minor flavor-driven tweaks: slightly more durable (denser microstructure)
 * and slightly faster mining speed.
 */
public final class MagTiers {

    public static final Tier MAGNETITE = new Tier() {
        @Override public int getUses() { return 280; }                    // iron 250
        @Override public float getSpeed() { return 6.5f; }                 // iron 6.0
        @Override public float getAttackDamageBonus() { return 2.5f; }     // iron 2.0
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
        @Override public int getEnchantmentValue() { return 14; }          // iron 14
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(MagItems.MAGNETITE_INGOT.get());
        }
    };

    /** Oxidised magnetite — weaker and more brittle. Tools made from it
     *  have shorter durability than magnetite and slightly slower mining
     *  speed (the rust degrades the cutting edge faster). Slightly better
     *  enchantment value because the oxide structure holds bindings well. */
    public static final Tier MAGHEMITE = new Tier() {
        @Override public int getUses() { return 200; }                    // iron 250, magnetite 280
        @Override public float getSpeed() { return 5.5f; }                 // iron 6.0
        @Override public float getAttackDamageBonus() { return 1.5f; }     // iron 2.0
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_STONE_TOOL; }
        @Override public int getEnchantmentValue() { return 16; }          // higher than iron — oxide structure accepts enchants well
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(MagItems.MAGHEMITE_INGOT.get());
        }
    };

    public static final Tier FERROMAGNETIC = new Tier() {
        @Override public int getUses() { return 720; }                    // diamond 1561, magnetite 280, iron 250
        @Override public float getSpeed() { return 7.5f; }                 // diamond 8.0
        @Override public float getAttackDamageBonus() { return 3.0f; }     // diamond 3.0
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
        @Override public int getEnchantmentValue() { return 18; }          // diamond 10, magnetite 14
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(MagItems.FERROMAGNETIC_INGOT.get());
        }
    };

    /** Magnetorheological-fluid tools. Functionally iron-tier (same speed,
     *  damage, mining level) but with a very deep durability pool — the fluid
     *  hardens on every swing/strike, so the cutting surface barely wears.
     *  Repaired with MR fluid buckets. */
    public static final Tier MR_FLUID = new Tier() {
        @Override public int getUses() { return 2500; }                   // iron 250 — "barely wears"
        @Override public float getSpeed() { return 6.0f; }                 // iron 6.0
        @Override public float getAttackDamageBonus() { return 2.0f; }     // iron 2.0
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
        @Override public int getEnchantmentValue() { return 14; }          // iron 14
        @Override public Ingredient getRepairIngredient() {
            return Ingredient.of(MagItems.MR_FLUID_BUCKET.get());
        }
    };

    private MagTiers() {}
}
