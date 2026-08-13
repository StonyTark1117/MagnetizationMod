package com.stonytark.magnetization.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TrackStylesCompatibilityResourceTest {
    @Test
    void omittedTrackStylesEmptyModelHasAFallback() throws Exception {
        final String path = "assets/coasterssimulatedextratypes/models/block/track/empty.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing Track Styles 1.0.0 empty-model fallback");
            final var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("minecraft:block/block", model.get("parent").getAsString());
        }
    }
}
