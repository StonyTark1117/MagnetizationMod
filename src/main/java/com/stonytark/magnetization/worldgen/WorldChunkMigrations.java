package com.stonytark.magnetization.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent, per-level chunk migration registry. A migration is identified by
 * a stable id and version; a chunk is skipped only when its stored version is
 * at least the requested version. This makes world upgrades explicit and safe:
 * restarting cannot reopen a one-session gate, while a deliberate version bump
 * can run a new migration exactly once per chunk.
 *
 * <p>The SavedData id intentionally remains {@code magnetization_surface_repaint}
 * so worlds written by the original repaint implementation are read and upgraded
 * instead of being repainted from scratch on the first framework-enabled launch.
 */
public final class WorldChunkMigrations extends SavedData {

    public static final String DATA_KEY = "magnetization_surface_repaint";
    private static final String MIGRATIONS = "Migrations";
    private static final String LEGACY_ID = "surface_repaint";

    private final Map<String, Map<Long, Integer>> completed = new HashMap<>();

    private WorldChunkMigrations() {}

    public static WorldChunkMigrations get(final ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(WorldChunkMigrations::new, WorldChunkMigrations::load), DATA_KEY);
    }

    /** Run {@code action} once for this migration version/chunk and persist success. */
    public static boolean apply(final ServerLevel level, final String migrationId,
                                final int version, final ChunkPos chunk, final Runnable action) {
        if (version <= 0) throw new IllegalArgumentException("Migration versions start at 1");
        final WorldChunkMigrations data = get(level);
        final Map<Long, Integer> entries = data.completed.computeIfAbsent(migrationId, k -> new HashMap<>());
        final long key = chunk.toLong();
        if (entries.getOrDefault(key, 0) >= version) return false;
        action.run();
        entries.put(key, version);
        data.setDirty();
        return true;
    }

    public static boolean applied(final ServerLevel level, final String migrationId,
                                  final int version, final ChunkPos chunk) {
        final Map<Long, Integer> entries = get(level).completed.get(migrationId);
        return entries != null && entries.getOrDefault(chunk.toLong(), 0) >= version;
    }

    /** Highest version recorded for this migration, or 0 if no chunk has run it. */
    public static int version(final ServerLevel level, final String migrationId) {
        final Map<Long, Integer> entries = get(level).completed.get(migrationId);
        if (entries == null) return 0;
        return entries.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public static int completedCount(final ServerLevel level, final String migrationId) {
        final Map<Long, Integer> entries = get(level).completed.get(migrationId);
        return entries == null ? 0 : entries.size();
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider lookup) {
        final CompoundTag migrations = new CompoundTag();
        for (final var migration : completed.entrySet()) {
            final ListTag chunks = new ListTag();
            for (final var chunk : migration.getValue().entrySet()) {
                final CompoundTag entry = new CompoundTag();
                entry.putLong("Pos", chunk.getKey());
                entry.putInt("Version", chunk.getValue());
                chunks.add(entry);
            }
            final CompoundTag migrationTag = new CompoundTag();
            migrationTag.put("Chunks", chunks);
            migrations.put(migration.getKey(), migrationTag);
        }
        tag.put(MIGRATIONS, migrations);
        return tag;
    }

    private static WorldChunkMigrations load(final CompoundTag tag, final HolderLookup.Provider lookup) {
        final WorldChunkMigrations data = new WorldChunkMigrations();
        if (tag.contains(MIGRATIONS, Tag.TAG_COMPOUND)) {
            final CompoundTag migrations = tag.getCompound(MIGRATIONS);
            for (final String id : migrations.getAllKeys()) {
                final ListTag chunks = migrations.getCompound(id).getList("Chunks", Tag.TAG_COMPOUND);
                final Map<Long, Integer> entries = data.completed.computeIfAbsent(id, k -> new HashMap<>());
                for (int i = 0; i < chunks.size(); i++) {
                    final CompoundTag entry = chunks.getCompound(i);
                    entries.put(entry.getLong("Pos"), Math.max(1, entry.getInt("Version")));
                }
            }
            return data;
        }

        // Compatibility with the pre-framework SavedData format: every old
        // painted chunk was already processed by the only migration, so import
        // it as version 1 and never replay it on upgrade.
        if (tag.contains("Painted", Tag.TAG_LONG_ARRAY)) {
            final Map<Long, Integer> legacy = data.completed.computeIfAbsent(LEGACY_ID, k -> new HashMap<>());
            final int version = Math.max(1, tag.getInt("Version"));
            for (final long chunk : tag.getLongArray("Painted")) legacy.put(chunk, version);
        }
        return data;
    }
}
