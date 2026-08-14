package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.golem.HematiteGolem;
import com.stonytark.magnetization.content.golem.MagneticGolem;
import com.stonytark.magnetization.content.golem.TitanomagnetiteGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

/** Chunk-indexed live magnetic-golem sources. Entries are refreshed by their
 * entity every server tick and removed immediately when the entity leaves. */
public final class MobileFieldRegistry {
    public record Source(UUID id, MagneticGolem entity, MagneticField rawField) {
        public boolean isTitanomagnetite() { return entity instanceof TitanomagnetiteGolem; }
        public boolean isHematite() { return entity instanceof HematiteGolem; }
    }

    private record Entry(long chunk, Source source) {}
    private static final WeakHashMap<ServerLevel, Map<UUID, Entry>> BY_LEVEL = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, Map<Long, Map<UUID, Source>>> BY_CHUNK = new WeakHashMap<>();

    private MobileFieldRegistry() {}

    public static synchronized void update(final ServerLevel level, final MagneticGolem entity,
                                           final MagneticField rawField) {
        removeEverywhere(entity.getUUID());
        if (entity.isRemoved()) return;
        final long chunk = ChunkPos.asLong(entity.blockPosition());
        final Source source = new Source(entity.getUUID(), entity, rawField);
        BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(entity.getUUID(), new Entry(chunk, source));
        BY_CHUNK.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(chunk, ignored -> new HashMap<>())
                .put(entity.getUUID(), source);
    }

    public static synchronized void remove(final MagneticGolem entity) {
        removeEverywhere(entity.getUUID());
    }

    private static void removeEverywhere(final UUID id) {
        for (final var levelEntry : new ArrayList<>(BY_LEVEL.entrySet())) {
            final ServerLevel level = levelEntry.getKey();
            final Entry old = levelEntry.getValue().remove(id);
            if (old == null) continue;
            final Map<Long, Map<UUID, Source>> chunks = BY_CHUNK.get(level);
            if (chunks != null) {
                final Map<UUID, Source> bucket = chunks.get(old.chunk());
                if (bucket != null) {
                    bucket.remove(id);
                    if (bucket.isEmpty()) chunks.remove(old.chunk());
                }
                if (chunks.isEmpty()) BY_CHUNK.remove(level);
            }
            if (levelEntry.getValue().isEmpty()) BY_LEVEL.remove(level);
        }
    }

    public static synchronized List<Source> snapshotNear(final ServerLevel level,
                                                         final BlockPos target,
                                                         final int radiusBlocks) {
        final Map<Long, Map<UUID, Source>> chunks = BY_CHUNK.get(level);
        if (chunks == null || chunks.isEmpty()) return Collections.emptyList();
        final int minX = Math.floorDiv(target.getX() - radiusBlocks, 16);
        final int maxX = Math.floorDiv(target.getX() + radiusBlocks, 16);
        final int minZ = Math.floorDiv(target.getZ() - radiusBlocks, 16);
        final int maxZ = Math.floorDiv(target.getZ() + radiusBlocks, 16);
        final List<Source> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            final Map<UUID, Source> bucket = chunks.get(ChunkPos.asLong(x, z));
            if (bucket != null) result.addAll(bucket.values());
        }
        return result;
    }

    /** Apply one strength step for every Hematite Golem within four blocks of
     * the source. The source ID is excluded for completeness. */
    public static MagneticField dampen(final ServerLevel level, final MagneticField raw,
                                       final @Nullable UUID sourceId) {
        int count = 0;
        for (final Source source : snapshotNear(level, BlockPos.containing(raw.origin()), 4)) {
            if (source.id().equals(sourceId) || !source.isHematite()) continue;
            if (source.entity().position().distanceToSqr(raw.origin()) <= 16.0d) count++;
        }
        if (count == 0) return raw;
        return raw.withSteppedStrength(stepDown(raw.strength(), count));
    }

    public static MagneticStrength stepDown(final MagneticStrength base, final int count) {
        return MagneticStrength.values()[Math.max(0, base.ordinal() - Math.max(0, count))];
    }

    public static synchronized int size(final ServerLevel level) {
        final Map<UUID, Entry> entries = BY_LEVEL.get(level);
        return entries == null ? 0 : entries.size();
    }

    public static synchronized boolean contains(final ServerLevel level, final UUID id) {
        final Map<UUID, Entry> entries = BY_LEVEL.get(level);
        return entries != null && entries.containsKey(id);
    }

    public static synchronized @Nullable Source source(final ServerLevel level, final UUID id) {
        final Map<UUID, Entry> entries = BY_LEVEL.get(level);
        final Entry entry = entries == null ? null : entries.get(id);
        return entry == null ? null : entry.source();
    }

    public static synchronized List<Source> snapshot(final ServerLevel level) {
        final Map<UUID, Entry> entries = BY_LEVEL.get(level);
        if (entries == null || entries.isEmpty()) return Collections.emptyList();
        final List<Source> result = new ArrayList<>(entries.size());
        for (final Entry entry : entries.values()) result.add(entry.source());
        return result;
    }

    public static synchronized void onLevelUnload(final ServerLevel level) {
        BY_LEVEL.remove(level);
        BY_CHUNK.remove(level);
    }
}
