package com.stonytark.magnetization.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/** Immutable discovery metadata; source membership changes invalidate it. */
public record FluidTargetRegions(int sourceCount, List<Long> chunks, List<Long> sections) {
    public static final FluidTargetRegions EMPTY = new FluidTargetRegions(0, List.of(), List.of());

    static FluidTargetRegions of(final Collection<BlockPos> sources) {
        if (sources.isEmpty()) return EMPTY;
        final var chunks = new LinkedHashSet<Long>();
        final var sections = new HashSet<Long>();
        for (final BlockPos pos : sources) {
            chunks.add(ChunkPos.asLong(pos));
            sections.add(SectionPos.asLong(pos));
        }
        return new FluidTargetRegions(sources.size(), List.copyOf(chunks), List.copyOf(sections));
    }
}
