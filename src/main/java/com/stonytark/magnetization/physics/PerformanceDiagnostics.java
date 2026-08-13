package com.stonytark.magnetization.physics;

import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in, low-frequency counters used with the performance stress harness.
 * Disabled runs pay only a predictable boolean branch at each instrumentation
 * point. Enable with {@code -Dmagnetization.performanceDiagnostics=true}.
 */
public final class PerformanceDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("magnetization/PerformanceDiagnostics");
    private static final boolean ENABLED = Boolean.getBoolean("magnetization.performanceDiagnostics");
    private static final long LOG_INTERVAL_TICKS = 1200L;
    private static final Map<ServerLevel, Counters> LEVEL_COUNTERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PerformanceDiagnostics() {}

    public static void recordGasRecompute(final ServerLevel level, final int cells, final boolean deduplicated) {
        if (!ENABLED) return;
        final Counters counters = counters(level);
        counters.gasRequests++;
        if (deduplicated) counters.gasDeduplicated++;
        else counters.gasCells += cells;
    }

    public static void recordFieldQuery(final ServerLevel level, final int candidates) {
        if (!ENABLED) return;
        final Counters counters = counters(level);
        counters.fieldQueries++;
        counters.fieldCandidates += candidates;
    }

    public static void recordTargetClassification(final ServerLevel level) {
        if (ENABLED) counters(level).targetClassifications++;
    }

    public static void recordTrainField(final ServerLevel level, final int trackedCarriages) {
        if (!ENABLED) return;
        final Counters counters = counters(level);
        counters.trainFields++;
        counters.trainCarriages += trackedCarriages;
    }

    public static void recordInventoryFullScan(final ServerLevel level, final int facesChecked) {
        if (!ENABLED) return;
        final Counters counters = counters(level);
        counters.inventoryFullScans++;
        counters.inventoryFacesChecked += facesChecked;
    }

    public static void onLevelUnload(final ServerLevel level) {
        LEVEL_COUNTERS.remove(level);
    }

    private static Counters counters(final ServerLevel level) {
        final Counters counters = LEVEL_COUNTERS.computeIfAbsent(level, ignored -> new Counters());
        final long now = level.getGameTime();
        if (counters.startedAt == Long.MIN_VALUE) counters.startedAt = now;
        if (now - counters.startedAt >= LOG_INTERVAL_TICKS) {
            LOGGER.info("MAG_PERF dimension={} ticks={} gas_requests={} gas_deduplicated={} gas_cells={} "
                            + "field_queries={} field_candidates={} target_classifications={} "
                            + "train_fields={} train_carriages={} inventory_full_scans={} inventory_faces_checked={}",
                    level.dimension().location(), now - counters.startedAt,
                    counters.gasRequests, counters.gasDeduplicated, counters.gasCells,
                    counters.fieldQueries, counters.fieldCandidates, counters.targetClassifications,
                    counters.trainFields, counters.trainCarriages,
                    counters.inventoryFullScans, counters.inventoryFacesChecked);
            counters.reset(now);
        }
        return counters;
    }

    private static final class Counters {
        private long startedAt = Long.MIN_VALUE;
        private long gasRequests;
        private long gasDeduplicated;
        private long gasCells;
        private long fieldQueries;
        private long fieldCandidates;
        private long targetClassifications;
        private long trainFields;
        private long trainCarriages;
        private long inventoryFullScans;
        private long inventoryFacesChecked;

        private void reset(final long now) {
            startedAt = now;
            gasRequests = 0L;
            gasDeduplicated = 0L;
            gasCells = 0L;
            fieldQueries = 0L;
            fieldCandidates = 0L;
            targetClassifications = 0L;
            trainFields = 0L;
            trainCarriages = 0L;
            inventoryFullScans = 0L;
            inventoryFacesChecked = 0L;
        }
    }
}
