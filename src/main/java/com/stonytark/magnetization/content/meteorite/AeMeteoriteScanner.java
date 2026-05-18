package com.stonytark.magnetization.content.meteorite;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches chunk loads on the server side; when a chunk contains an Applied
 * Energistics 2 meteor structure, registers a {@link MeteoriteFieldRegistry}
 * entry at the structure's bounding-box centre. AE2 meteorites then emit the
 * same decaying magnetic field a native meteorite_core would, no extra block
 * placed.
 *
 * <p>Pure-reflection AE2 detection: we never import an AE2 class. Match by
 * the structure registry key's string form — {@code namespace == "ae2"} and
 * {@code path.contains("meteorite")}. That's resilient to AE2 renaming the
 * specific structure id between versions (which historically has happened).
 *
 * <p>Gated by:
 * <ul>
 *   <li>{@code ModList.isLoaded("ae2")} — no-op when AE2 isn't installed,
 *       so no class-load on the AE2 side.</li>
 *   <li>{@link MagConfig#AE2_METEORITE_HOOK_ENABLED} — server-tunable kill
 *       switch in case the integration misbehaves on a particular AE2 build.</li>
 * </ul>
 *
 * <p>Per-chunk dedup: once we've scanned a chunk we record its position in
 * {@link #SCANNED_CHUNKS} so subsequent re-loads don't re-scan. Persistence
 * of the resulting field entries is handled by {@link MeteoriteFieldRegistry}
 * — chunk-load → register → SavedData picks it up.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class AeMeteoriteScanner {

    /** Per-level set of chunk positions we've already scanned. Concurrent so
     *  multi-level servers can scan in parallel; the inner sets are
     *  synchronizedSets so add() is atomic. */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Set<Long>>
            SCANNED_CHUNKS = new ConcurrentHashMap<>();

    private AeMeteoriteScanner() {}

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (!ModList.get().isLoaded("ae2")) return;
        if (!liveEnabled()) return;

        final LevelChunk chunk;
        if (event.getChunk() instanceof LevelChunk lc) chunk = lc;
        else return;  // ChunkAccess but not yet FULL — skip; we'll get a Load event later.

        final ChunkPos cp = chunk.getPos();
        final Set<Long> seen = SCANNED_CHUNKS.computeIfAbsent(server.dimension(),
                k -> java.util.Collections.synchronizedSet(new java.util.HashSet<>()));
        if (!seen.add(cp.toLong())) return;  // already scanned this chunk

        // Walk the chunk's structure starts; for each one whose registry key
        // looks like an AE2 meteorite, derive the centre and register a field.
        final Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return;
        final long now = server.getGameTime();
        for (final Map.Entry<Structure, StructureStart> e : starts.entrySet()) {
            final ResourceLocation key = server.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(e.getKey());
            if (key == null) continue;
            if (!isAeMeteorite(key)) continue;
            final BoundingBox bb = e.getValue().getBoundingBox();
            final BlockPos centre = new BlockPos(
                    (bb.minX() + bb.maxX()) / 2,
                    (bb.minY() + bb.maxY()) / 2,
                    (bb.minZ() + bb.maxZ()) / 2);
            MeteoriteFieldRegistry.register(server, centre, now);
        }
    }

    /** Matches any structure key whose namespace is {@code "ae2"} and whose
     *  path contains {@code "meteorite"} (case-insensitive). Resilient to
     *  AE2 renaming the specific structure id — historically the path has
     *  been {@code "meteorite"} and {@code "meteorites"} across versions. */
    static boolean isAeMeteorite(final ResourceLocation key) {
        if (!"ae2".equals(key.getNamespace())) return false;
        return key.getPath().toLowerCase(java.util.Locale.ROOT).contains("meteorite");
    }

    private static boolean liveEnabled() {
        try { return MagConfig.AE2_METEORITE_HOOK_ENABLED.get(); }
        catch (final Throwable t) { return true; }
    }
}
