package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.content.fluid.FluidRedstone;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("magnetization")
@PrefixGameTestTemplate(false)
public final class PerformanceRegressionGameTests {
    private PerformanceRegressionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fluidRedstonePreservesImmediateSourcesSplitsAndRejoins(GameTestHelper h) {
        final var level = h.getLevel();
        final BlockPos start = h.absolutePos(new BlockPos(1, 2, 1));
        for (int i = 0; i < 5; i++) {
            level.setBlock(start.east(i).below(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(start.east(i), MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(start.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < 5; i++) power(h, start.east(i), 15 - i);
        // Both edits happen in this callback: next-tick batching would fail.
        level.setBlock(start.east(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        power(h, start, 15); power(h, start.east(1), 14);
        power(h, start.east(3), 0); power(h, start.east(4), 0);
        level.setBlock(start.east(2), MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < 5; i++) power(h, start.east(i), 15 - i);
        level.setBlock(start.east(5), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        power(h, start.east(4), 15); power(h, start.east(2), 13);
        level.setBlock(start.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < 5; i++) power(h, start.east(i), 11 + i);
        level.setBlock(start.east(5), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < 5; i++) power(h, start.east(i), 0);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fluidRedstoneReadsIndirectStrongPowerAndClearsItImmediately(GameTestHelper h) {
        final var level = h.getLevel();
        final BlockPos fluid = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(fluid.below(), Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(fluid, MagBlocks.FERROFLUID_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(fluid.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        // A powered wall lever strongly powers its supporting stone block.
        level.setBlock(fluid.west(2), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                        net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                        net.minecraft.core.Direction.WEST)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, true), Block.UPDATE_ALL);
        FluidRedstone.onNeighborChanged(level, fluid, MagBlocks.FERROFLUID_BLOCK.get());
        power(h, fluid, 15);
        level.setBlock(fluid.west(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        FluidRedstone.onNeighborChanged(level, fluid, MagBlocks.FERROFLUID_BLOCK.get());
        power(h, fluid, 0);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lenzClassificationDoesNotSurviveWorldEditsAndKeepsSampleCap(GameTestHelper h) {
        final var level = h.getLevel();
        final BlockPos p = h.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(p, Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(p.east(), Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        final var box = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                p.getX(), p.getY() + 2, p.getZ(), p.getX() + 1, p.getY() + 3, p.getZ() + 1);
        int before = com.stonytark.magnetization.content.effect.LenzBrakingHandler.countOverlappingConductors(level, box);
        h.assertTrue(before >= 2 && before == legacyLenz(level, box), "Initial Lenz count differs from legacy scan");
        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        int after = com.stonytark.magnetization.content.effect.LenzBrakingHandler.countOverlappingConductors(level, box);
        h.assertTrue(after == before - 1 && after == legacyLenz(level, box), "Lenz retained classification after a block edit");
        final var capped = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                p.getX() - 10, p.getY() - 2, p.getZ() - 10,
                p.getX() + 40, p.getY() + 15, p.getZ() + 40);
        h.assertTrue(com.stonytark.magnetization.content.effect.LenzBrakingHandler.countOverlappingConductors(level, capped)
                == legacyLenz(level, capped), "Lenz changed scan order or 2,048-cell cap");
        h.succeed();
    }

    private static int legacyLenz(net.minecraft.server.level.ServerLevel level,
                                  dev.ryanhcode.sable.companion.math.BoundingBox3dc box) {
        int found = 0, examined = 0;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = (int) Math.floor(box.minX()) - 3; x <= (int) Math.ceil(box.maxX()) + 3; x++)
            for (int z = (int) Math.floor(box.minZ()) - 3; z <= (int) Math.ceil(box.maxZ()) + 3; z++)
                for (int y = (int) Math.floor(box.minY()) - 3; y <= (int) Math.ceil(box.maxY()) + 3; y++) {
                    if (++examined > 2048) return found;
                    pos.set(x, y, z);
                    if (level.isLoaded(pos) && com.stonytark.magnetization.compat.FerromagneticCompat.is(
                            level.getBlockState(pos), com.stonytark.magnetization.api.MagTags.EDDY_CONDUCTORS)) found++;
                }
        return found;
    }

    private static void power(GameTestHelper h, BlockPos pos, int expected) {
        int actual = FluidRedstone.signal(h.getLevel().getBlockState(pos));
        h.assertTrue(actual == expected, "Immediate fluid power at " + pos + ": expected " + expected + ", got " + actual);
    }
}
