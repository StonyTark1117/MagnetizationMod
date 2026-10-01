package com.stonytark.magnetization.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/** Test-only access to pinned optional runtimes, without adding hard mod dependencies. */
final class NativeCompatTestSupport {
    private NativeCompatTestSupport() {}

    static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(
                () -> new IllegalStateException("Missing native block " + id));
    }

    static ItemStack stack(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow(
                () -> new IllegalStateException("Missing native item " + id)).getDefaultInstance();
    }

    static BlockEntity place(GameTestHelper h, BlockPos pos, String id) {
        h.setBlock(pos, block(id));
        final BlockEntity be = h.getBlockEntity(pos);
        h.assertTrue(be != null, "No native block entity for " + id);
        return be;
    }

    static Object field(Object target, String name) {
        try { return findField(target.getClass(), name).get(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }

    static void set(Object target, String name, Object value) {
        try { findField(target.getClass(), name).set(target, value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try { return target.getClass().getMethod(name, types).invoke(target, args); }
        catch (InvocationTargetException e) { throw new IllegalStateException(name, e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(name, e); }
    }

    static void forceTicking(GameTestHelper h, BlockPos min, BlockPos max) {
        var level = h.getLevel();
        var a = h.absolutePos(min);
        var b = h.absolutePos(max);
        for (int x = Math.floorDiv(Math.min(a.getX(), b.getX()), 16); x <= Math.floorDiv(Math.max(a.getX(), b.getX()), 16); x++)
            for (int z = Math.floorDiv(Math.min(a.getZ(), b.getZ()), 16); z <= Math.floorDiv(Math.max(a.getZ(), b.getZ()), 16); z++) {
                var chunk = new net.minecraft.world.level.ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(x, z, true);
                }
            }
        // GameTestServer disposes this test world. Releasing a chunk when an earlier
        // batch ends can strand the next batch while its structure is being prepared.
    }

    static void cleanup(GameTestHelper h, Runnable cleanup) {
        h.testInfo.addListener(
                new net.minecraft.gametest.framework.GameTestListener() {
                    public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
                    public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                    public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                    public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo original,
                            net.minecraft.gametest.framework.GameTestInfo rerun,
                            net.minecraft.gametest.framework.GameTestRunner runner) {}
                });
    }

    static Object call(Object target, String name) { return call(target, name, new Class<?>[0]); }
}
