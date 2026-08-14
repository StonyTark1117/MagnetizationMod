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
        final Path textureDir = RES.resolve("assets/magnetization/textures/entity");
        final var soft = ImageIO.read(textureDir.resolve("mr_fluid_golem.png").toFile());
        final var hardened = ImageIO.read(textureDir.resolve("mr_fluid_golem_hardened.png").toFile());
        assertNotNull(soft);
        assertNotNull(hardened);
        assertEquals(128, soft.getWidth());
        assertEquals(128, soft.getHeight());
        assertEquals(128, hardened.getWidth());
        assertEquals(128, hardened.getHeight());
        hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(textureDir.resolve("mr_fluid_golem.png")))));
        hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(textureDir.resolve("mr_fluid_golem_hardened.png")))));
        assertEquals(2, hashes.size(), "soft and hardened MR textures must remain distinct");

        final var frameHashes = new HashSet<String>();
        for (int frame = 0; frame < 16; frame++) {
            final Path framePath = textureDir.resolve("mr_fluid_golem_" + frame + ".png");
            final var image = ImageIO.read(framePath.toFile());
            assertNotNull(image, framePath.toString());
            assertEquals(128, image.getWidth(), framePath.toString());
            assertEquals(128, image.getHeight(), framePath.toString());
            frameHashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(framePath))));
        }
        assertEquals(16, frameHashes.size(), "every soft MR frame must visibly animate");
        assertArrayEquals(Files.readAllBytes(textureDir.resolve("mr_fluid_golem_0.png")),
                Files.readAllBytes(textureDir.resolve("mr_fluid_golem.png")),
                "the legacy soft texture must remain a valid frame-zero fallback");

        final var armor = ImageIO.read(RES.resolve(
                "assets/magnetization/textures/models/armor/mr_liquid_layer_1_0.png").toFile());
        final double[] softMean = opaqueMean(soft);
        final double[] armorMean = opaqueMean(armor);
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(armorMean[channel], softMean[channel], 2.0,
                    "golem fluid palette must match MR armor channel " + channel);
        }

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

    @Test void mrFluidGolemSoftStateCannotRegressToAStaticTextureSwap() throws Exception {
        final String renderer = Files.readString(Path.of(
                "src/main/java/com/stonytark/magnetization/client/MrFluidGolemRenderer.java"));
        assertTrue(renderer.contains("golem.isHardened()) return HARDENED"),
                "hardening must select the rigid texture directly");
        assertTrue(renderer.contains("FLUID_FRAMES[(entity.tickCount / FRAME_TIME) % FRAMES]"),
                "soft state must select complete UV-correct frames");
        assertTrue(renderer.contains("FRAMES = 16"));
        assertTrue(renderer.contains("FRAME_TIME = 3"));
        assertFalse(renderer.contains("addLayer("),
                "MR Fluid Golem must render one coherent mesh without a flickering overlay");
        assertFalse(renderer.contains("ModelBakery.WATER_"));
        assertFalse(renderer.contains("RenderType.energySwirl"));

        final String armorLayer = Files.readString(Path.of(
                "src/main/java/com/stonytark/magnetization/client/MrLiquidArmorLayer.java"));
        final String generator = Files.readString(Path.of("tools/gen_mr_golem.py"));
        assertTrue(armorLayer.contains("FRAMES = 16"));
        assertTrue(armorLayer.contains("FRAME_TIME = 3"));
        assertTrue(generator.contains("FRAMES = 16"));
        assertTrue(generator.contains("FLUID_TINT = (95, 95, 105)"));
    }

    private static double[] opaqueMean(final java.awt.image.BufferedImage image) {
        final double[] sums = new double[3];
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                sums[0] += (argb >>> 16) & 0xFF;
                sums[1] += (argb >>> 8) & 0xFF;
                sums[2] += argb & 0xFF;
                count++;
            }
        }
        assertTrue(count > 0, "texture must contain opaque pixels");
        return new double[]{sums[0] / count, sums[1] / count, sums[2] / count};
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
