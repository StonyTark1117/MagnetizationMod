package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Core Create conveyor fixtures; deliberately separate from Create Tracks/coaster tests. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreateConveyorGameTests {
    private CreateConveyorGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "createChainConveyor")
    public static void chainConveyorBlockEntitySurvivesSableAssembly(final GameTestHelper helper) {
        final Block conveyor = com.simibubi.create.AllBlocks.CHAIN_CONVEYOR.get();
        final BlockPos rel = new BlockPos(4, 2, 4);
        final BlockPos pos = helper.absolutePos(rel);
        helper.setBlock(rel, conveyor.defaultBlockState());
        final Block block = conveyor;
        final var be = helper.getBlockEntity(rel);
        helper.assertTrue(be != null, "Create chain conveyor did not create its block entity");
        final ServerSubLevel ship = assemble(helper, pos, block);
        assertTransferred(helper, ship, be.getClass(), "Chain conveyor block entity");
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "createConveyor")
    public static void normalConveyorBlockEntitySurvivesSableAssembly(final GameTestHelper helper) {
        final Block conveyor = com.simibubi.create.AllBlocks.BELT.get();
        final BlockPos rel = new BlockPos(4, 2, 4);
        final BlockPos pos = helper.absolutePos(rel);
        helper.setBlock(rel, conveyor.defaultBlockState());
        final var be = new com.simibubi.create.content.kinetics.belt.BeltBlockEntity(
                com.simibubi.create.AllBlockEntityTypes.BELT.get(), pos, conveyor.defaultBlockState());
        helper.getLevel().setBlockEntity(be);
        final ServerSubLevel ship = assemble(helper, pos, conveyor);
        assertTransferred(helper, ship,
                com.simibubi.create.content.kinetics.belt.BeltBlockEntity.class,
                "Create conveyor block entity");
    }

    private static ServerSubLevel assemble(final GameTestHelper helper, final BlockPos pos, final Block block) {
        final BlockPos iron = pos.east();
        helper.getLevel().setBlock(iron, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        return SubLevelAssemblyHelper.assembleBlocks(helper.getLevel(), pos, java.util.List.of(pos, iron),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 2, pos.getY() + 1, pos.getZ() + 1));
    }

    private static void assertTransferred(final GameTestHelper helper, final ServerSubLevel ship,
                                           final Class<?> type, final String label) {
        helper.assertTrue(ship != null, "Sable could not assemble the " + label + " fixture");
        if (ship == null) return;
        try {
            boolean transferred = false;
            for (final var holder : ship.getPlot().getLoadedChunks()) {
                transferred |= holder.getChunk().getBlockEntities().values().stream().anyMatch(type::isInstance);
            }
            helper.assertTrue(transferred, label + " was not transferred into Sable");
        } finally {
            final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
            if (container != null) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        }
        helper.succeed();
    }
}
