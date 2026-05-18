package com.stonytark.magnetization.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tier-progression guard for the magnetite ↔ maghemite ↔ ferromagnetic stack.
 *  Maghemite is deliberately weaker than magnetite (it's literally oxidised
 *  magnetite — the design intent is "the consequence of letting your
 *  magnetite rust"). Ferromagnetic sits above magnetite as the alloy tier.
 *  A balance tweak that accidentally inverts a stat here would silently
 *  reward hoarders for letting their gear decay, breaking the gameplay loop. */
class MagTiersTest {

    @Test
    void maghemiteUsesStrictlyLessThanMagnetite() {
        assertTrue(MagTiers.MAGHEMITE.getUses() < MagTiers.MAGNETITE.getUses(),
                "maghemite uses (" + MagTiers.MAGHEMITE.getUses() +
                ") must be < magnetite (" + MagTiers.MAGNETITE.getUses() + ")");
    }

    @Test
    void maghemiteAttackStrictlyLessThanMagnetite() {
        assertTrue(MagTiers.MAGHEMITE.getAttackDamageBonus() < MagTiers.MAGNETITE.getAttackDamageBonus(),
                "maghemite attack (" + MagTiers.MAGHEMITE.getAttackDamageBonus() +
                ") must be < magnetite (" + MagTiers.MAGNETITE.getAttackDamageBonus() + ")");
    }

    @Test
    void maghemiteSpeedStrictlyLessThanMagnetite() {
        assertTrue(MagTiers.MAGHEMITE.getSpeed() < MagTiers.MAGNETITE.getSpeed(),
                "maghemite mining speed (" + MagTiers.MAGHEMITE.getSpeed() +
                ") must be < magnetite (" + MagTiers.MAGNETITE.getSpeed() + ")");
    }

    @Test
    void magnetiteIsIronTierMaghemiteIsStoneTier() {
        // Mining-tier identity matters: maghemite mustn't be able to mine
        // diamond ore (iron-tier blocks), or the "decay punishes you" loop
        // becomes "decay gives you stone-tier replacement that still works
        // for everything". Check via the incorrect-blocks tag.
        assertEquals("minecraft:incorrect_for_iron_tool",
                MagTiers.MAGNETITE.getIncorrectBlocksForDrops().location().toString(),
                "magnetite must be iron-tier (can mine diamond etc.)");
        assertEquals("minecraft:incorrect_for_stone_tool",
                MagTiers.MAGHEMITE.getIncorrectBlocksForDrops().location().toString(),
                "maghemite must be stone-tier (can't mine diamond, must use ferromagnetic)");
    }

    @Test
    void ferromagneticStrictlyExceedsMagnetiteOnAllStats() {
        assertTrue(MagTiers.FERROMAGNETIC.getUses()              > MagTiers.MAGNETITE.getUses());
        assertTrue(MagTiers.FERROMAGNETIC.getSpeed()             > MagTiers.MAGNETITE.getSpeed());
        assertTrue(MagTiers.FERROMAGNETIC.getAttackDamageBonus() > MagTiers.MAGNETITE.getAttackDamageBonus());
    }

    @Test
    void pinExactStatsForRegressionCatch() {
        // Pin the exact published numbers. Any rebalance has to come through
        // here intentionally, with the test updated, rather than being a
        // silent diff that ships unnoticed.
        assertEquals(200,  MagTiers.MAGHEMITE.getUses());
        assertEquals(5.5f, MagTiers.MAGHEMITE.getSpeed());
        assertEquals(1.5f, MagTiers.MAGHEMITE.getAttackDamageBonus());

        assertEquals(280,  MagTiers.MAGNETITE.getUses());
        assertEquals(6.5f, MagTiers.MAGNETITE.getSpeed());
        assertEquals(2.5f, MagTiers.MAGNETITE.getAttackDamageBonus());

        assertEquals(720,  MagTiers.FERROMAGNETIC.getUses());
        assertEquals(7.5f, MagTiers.FERROMAGNETIC.getSpeed());
        assertEquals(3.0f, MagTiers.FERROMAGNETIC.getAttackDamageBonus());
    }
}
