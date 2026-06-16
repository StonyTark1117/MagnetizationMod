package com.stonytark.magnetization.content.effect;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.worldgen.PetrifiedForestBiome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * Frequent lightning storms in the Petrified Forest biome — the biome's
 * defining environmental flavor, and the reason {@link LightningRemnantMagnetism}
 * exists as a gameplay system worth seeing. Runs every {@link #INTERVAL_TICKS}
 * server ticks per level: for each player whose feet are in the petrified
 * forest, rolls a strike chance and (on success) drops a lightning bolt at a
 * random surface column within a small radius. Vanilla weather state doesn't
 * matter — these strikes happen regardless of whether the world is "raining."
 *
 * <p>Cheap implementation: we iterate players only (not chunks). A player
 * standing in the biome sees ≈3 strikes per minute on average; players outside
 * the biome see none. Strikes can hit the player themselves (rare lateral
 * placement), which is intentional — that's how LIRM stamps your gear.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class PetrifiedForestStorms {

    /** Per-player probability of a strike on each evaluation tick.
     *  0.5 × (1 / 5 s) ≈ 6 strikes/minute when a player is in the biome. */
    private static final double STRIKE_CHANCE_PER_PLAYER = 0.5d;

    /** Lateral radius (blocks) within which strikes can land around the player.
     *  Small enough that strikes feel local; large enough that most don't
     *  actually hit the player. */
    private static final int STRIKE_HORIZONTAL_RADIUS = 16;

    private PetrifiedForestStorms() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!PetrifiedForestBiome.enabled()) return;
        final Level level = event.getLevel();
        if (!(level instanceof ServerLevel server)) return;
        if ((server.getGameTime() % com.stonytark.magnetization.config.MagConfig.petrifiedStormTicks()) != 0L) return;

        // Snapshot the player list: lightning spawn can mutate per-player state
        // (entity damage / death) and we don't want concurrent-modification.
        final List<ServerPlayer> players = server.players();
        if (players.isEmpty()) return;

        for (final ServerPlayer player : players) {
            if (player.isSpectator() || player.isDeadOrDying()) continue;
            if (!PetrifiedForestBiome.isAt(server, player.blockPosition())) continue;
            if (server.random.nextDouble() >= STRIKE_CHANCE_PER_PLAYER) continue;

            // Random offset in a horizontal disc around the player; find the
            // top surface block so the strike lands visibly on terrain.
            final int dx = server.random.nextInt(STRIKE_HORIZONTAL_RADIUS * 2 + 1)
                    - STRIKE_HORIZONTAL_RADIUS;
            final int dz = server.random.nextInt(STRIKE_HORIZONTAL_RADIUS * 2 + 1)
                    - STRIKE_HORIZONTAL_RADIUS;
            final int x = player.getBlockX() + dx;
            final int z = player.getBlockZ() + dz;
            // Re-check biome at the target — players near the edge might roll a
            // strike on a non-forest cell, which we skip to keep the effect
            // contained to the biome boundary.
            final BlockPos surface = server.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            if (!PetrifiedForestBiome.isAt(server, surface)) continue;

            spawnBolt(server, surface);

            if (MagConfig.debugLogging()) {
                org.slf4j.LoggerFactory.getLogger("magnetization/PetrifiedStorms")
                        .info("Petrified storm strike at {} (player {})",
                                surface.toShortString(), player.getScoreboardName());
            }
        }
    }

    private static void spawnBolt(final ServerLevel server, final BlockPos pos) {
        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt == null) return;
        bolt.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, 0.0f, 0.0f);
        server.addFreshEntity(bolt);
    }
}
