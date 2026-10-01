package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.client.OreCompassScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the actual loaded-chunk scan, distance ordering, and cache invalidation. */
@GameTestHolder("magnetization_ore_compass")
@PrefixGameTestTemplate(false)
public final class OreCompassGameTests {
    private OreCompassGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void fullRangeNearestAndMovement(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 120, 1));
        final BlockPos boundary = origin.east(32);
        final BlockPos deep = origin.below(20);
        final BlockPos close = origin.east(2);
        final var diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        try {
            level.getChunkAt(boundary);
            level.setBlockAndUpdate(boundary, diamond);
            helper.assertTrue(boundary.equals(OreCompassScanner.nearest(level, origin, "diamond",
                    state -> state.is(Blocks.DIAMOND_BLOCK))),
                    "An ore at the advertised 32-block boundary was missed");

            level.setBlockAndUpdate(boundary, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(deep, diamond);
            level.setBlockAndUpdate(close, diamond);
            helper.assertTrue(close.equals(OreCompassScanner.nearest(level, origin, "diamond",
                    state -> state.is(Blocks.DIAMOND_BLOCK))),
                    "The first ore in vertical scan order displaced a closer ore");

            level.setBlockAndUpdate(boundary, diamond);
            helper.assertTrue(boundary.equals(OreCompassScanner.nearest(level, boundary, "diamond",
                    state -> state.is(Blocks.DIAMOND_BLOCK))),
                    "Moving during the cache interval retained the old target");
            helper.succeed();
        } finally {
            level.setBlockAndUpdate(boundary, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(deep, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(close, Blocks.AIR.defaultBlockState());
        }
    }
}
