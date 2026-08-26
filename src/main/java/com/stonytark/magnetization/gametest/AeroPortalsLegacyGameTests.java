package com.stonytark.magnetization.gametest;

import com.breakinblocks.aeroportals.portal.PortalTeleport;
import com.stonytark.magnetization.compat.aeroportals.MagAeroPortalsCompat;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Behavioral coverage against the oldest supported AeroPortals 1.1.2 runtime. */
@GameTestHolder("magnetization_aeroportals_legacy")
@PrefixGameTestTemplate(false)
public final class AeroPortalsLegacyGameTests {
    private AeroPortalsLegacyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void transfersBaseMagneticStateWithoutNewerRemapApi(final GameTestHelper helper) {
        final ServerLevel src = helper.getLevel();
        final ServerLevel dst = src.getServer().getLevel(Level.NETHER);
        if (dst == null) {
            helper.fail("AeroPortals 1.1.2 compatibility test requires the Nether");
            return;
        }

        final BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        final List<BlockPos> blocks = List.of(origin, origin.east(), origin.east(2), origin.east(3));
        src.setBlock(origin, MagBlocks.POLARITY_INVERTER.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        src.setBlock(origin.east(), MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        src.setBlock(origin.east(2), MagBlocks.RAILGUN_EMITTER.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        src.setBlock(origin.east(3), Blocks.CHEST.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        final ChestBlockEntity sourceChest = (ChestBlockEntity) src.getBlockEntity(origin.east(3));
        if (sourceChest == null) {
            helper.fail("Could not create AeroPortals 1.1.2 inventory fixture");
            return;
        }
        sourceChest.setItem(0, new ItemStack(MagItems.HELIUM_3_CELL.get(), 5));

        final BoundingBox3i bounds = new BoundingBox3i(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 4, origin.getY() + 1, origin.getZ() + 1);
        final ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(src, origin, blocks, bounds);
        if (ship == null) {
            helper.fail("Could not assemble AeroPortals 1.1.2 compatibility ship");
            return;
        }

        final UUID uuid = ship.getUniqueId();
        final var before = ShipMagneticRegistry.get(src, ship);
        final RailgunEmitterBlockEntity sourceEmitter = find(ship, RailgunEmitterBlockEntity.class);
        if (sourceEmitter == null) {
            remove(src, ship);
            helper.fail("Assembled legacy compatibility ship lost its railgun emitter");
            return;
        }
        sourceEmitter.setManualMode(true);
        sourceEmitter.setRailLength(7);

        final Vec3 destination = new Vec3(origin.getX() / 8.0 + 0.5, 160.0,
                origin.getZ() / 8.0 + 0.5);
        PortalTeleport.teleportToDimension(src, ship, dst, destination, true,
                "magnetization:legacy-gametest");

        // AeroPortals posts the transfer event synchronously. Capture the rebuilt
        // object before its remote destination chunk can be moved into holding.
        final SubLevelContainer srcContainer = SubLevelContainer.getContainer(src);
        final ServerSubLevel moved = MagAeroPortalsCompat.consumeRecentTransfer(uuid);
        if (srcContainer != null && srcContainer.getSubLevel(uuid) != null) {
            helper.fail("AeroPortals 1.1.2 left the ship registered in its source dimension");
            return;
        }
        if (moved == null) {
            helper.fail("AeroPortals 1.1.2 did not emit a compatible transfer event");
            return;
        }

        final var after = ShipMagneticRegistry.get(dst, moved);
        final RailgunEmitterBlockEntity movedEmitter = find(moved, RailgunEmitterBlockEntity.class);
        final ChestBlockEntity movedChest = find(moved, ChestBlockEntity.class);
        helper.assertTrue(uuid.equals(moved.getUniqueId()),
                "AeroPortals 1.1.2 changed the ship UUID during reconstruction");
        helper.assertTrue(before.equals(after),
                "Derived magnetic state changed across the 1.1.2 transfer: " + before + " -> " + after);
        helper.assertTrue(movedEmitter != null && movedEmitter.manualMode() && movedEmitter.railLength() == 7,
                "Railgun block-entity state did not survive the 1.1.2 transfer");
        helper.assertTrue(movedChest != null && movedChest.getItem(0).is(MagItems.HELIUM_3_CELL.get())
                        && movedChest.getItem(0).getCount() == 5,
                "Inventory contents did not survive the 1.1.2 transfer");
        helper.succeed();
    }

    private static <T extends BlockEntity> T find(final ServerSubLevel ship, final Class<T> type) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (type.isInstance(blockEntity)) return type.cast(blockEntity);
            }
        }
        return null;
    }

    private static void remove(final ServerLevel level, final ServerSubLevel ship) {
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.getSubLevel(ship.getUniqueId()) != null) {
            container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        }
    }
}
