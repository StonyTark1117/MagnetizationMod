package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.menu.MachineMenu;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** End-to-end coverage for the railgun's optional ordinary-block projectile mode. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailgunAutoAssemblyGameTests {
    private static final String EMPTY = "empty";

    private RailgunAutoAssemblyGameTests() { }

    /** A GUI click on either emitter enables the whole arc. A flat 3x3 staged one
     * block below the rail line must become one ship, lift to that line, hold for
     * a paired remote, and launch when requested. */
    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "railgunAutoAssembly")
    public static void autoAssemblyBuildsLiftsAndLaunchesFlatThreeByThree(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos fixture = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos first = new BlockPos(fixture.getX(), 280, fixture.getZ());
        final BlockPos second = first.offset(4, 0, 0);
        buildRail(level, first);
        buildRail(level, second);
        power(level, first);
        power(level, second);

        final RailgunEmitterBlockEntity a = emitter(level, first);
        final RailgunEmitterBlockEntity b = emitter(level, second);
        if (a == null || b == null) {
            cleanupRails(level, first, second);
            helper.fail("Auto-assembly test railgun emitters were not created");
            return;
        }
        final var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            cleanupRails(level, first, second);
            helper.fail("Sable sub-level container is unavailable");
            return;
        }
        final Set<UUID> shipsBefore = shipIds(container);
        final int priorLimit = MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.get();
        final int priorThickness = MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.get();
        MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(0);
        MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.set(1);

        // Pair the remote first so the assembled projectile stays parked long
        // enough to inspect its launch-height alignment.
        a.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));
        final var player = helper.makeMockPlayer(GameType.CREATIVE);
        final MachineMenu menu = new MachineMenu(1, player.getInventory(),
                ContainerLevelAccess.create(level, second), second,
                MachineMenu.Kind.RAILGUN, b.remoteContainer());
        final boolean toggleAccepted = menu.clickMenuButton(player, MachineMenu.BUTTON_RAILGUN_AUTO_ASSEMBLE);
        player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

        final List<BlockPos> payload = new ArrayList<>();
        for (int across = 1; across <= 3; across++) {
            for (int along = 1; along <= 3; along++) {
                final BlockPos pos = first.offset(across, -1, -along);
                payload.add(pos);
                level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        helper.runAfterDelay(30L, () -> {
            final ServerSubLevel projectile = newShip(container, shipsBefore);
            try {
                helper.assertTrue(toggleAccepted, "Railgun auto-assembly GUI button was rejected");
                helper.assertTrue(a.autoAssemble() && b.autoAssemble(),
                        "Auto-assembly mode must mirror to both paired emitters");
                helper.assertTrue((a.guiStat2() & RailgunEmitterBlockEntity.AUTO_ASSEMBLE_BIT) != 0
                                && a.guiStat2() == b.guiStat2(),
                        "Both synchronized GUI payloads must expose the auto-assembly bit");
                helper.assertTrue(payload.stream().allMatch(pos -> level.getBlockState(pos).isAir()),
                        "Every block in the flat 3x3 payload must be assembled, not truncated");
                helper.assertTrue(projectile != null, "The flat 3x3 payload did not become a Sable ship");
                if (projectile == null) return;
                helper.assertTrue(a.arcState() == RailgunEmitterBlockEntity.ArcState.HOLDING,
                        "A manually paired arc must hold its assembled projectile; state=" + a.arcState());
                helper.assertTrue(Math.abs(projectile.logicalPose().position().y() - (first.getY() + 0.5d)) < 0.75d,
                        "Assembled projectile was not raised to rail launch height; y="
                                + projectile.logicalPose().position().y());

                a.requestFire();
                // Sample immediately after launch. The short test rails impart
                // enough speed that the disposable body may leave Sable's valid
                // region well before the previous 24-tick observation point.
                helper.runAfterDelay(6L, () -> {
                    try {
                        final RigidBodyHandle handle = RigidBodyHandle.of(projectile);
                        final Vector3d velocity = handle == null ? new Vector3d()
                                : handle.getLinearVelocity(new Vector3d());
                        helper.assertTrue(velocity.z < -0.5d,
                                "Assembled projectile was not sent down the north-facing rails; velocity=" + velocity);
                        helper.succeed();
                    } finally {
                        cleanupShip(container, projectile);
                        cleanupRails(level, first, second);
                        MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(priorLimit);
                        MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.set(priorThickness);
                    }
                });
            } catch (final Throwable failure) {
                cleanupShip(container, projectile);
                cleanupRails(level, first, second);
                MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(priorLimit);
                MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.set(priorThickness);
                throw failure;
            }
        });
    }

    /** A positive cap rejects the complete staged set and never assembles a
     * partial projectile. Raising the cap to the exact block count then permits it. */
    @GameTest(template = EMPTY, timeoutTicks = 140, batch = "railgunAutoAssemblyLimit")
    public static void autoAssemblyLimitRejectsWholeOversizedPayload(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos fixture = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos first = new BlockPos(fixture.getX(), 310, fixture.getZ());
        final BlockPos second = first.offset(2, 0, 0);
        buildRail(level, first);
        buildRail(level, second);
        power(level, first);
        power(level, second);
        final RailgunEmitterBlockEntity a = emitter(level, first);
        final var container = SubLevelContainer.getContainer(level);
        if (a == null || container == null) {
            cleanupRails(level, first, second);
            helper.fail("Limit test railgun or Sable container is unavailable");
            return;
        }

        final int priorLimit = MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.get();
        final int priorThickness = MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.get();
        MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(1);
        MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.set(0);
        com.stonytark.magnetization.content.railgun.RailgunHandler.setArcAutoAssemble(level, first, true);
        final BlockPos one = first.offset(1, 0, -2);
        final BlockPos two = first.offset(1, 0, -3);
        level.setBlock(one, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(two, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        final Set<UUID> shipsBefore = shipIds(container);

        helper.runAfterDelay(20L, () -> {
            try {
                helper.assertTrue(!level.getBlockState(one).isAir() && !level.getBlockState(two).isAir(),
                        "A one-block cap must reject the whole two-block payload without truncation");
                helper.assertTrue(shipIds(container).equals(shipsBefore),
                        "An oversized staged payload must not allocate a partial ship");
                MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(2);
                // Auto-assembly runs on the next machine ticks; inspect the new
                // projectile before the unpaired automatic arc launches it away.
                helper.runAfterDelay(6L, () -> {
                    final ServerSubLevel projectile = newShip(container, shipsBefore);
                    try {
                        helper.assertTrue(level.getBlockState(one).isAir() && level.getBlockState(two).isAir(),
                                "The exact two-block cap should permit the complete payload");
                        helper.assertTrue(projectile != null,
                                "Permitted two-block payload did not become one ship");
                        helper.succeed();
                    } finally {
                        cleanupShip(container, projectile);
                        cleanupRails(level, first, second);
                        MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(priorLimit);
                        MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.set(priorThickness);
                    }
                });
            } catch (final Throwable failure) {
                cleanupRails(level, first, second);
                MagConfig.RAILGUN_AUTO_ASSEMBLE_MAX_BLOCKS.set(priorLimit);
                MagConfig.RAILGUN_CHANNEL_HALF_THICKNESS.set(priorThickness);
                throw failure;
            }
        });
    }

    private static void buildRail(final ServerLevel level, final BlockPos emitter) {
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        for (int i = 1; i <= 6; i++) {
            level.setBlock(emitter.relative(Direction.NORTH, i),
                    Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void power(final ServerLevel level, final BlockPos pos) {
        final RailgunEmitterBlockEntity emitter = emitter(level, pos);
        if (emitter != null) emitter.energyBuffer().receiveEnergy(1_000_000, false);
    }

    private static RailgunEmitterBlockEntity emitter(final ServerLevel level, final BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RailgunEmitterBlockEntity emitter ? emitter : null;
    }

    private static Set<UUID> shipIds(final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container) {
        final Set<UUID> ids = new HashSet<>();
        for (final var sub : container.getAllSubLevels()) {
            if (sub instanceof ServerSubLevel ship) ids.add(ship.getUniqueId());
        }
        return ids;
    }

    private static ServerSubLevel newShip(final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container,
                                          final Set<UUID> before) {
        for (final var sub : container.getAllSubLevels()) {
            if (sub instanceof ServerSubLevel ship && !before.contains(ship.getUniqueId())) return ship;
        }
        return null;
    }

    private static void cleanupShip(final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container,
                                    final ServerSubLevel ship) {
        if (ship != null && !ship.isRemoved()) container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }

    private static void cleanupRails(final ServerLevel level, final BlockPos first, final BlockPos second) {
        for (final BlockPos emitter : List.of(first, second)) {
            level.setBlock(emitter, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (int i = 1; i <= 6; i++) {
                level.setBlock(emitter.relative(Direction.NORTH, i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
