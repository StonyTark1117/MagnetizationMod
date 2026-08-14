package com.stonytark.magnetization.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GasDetectorStatusPayloadTest {
    @Test
    void codecPreservesServerOwnedExposureAndSafetyState() {
        final var expected = new GasDetectorStatusPayload(true, 37, 80, 3, true, 2.75d);
        final var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);

        GasDetectorStatusPayload.CODEC.encode(buffer, expected);

        assertEquals(expected, GasDetectorStatusPayload.CODEC.decode(buffer));
    }
}
