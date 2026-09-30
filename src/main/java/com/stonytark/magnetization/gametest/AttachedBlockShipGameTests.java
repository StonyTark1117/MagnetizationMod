package com.stonytark.magnetization.gametest;

import com.google.gson.JsonParser;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.physics.ShipMagneticScanner;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native placement (including door halves/attachments), sustained movement, live redstone interaction. */
@GameTestHolder("magnetization_attached_ship")
@PrefixGameTestTemplate(false)
public final class AttachedBlockShipGameTests {
    @GameTest(template = "empty", timeoutTicks = 160, batch = "attachedMovingShip")
    public static void nativePlacedTaggedBlocksRetainAttachmentsAndBehavior(final GameTestHelper helper) throws Exception {
        final var namespaces = Set.of(System.getProperty("magnetization.audit.namespaces").split(","));
        final Set<ResourceLocation> ids = new LinkedHashSet<>();
        try (var in = AttachedBlockShipGameTests.class.getResourceAsStream("/data/magnetization/tags/block/ferromagnetic_blocks.json")) {
            for (var value : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values")) {
                final var raw = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                if (!raw.startsWith("#")) {
                    var id = ResourceLocation.parse(raw);
                    if (namespaces.contains(id.getNamespace())) ids.add(id);
                }
            }
        }
        helper.assertTrue(!ids.isEmpty(), "No tagged blocks in native placement profile");
        final var level = helper.getLevel();
        final var base = helper.absolutePos(new BlockPos(0, 100, 0));
        final var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        final Set<BlockPos> blocks = new LinkedHashSet<>();
        final Map<ResourceLocation, Integer> expected = new LinkedHashMap<>();
        for (var p : BlockPos.betweenClosed(base.offset(-1,-1,-1), base.offset(31,-1,((ids.size()+7)/8)*4))) {
            level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState()); blocks.add(p.immutable());
        }
        final Map<ResourceLocation, Boolean> poweredOpen = new HashMap<>();
        int index = 0;
        for (var id : ids) {
            final var pos = base.offset((index % 8)*4, 0, (index / 8)*4); index++;
            // Every fixture has a floor, rear wall, and ceiling for standing/wall/hanging placements.
            for (var support : List.of(pos.below(), pos.north(), pos.north().above(), pos.north().above(2), pos.north().above(3), pos.above(3))) {
                level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState()); blocks.add(support);
            }
            // Raw Gravitite naturally floats upward after two ticks. A ceiling is
            // its normal native restraint, just as support is required below sand.
            if (id.equals(ResourceLocation.parse("aether:gravitite_ore"))) {
                level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState()); blocks.add(pos.above());
            }
            final var item = BuiltInRegistries.BLOCK.get(id).asItem();
            helper.assertTrue(item instanceof BlockItem, "No native block item for " + id);
            player.setPos(Vec3.atCenterOf(pos.south()));
            player.setYRot(180);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
            boolean placed = false;
            for (var hit : List.of(new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false),
                    new BlockHitResult(Vec3.atCenterOf(pos.north()), Direction.SOUTH, pos.north(), false),
                    new BlockHitResult(Vec3.atCenterOf(pos.above()), Direction.DOWN, pos.above(), false))) {
                final var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
                if (((BlockItem)item).place(context).consumesAction()) { placed = true; break; }
            }
            helper.assertTrue(placed && level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(id)), "Native placement failed for " + id);
            if (level.getBlockState(pos).hasProperty(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(pos.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos.south());
                poweredOpen.put(id, level.getBlockState(pos).getValue(BlockStateProperties.OPEN));
                level.removeBlock(pos.south(), false);
                level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos.south());
            }
            int count = 0;
            for (var p : BlockPos.betweenClosed(pos.offset(-1,-1,-1), pos.offset(1,3,1))) {
                if (!level.getBlockState(p).isAir()) {
                    blocks.add(p.immutable());
                    if (level.getBlockState(p).is(BuiltInRegistries.BLOCK.get(id))) count++;
                }
            }
            expected.put(id, count);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        final var ship = SubLevelAssemblyHelper.assembleBlocks(level, base, new ArrayList<>(blocks),
                new BoundingBox3i(base.getX()-1, base.getY()-1, base.getZ()-1, base.getX()+32, base.getY()+4, base.getZ()+((index+7)/8)*4));
        SubLevelContainer.getContainer(level).addForceLoadTicket(ship,
                dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType.COMMAND_FORCED, net.minecraft.util.Unit.INSTANCE);
        assertBlocks(helper, ship, expected);
        helper.runAfterDelay(5, () -> {
            try {
                assertBlocks(helper, ship, expected);
                final var start = new Vector3d(ship.logicalPose().position());
                final var handle = RigidBodyHandle.of(ship);
                helper.assertTrue(handle != null && handle.isValid(), "Fixture ship has no physics body");
                handle.addLinearAndAngularVelocity(new Vector3d(0.5, 0, 0), new Vector3d(0, 0.1, 0));
                helper.runAfterDelay(40, () -> {
                    try {
                        helper.assertTrue(start.distance(ship.logicalPose().position()) > 0.2, "Fixture ship did not move");
                        assertBlocks(helper, ship, expected);
                        int operated = 0;
                        for (var pos : magneticPositions(ship)) {
                            var state = level.getBlockState(pos);
                            // Trigger real native redstone neighbor handling for doors, gates and trapdoors.
                            if (state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) {
                                level.setBlockAndUpdate(pos.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                                level.neighborChanged(pos, Blocks.REDSTONE_BLOCK, pos.south());
                                helper.assertTrue(level.getBlockState(pos).getValue(BlockStateProperties.OPEN).equals(
                                        poweredOpen.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()))),
                                        "Ship redstone response differs from native world control: " + state);
                                operated++;
                            }
                        }
                        org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info(
                                "ATTACHED_SHIP ids={} retained={} nativeRedstoneOperations={}", expected.keySet(), expected.values().stream().mapToInt(Integer::intValue).sum(), operated);
                    } finally { remove(helper, ship); }
                    helper.succeed();
                });
            } catch (RuntimeException | AssertionError e) {
                remove(helper, ship); throw e;
            }
        });
    }

    private static List<BlockPos> magneticPositions(ServerSubLevel ship) {
        final List<BlockPos> out = new ArrayList<>();
        for (var holder : ship.getPlot().getLoadedChunks()) {
            final var chunk = holder.getChunk();
            for (int i = 0; i < chunk.getSections().length; i++) {
                final var section = chunk.getSections()[i];
                if (section == null || section.hasOnlyAir()) continue;
                final int y = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int x=0;x<16;x++) for (int dy=0;dy<16;dy++) for (int z=0;z<16;z++) {
                    if (section.getBlockState(x,dy,z).is(MagTags.FERROMAGNETIC_BLOCKS)) {
                        out.add(new BlockPos(chunk.getPos().getMinBlockX()+x,y+dy,chunk.getPos().getMinBlockZ()+z));
                    }
                }
            }
        }
        return out;
    }
    private static void remove(GameTestHelper helper, ServerSubLevel ship) {
        var container = SubLevelContainer.getContainer(helper.getLevel());
        if (container.getAllSubLevels().contains(ship)) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }
    private static void assertBlocks(GameTestHelper helper, ServerSubLevel ship, Map<ResourceLocation,Integer> expected) {
        final Map<ResourceLocation,Integer> actual = new LinkedHashMap<>();
        for (var pos : magneticPositions(ship)) actual.merge(BuiltInRegistries.BLOCK.getKey(ship.getLevel().getBlockState(pos).getBlock()), 1, Integer::sum);
        helper.assertTrue(actual.equals(expected), "Attached blocks changed: removed=" + ship.isRemoved() + " chunks=" + ship.getPlot().getLoadedChunks().size() + " scanner=" + ShipMagneticScanner.scan(ship) + " missing=" + expected.entrySet().stream().filter(e -> !e.getValue().equals(actual.get(e.getKey()))).map(Map.Entry::getKey).toList());
        helper.assertTrue(ShipMagneticScanner.scan(ship).ferrousBlockCount() == expected.values().stream().mapToInt(Integer::intValue).sum(),
                "Moving ship material classification differs");
    }
}
