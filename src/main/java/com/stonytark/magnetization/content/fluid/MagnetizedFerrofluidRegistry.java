package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Loaded magnetized-fluid sources, retaining live polarity and a chunk index. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagnetizedFerrofluidRegistry {
    private static final Map<Level, SourceMap> BY_LEVEL = new WeakHashMap<>();
    private MagnetizedFerrofluidRegistry() {}

    public static synchronized void add(final Level level, final BlockPos pos, final MagneticPolarity polarity) {
        BY_LEVEL.computeIfAbsent(level, ignored -> new SourceMap()).put(pos, polarity);
    }

    public static synchronized void remove(final Level level, final BlockPos pos) {
        final SourceMap sources = BY_LEVEL.get(level);
        if (sources != null) sources.remove(pos);
    }

    /** Live map, including indexed mutations through its key/entry views. */
    public static synchronized Map<BlockPos, MagneticPolarity> forLevel(final Level level) {
        final SourceMap sources = BY_LEVEL.get(level);
        return sources == null ? Map.of() : sources;
    }

    public static synchronized List<BlockPos> snapshotNear(final Level level, final BlockPos target, final int radius) {
        final SourceMap sources = BY_LEVEL.get(level);
        return sources == null ? List.of() : sources.near(target, radius);
    }

    public static synchronized void dropChunk(final Level level, final ChunkPos chunk) {
        final SourceMap sources = BY_LEVEL.get(level);
        if (sources != null) sources.dropChunk(chunk.toLong());
    }

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) rebuildChunkIndex(level, chunk);
    }

    @SubscribeEvent
    public static void onChunkUnload(final ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level) dropChunk(level, event.getChunk().getPos());
    }

    @SubscribeEvent
    public static synchronized void onLevelUnload(final LevelEvent.Unload event) {
        final SourceMap sources = BY_LEVEL.remove(event.getLevel());
        if (sources != null) sources.clear();
    }

    /** Palette-gated reload discovery; unloading never reads chunk contents. */
    public static synchronized void rebuildChunkIndex(final Level level, final LevelChunk chunk) {
        dropChunk(level, chunk.getPos());
        final var block = MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get();
        final var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            final var section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) continue;
            final int baseY = (chunk.getMinSection() + i) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                final var state = section.getBlockState(x, y, z);
                if (state.is(block) && state.getFluidState().isSource()) {
                    add(level, new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y,
                            chunk.getPos().getMinBlockZ() + z), state.getValue(MagnetizedFerrofluidBlock.POLARITY));
                }
            }
        }
    }

    /** The map remains writable for API compatibility; every mutation goes
     * through the same index maintenance, including iterator.remove/setValue. */
    private static final class SourceMap extends AbstractMap<BlockPos, MagneticPolarity> {
        private final Map<BlockPos, MagneticPolarity> positions = new ConcurrentHashMap<>();
        private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();

        @Override public int size() { return positions.size(); }
        @Override public MagneticPolarity get(Object key) { return positions.get(key); }
        @Override public boolean containsKey(Object key) { return positions.containsKey(key); }
        @Override public synchronized MagneticPolarity put(BlockPos key, MagneticPolarity value) {
            final BlockPos pos = key.immutable();
            final MagneticPolarity old = positions.put(pos, value);
            if (old == null) chunks.computeIfAbsent(ChunkPos.asLong(pos), ignored -> new HashSet<>()).add(pos);
            return old;
        }
        @Override public synchronized MagneticPolarity remove(Object key) {
            final MagneticPolarity old = positions.remove(key);
            if (old != null && key instanceof BlockPos pos) {
                final long chunk = ChunkPos.asLong(pos);
                final Set<BlockPos> bucket = chunks.get(chunk);
                if (bucket != null) {
                    bucket.remove(pos);
                    if (bucket.isEmpty()) chunks.remove(chunk);
                }
            }
            return old;
        }
        @Override public synchronized void clear() { positions.clear(); chunks.clear(); }

        private synchronized void dropChunk(long key) {
            final Set<BlockPos> bucket = chunks.remove(key);
            if (bucket != null) bucket.forEach(positions::remove);
        }
        private synchronized List<BlockPos> near(BlockPos target, int radius) {
            if (radius < 0) throw new IllegalArgumentException("Negative source radius");
            if (positions.isEmpty()) return List.of();
            final List<BlockPos> result = new ArrayList<>();
            for (int x = Math.floorDiv(target.getX() - radius, 16); x <= Math.floorDiv(target.getX() + radius, 16); x++) {
                for (int z = Math.floorDiv(target.getZ() - radius, 16); z <= Math.floorDiv(target.getZ() + radius, 16); z++) {
                    final Set<BlockPos> bucket = chunks.get(ChunkPos.asLong(x, z));
                    if (bucket != null) result.addAll(bucket);
                }
            }
            return result;
        }

        @Override public Set<BlockPos> keySet() {
            return new AbstractSet<>() {
                @Override public int size() { return positions.size(); }
                @Override public boolean contains(Object key) { return positions.containsKey(key); }
                @Override public boolean remove(Object key) { return SourceMap.this.remove(key) != null; }
                @Override public void clear() { SourceMap.this.clear(); }
                @Override public Iterator<BlockPos> iterator() {
                    final Iterator<BlockPos> keys = positions.keySet().iterator();
                    return new Iterator<>() {
                        private BlockPos current;
                        public boolean hasNext() { return keys.hasNext(); }
                        public BlockPos next() { return current = keys.next(); }
                        public void remove() {
                            if (current == null) throw new IllegalStateException();
                            SourceMap.this.remove(current);
                            current = null;
                        }
                    };
                }
            };
        }

        @Override public Set<Entry<BlockPos, MagneticPolarity>> entrySet() {
            return new AbstractSet<>() {
                @Override public int size() { return positions.size(); }
                @Override public void clear() { SourceMap.this.clear(); }
                @Override public Iterator<Entry<BlockPos, MagneticPolarity>> iterator() {
                    final Iterator<Entry<BlockPos, MagneticPolarity>> entries = positions.entrySet().iterator();
                    return new Iterator<>() {
                        private BlockPos current;
                        public boolean hasNext() { return entries.hasNext(); }
                        public Entry<BlockPos, MagneticPolarity> next() {
                            final var entry = entries.next();
                            current = entry.getKey();
                            return new SimpleEntry<>(entry) {
                                @Override public MagneticPolarity setValue(MagneticPolarity value) {
                                    SourceMap.this.put(getKey(), value);
                                    return super.setValue(value);
                                }
                            };
                        }
                        public void remove() {
                            if (current == null) throw new IllegalStateException();
                            SourceMap.this.remove(current);
                            current = null;
                        }
                    };
                }
            };
        }
    }
}
