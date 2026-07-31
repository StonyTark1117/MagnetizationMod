package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.immersiveaeronautics.MagImmersiveAeronauticsCompat;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.content.railgun.RailgunRemoteItem;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge;

import java.util.List;

/** Real transfer coverage for the optional Immersive Aeronautics hook. */
@GameTestHolder("magnetization_immersive_aeronautics")
@PrefixGameTestTemplate(false)
public final class ImmersiveAeronauticsGameTests {

    private ImmersiveAeronauticsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void railgunRemotesFollowTransferredShip(final GameTestHelper helper) {
        final ServerLevel source = helper.getLevel();
        final ServerLevel destination = source.getServer().getLevel(Level.NETHER);
        if (destination == null) {
            helper.fail("Immersive Aeronautics test requires the Nether");
            return;
        }

        final BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        final List<BlockPos> blocks = List.of(origin, origin.east(), origin.east(2));
        source.setBlock(origin, MagBlocks.POLARITY_INVERTER.get().defaultBlockState(), Block.UPDATE_ALL);
        source.setBlock(origin.east(), MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        source.setBlock(origin.east(2), MagBlocks.RAILGUN_EMITTER.get().defaultBlockState(), Block.UPDATE_ALL);

        final ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(source, origin, blocks,
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + 3, origin.getY() + 1, origin.getZ() + 1));
        if (ship == null) {
            helper.fail("Could not assemble Immersive Aeronautics compatibility ship");
            return;
        }

        final RailgunEmitterBlockEntity oldEmitter = findRailgunEmitter(ship);
        if (oldEmitter == null) {
            helper.fail("Assembled ship lost its railgun emitter");
            return;
        }
        oldEmitter.setManualMode(true);
        oldEmitter.setRailLength(7);

        final ItemStack installed = new ItemStack(MagItems.RAILGUN_REMOTE.get());
        RailgunRemoteItem.bind(installed, oldEmitter, source.dimension());
        oldEmitter.remoteContainer().setItem(0, installed);

        final var player = helper.makeMockPlayer(GameType.SURVIVAL);
        final ItemStack held = new ItemStack(MagItems.RAILGUN_REMOTE.get());
        RailgunRemoteItem.bind(held, oldEmitter, source.dimension());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        final var result = IPSableBridge.moveThroughPortal(ship, source, destination,
                new net.minecraft.world.phys.Vec3(8.5, 160.0, 8.5), new Quaterniond());
        if (result == null) {
            helper.fail("Immersive Aeronautics failed to reconstruct the ship");
            return;
        }

        final ServerSubLevel moved = MagImmersiveAeronauticsCompat.consumeRecentTransfer(ship.getUniqueId());
        if (moved == null) {
            helper.fail("Magnetization's Immersive Aeronautics transfer hook did not run");
            return;
        }
        final RailgunEmitterBlockEntity newEmitter = findRailgunEmitter(moved);
        if (newEmitter == null) {
            helper.fail("Railgun emitter did not survive Immersive Aeronautics transfer");
            return;
        }

        MagImmersiveAeronauticsCompat.remapInventoryAfterTransfer(
                player.getInventory(), source, destination,
                result.oldRegionMin(), result.regionBlocks(), result.shift());

        assertBinding(helper, newEmitter.remoteContainer().getItem(0), destination, newEmitter.getBlockPos(),
                "installed remote");
        assertBinding(helper, player.getItemInHand(InteractionHand.MAIN_HAND), destination, newEmitter.getBlockPos(),
                "player-held remote");
        helper.assertTrue(newEmitter.manualMode() && newEmitter.railLength() == 7,
                "Railgun state did not survive transfer");
        helper.succeed();
    }

    private static void assertBinding(final GameTestHelper helper, final ItemStack remote,
                                      final ServerLevel level, final BlockPos expected,
                                      final String description) {
        helper.assertTrue(level.dimension().equals(RailgunRemoteItem.boundDim(remote))
                        && expected.equals(RailgunRemoteItem.boundPos(remote)),
                description + " retained stale binding " + RailgunRemoteItem.boundPos(remote)
                        + "@" + RailgunRemoteItem.boundDim(remote)
                        + "; expected " + expected + "@" + level.dimension().location());
    }

    private static RailgunEmitterBlockEntity findRailgunEmitter(final ServerSubLevel ship) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof RailgunEmitterBlockEntity railgun) return railgun;
            }
        }
        return null;
    }
}
