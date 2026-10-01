package com.stonytark.magnetization.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CosmicCompassTargetPayloadTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final Vec3 ORIGIN = BlockPos.ZERO.getCenter();

    @Test
    void codecPreservesTargetDimensionAndDecayDeadline() {
        final var payload = new CosmicCompassTargetPayload(OVERWORLD,
                Optional.of(new BlockPos(-123, -45, 6789)), 123456789L);
        assertEquals(payload, roundtrip(payload));
    }

    @Test
    void emptyUpdateClearsTheTarget() {
        final var payload = roundtrip(new CosmicCompassTargetPayload(OVERWORLD, Optional.empty(), 100));
        assertNull(payload.targetFor(OVERWORLD, 99, ORIGIN, 512));
    }

    @Test
    void rejectsOtherDimensionsAndExpiresAtTheServerDeadline() {
        final BlockPos pos = new BlockPos(10, 0, 0);
        final var payload = new CosmicCompassTargetPayload(OVERWORLD, Optional.of(pos), 100);
        assertEquals(pos, payload.targetFor(OVERWORLD, 99, ORIGIN, 512));
        assertNull(payload.targetFor(OVERWORLD, 100, ORIGIN, 512));
        assertNull(payload.targetFor(ResourceLocation.withDefaultNamespace("the_nether"), 99, ORIGIN, 512));
    }

    @Test
    void stopsTrackingIfTheHolderMovesOutOfRangeBetweenUpdates() {
        final var payload = new CosmicCompassTargetPayload(OVERWORLD,
                Optional.of(new BlockPos(31, 0, 0)), 100);
        assertEquals(new BlockPos(31, 0, 0), payload.targetFor(OVERWORLD, 99, ORIGIN, 32));
        assertNull(payload.targetFor(OVERWORLD, 99, ORIGIN.add(-1, 0, 0), 32));
    }

    private static CosmicCompassTargetPayload roundtrip(final CosmicCompassTargetPayload payload) {
        final var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CosmicCompassTargetPayload.CODEC.encode(buffer, payload);
            return CosmicCompassTargetPayload.CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
