package com.stonytark.magnetization.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GasVentAssetTest {
    private static final Path MAIN = Path.of("src/main/resources");
    private static final Path GENERATED = Path.of("src/generated/resources");

    @Test
    void ventAndUnobtainableCloudHaveCompleteAssets() throws Exception {
        for (final String resource : List.of(
                "assets/magnetization/blockstates/gas_vent.json",
                "assets/magnetization/blockstates/proxy_gas_cloud.json",
                "assets/magnetization/models/block/gas_vent.json",
                "assets/magnetization/models/block/proxy_gas_cloud.json",
                "assets/magnetization/models/item/gas_vent.json",
                "assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/gas_vent.json",
                "data/magnetization/recipe/gas_vent.json",
                "data/magnetization/loot_table/blocks/gas_vent.json")) {
            assertTrue(exists(resource), () -> "Missing Gas Vent resource " + resource);
        }
        assertFalse(exists("assets/magnetization/models/item/proxy_gas_cloud.json"),
                "Internal cloud must not have an obtainable item model");
        assertFalse(exists("data/magnetization/loot_table/blocks/proxy_gas_cloud.json"),
                "Internal cloud must not have a loot table");

        final JsonObject ventStates = parse(MAIN.resolve(
                "assets/magnetization/blockstates/gas_vent.json")).getAsJsonObject("variants");
        assertEquals(Set.of("facing=north", "facing=south", "facing=east", "facing=west",
                        "facing=up", "facing=down"), ventStates.keySet());
        final JsonObject cloudStates = parse(MAIN.resolve(
                "assets/magnetization/blockstates/proxy_gas_cloud.json")).getAsJsonObject("variants");
        assertEquals(Set.of("excited=false", "excited=true"), cloudStates.keySet());

        final JsonObject cloudModel = parse(MAIN.resolve(
                "assets/magnetization/models/block/proxy_gas_cloud.json"));
        assertFalse(cloudModel.get("ambientocclusion").getAsBoolean());
        assertTrue(cloudModel.getAsJsonArray("elements").size() >= 3,
                "The proxy must render as a lobed cloud, not a full opaque cube");
        cloudModel.getAsJsonArray("elements").forEach(element -> {
            final JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            assertEquals(Set.of("up", "down", "north", "south", "east", "west"), faces.keySet());
            faces.entrySet().forEach(face -> assertEquals(0,
                    face.getValue().getAsJsonObject().get("tintindex").getAsInt()));
        });

        final var textureStream = GasVentAssetTest.class.getClassLoader().getResourceAsStream(
                "assets/magnetization/textures/block/gas_vent_front.png");
        assertNotNull(textureStream);
        final var texture = ImageIO.read(textureStream);
        assertEquals(16, texture.getWidth());
        assertEquals(16, texture.getHeight());

        final var cloudTextureStream = GasVentAssetTest.class.getClassLoader().getResourceAsStream(
                "assets/magnetization/textures/block/proxy_gas_cloud.png");
        assertNotNull(cloudTextureStream);
        final var cloudTexture = ImageIO.read(cloudTextureStream);
        assertEquals(16, cloudTexture.getWidth());
        assertEquals(16, cloudTexture.getHeight());
    }

    @Test
    void recipeMatchesTheDocumentedUniquePattern() throws IOException {
        final JsonObject recipe = parse(GENERATED.resolve(
                "data/magnetization/recipe/gas_vent.json"));
        assertEquals(List.of(" p ", "scs", " i "), recipe.getAsJsonArray("pattern").asList().stream()
                .map(element -> element.getAsString()).toList());
        final JsonObject key = recipe.getAsJsonObject("key");
        assertEquals("create:fluid_pipe", key.getAsJsonObject("p").get("item").getAsString());
        assertEquals("create:copper_casing", key.getAsJsonObject("c").get("item").getAsString());
        assertEquals("create:copper_sheet", key.getAsJsonObject("s").get("item").getAsString());
        assertEquals("minecraft:iron_bars", key.getAsJsonObject("i").get("item").getAsString());
        assertEquals("magnetization:gas_vent",
                recipe.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void allVentHudAndManualTextIsLocalized() throws IOException {
        final JsonObject lang = parse(MAIN.resolve("assets/magnetization/lang/en_us.json"));
        for (final String key : List.of(
                "block.magnetization.gas_vent",
                "block.magnetization.proxy_gas_cloud",
                "tooltip.magnetization.gas_vent.use",
                "tooltip.magnetization.gas_vent.empty",
                "tooltip.magnetization.gas_vent.gas",
                "tooltip.magnetization.gas_vent.amount",
                "tooltip.magnetization.gas_vent.output_clear",
                "tooltip.magnetization.gas_vent.output_cloud",
                "tooltip.magnetization.gas_vent.output_blocked",
                "tooltip.magnetization.gas_vent.exciter_missing",
                "tooltip.magnetization.gas_vent.exciter_ready",
                "tooltip.magnetization.gas_vent.exciter_unpowered",
                "tooltip.magnetization.gas_vent.exciter_redstone",
                "tooltip.magnetization.gas_cloud.unknown",
                "tooltip.magnetization.gas_cloud.excited",
                "tooltip.magnetization.gas_cloud.dormant",
                "book.magnetization.entry.gas_vent.name",
                "book.magnetization.entry.gas_vent.spotlight",
                "book.magnetization.entry.gas_vent.crafting",
                "book.magnetization.entry.gas_vent.cloud.title",
                "book.magnetization.entry.gas_vent.cloud.text")) {
            assertTrue(lang.has(key) && !lang.get(key).getAsString().isBlank(),
                    () -> "Missing Gas Vent translation " + key);
        }
    }

    private static boolean exists(final String relative) {
        return Files.isRegularFile(MAIN.resolve(relative)) || Files.isRegularFile(GENERATED.resolve(relative));
    }

    private static JsonObject parse(final Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
