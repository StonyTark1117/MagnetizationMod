package com.stonytark.magnetization.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class IronOxideGolemResourceTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final List<String> IDS = List.of("magnetite_golem", "pyrrhotite_golem",
            "hematite_golem", "titanomagnetite_golem");

    @Test void everyGolemHasLootAndLocalization() throws Exception {
        final String lang = Files.readString(RES.resolve("assets/magnetization/lang/en_us.json"));
        JsonParser.parseString(lang);
        final Map<String, List<String>> expectedDrops = Map.of(
                "magnetite_golem", List.of("magnetization:magnetite_ingot", "magnetization:maghemite_ingot"),
                "pyrrhotite_golem", List.of("magnetization:pyrrhotite_ingot"),
                "hematite_golem", List.of("magnetization:hematite_ingot"),
                "titanomagnetite_golem", List.of("magnetization:titanomagnetite_ingot"));
        for (final String id : IDS) {
            final Path loot = RES.resolve("data/magnetization/loot_table/entities/" + id + ".json");
            assertTrue(Files.isRegularFile(loot), id + " loot table");
            final var root = JsonParser.parseString(Files.readString(loot)).getAsJsonObject();
            final var pools = root.getAsJsonArray("pools");
            assertEquals(expectedDrops.get(id).size(), pools.size(), id + " loot pools");
            for (int i = 0; i < pools.size(); i++) {
                final var entry = pools.get(i).getAsJsonObject().getAsJsonArray("entries")
                        .get(0).getAsJsonObject();
                assertEquals(expectedDrops.get(id).get(i), entry.get("name").getAsString(), id);
                final var count = entry.getAsJsonArray("functions").get(0).getAsJsonObject()
                        .getAsJsonObject("count");
                assertEquals(3, count.get("min").getAsInt(), id);
                assertEquals(5, count.get("max").getAsInt(), id);
            }
            assertTrue(lang.contains("entity.magnetization." + id), id + " localization");
        }
        final String magnetiteLoot = Files.readString(
                RES.resolve("data/magnetization/loot_table/entities/magnetite_golem.json"));
        assertTrue(magnetiteLoot.contains("{Oxidized:0b}"));
        assertTrue(magnetiteLoot.contains("{Oxidized:1b}"));
    }

    @Test void stateTexturesKeepIronGolemUvDimensions() throws Exception {
        final var hashes = new HashSet<String>();
        for (final String id : List.of("magnetite_golem", "maghemite_golem", "pyrrhotite_golem",
                "pyrrhotite_golem_active", "hematite_golem", "titanomagnetite_golem",
                "titanomagnetite_golem_charged")) {
            final var image = ImageIO.read(RES.resolve("assets/magnetization/textures/entity/" + id + ".png").toFile());
            assertNotNull(image, id);
            assertEquals(128, image.getWidth(), id);
            assertEquals(128, image.getHeight(), id);
            hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(RES.resolve("assets/magnetization/textures/entity/" + id + ".png")))));
        }
        assertEquals(7, hashes.size(), "every visual state should have a distinct texture");
    }

    @Test void mrFluidGolemHasDistinctSoftAndHardenedPresentation() throws Exception {
        final var hashes = new HashSet<String>();
        for (final String id : List.of("mr_fluid_golem", "mr_fluid_golem_hardened")) {
            final Path texture = RES.resolve("assets/magnetization/textures/entity/" + id + ".png");
            final var image = ImageIO.read(texture.toFile());
            assertNotNull(image, id);
            assertEquals(128, image.getWidth(), id);
            assertEquals(128, image.getHeight(), id);
            hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(texture))));
        }
        assertEquals(2, hashes.size(), "soft and hardened MR textures must remain distinct");

        final var lang = JsonParser.parseString(Files.readString(
                RES.resolve("assets/magnetization/lang/en_us.json"))).getAsJsonObject();
        assertTrue(lang.has("tooltip.magnetization.golem.mr_fluid.fluid"));
        assertTrue(lang.has("tooltip.magnetization.golem.mr_fluid.hardened"));

        final String loot = Files.readString(
                RES.resolve("data/magnetization/loot_table/entities/mr_fluid_golem.json"));
        assertTrue(loot.contains("magnetization:mr_fluid_bucket"),
                "MR Fluid Golem must return its own material instead of vanilla iron");
        assertFalse(loot.contains("minecraft:iron_nugget"));
    }

    @Test void manualAndAdvancementsAreValidJson() throws Exception {
        final String manual = Files.readString(RES.resolve(
                "assets/magnetization/patchouli_books/field_manual/en_us/entries/machines/iron_oxide_golems.json"));
        JsonParser.parseString(manual);
        for (final String material : List.of("magnetite", "pyrrhotite", "hematite", "titanomagnetite")) {
            assertTrue(manual.contains("iron_oxide_golems." + material), material + " manual page");
        }

        final var any = JsonParser.parseString(Files.readString(
                RES.resolve("data/magnetization/advancement/iron_oxide_golem.json"))).getAsJsonObject();
        final var all = JsonParser.parseString(Files.readString(
                RES.resolve("data/magnetization/advancement/all_iron_oxide_golems.json"))).getAsJsonObject();
        assertEquals(4, any.getAsJsonObject("criteria").size());
        assertEquals(1, any.getAsJsonArray("requirements").size(), "any-golem advancement must be OR");
        assertEquals(4, any.getAsJsonArray("requirements").get(0).getAsJsonArray().size());
        assertEquals(4, all.getAsJsonArray("requirements").size(), "challenge must require all four");
    }

    @Test void creativeEggsAreLocalizedModeledAndNeverCrafted() throws Exception {
        final String lang = Files.readString(RES.resolve("assets/magnetization/lang/en_us.json"));
        for (final String id : IDS) {
            final String egg = id + "_spawn_egg";
            assertTrue(lang.contains("item.magnetization." + egg), egg + " localization");
            final Path model = RES.resolve("assets/magnetization/models/item/" + egg + ".json");
            assertTrue(Files.isRegularFile(model), egg + " model");
            assertEquals("minecraft:item/template_spawn_egg", JsonParser.parseString(Files.readString(model))
                    .getAsJsonObject().get("parent").getAsString());
            assertFalse(Files.exists(RES.resolve("data/magnetization/recipe/" + egg + ".json")),
                    egg + " must remain creative-only");
        }
    }

    @Test void everyGolemToggleIsLocalized() throws Exception {
        final var lang = JsonParser.parseString(Files.readString(
                RES.resolve("assets/magnetization/lang/en_us.json"))).getAsJsonObject();
        for (final String material : List.of("gallium", "mrFluid", "magnetite", "pyrrhotite",
                "hematite", "titanomagnetite")) {
            final String key = "magnetization.configuration.content." + material + "GolemEnabled";
            assertTrue(lang.has(key), key);
            assertTrue(lang.has(key + ".tooltip"), key + ".tooltip");
        }
        assertTrue(lang.has("message.magnetization.golem_disabled"));
        assertTrue(lang.has("tooltip.magnetization.golem.disabled"));
    }

    @Test void everyCustomGolemSoundHasAnAccessibleSubtitle() throws Exception {
        final var sounds = JsonParser.parseString(Files.readString(
                RES.resolve("assets/magnetization/sounds.json"))).getAsJsonObject();
        final var lang = JsonParser.parseString(Files.readString(
                RES.resolve("assets/magnetization/lang/en_us.json"))).getAsJsonObject();
        for (final String sound : List.of("golem.polarize", "golem.oxidize", "golem.heat_change",
                "golem.dampen", "golem.capture", "golem.harden", "golem.soften")) {
            final String subtitle = sounds.getAsJsonObject(sound).get("subtitle").getAsString();
            assertTrue(lang.has(subtitle), sound + " subtitle translation");
            assertFalse(sounds.getAsJsonObject(sound).getAsJsonArray("sounds").isEmpty(), sound);
        }
    }
}
