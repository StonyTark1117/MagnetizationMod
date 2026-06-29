package com.stonytark.magnetization.content.sail;

import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-ship cached count of Solar Sail panels, so each panel can scale its
 * thrust + cruising-speed cap with the craft's total sail area without every
 * panel re-scanning the ship every tick. TTL-cached like
 * {@link com.stonytark.magnetization.physics.ShipMagneticRegistry}; a 2 s
 * staleness is harmless for a slowly-built sail.
 */
@EventBusSubscriber(modid = com.stonytark.magnetization.Magnetization.MOD_ID)
public final class SailPanelCounter {

    private static final long TTL_TICKS = 40L;
    private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(int count, long tick) {}

    private SailPanelCounter() {}

    /** Drop the per-ship cache when the server stops. The cache is keyed by ship
     *  UUID (not by level), so without this it would retain one entry per ship ever
     *  built for the whole game session — a slow leak across world reloads. Ships
     *  re-fill lazily on the next {@link #count}. */
    @SubscribeEvent
    public static void onServerStopped(final ServerStoppedEvent event) {
        CACHE.clear();
    }

    /** Total Solar Sail panels on {@code ship} (≥ 1 while at least this one exists). */
    public static int count(final ServerLevel level, final ServerSubLevel ship) {
        final long now = level.getGameTime();
        final UUID id = ship.getUniqueId();
        final Entry cached = CACHE.get(id);
        if (cached != null && now - cached.tick() < TTL_TICKS) return cached.count();
        final int n = scan(ship);
        CACHE.put(id, new Entry(n, now));
        return n;
    }

    private static int scan(final ServerSubLevel ship) {
        int panels = 0;
        try {
            for (final PlotChunkHolder holder : ship.getPlot().getLoadedChunks()) {
                final LevelChunk chunk = holder.getChunk();
                for (final LevelChunkSection section : chunk.getSections()) {
                    if (section == null || section.hasOnlyAir()) continue;
                    final PalettedContainer<BlockState> states = section.getStates();
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dy = 0; dy < 16; dy++) {
                            for (int dz = 0; dz < 16; dz++) {
                                if (states.get(dx, dy, dz).is(MagBlocks.SOLAR_SAIL.get())) panels++;
                            }
                        }
                    }
                }
            }
        } catch (final Throwable ignored) {
            // Sub-level torn down mid-scan — fall back to whatever we counted.
        }
        return Math.max(panels, 1);
    }
}
