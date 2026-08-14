package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral coverage for MR Fluid promises restored from earlier releases. */
@GameTestHolder("magnetization_regressions")
@PrefixGameTestTemplate(false)
public final class MrFluidRegressionGameTests {

    private MrFluidRegressionGameTests() {}

    /**
     * The original 1.2 MR Fluid promise: a wired redstone signal hardens the
     * fluid into a walkable block, and removing that signal restores the source.
     * This is deliberately independent of any magnetic field.
     */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void mrFluidHardensWithRedstoneAndReverts(final GameTestHelper helper) {
        // The generated GameTest world can contain passive magnetic ores below
        // the arena. Pick a loaded vertical position that is genuinely outside
        // every field so this proves the redstone path independently.
        final BlockPos[] selected = {null};
        for (int y = 2; y <= 98 && selected[0] == null; y += 8) {
            final BlockPos candidate = new BlockPos(2, y, 1);
            if (!MagneticFields.isInField(helper.getLevel(), helper.absolutePos(candidate))) {
                selected[0] = candidate;
            }
        }
        helper.assertTrue(selected[0] != null,
                "Could not find a field-free position for the redstone-only MR Fluid test");
        final BlockPos fluid = selected[0];
        final BlockPos power = fluid.west();
        helper.setBlock(fluid.below(), Blocks.STONE);
        helper.setBlock(fluid, MagBlocks.MR_FLUID_BLOCK.get());
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(helper.getBlockState(fluid).is(MagBlocks.HARDENED_MR_FLUID.get()),
                    "Redstone-powered MR fluid should harden without a magnetic field");
            helper.setBlock(power, Blocks.AIR);
            helper.runAfterDelay(20L, () -> {
                helper.assertTrue(!MagneticFields.isInField(helper.getLevel(), helper.absolutePos(fluid)),
                        "The redstone-only MR Fluid test position entered an unrelated magnetic field");
                final BlockState reverted = helper.getBlockState(fluid);
                helper.assertTrue(reverted.is(MagBlocks.MR_FLUID_BLOCK.get())
                                && reverted.getFluidState().isSource(),
                        "MR fluid should revert to its source when redstone is removed; got " + reverted);
                helper.succeed();
            });
        });
    }
}
