package com.stonytark.magnetization.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Native production line, separate from the explicitly failing pulley reproducer. */
@GameTestHolder("magnetization_supplementaries_production")
@PrefixGameTestTemplate(false)
public final class SupplementariesProductionGameTests {
    /** Native furnace cooking and hopper extraction, with powered/unpowered world controls. */
    @GameTest(template="empty", timeoutTicks=560, batch="nativeBellowsProduction")
    public static void bellowsAcceleratesFurnaceAndHopperLineOnMovingShip(GameTestHelper h) {
        final var base = h.absolutePos(new BlockPos(2, 180, 2));
        final var level = h.getLevel();
        final var blocks = furnaceLine(h, base, true);
        final var worldPowered = base.south(8);
        final var worldUnpowered = base.south(16);
        furnaceLine(h, worldPowered, true);
        furnaceLine(h, worldUnpowered, false);
        final var ship = assemble(h, base, blocks);
        final var bellows = tile(ship, "BellowsBlockTile");
        final var chestPos = bellows.getBlockPos().east().below(2);
        final var start = new Vector3d(ship.logicalPose().position());
        h.runAfterDelay(5, () -> RigidBodyHandle.of(ship).addLinearAndAngularVelocity(
                new Vector3d(0.5, 0, 0), new Vector3d(0, 0.04, 0)));
        h.runAfterDelay(500, () -> {
            int moving = ironCount(h, chestPos);
            int powered = ironCount(h, worldPowered.east().below(2));
            int unpowered = ironCount(h, worldUnpowered.east().below(2));
            h.assertTrue(moving > unpowered && powered > unpowered,
                    "Bellows did not accelerate native cooking/extraction: moving="+moving+" powered="+powered+" unpowered="+unpowered);
            h.assertTrue(moving == powered, "Moving ship differs from powered ground production");
            h.assertTrue(start.distance(ship.logicalPose().position()) > 0.25, "Production ship did not move");
            for (var origin : List.of(bellows.getBlockPos(), worldPowered, worldUnpowered)) {
                var furnace = (Container)level.getBlockEntity(origin.east());
                var hopper = (Container)level.getBlockEntity(origin.east().below());
                h.assertTrue(furnace.getItem(1).getCount() < 2, "Native furnace did not consume fuel");
                int total = furnace.getItem(0).getCount() + furnace.getItem(2).getCount()
                        + ironCount(h, origin.east().below(2));
                for (int slot=0; slot<hopper.getContainerSize(); slot++) total += hopper.getItem(slot).getCount();
                h.assertTrue(total == 8, "Production line lost or duplicated material");
            }
            org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info(
                    "NATIVE_BELLOWS_LINE_PASS moving={} poweredGround={} unpoweredGround={} inputConserved=8 ticks=500", moving,powered,unpowered);
            remove(h,ship); h.succeed();
        });
    }

    private static List<BlockPos> furnaceLine(GameTestHelper h, BlockPos base, boolean powered) {
        final var level = h.getLevel();
        final List<BlockPos> blocks = new ArrayList<>();
        final var bellows = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("supplementaries:bellows"));
        level.setBlockAndUpdate(base, bellows.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.EAST));
        level.setBlockAndUpdate(base.east(), Blocks.FURNACE.defaultBlockState());
        level.setBlockAndUpdate(base.east().below(), Blocks.HOPPER.defaultBlockState().setValue(BlockStateProperties.FACING_HOPPER,Direction.DOWN));
        level.setBlockAndUpdate(base.east().below(2), Blocks.CHEST.defaultBlockState());
        var furnace = (Container)level.getBlockEntity(base.east());
        furnace.setItem(0,new ItemStack(Items.RAW_IRON,8));
        furnace.setItem(1,new ItemStack(Items.COAL,2));
        blocks.addAll(List.of(base,base.east(),base.east().below(),base.east().below(2)));
        if (powered) { level.setBlockAndUpdate(base.west(),Blocks.REDSTONE_BLOCK.defaultBlockState()); blocks.add(base.west()); }
        for (int x=-1; x<=1; x++) {
            var floor=base.offset(x,-3,0); level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());blocks.add(floor);
        }
        // Connect the bellows to the output platform without blocking the extraction hopper.
        for(int y=-2;y<=-1;y++) { var support=base.offset(0,y,0);level.setBlockAndUpdate(support,Blocks.STONE.defaultBlockState());blocks.add(support); }
        return blocks;
    }

    private static int ironCount(GameTestHelper h, BlockPos pos) {
        h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof Container,"Missing output inventory at "+pos);
        var container=(Container)h.getLevel().getBlockEntity(pos);
        int count=0;
        for(int slot=0;slot<container.getContainerSize();slot++) {
            var stack=container.getItem(slot);
            h.assertTrue(stack.isEmpty() || stack.is(Items.IRON_INGOT),"Unexpected production output "+stack);
            count+=stack.getCount();
        }
        return count;
    }

    private static ServerSubLevel assemble(GameTestHelper h, BlockPos base, List<BlockPos> blocks) {
        final var ship=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),base,blocks,
                new BoundingBox3i(base.getX()-2,base.getY()-4,base.getZ()-2,base.getX()+3,base.getY()+2,base.getZ()+3));
        SubLevelContainer.getContainer(h.getLevel()).addForceLoadTicket(ship,dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED,net.minecraft.util.Unit.INSTANCE);
        return ship;
    }
    private static BlockEntity tile(ServerSubLevel ship,String name) {
        for(var holder:ship.getPlot().getLoadedChunks()) for(var tile:holder.getChunk().getBlockEntities().values())
            if(tile.getClass().getSimpleName().equals(name)) return tile;
        throw new IllegalStateException("Assembled machine missing: "+name);
    }
    private static void remove(GameTestHelper h,ServerSubLevel ship) {
        SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(ship,SubLevelRemovalReason.REMOVED);
    }
}
