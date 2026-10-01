package com.stonytark.magnetization.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read only text actually referenced by a chapter, allowing readable pagination. */
final class GuideTextContents {
    private GuideTextContents() {}

    static String chapter(final String entry, final JsonObject translations) throws IOException {
        final Path path = Path.of("src/main/resources/assets/magnetization/patchouli_books/field_manual/en_us/entries", entry + ".json");
        final var pages = JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("pages");
        final var text = new StringBuilder();
        for (final var element : pages) {
            final var page = element.getAsJsonObject();
            if (page.has("text")) text.append(translations.get(page.get("text").getAsString()).getAsString()).append(' ');
        }
        return text.toString();
    }
}
