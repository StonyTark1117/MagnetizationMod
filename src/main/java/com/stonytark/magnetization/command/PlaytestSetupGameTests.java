package com.stonytark.magnetization.command;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves both reproducible manual-playtest presets stage their critical fixtures. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlaytestSetupGameTests {
    private PlaytestSetupGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240, batch = "playtestSetup")
    public static void presetsStageAndReplaceOneAnother(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos anchor = new BlockPos(abs.getX(), 240, abs.getZ());

        PlaytestWorldSetup.stageForTest(level, anchor, "lab");
        helper.assertTrue(level.getBlockState(anchor.offset(2, 0, 17)).is(MagBlocks.ELECTROLYZER.get()),
                "Lab must stage its Electrolyzer station");
        helper.assertTrue(level.getBlockState(anchor.offset(14, 0, 18)).is(MagBlocks.TOKAMAK_CONTROLLER.get()),
                "Lab must stage its formed Tokamak station");
        helper.assertTrue(level.getBlockState(anchor.offset(26, 1, 18)).is(MagBlocks.FUSION_THRUSTER.get()),
                "Lab must stage its Fusion panel interiors");
        helper.assertTrue(level.getBlockState(anchor.offset(37, 0, 22)).is(MagBlocks.RAILGUN_EMITTER.get())
                        && level.getBlockState(anchor.offset(37, 0, 21)).is(Blocks.COPPER_BLOCK),
                "Lab must stage a powered Railgun lane");
        helper.assertTrue(level.getBlockState(anchor.offset(48, 0, 18)).is(MagBlocks.DIPOLE_ELECTROMAGNET.get()),
                "Lab must stage the Dipole orientation row");
        helper.assertTrue(level.getBlockEntity(anchor.offset(2, 0, 10)) instanceof ChestBlockEntity allItems
                        && !allItems.isEmpty(),
                "Lab must stage its all-items chest bank");
        PlaytestWorldSetup.seedPersistence(level, anchor);
        helper.assertTrue(PlaytestWorldSetup.persistenceStateValid(level, anchor),
                "Lab persistence scenario must seed every release-critical machine");

        PlaytestWorldSetup.stageForTest(level, anchor, "survival");
        helper.assertTrue(level.getBlockState(anchor.offset(9, 1, 5)).is(MagBlocks.ELECTROLYZER.get()),
                "Survival preset must stage an empty Electrolyzer");
        helper.assertTrue(level.getBlockState(anchor.offset(14, 1, 7)).is(MagBlocks.TOKAMAK_CONTROLLER.get()),
                "Survival preset must stage a formed Tokamak");
        helper.assertTrue(level.getBlockEntity(anchor.offset(2, 1, 9)) instanceof ChestBlockEntity rawInputs
                        && contains(rawInputs, MagItems.RAW_LITHIUM.get())
                        && !contains(rawInputs, MagItems.TRITIUM_CELL.get())
                        && !contains(rawInputs, MagItems.HELIUM_3_CELL.get()),
                "Survival supplies must contain raw lithium but no finished isotope fuel");

        PlaytestWorldSetup.clearForTest(level, anchor);
        helper.succeed();
    }

    private static boolean contains(final ChestBlockEntity chest, final net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) return true;
        }
        return false;
    }
}
