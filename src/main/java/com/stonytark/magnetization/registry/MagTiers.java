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

    private MagTiers() {}
}
