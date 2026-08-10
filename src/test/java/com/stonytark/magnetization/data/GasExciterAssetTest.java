package com.stonytark.magnetization.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasExciterAssetTest {
    @Test
    void inactiveAndActiveModelsUseDistinctSixteenPixelTextures() throws Exception {
        final var loader = GasExciterAssetTest.class.getClassLoader();
        final var inactiveStream = loader.getResourceAsStream(
                "assets/magnetization/textures/block/gas_exciter.png");
        final var activeStream = loader.getResourceAsStream(
                "assets/magnetization/textures/block/gas_exciter_active.png");
        assertNotNull(inactiveStream);
        assertNotNull(activeStream);
        final var inactive = ImageIO.read(inactiveStream);
        final var active = ImageIO.read(activeStream);
        assertEquals(16, inactive.getWidth());
        assertEquals(16, inactive.getHeight());
        assertEquals(16, active.getWidth());
        assertEquals(16, active.getHeight());
        assertNotEquals(inactive.getRGB(8, 8), active.getRGB(8, 8),
                "The active plasma aperture must be visibly brighter than the inactive one");

        final var blockstateStream = loader.getResourceAsStream(
                "assets/magnetization/blockstates/gas_exciter.json");
        assertNotNull(blockstateStream);
        final var variants = JsonParser.parseReader(new InputStreamReader(
                blockstateStream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
        assertTrue(variants.has("lit=false"));
        assertTrue(variants.has("lit=true"));
        assertEquals("magnetization:block/gas_exciter_active",
                variants.getAsJsonObject("lit=true").get("model").getAsString());
    }

    @Test
    void wthitGasAndStatusLinesAreLocalized() throws Exception {
        final var stream = GasExciterAssetTest.class.getClassLoader().getResourceAsStream(
                "assets/magnetization/lang/en_us.json");
        assertNotNull(stream);
        final var lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        for (final String suffix : new String[]{"gas", "no_gas", "on", "off", "off_redstone"}) {
            assertTrue(lang.has("tooltip.magnetization.gas_exciter." + suffix),
                    "Missing Gas Exciter WTHIT translation: " + suffix);
        }
    }
}
