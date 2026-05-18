package com.stonytark.magnetization.content.meteorite;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Boundary tests for {@link AeMeteoriteScanner#isAeMeteorite(ResourceLocation)}.
 *  AE2 has renamed its meteor structure path between releases ({@code meteorite},
 *  {@code meteorites}, {@code sky_stone_meteorite} have all shipped); the
 *  matcher is intentionally fuzzy on path but strict on namespace so unrelated
 *  mods named ae2_* don't accidentally hook our field emitter. */
class AeMeteoriteScannerTest {

    @Test
    void matchesCanonicalMeteorite() {
        assertTrue(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("ae2", "meteorite")));
    }

    @Test
    void matchesPluralisedPath() {
        assertTrue(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("ae2", "meteorites")));
    }

    @Test
    void matchesPrefixedPath() {
        assertTrue(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("ae2", "sky_stone_meteorite")));
    }

    @Test
    void rejectsOtherNamespace() {
        // A mod that happens to have "meteorite" in its structure name but
        // isn't AE2 should not trigger our hook.
        assertFalse(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("magnetization", "meteorite")));
        assertFalse(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("minecraft", "meteorite")));
    }

    @Test
    void rejectsNonMeteoriteAe2Structures() {
        // AE2's other worldgen structures must not match.
        assertFalse(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("ae2", "spatial_anchor")));
        assertFalse(AeMeteoriteScanner.isAeMeteorite(
                ResourceLocation.fromNamespaceAndPath("ae2", "")));
    }
}
