package com.stonytark.magnetization.worldgen;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Post-generation surface repaint for our biomes. TerraBlender's surface-rule
 * injection is silently overwritten when Citadel (Alex's Caves dep) also wraps
 * {@code NoiseGeneratorSettings.surfaceRule()} for the overworld — both mods
 * @Inject at the same target and the last-loaded one wins. Result: our
 * registered {@link MagSurfaceRules} never fires at chunk-gen, so the visible
 * surface stays vanilla grass.
 *
 * <p>Strategy: scan a small radius around each online player once per second,
 * paint any not-yet-seen chunk we find in one of our biomes, and cap the work
 * at {@link #MAX_CHUNKS_PER_TICK} chunks across all players so a player who
 * teleports into a fresh anomaly biome doesn't freeze the server while the
 * repaint catches up. Earlier ChunkEvent.Load-based approach raced the
 * /tp pipeline (chunks loaded with player still at the old position, the
 * "near a player" gate skipped them, and they were marked seen so the
 * repaint never re-fired). Polling on the player avoids that race entirely.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class ChunkSurfaceRepaintHandler {

    /** Tick interval between scans. 20 ticks = 1s — well above what the
     *  player can visually notice as a "this chunk was just painted" lag. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    /** Chunk-radius around each player to scan per pass. 8 covers the
     *  default render-distance core; chunks farther out get painted on
     *  later passes as the player moves toward them. */
    private static final int SCAN_RADIUS_CHUNKS = 8;

    /** Hard cap on chunks repainted per single server tick across the whole
     *  scan. Keeps the worst case (player tps into a totally fresh region)
     *  from monopolising the tick thread — leftover chunks just get caught
     *  on the next pass 1s later. */
    private static final int MAX_CHUNKS_PER_TICK = 8;

    /** Per-(dim, chunk) seen-set: paint-once gate. Lives for the JVM session;
     *  surface paint is idempotent against the vanilla-replaceable predicate
     *  so re-applying on session restart is fine. */
    private static final Set<Long> SEEN = ConcurrentHashMap.newKeySet();

    private ChunkSurfaceRepaintHandler() {}

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        final var server = event.getServer();
        if (server == null) return;
        if ((server.getTickCount() % SCAN_INTERVAL_TICKS) != 0) return;
        // Nothing to repaint if neither custom biome is generating — skip the whole
        // player×chunk sweep (and the SEEN growth) entirely on servers that disabled
        // both biomes via config.
        if (!AnomalyBiome.enabled() && !PetrifiedForestBiome.enabled()) return;

        int budget = MAX_CHUNKS_PER_TICK;
        for (final ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            // Collect unique chunk positions near any player on this level.
            final Set<Long> targets = new HashSet<>();
            for (final ServerPlayer p : level.players()) {
                final int pcx = p.blockPosition().getX() >> 4;
                final int pcz = p.blockPosition().getZ() >> 4;
                for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
                    for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                        targets.add(ChunkPos.asLong(pcx + dx, pcz + dz));
                    }
                }
            }
            final long dimHash = ((long) level.dimension().location().hashCode()) << 32;
            for (final long packed : targets) {
                if (budget <= 0) return;
                final long key = dimHash ^ packed;
                if (SEEN.contains(key)) continue;
                final var pos = new ChunkPos(packed);
                // Only act on already-loaded chunks; skip otherwise so we
                // don't force a load we wouldn't have done anyway.
                if (!level.hasChunk(pos.x, pos.z)) continue;
                final LevelChunk chunk = level.getChunk(pos.x, pos.z);
                // Examining a chunk is the expensive part — a full 16×16 column walk
                // with a heightmap + biome lookup per column — whether or not it ends
                // up painting (a wrong-biome chunk still walks every column before
                // finding nothing to do). Charge the per-tick budget for every
                // examination, not just successful paints, so dropping into a fresh
                // region examines at most MAX_CHUNKS_PER_TICK chunks per tick instead
                // of walking the whole render-distance neighborhood in one tick.
                // Mark seen either way so it's a paint-once / examine-once gate.
                paintIfRelevant(level, chunk);
                SEEN.add(key);
                budget--;
            }
        }
    }

    /** Walk every column of the chunk, painting tops + 1-below for any
     *  column whose biome is one of ours. Returns true if at least one
     *  block was actually changed (so the caller can charge it against
     *  the per-tick budget). */
    private static boolean paintIfRelevant(final ServerLevel level, final LevelChunk chunk) {
        final ChunkPos cp = chunk.getPos();
        final int maxY = level.getMaxBuildHeight() - 1;
        final int minY = level.getMinBuildHeight();
        boolean modified = false;

        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int x = cp.getMinBlockX() + dx;
                final int z = cp.getMinBlockZ() + dz;

                int topY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 1;
                if (topY < minY + 1) continue;
                // Walk up if a decoration sits above the heightmap top.
                while (topY < maxY && !chunk.getBlockState(cursor.set(x, topY + 1, z)).isAir()) {
                    topY++;
                }

                final BlockPos topPos = new BlockPos(x, topY, z);
                final var biomeHolder = chunk.getNoiseBiome((x & 15) >> 2, topY >> 2, (z & 15) >> 2);

                if (biomeHolder.is(AnomalyBiome.KEY)) {
                    if (paintAnomalyColumn(level, chunk, x, z, topY, topPos)) modified = true;
                } else if (biomeHolder.is(PetrifiedForestBiome.KEY)) {
                    if (paintPetrifiedColumn(level, chunk, x, z, topY, topPos)) modified = true;
                }
            }
        }
        return modified;
    }

    private static boolean paintAnomalyColumn(final ServerLevel level,
                                               final LevelChunk chunk,
                                               final int x, final int z, final int topY,
                                               final BlockPos topPos) {
        final BlockState topState = chunk.getBlockState(topPos);

        // Anomaly water bodies are ferrofluid: walk down the water column from the
        // surface and swap each water source for ferrofluid (bounded depth).
        if (topState.is(Blocks.WATER)) {
            final BlockState ferro = com.stonytark.magnetization.registry.MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState();
            final BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
            boolean swapped = false;
            for (int y = topY, depth = 0; depth < 12 && y >= level.getMinBuildHeight(); y--, depth++) {
                c.set(x, y, z);
                if (!chunk.getBlockState(c).is(Blocks.WATER)) break;
                level.setBlock(c.immutable(), ferro, 2);
                swapped = true;
            }
            return swapped;
        }

        if (!isVanillaReplaceable(topState)) return false;

        boolean changed = false;
        level.setBlock(topPos, pickAnomaliteTop(x, z), 2);
        changed = true;

        if (topY - 1 >= level.getMinBuildHeight()) {
            final BlockPos subPos = new BlockPos(x, topY - 1, z);
            final BlockState subState = chunk.getBlockState(subPos);
            if (isVanillaReplaceable(subState) || subState.is(Blocks.STONE)) {
                level.setBlock(subPos, pickAnomaliteSub(x, z), 2);
            }
        }
        return changed;
    }

    private static boolean paintPetrifiedColumn(final ServerLevel level,
                                                 final LevelChunk chunk,
                                                 final int x, final int z, final int topY,
                                                 final BlockPos topPos) {
        final BlockState topState = chunk.getBlockState(topPos);
        if (!isVanillaReplaceable(topState)) return false;

        level.setBlock(topPos, Blocks.COARSE_DIRT.defaultBlockState(), 2);
        if (topY - 1 >= level.getMinBuildHeight()) {
            final BlockPos subPos = new BlockPos(x, topY - 1, z);
            final BlockState subState = chunk.getBlockState(subPos);
            if (subState.is(Blocks.GRASS_BLOCK) || subState.is(Blocks.DIRT)) {
                level.setBlock(subPos, Blocks.DIRT.defaultBlockState(), 2);
            }
        }
        return true;
    }

    private static boolean isVanillaReplaceable(final BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
            || state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.SAND)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.SNOW_BLOCK);
    }

    private static BlockState pickAnomaliteTop(final int x, final int z) {
        final int hash = Math.floorMod((x * 73856093) ^ (z * 19349663), 100);
        if (hash < 65) return MagBlocks.ANOMALY_STONE.get().defaultBlockState();
        if (hash < 80) return MagBlocks.MAGNETIC_GRAVEL.get().defaultBlockState();
        if (hash < 85) return MagBlocks.MAGNETITE_ORE.get().defaultBlockState();
        if (hash < 88) return Blocks.IRON_ORE.defaultBlockState();
        if (hash < 91) return MagBlocks.HEMATITE_ORE.get().defaultBlockState();
        if (hash < 94) return MagBlocks.MAGHEMITE_ORE.get().defaultBlockState();
        if (hash < 96) return MagBlocks.PYRRHOTITE_ORE.get().defaultBlockState();
        if (hash < 98) return MagBlocks.TITANOMAGNETITE_ORE.get().defaultBlockState();
        if (hash < 99) return Blocks.GOLD_ORE.defaultBlockState();
        return MagBlocks.RAW_MAGNETITE_BLOCK.get().defaultBlockState();
    }

    private static BlockState pickAnomaliteSub(final int x, final int z) {
        final int hash = Math.floorMod((x * 83492791) ^ (z * 24036583), 100);
        if (hash < 80) return MagBlocks.ANOMALY_STONE.get().defaultBlockState();
        if (hash < 85) return MagBlocks.MAGNETITE_ORE.get().defaultBlockState();
        if (hash < 88) return Blocks.IRON_ORE.defaultBlockState();
        if (hash < 91) return MagBlocks.HEMATITE_ORE.get().defaultBlockState();
        if (hash < 94) return MagBlocks.MAGHEMITE_ORE.get().defaultBlockState();
        if (hash < 96) return MagBlocks.PYRRHOTITE_ORE.get().defaultBlockState();
        if (hash < 98) return MagBlocks.TITANOMAGNETITE_ORE.get().defaultBlockState();
        if (hash < 99) return Blocks.GOLD_ORE.defaultBlockState();
        return MagBlocks.RAW_MAGNETITE_BLOCK.get().defaultBlockState();
    }
}
