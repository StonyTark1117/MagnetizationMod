package com.stonytark.magnetization.api;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip codec guarantees for {@link EmitterPreset}. The Imprint Module
 *  persists this record into a data component and ships it across the wire,
 *  so JSON and stream codecs both have to round-trip cleanly — a regression
 *  here would silently corrupt captured presets on world reload or rejoin. */
class EmitterPresetTest {

    private static final EmitterPreset SAMPLE = new EmitterPreset(
            MagneticStrength.STRONG,
            MagneticPolarity.SOUTH,
            42,
            ResourceLocation.fromNamespaceAndPath("magnetization", "electromagnet"));

    @Test
    void jsonCodecRoundTrips() {
        final DataResult<JsonElement> encoded = EmitterPreset.CODEC.encodeStart(JsonOps.INSTANCE, SAMPLE);
        assertTrue(encoded.result().isPresent(), "encode must succeed");
        final DataResult<EmitterPreset> decoded =
                EmitterPreset.CODEC.parse(JsonOps.INSTANCE, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "decode must succeed");
        assertEquals(SAMPLE, decoded.result().get());
    }

    @Test
    void streamCodecRoundTrips() {
        final RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        EmitterPreset.STREAM_CODEC.encode(buf, SAMPLE);
        final EmitterPreset out = EmitterPreset.STREAM_CODEC.decode(buf);
        assertEquals(SAMPLE, out);
    }

    @Test
    void recordAccessorsExposeAllFields() {
        assertEquals(MagneticStrength.STRONG, SAMPLE.strength());
        assertEquals(MagneticPolarity.SOUTH,  SAMPLE.polarity());
        assertEquals(42, SAMPLE.range());
        assertEquals("magnetization:electromagnet", SAMPLE.sourceBlockId().toString());
    }
}
