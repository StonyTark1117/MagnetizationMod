package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FerrofluidChunkSearchTest {
    @Test
    void preservesBlockBasedBoundsAcrossChunkEdgesAndNegativeCoordinates() {
        for (int radius : new int[]{32, 512}) {
            for (int chunk : new int[]{-1_875_000, -17, -1, 0, 1, 17, 1_874_999}) {
                for (int offset = 0; offset < 16; offset++) {
                    List<BlockPos> anchors = List.of(new BlockPos(chunk * 16 + offset, 64, -chunk * 16 + 15 - offset));
                    assertEquals(original(anchors, radius), expanded(anchors, radius));
                }
            }
        }
    }

    @Test
    void preservesSeparatedPoolsAndIgnoresHeightOnlyForCandidateDiscovery() {
        List<BlockPos> anchors = List.of(new BlockPos(-17, -60, -1), new BlockPos(-17, 200, -1),
                new BlockPos(-1, 64, 15), new BlockPos(16, 64, 16), new BlockPos(5000, 70, -8000));
        for (int radius : new int[]{32, 512}) assertEquals(original(anchors, radius), expanded(anchors, radius));
    }

    @Test
    void tenThousandSourcesExpandFortyNineChunksExactlyOnce() {
        List<BlockPos> anchors = new ArrayList<>();
        for (int x = 0; x < 100; x++) for (int z = 0; z < 100; z++) anchors.add(new BlockPos(x, 64, z));
        Set<Long> occupied = FerrofluidChunkSearch.occupiedChunks(anchors);
        assertEquals(49, occupied.size());
        CountingSet result = new CountingSet();
        FerrofluidChunkSearch.expand(result, occupied, 512);
        assertEquals(207_025L, result.attempts);
        assertEquals(5041, result.size());
        // The original union covers every chunk of this rectangle, including both edges.
        Set<Long> expected = new HashSet<>();
        for (int x = -32; x <= 38; x++) for (int z = -32; z <= 38; z++) expected.add(ChunkPos.asLong(x, z));
        assertEquals(expected, result);
    }

    @Test
    void rejectsRadiiThatWouldChangeBlockBasedCoverage() {
        assertThrows(IllegalArgumentException.class, () -> FerrofluidChunkSearch.expand(new HashSet<>(), Set.of(0L), 31));
        assertTrue(expanded(List.of(), 512).isEmpty());
    }

    private static Set<Long> expanded(List<BlockPos> anchors, int radius) {
        Set<Long> result = new HashSet<>();
        FerrofluidChunkSearch.expand(result, FerrofluidChunkSearch.occupiedChunks(anchors), radius);
        return result;
    }

    private static Set<Long> original(List<BlockPos> anchors, int radius) {
        Set<Long> result = new HashSet<>();
        for (BlockPos pos : anchors) {
            for (int x = Math.floorDiv(pos.getX() - radius, 16); x <= Math.floorDiv(pos.getX() + radius, 16); x++) {
                for (int z = Math.floorDiv(pos.getZ() - radius, 16); z <= Math.floorDiv(pos.getZ() + radius, 16); z++) {
                    result.add(ChunkPos.asLong(x, z));
                }
            }
        }
        return result;
    }

    private static class CountingSet extends HashSet<Long> {
        long attempts;
        @Override public boolean add(Long value) { attempts++; return super.add(value); }
    }
}
