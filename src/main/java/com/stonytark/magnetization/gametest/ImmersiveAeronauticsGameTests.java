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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.lang.reflect.Method;
import java.util.List;

/** Real transfer coverage for the optional Immersive Aeronautics hook. */
@GameTestHolder("magnetization_immersive_aeronautics")
@PrefixGameTestTemplate(false)
public final class ImmersiveAeronauticsGameTests {

    private ImmersiveAeronauticsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240, batch = "zIaRemoteTransfer")
    public static void railgunRemotesFollowTransferredShip(final GameTestHelper helper) {
        if (!immersivePortalsApiAvailable()) {
            helper.succeed();
            return;
        }
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

        final var result = moveThroughPortal(ship, source, destination,
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
                (BlockPos) invoke(result, "oldRegionMin"),
                (Integer) invoke(result, "regionBlocks"),
                (BlockPos) invoke(result, "shift"));

        assertBinding(helper, newEmitter.remoteContainer().getItem(0), destination, newEmitter.getBlockPos(),
                "installed remote");
        assertBinding(helper, player.getItemInHand(InteractionHand.MAIN_HAND), destination, newEmitter.getBlockPos(),
                "player-held remote");
        helper.assertTrue(newEmitter.manualMode() && newEmitter.railLength() == 7,
                "Railgun state did not survive transfer");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240, batch = "aIaCrossPortalField")
    public static void electromagnetFieldCrossesPortal(final GameTestHelper helper) {
        if (!immersivePortalsApiAvailable()) {
            helper.succeed();
            return;
        }
        final ServerLevel source = helper.getLevel();
        final ServerLevel destination = source.getServer().getLevel(Level.NETHER);
        if (destination == null) {
            helper.fail("Immersive Aeronautics field test requires the Nether");
            return;
        }

        final BlockPos emitterPos = helper.absolutePos(new BlockPos(1, 2, 1));
        source.setBlock(emitterPos, MagBlocks.ELECTROMAGNET.get().defaultBlockState(), Block.UPDATE_ALL);
        source.setBlock(emitterPos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        final var emitter = (com.stonytark.magnetization.content.electromagnet.ElectromagnetBlockEntity)
                source.getBlockEntity(emitterPos);
        emitter.setRangeOverride(16);

        final net.minecraft.world.phys.Vec3 sourcePortal = net.minecraft.world.phys.Vec3.atCenterOf(emitterPos.east(2));
        // Keep this destination clear of the remote-transfer test's ship at 8,160,8.
        final net.minecraft.world.phys.Vec3 destinationPortal = new net.minecraft.world.phys.Vec3(100.5, 160.5, 8.5);
        final Object portal;
        try {
            final Class<?> portalClass = Class.forName("qouteall.imm_ptl.core.portal.Portal");
            final Object entityType = portalClass.getField("ENTITY_TYPE").get(null);
            portal = portalClass.getConstructor(net.minecraft.world.entity.EntityType.class,
                            net.minecraft.world.level.Level.class)
                    .newInstance(entityType, source);
            invoke(portal, "setOriginPos", sourcePortal);
            invoke(portal, "setOrientationAndSize", new net.minecraft.world.phys.Vec3(0, 0, 1),
                    new net.minecraft.world.phys.Vec3(0, 1, 0), 5.0, 5.0);
            invoke(portal, "setDestinationDimension", destination.dimension());
            invoke(portal, "setDestination", destinationPortal);
            final Class<?> quaternionClass = Class.forName("qouteall.q_misc_util.my_util.DQuaternion");
            final Object quaternion = quaternionClass.getConstructor(double.class, double.class, double.class, double.class)
                    .newInstance(0.0, 0.0, 0.0, 1.0);
            invoke(portal, "setRotationTransformationD", quaternion);
            invoke(portal, "setScaling", 1.0);
            invoke(portal, "setTeleportable", true);
            invoke(portal, "updateCache");
            source.addFreshEntity((net.minecraft.world.entity.Entity) portal);
        } catch (final ReflectiveOperationException exception) {
            helper.fail("Immersive Portals API is unavailable: " + exception.getMessage());
            return;
        }

        final BlockPos assemblyPos = new BlockPos(4, 160, 4);
        destination.setBlock(assemblyPos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        final ServerSubLevel target = SubLevelAssemblyHelper.assembleBlocks(destination, assemblyPos,
                List.of(assemblyPos), new BoundingBox3i(assemblyPos.getX(), assemblyPos.getY(), assemblyPos.getZ(),
                        assemblyPos.getX() + 1, assemblyPos.getY() + 1, assemblyPos.getZ() + 1));
        if (target == null) {
            ((net.minecraft.world.entity.Entity) portal).discard();
            helper.fail("Could not assemble Nether target ship");
            return;
        }
        final BlockPos targetPos = new BlockPos(103, 160, 8);
        final var targetChunk = new net.minecraft.world.level.ChunkPos(targetPos);
        destination.setChunkForced(targetChunk.x, targetChunk.z, true);
        final var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(destination);
        container.physicsSystem().getPipeline().teleport(target,
                new org.joml.Vector3d(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5),
                new Quaterniond());

        helper.runAfterDelay(30L, () -> {
            try {
                final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(target);
                if (handle == null) {
                    helper.fail("Nether target ship lost its physics handle");
                    return;
                }
                final org.joml.Vector3d velocity = handle.getLinearVelocity(new org.joml.Vector3d());
                helper.assertTrue(velocity.x < -0.01,
                        "Overworld electromagnet should pull the Nether ship toward the portal; velocity=" + velocity);
                helper.succeed();
            } finally {
                ((net.minecraft.world.entity.Entity) portal).discard();
                destination.setChunkForced(targetChunk.x, targetChunk.z, false);
            }
        });
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

    /** Keep optional Immersive Portals bytecode out of the normal test profile. */
    private static Object moveThroughPortal(final ServerSubLevel ship, final ServerLevel source,
                                            final ServerLevel destination, final net.minecraft.world.phys.Vec3 target,
                                            final Quaterniond rotation) {
        try {
            final Class<?> bridge = Class.forName(
                    "qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge");
            final Method move = bridge.getMethod("moveThroughPortal", ServerSubLevel.class,
                    ServerLevel.class, ServerLevel.class, net.minecraft.world.phys.Vec3.class, Quaterniond.class);
            return move.invoke(null, ship, source, destination, target, rotation);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Immersive Aeronautics transfer API is unavailable", exception);
        }
    }

    private static Object invoke(final Object target, final String name, final Object... args) {
        for (final Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try {
                return method.invoke(target, args);
            } catch (final IllegalArgumentException ignored) {
                // Try the next overload; the optional API has changed signatures before.
            } catch (final ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not invoke optional Immersive Portals method " + name,
                        exception);
            }
        }
        throw new IllegalStateException("Optional Immersive Portals method is missing: " + name
                + "/" + args.length);
    }

    private static RailgunEmitterBlockEntity findRailgunEmitter(final ServerSubLevel ship) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof RailgunEmitterBlockEntity railgun) return railgun;
            }
        }
        return null;
    }

    private static boolean immersivePortalsApiAvailable() {
        try {
            Class.forName("qouteall.imm_ptl.core.portal.Portal");
            Class.forName("qouteall.imm_ptl.core.compat.sable_integration.IPSableBridge");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
