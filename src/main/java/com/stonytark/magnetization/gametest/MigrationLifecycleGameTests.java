package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.worldgen.ChunkSurfaceRepaintHandler;
import com.stonytark.magnetization.worldgen.WorldChunkMigrations;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Persistent migration versioning, reload, and cross-level isolation coverage. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MigrationLifecycleGameTests {
    private static final String EMPTY = "empty";

    private MigrationLifecycleGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "migrationLifecycle")
    public static void successiveVersionsReloadAndIsolateState(final GameTestHelper helper) {
        final ServerLevel overworld = helper.getLevel();
        final ServerLevel nether = overworld.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            helper.fail("Migration lifecycle test requires the Nether");
            return;
        }

        final String id = "gametest_migration_" + UUID.randomUUID();
        final ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(1, 1, 1)));
        final AtomicInteger runs = new AtomicInteger();

        helper.assertTrue(WorldChunkMigrations.apply(overworld, id, 1, chunk, runs::incrementAndGet),
                "First migration version must run");
        helper.assertTrue(!WorldChunkMigrations.apply(overworld, id, 1, chunk, runs::incrementAndGet),
                "An already-processed chunk must not run version 1 twice");
        helper.assertTrue(WorldChunkMigrations.apply(overworld, id, 2, chunk, runs::incrementAndGet),
                "A successive migration version must run once");
        helper.assertTrue(!WorldChunkMigrations.apply(overworld, id, 2, chunk, runs::incrementAndGet),
                "Version 2 must remain closed after it runs");
        helper.assertTrue(runs.get() == 2 && WorldChunkMigrations.version(overworld, id) == 2,
                "Expected exactly one run for each successive version; runs=" + runs);

        final WorldChunkMigrations saved = WorldChunkMigrations.get(overworld);
        final CompoundTag snapshot = saved.save(new CompoundTag(), overworld.registryAccess());
        final WorldChunkMigrations reloaded = reload(snapshot, overworld.registryAccess());
        helper.assertTrue(reloadedVersion(reloaded, id, chunk) == 2,
                "Simulated reload lost migration version 2");
        helper.assertTrue(!WorldChunkMigrations.apply(overworld, id, 2, chunk, runs::incrementAndGet)
                        && runs.get() == 2,
                "A processed chunk must remain untouched after simulated reload");

        // The same migration id and packed chunk coordinate are independent in a
        // second dimension: SavedData is owned by each ServerLevel.
        final AtomicInteger netherRuns = new AtomicInteger();
        helper.assertTrue(WorldChunkMigrations.apply(nether, id, 1, chunk, netherRuns::incrementAndGet),
                "Migration state must be isolated between dimensions");
        helper.assertTrue(netherRuns.get() == 1 && WorldChunkMigrations.completedCount(overworld, id) == 1,
                "Nether migration must not alter the Overworld registry");

        // A non-vanilla surface block represents a player placement. The repaint
        // routine's replacement gate must leave it untouched even when invoked on
        // a column selected for an anomaly repaint.
        final LevelChunk levelChunk = overworld.getChunk(chunk.x, chunk.z);
        final BlockPos playerSurface = new BlockPos(chunk.getMinBlockX() + 1, 80, chunk.getMinBlockZ() + 1);
        overworld.setBlock(playerSurface, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        final BlockPos.MutableBlockPos top = new BlockPos.MutableBlockPos(
                playerSurface.getX(), playerSurface.getY(), playerSurface.getZ());
        final boolean changed = invokePaintAnomaly(overworld, levelChunk, playerSurface, top);
        helper.assertTrue(!changed && overworld.getBlockState(playerSurface).is(Blocks.DIAMOND_BLOCK),
                "Player-placed surface block must be preserved by migration repaint");
        helper.succeed();
    }

    private static boolean invokePaintAnomaly(final ServerLevel level, final LevelChunk chunk,
                                              final BlockPos pos, final BlockPos.MutableBlockPos top) {
        try {
            final Method method = ChunkSurfaceRepaintHandler.class.getDeclaredMethod(
                    "paintAnomalyColumn", ServerLevel.class, LevelChunk.class,
                    int.class, int.class, int.class, BlockPos.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, level, chunk, pos.getX(), pos.getZ(), pos.getY(), top);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke surface repaint guard", exception);
        }
    }

    private static WorldChunkMigrations reload(final CompoundTag tag, final HolderLookup.Provider lookup) {
        try {
            final Method load = WorldChunkMigrations.class.getDeclaredMethod("load", CompoundTag.class,
                    HolderLookup.Provider.class);
            load.setAccessible(true);
            return (WorldChunkMigrations) load.invoke(null, tag, lookup);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not simulate migration SavedData reload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static int reloadedVersion(final WorldChunkMigrations data, final String id, final ChunkPos chunk) {
        try {
            final Field field = WorldChunkMigrations.class.getDeclaredField("completed");
            field.setAccessible(true);
            final Map<String, Map<Long, Integer>> completed = (Map<String, Map<Long, Integer>>) field.get(data);
            return completed.getOrDefault(id, Map.of()).getOrDefault(chunk.toLong(), 0);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not inspect simulated migration reload", exception);
        }
    }
}
