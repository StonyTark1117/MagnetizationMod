package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.physics.ShipMagneticScanner;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3d;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Real upstream inventories, moving Sable body, closed/reopened disk storage and live restored body. */
@GameTestHolder("magnetization_storage_ship")
@PrefixGameTestTemplate(false)
public final class StorageShipGameTests {
    private static final List<String> TYPES = List.of("iron_chest", "gold_chest", "netherite_chest",
            "iron_barrel", "gold_barrel", "netherite_barrel", "chest");

    @GameTest(template = "empty", timeoutTicks = 180, batch = "storageDiskLifecycle")
    public static void contentsUpgradesAndSettingsSurviveMovingShipDiskReload(final GameTestHelper helper) throws Exception {
        final var level = helper.getLevel();
        final var origin = helper.absolutePos(new BlockPos(0, 100, 0));
        final List<BlockPos> blocks = new ArrayList<>();
        for (int i = 0; i < TYPES.size(); i++) {
            final var pos = origin.east(i);
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            final var id = ResourceLocation.parse("sophisticatedstorage:" + TYPES.get(i));
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Missing fixture " + id);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(id).defaultBlockState());
            final var be = level.getBlockEntity(pos);
            final var contents = handler(be, "getInventoryHandler");
            helper.assertTrue(contents.insertItem(0, new ItemStack(Items.DIAMOND, 17 + i), false).isEmpty(), "Native inventory refused fixture");
            final var upgrade = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedstorage:stack_upgrade_tier_1")));
            helper.assertTrue(!upgrade.isEmpty() && handler(be, "getUpgradeHandler").insertItem(0, upgrade, false).isEmpty(), "Native upgrade refused fixture");
            storageClass().getMethod("setCustomName", Component.class).invoke(be, Component.literal(TYPES.get(i)));
            storageClass().getMethod("toggleLock").invoke(be);
            blocks.add(pos); blocks.add(pos.below());
        }
        final var ship = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks,
                new BoundingBox3i(origin.getX(), origin.getY()-1, origin.getZ(), origin.getX()+TYPES.size(), origin.getY()+1, origin.getZ()+1));
        helper.runAfterDelay(5, () -> {
            try {
                assertStorage(helper, ship);
                final var handle = RigidBodyHandle.of(ship);
                helper.assertTrue(handle != null && handle.isValid(), "Storage ship has no physics body");
                final var start = new Vector3d(ship.logicalPose().position());
                handle.addLinearAndAngularVelocity(new Vector3d(1, 0, 0), new Vector3d(0, 0.3, 0));
                helper.runAfterDelay(25, () -> {
                    ServerSubLevel restored = null;
                    try {
                        helper.assertTrue(start.distance(ship.logicalPose().position()) > 0.25, "Storage ship did not move over 25 ticks");
                        assertStorage(helper, ship);
                        final var data = SubLevelSerializer.toData(ship, List.of());
                        final var path = Files.createTempDirectory("magnetization-storage-roundtrip-");
                        final GlobalSavedSubLevelPointer pointer;
                        try (var disk = new SubLevelStorage(path)) {
                            pointer = disk.attemptSaveSubLevel(new ChunkPos(origin), data);
                            helper.assertTrue(pointer != null, "Sable storage write failed");
                            disk.flush();
                        }
                        remove(helper, ship);
                        try (var reopened = new SubLevelStorage(path)) {
                            final var loaded = reopened.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
                            helper.assertTrue(loaded != null, "Sable storage read failed");
                            restored = SubLevelSerializer.fullyLoad(level, loaded);
                        }
                        helper.assertTrue(restored != null, "Sable could not restore the saved ship");
                        assertStorage(helper, restored);
                        final var loadedShip = restored;
                        helper.runAfterDelay(20, () -> {
                            try { assertStorage(helper, loadedShip); }
                            catch (Exception e) { throw new RuntimeException(e); }
                            finally { remove(helper, loadedShip); }
                            helper.succeed();
                        });
                    } catch (Exception | AssertionError e) {
                        if (restored != null) remove(helper, restored);
                        remove(helper, ship);
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception | AssertionError e) { remove(helper, ship); throw new RuntimeException(e); }
        });
    }

    private static void assertStorage(GameTestHelper helper, ServerSubLevel ship) throws Exception {
        helper.assertTrue(ShipMagneticScanner.scan(ship).ferrousBlockCount() == 6, "Metal/wood storage classification changed");
        final var entities = ship.getPlot().getLoadedChunks().stream().flatMap(h -> h.getChunk().getBlockEntities().values().stream())
                .filter(be -> storageClassUnchecked().isInstance(be)).toList();
        helper.assertTrue(entities.size() == TYPES.size(), "Storage blocks lost: " + entities.size());
        for (var be : entities) {
            final var name = (Component) storageClass().getMethod("getCustomName").invoke(be);
            final int index = name == null ? -1 : TYPES.indexOf(name.getString());
            helper.assertTrue(index >= 0, "Storage name lost");
            final var stack = handler(be, "getInventoryHandler").getStackInSlot(0);
            helper.assertTrue(stack.is(Items.DIAMOND) && stack.getCount() == 17 + index, "Contents lost in " + name);
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(handler(be, "getUpgradeHandler").getStackInSlot(0).getItem())
                    .equals(ResourceLocation.parse("sophisticatedstorage:stack_upgrade_tier_1")), "Upgrade lost in " + name);
            helper.assertTrue((boolean) storageClass().getMethod("isLocked").invoke(be), "Lock lost in " + name);
            final var external = (IItemHandler) storageClass().getMethod("getExternalItemHandler", net.minecraft.core.Direction.class)
                    .invoke(be, net.minecraft.core.Direction.UP);
            helper.assertTrue(external.extractItem(0, 1, true).is(Items.DIAMOND), "Restored storage IO failed in " + name);
        }
    }

    private static IItemHandler handler(BlockEntity be, String name) throws Exception {
        final var wrapper = storageClass().getMethod("getStorageWrapper").invoke(be);
        return (IItemHandler) Class.forName("net.p3pp3rf1y.sophisticatedstorage.block.StorageWrapper").getMethod(name).invoke(wrapper);
    }
    private static Class<?> storageClass() throws ClassNotFoundException {
        return Class.forName("net.p3pp3rf1y.sophisticatedstorage.block.StorageBlockEntity");
    }
    private static Class<?> storageClassUnchecked() {
        try { return storageClass(); } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void remove(GameTestHelper helper, ServerSubLevel ship) {
        var container = SubLevelContainer.getContainer(helper.getLevel());
        if (container.getAllSubLevels().contains(ship)) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }
}
