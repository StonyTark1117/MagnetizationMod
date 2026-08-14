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
        // The preset spans several chunks. Use the already generated spawn area
        // so this content test does not synchronously generate remote chunks in
        // the shared GameTest server and turn worldgen latency into a test hang.
        final BlockPos anchor = new BlockPos(0, 240, 0);

        PlaytestWorldSetup.stageForTest(level, anchor, "lab");
        helper.assertTrue(level.getBlockState(anchor.offset(2, 0, 17)).is(MagBlocks.ELECTROLYZER.get()),
                "Lab must stage its Electrolyzer station");
        helper.assertTrue(level.getBlockState(anchor.offset(14, 0, 18)).is(MagBlocks.TOKAMAK_CONTROLLER.get()),
                "Lab must stage its formed 3x3 Tokamak station");
        helper.assertTrue(level.getBlockState(anchor.offset(19, 0, 18)).is(MagBlocks.TOKAMAK_CONTROLLER.get())
                        && level.getBlockState(anchor.offset(18, 0, 18)).is(MagBlocks.TOKAMAK_CONTROLLER.get())
                        && level.getBlockState(anchor.offset(17, 0, 16)).is(MagBlocks.TOKAMAK_COIL.get()),
                "Lab must stage a 5x5 Tokamak with a solid 3x3 core and coil perimeter");
        helper.assertTrue(level.getBlockState(anchor.offset(26, 1, 18)).is(MagBlocks.FUSION_THRUSTER.get()),
                "Lab must stage its Fusion panel interiors");
        helper.assertTrue(level.getBlockState(anchor.offset(37, 0, 22)).is(MagBlocks.RAILGUN_EMITTER.get())
                        && level.getBlockState(anchor.offset(41, 0, 22)).is(MagBlocks.RAILGUN_EMITTER.get())
                        && level.getBlockState(anchor.offset(37, 0, 21)).is(Blocks.COPPER_BLOCK),
                "Lab must stage a powered Railgun lane");
        helper.assertTrue(level.getBlockState(anchor.offset(48, 0, 18)).is(MagBlocks.DIPOLE_ELECTROMAGNET.get()),
                "Lab must stage the Dipole orientation row");
        helper.assertTrue(level.getBlockState(anchor.offset(2, 0, 51)).is(MagBlocks.GAS_EXCITER.get())
                        && level.getBlockState(anchor.offset(3, 0, 51)).is(MagBlocks.HELIUM_BLOCK.get()),
                "Lab must stage the 1.4 gas-excitation station");
        helper.assertTrue(level.getBlockState(anchor.offset(20, 0, 51)).is(MagBlocks.AIR_SEPARATOR.get()),
                "Lab must stage the 1.4 Air Separator station");
        helper.assertTrue(level.getBlockState(anchor.offset(31, 0, 51)).is(MagBlocks.ION_THRUSTER.get()),
                "Lab must stage the 1.4 Ion Thruster station");
        helper.assertTrue(level.getBlockState(anchor.offset(40, 0, 51)).is(MagBlocks.BASTNASITE_ORE.get())
                        && level.getBlockState(anchor.offset(55, 0, 54)).is(MagBlocks.NEODYMIUM_MAGNET.get()),
                "Lab must stage the 1.4 rare-earth source and finished-magnet gallery");
        helper.assertTrue(level.getBlockEntity(anchor.offset(2, 0, 10)) instanceof ChestBlockEntity allItems
                        && !allItems.isEmpty(),
                "Lab must stage its all-items chest bank");
        helper.assertTrue(level.getBlockEntity(anchor.offset(16, 0, 32)) instanceof ChestBlockEntity golemSupplies
                        && contains(golemSupplies, MagItems.MR_FLUID_GOLEM_SPAWN_EGG.get())
                        && contains(golemSupplies, MagItems.MAGNETITE_GOLEM_SPAWN_EGG.get())
                        && contains(golemSupplies, MagItems.PYRRHOTITE_GOLEM_SPAWN_EGG.get())
                        && contains(golemSupplies, MagItems.HEMATITE_GOLEM_SPAWN_EGG.get())
                        && contains(golemSupplies, MagItems.TITANOMAGNETITE_GOLEM_SPAWN_EGG.get()),
                "Lab must stage direct inspection supplies for every spawn-egg golem family");
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
                        && contains(rawInputs, MagItems.BASTNASITE_ORE.get())
                        && contains(rawInputs, MagItems.MONAZITE_ORE.get())
                        && contains(rawInputs, MagItems.COBALTITE_ORE.get())
                        && contains(rawInputs, MagItems.BORAX_ORE.get())
                        && !contains(rawInputs, MagItems.SAMARIUM_COBALT_ALLOY.get())
                        && !contains(rawInputs, MagItems.NEODYMIUM_ALLOY.get())
                        && !contains(rawInputs, MagItems.TRITIUM_CELL.get())
                        && !contains(rawInputs, MagItems.HELIUM_3_CELL.get()),
                "Survival supplies must contain raw lithium but no finished isotope fuel");

        PlaytestWorldSetup.clearForTest(level, anchor);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "playtestRailgunScenario")
    public static void railgunScenarioStagesExactAutoAssemblyPayload(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos anchor = new BlockPos(0, 220, 0);
        PlaytestWorldSetup.stageForTest(level, anchor, "lab");
        final int staged = PlaytestWorldSetup.stageRailgunAutoAssembly(level, anchor);
        final var first = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity)
                level.getBlockEntity(anchor.offset(37, 0, 22));
        final var second = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity)
                level.getBlockEntity(anchor.offset(41, 0, 22));
        helper.assertTrue(staged == 9, "Railgun preview must stage one complete 3x3 payload");
        helper.assertTrue(first != null && second != null && first.autoAssemble() && second.autoAssemble(),
                "Railgun preview must enable auto-assembly on both emitters");
        for (int across = 1; across <= 3; across++) for (int along = 1; along <= 3; along++) {
            helper.assertTrue(!level.getBlockState(anchor.offset(37 + across, -1, 22 - along)).isAir(),
                    "Railgun preview payload is missing block " + across + "," + along);
        }
        PlaytestWorldSetup.clearForTest(level, anchor);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "playtestThrusterExhaustScenario")
    public static void thrusterExhaustScenarioAssemblesFueledPreviewCraft(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos anchor = new BlockPos(0, 220, 0);
        PlaytestWorldSetup.stageForTest(level, anchor, "lab");
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        helper.assertTrue(container != null, "Sable container must exist for the exhaust preview");
        final java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
        for (final var subLevel : container.getAllSubLevels()) before.add(subLevel.getUniqueId());

        final int blocks = PlaytestWorldSetup.stageThrusterExhaust(level, anchor);
        dev.ryanhcode.sable.sublevel.ServerSubLevel preview = null;
        for (final var subLevel : container.getAllSubLevels()) {
            if (subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ship
                    && !before.contains(ship.getUniqueId())) {
                preview = ship;
                break;
            }
        }
        helper.assertTrue(blocks == 21, "Exhaust preview must assemble six simple-rig and fifteen Fusion blocks");
        helper.assertTrue(preview != null && !preview.getMassTracker().isInvalid(),
                "Exhaust preview must become one valid Sable craft");
        if (preview != null) {
            container.removeSubLevel(preview,
                    dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
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
