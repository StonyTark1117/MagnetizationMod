package com.stonytark.magnetization.physics;

import com.stonytark.magnetization.api.ShipMagneticState;
import com.stonytark.magnetization.config.MagConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-ship cache of {@link ShipMagneticState}. The {@link FieldApplicator}
 * consults this on every emitter pass; the alternative — re-scanning a ship's
 * blocks on every tick of every field — would be ruinous on large airships.
 *
 * <p>Scan ordering:
 * <ul>
 *   <li>cold lookup → scan now, cache, return</li>
 *   <li>warm lookup, age &lt; interval → return cached</li>
 *   <li>warm lookup, age ≥ interval → re-scan, refresh cache, return</li>
 * </ul>
 * Ships that are never touched by an emitter pay nothing because the cache is
 * filled lazily on first {@link #get} call. Entries are keyed by the ship's
 * UUID and live in a per-level map, so different dimensions don't share cache
 * lines and a dimension unload drops every entry it owned via
 * {@link #onLevelUnload(ServerLevel)}.
 */
public final class ShipMagneticRegistry {

    private static final Logger LOG = LoggerFactory.getLogger("magnetization/ShipMagneticRegistry");
    private static final Map<ServerLevel, Map<UUID, CachedEntry>> BY_LEVEL = new ConcurrentHashMap<>();

    private static final class CachedEntry {
        final ShipMagneticState state;
        final long tick;

        CachedEntry(final ShipMagneticState state, final long tick) {
            this.state = state;
            this.tick = tick;
        }
    }

    private ShipMagneticRegistry() {}

    /** Resolve the current state for {@code ship}, scanning if the cache is
     *  cold or stale. Returns {@link ShipMagneticState#DEFAULT} for the rare
     *  cases where the scan throws — e.g. a sub-level removed mid-scan — so the
     *  caller never sees a null. */
    public static ShipMagneticState get(final ServerLevel level, final ServerSubLevel ship) {
        final long now = level.getGameTime();
        final long interval = scanIntervalTicks();
        final Map<UUID, CachedEntry> levelMap = BY_LEVEL.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        final CachedEntry cached = levelMap.get(ship.getUniqueId());
        if (cached != null && (now - cached.tick) < interval) return cached.state;

        ShipMagneticState fresh;
        try {
            fresh = ShipMagneticScanner.scan(ship);
        } catch (final Throwable t) {
            // Scanner blew up (rare — usually a JNI panic from Sable mid-shatter).
            // Fall back to DEFAULT so callers don't see null, but surface the
            // failure so a recurring scan break doesn't go unnoticed.
            LOG.warn("Ship scan failed for {}, using DEFAULT state", ship.getUniqueId(), t);
            fresh = ShipMagneticState.DEFAULT;
        }
        levelMap.put(ship.getUniqueId(), new CachedEntry(fresh, now));
        return fresh;
    }

    /** Force-invalidate the cache entry for {@code ship} — called when a ship is
     *  removed/shattered so a re-used UUID doesn't get a stale read. Safe to call
     *  for ships that were never in the cache. */
    public static void invalidate(final ServerLevel level, final ServerSubLevel ship) {
        final Map<UUID, CachedEntry> levelMap = BY_LEVEL.get(level);
        if (levelMap != null) levelMap.remove(ship.getUniqueId());
    }

    /** Drop every cached entry for {@code level} so the next {@link #get} call on
     *  any ship re-scans from scratch. Used by block-change hooks (e.g.
     *  {@link com.stonytark.magnetization.content.inverter.PolarityInverterBlock}
     *  on place/break) to bypass the TTL when a structurally relevant block
     *  moves — looking up the specific containing ship from a sub-level-local
     *  blockpos is fiddly across the Sable boundary, but invalidating the level
     *  is cheap (one map clear, ships re-fill lazily on demand). */
    public static void invalidateAll(final ServerLevel level) {
        final Map<UUID, CachedEntry> levelMap = BY_LEVEL.get(level);
        if (levelMap != null) levelMap.clear();
    }

    /** Drop every entry for {@code level} — call on dimension unload so memory
     *  isn't leaked between worlds. */
    public static void onLevelUnload(final ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    private static long scanIntervalTicks() {
        try { return MagConfig.SHIP_SCAN_INTERVAL_TICKS.get(); } catch (final Throwable t) { return 100L; }
    }
}
