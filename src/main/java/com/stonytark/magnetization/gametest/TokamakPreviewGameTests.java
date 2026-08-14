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
        helper.assertTrue(helper.absolutePos(controller).equals(TokamakRingPreview.findController(
                        helper.getLevel(), helper.absolutePos(controller.offset(2, 0, 0)), 7)),
                "Goggle targeting could not resolve the controller from a 5x5 outer coil");

        final var construction = TokamakRingPreview.constructionPreview(
                helper.getLevel(), helper.absolutePos(controller), 7);
        helper.assertTrue(construction.valid() && construction.edge() == 5,
                "Construction diagnostics did not select the completed expanded ring");

        final BlockPos missing = controller.offset(2, 0, 0);
        helper.setBlock(missing, net.minecraft.world.level.block.Blocks.AIR);
        final var broken = TokamakRingPreview.previewExact(
                helper.getLevel(), helper.absolutePos(controller), 5, 7);
        helper.assertTrue(!broken.valid() && broken.edge() == 5
                        && broken.invalidEdges().contains(helper.absolutePos(missing)),
                "Expanded-ring diagnostics did not identify its missing outer coil");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "tokamakPreview")
    public static void expandedTokamakActuallyGeneratesAtScaledRate(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(5, 1, 5);
        helper.setBlock(controller, MagBlocks.TOKAMAK_CONTROLLER.get());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    helper.setBlock(controller.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
                }
            }
        }

        final var be = (com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity)
                helper.getBlockEntity(controller);
        be.fuelContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.DEUTERIUM_CELL.get()));
        com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity.serverTick(
                helper.getLevel(), helper.absolutePos(controller), be.getBlockState(), be);

        final int multiplier = 3;
        helper.assertTrue(be.energyBuffer().getMaxEnergyStored()
                        == com.stonytark.magnetization.config.MagConfig.tokamakFeCapacity() * multiplier,
                "Expanded Tokamak did not scale its FE capacity to 3x");
        helper.assertTrue(be.energyBuffer().getEnergyStored()
                        == com.stonytark.magnetization.config.MagConfig.tokamakGenPerTick() * multiplier,
                "Expanded Tokamak did not generate Deuterium power at 3x");
        helper.assertTrue(helper.getBlockState(controller)
                        .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
                "Expanded Tokamak stayed unlit after a fueled server tick");
        final var display = be.displayData();
        helper.assertTrue(display.structureSize() == 5 && display.structureScale() == 3,
                "Expanded Tokamak did not expose its 5x5 ring and 3x scale to GUI/HUD consumers");
        helper.assertTrue(display.status()
                        == com.stonytark.magnetization.menu.MachineDisplayData.Status.ACTIVE,
                "Fueled expanded Tokamak did not expose ACTIVE status");
        helper.assertTrue(be.hudLines().stream().anyMatch(line -> line.getString().contains("5x5"))
                        && be.hudLines().stream().anyMatch(line -> line.getString().contains("3")),
                "Expanded Tokamak HUD omitted its ring size or performance multiplier");
        helper.succeed();
    }
}
