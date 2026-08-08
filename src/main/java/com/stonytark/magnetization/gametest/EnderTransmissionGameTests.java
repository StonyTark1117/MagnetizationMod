package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Published-runtime checks for Ender Transmission's actual power transport. */
@GameTestHolder("magnetization_ender_transmission")
@PrefixGameTestTemplate(false)
public final class EnderTransmissionGameTests {
    private EnderTransmissionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void remoteKineticChannelAndChunkLoaderRemainCompatible(
            final GameTestHelper helper) {
        final BlockPos firstPos = new BlockPos(1, 2, 1);
        final BlockPos secondPos = new BlockPos(5, 2, 1);
        final BlockPos loaderPos = new BlockPos(3, 2, 4);
        helper.setBlock(firstPos, block("energy_transmitter"));
        helper.setBlock(secondPos, block("energy_transmitter"));
        helper.setBlock(loaderPos, block("chunk_loader"));

        helper.runAfterDelay(3, () -> {
            final KineticBlockEntity first = (KineticBlockEntity) helper.getBlockEntity(firstPos);
            final KineticBlockEntity second = (KineticBlockEntity) helper.getBlockEntity(secondPos);
            first.getPersistentData().putInt("channel", 7);
            second.getPersistentData().putInt("channel", 7);
            first.getPersistentData().putString("password", "magnetization_gametest");
            second.getPersistentData().putString("password", "magnetization_gametest");
            invoke(first, "reloadSettings");
            invoke(second, "reloadSettings");
            invoke(first, "afterReload");
            invoke(second, "afterReload");

            final Object connected = invoke(first, "getConnectedTransmitters");
            helper.assertTrue(connected instanceof java.util.List<?> list && list.contains(second),
                    "Ender Transmission did not link matching remote kinetic transmitters");

            final KineticBlockEntity loader = (KineticBlockEntity) helper.getBlockEntity(loaderPos);
            loader.setSpeed(256.0F);
            invoke(loader, "tick");
            final ChunkPos center = new ChunkPos(helper.absolutePos(loaderPos));
            helper.assertTrue(helper.getLevel().getForcedChunks().contains(
                            ChunkPos.asLong(center.x + 2, center.z + 2)),
                    "Powered Ender Transmission chunk loader did not force its advertised remote radius");
            loader.setSpeed(0.0F);
            invoke(loader, "tick");
            helper.assertTrue(!helper.getLevel().getForcedChunks().contains(
                            ChunkPos.asLong(center.x + 2, center.z + 2)),
                    "Stopped Ender Transmission chunk loader retained a stale force ticket");
            helper.succeed();
        });
    }

    private static Object invoke(final Object target, final String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Ender Transmission runtime contract changed: " + method,
                    exception);
        }
    }

    private static Block block(final String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "createendertransmission", path));
    }
}
