package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.tokamak.TokamakRingPreview;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Integration coverage for the client-facing Tokamak construction diagnostic. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TokamakPreviewGameTests {

    private TokamakPreviewGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "tokamakPreview")
    public static void tokamakPreviewMarksMissingCoil(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, MagBlocks.TOKAMAK_CONTROLLER.get());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) helper.setBlock(controller.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
            }
        }

        final BlockPos absoluteController = helper.absolutePos(controller);
        final var valid = TokamakRingPreview.preview(helper.getLevel(), absoluteController);
        helper.assertTrue(valid.valid() && valid.requiredFrame().size() == 8 && valid.invalidEdges().isEmpty(),
                "Complete Tokamak ring should preview as valid");

        final BlockPos missing = absoluteController.offset(-1, 0, -1);
        helper.getLevel().setBlock(missing, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        final var broken = TokamakRingPreview.preview(helper.getLevel(), absoluteController);
        helper.assertTrue(!broken.valid() && broken.invalidEdges().contains(missing),
                "Preview should mark the missing Tokamak coil at " + missing);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "tokamakPreview")
    public static void tokamakPreviewAcceptsExpandedRing(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(5, 1, 5);
        helper.setBlock(controller, MagBlocks.TOKAMAK_CONTROLLER.get());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    helper.setBlock(controller.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
                }
            }
        }

        final var preview = TokamakRingPreview.preview(helper.getLevel(), helper.absolutePos(controller), 7);
        helper.assertTrue(preview.valid() && preview.edge() == 5 && preview.coilCount() == 16,
                "A complete 5x5 Tokamak perimeter should be accepted as an expanded ring");
        helper.assertTrue(com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity
                        .ringMultiplier(helper.getLevel(), helper.absolutePos(controller)) == 3,
                "Expanded Tokamak ring did not receive its expected 3x performance scale");
        helper.succeed();
    }
}
