package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.fluid.FerrofluidSourceRegistry;
import com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.LoadedChunkAccess;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Chunk-local index and target-centric scheduler for optional-mod field emitters.
 * Create: New Age worldgen can place tens of thousands of magnetite blocks in a
 * pregenerated save, so no tick path may walk the complete external index.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class ExternalEmitterTracker {
    /** Every supported external field currently has a maximum 32-block range. */
    static final int MAX_EXTERNAL_FIELD_RANGE = (int) MagneticStrength.EXTREME.range();
    /** Hard scan bound before the smaller application budget is selected. */
    static final int MAX_CANDIDATES_PER_TICK = 2048;
    /** Target collection is also bounded so entity piles cannot dominate a tick. */
    static final int MAX_TARGETS_PER_TICK = 512;

    private static final WeakHashMap<ServerLevel, Integer> TARGET_CURSOR = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, Integer> CANDIDATE_CURSOR = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, Integer> LAST_CANDIDATES = new WeakHashMap<>();
    private static final WeakHashMap<ServerLevel, Integer> LAST_APPLIED = new WeakHashMap<>();

    private ExternalEmitterTracker() {}

    @SubscribeEvent
    public static void onChunkLoad(final ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        // Chunk events already run on the owning level thread. Replace the bucket
        // now; deferring this work lets unload race ahead and resurrect stale data.
        rebuildChunkIndex(level, chunk);
    }

    @SubscribeEvent
    public static void onChunkUnload(final ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) return;
        // Never inspect unloading states or scan the global registry. The chunk
        // key is authoritative, so dropping its external bucket is unconditional.
        EmitterRegistry.dropExternalChunk(level, event.getChunk().getPos());
    }

    @SubscribeEvent
    public static void onPlace(final BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (ExternalFieldCompat.isIndexableEmitter(event.getPlacedBlock())) {
            EmitterRegistry.registerExternal(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBreak(final BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && ExternalFieldCompat.isKnownEmitter(event.getState())) {
            EmitterRegistry.unregisterExternal(level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        final TargetSchedule schedule = scheduleNearActiveTargets(server);
        LAST_CANDIDATES.put(server, schedule.candidates.size());
        final int applied = applyBudgeted(server, schedule.candidates, applicationBudget());
        LAST_APPLIED.put(server, applied);
        EnderFieldRelayCompat.apply(server, schedule.chunkKeys);
    }

    private static TargetSchedule scheduleNearActiveTargets(final ServerLevel server) {
        final List<ChunkBounds> targets = gatherTargets(server);
        if (targets.isEmpty()) return new TargetSchedule(List.of(), Set.of());

        final int start = Math.floorMod(TARGET_CURSOR.getOrDefault(server, 0), targets.size());
        final int targetCount = Math.min(MAX_TARGETS_PER_TICK, targets.size());
        final Set<Long> chunkKeys = new LinkedHashSet<>();
        for (int i = 0; i < targetCount; i++) {
            addChunkKeys(chunkKeys, targets.get((start + i) % targets.size()));
        }
        TARGET_CURSOR.put(server, (start + targetCount) % targets.size());
        return new TargetSchedule(new ArrayList<>(EmitterRegistry.snapshotExternalInChunks(
                server, chunkKeys, MAX_CANDIDATES_PER_TICK)), chunkKeys);
    }

    private static List<ChunkBounds> gatherTargets(final ServerLevel server) {
        final List<ChunkBounds> targets = new ArrayList<>();
        for (final Entity entity : server.getAllEntities()) {
            if (FieldApplicator.isMagnetizableTarget(entity)) {
                targets.add(around(entity.getX(), entity.getZ(), MAX_EXTERNAL_FIELD_RANGE));
            }
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container != null) {
            for (final var subLevel : container.getAllSubLevels()) {
                if (!(subLevel instanceof ServerSubLevel ship)
                        || ship.getMassTracker().isInvalid() || ship.getMassTracker().getMass() <= 0.0d) continue;
                final BoundingBox3dc box = ship.boundingBox();
                targets.add(bounds(box.minX() - MAX_EXTERNAL_FIELD_RANGE,
                        box.maxX() + MAX_EXTERNAL_FIELD_RANGE,
                        box.minZ() - MAX_EXTERNAL_FIELD_RANGE,
                        box.maxZ() + MAX_EXTERNAL_FIELD_RANGE));
            }
        }

        for (final BlockPos pos : FerrofluidSourceRegistry.snapshot(server)) {
            targets.add(around(pos.getX() + 0.5d, pos.getZ() + 0.5d, MAX_EXTERNAL_FIELD_RANGE));
        }
        for (final BlockPos pos : MagnetizedFerrofluidRegistry.forLevel(server).keySet()) {
            targets.add(around(pos.getX() + 0.5d, pos.getZ() + 0.5d, MAX_EXTERNAL_FIELD_RANGE));
        }
        return targets;
    }

    private static int applyBudgeted(final ServerLevel server, final List<BlockPos> candidates,
                                     final int budget) {
        if (budget <= 0 || candidates.isEmpty()) return 0;
        final int start = Math.floorMod(CANDIDATE_CURSOR.getOrDefault(server, 0), candidates.size());
        final int attempts = Math.min(budget, candidates.size());
        int applied = 0;
        for (int i = 0; i < attempts; i++) {
            final BlockPos pos = candidates.get((start + i) % candidates.size());
            final BlockState state = LoadedChunkAccess.blockState(server, pos);
            if (state == null || !ExternalFieldCompat.isIndexableEmitter(state)) {
                EmitterRegistry.unregisterExternal(server, pos);
                continue;
            }
            if (!ExternalFieldCompat.isSupportedEmitter(state)) continue;
            final MagneticField field = ExternalFieldCompat.currentField(server, pos, state);
            if (field == null) continue;
            if (ExternalFieldCompat.shipsOnly(state)) {
                FieldApplicator.applyToSubLevelsOnly(server, field, null, null);
            } else {
                FieldApplicator.apply(server, field);
            }
            applied++;
        }
        CANDIDATE_CURSOR.put(server, (start + attempts) % candidates.size());
        return applied;
    }

    private static int applicationBudget() {
        return com.stonytark.magnetization.config.MagConfig.externalFieldApplicationBudget();
    }

    private static List<BlockPos> scan(final LevelChunk chunk) {
        final List<BlockPos> positions = new ArrayList<>();
        final LevelChunkSection[] sections = chunk.getSections();
        final int minSection = chunk.getMinSection();
        final int baseX = chunk.getPos().getMinBlockX();
        final int baseZ = chunk.getPos().getMinBlockZ();
        for (int si = 0; si < sections.length; si++) {
            final LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(ExternalFieldCompat::isIndexableEmitter)) continue;
            final int baseY = (minSection + si) << 4;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                if (ExternalFieldCompat.isIndexableEmitter(section.getBlockState(x, y, z))) {
                    positions.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                }
            }
        }
        return positions;
    }

    /** Explicit rebuild hook used by focused compatibility GameTests. */
    public static void rebuildChunkIndex(final Level level, final LevelChunk chunk) {
        EmitterRegistry.replaceExternalChunk(level, chunk.getPos(), scan(chunk));
    }

    private record ChunkBounds(int minX, int maxX, int minZ, int maxZ) {}
    private record TargetSchedule(List<BlockPos> candidates, Collection<Long> chunkKeys) {}

    private static ChunkBounds around(final double x, final double z, final int radius) {
        return bounds(x - radius, x + radius, z - radius, z + radius);
    }

    private static ChunkBounds bounds(final double minX, final double maxX,
                                      final double minZ, final double maxZ) {
        return new ChunkBounds(Math.floorDiv(Mth.floor(minX), 16), Math.floorDiv(Mth.floor(maxX), 16),
                Math.floorDiv(Mth.floor(minZ), 16), Math.floorDiv(Mth.floor(maxZ), 16));
    }

    private static void addChunkKeys(final Set<Long> keys, final ChunkBounds bounds) {
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) keys.add(ChunkPos.asLong(x, z));
        }
    }

    /** Diagnostic counters used by focused GameTests. */
    public static int lastCandidateCount(final ServerLevel level) {
        return LAST_CANDIDATES.getOrDefault(level, 0);
    }

    public static int lastAppliedCount(final ServerLevel level) {
        return LAST_APPLIED.getOrDefault(level, 0);
    }
}
