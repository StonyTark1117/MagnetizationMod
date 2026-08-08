package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.ShipMagneticState;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.physics.ShipTickBudget;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Ship lifecycle and moving/rotated-machine integration coverage. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShipLifecycleGameTests {
    private static final String EMPTY = "empty";

    private ShipLifecycleGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "shipLifecycle")
    public static void magneticStateSurvivesShipDisassemblyAndReassembly(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        final List<BlockPos> blocks = List.of(origin, origin.east(), origin.east(2));
        final List<net.minecraft.world.level.block.state.BlockState> states = List.of(
                MagBlocks.POLARITY_INVERTER.get().defaultBlockState(),
                MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState(),
                Blocks.IRON_BLOCK.defaultBlockState());
        place(level, blocks, states);

        final ServerSubLevel first = assemble(level, origin, blocks);
        final ShipMagneticState before = ShipMagneticRegistry.get(level, first);
        remove(level, first);
        place(level, blocks, states);

        final ServerSubLevel second = assemble(level, origin, blocks);
        try {
            final ShipMagneticState after = ShipMagneticRegistry.get(level, second);
            helper.assertTrue(before.equals(after),
                    "Magnetic state changed across disassembly/reassembly: " + before + " -> " + after);
            helper.assertTrue(after.polarity() == com.stonytark.magnetization.api.MagneticPolarity.SOUTH
                            && after.inverterBlockCount() == 1 && after.ferrousBlockCount() == 2,
                    "Reassembled ship lost scanned magnetic contents: " + after);
        } finally {
            remove(level, second);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "shipLifecycle")
    public static void magneticStateDisappearsAfterShipRemoval(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        final List<BlockPos> blocks = List.of(origin, origin.east());
        final List<net.minecraft.world.level.block.state.BlockState> states = List.of(
                MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState());
        place(level, blocks, states);
        final ServerSubLevel ship = assemble(level, origin, blocks);
        final ShipMagneticState cached = ShipMagneticRegistry.get(level, ship);
        helper.assertTrue(cached.ferrousBlockCount() == 2, "Expected a cached magnetic ship state: " + cached);

        remove(level, ship);
        final ShipMagneticState afterRemoval = ShipMagneticRegistry.get(level, ship);
        helper.assertTrue(afterRemoval.equals(ShipMagneticState.DEFAULT),
                "Removed ship must not retain cached state: " + afterRemoval);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "shipLifecycle")
    public static void connectedNestedSublevelsShareOneForceBudget(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        final List<BlockPos> blocks = List.of(origin);
        place(level, blocks, List.of(Blocks.IRON_BLOCK.defaultBlockState()));
        final ServerSubLevel parent = assemble(level, origin, blocks);
        final BlockPos childOrigin = origin.offset(0, 0, 2);
        place(level, List.of(childOrigin), List.of(Blocks.IRON_BLOCK.defaultBlockState()));
        final ServerSubLevel child = assemble(level, childOrigin, List.of(childOrigin));
        try {
            child.setSplitFrom(parent, new dev.ryanhcode.sable.companion.math.Pose3d(parent.logicalPose()));
            final var chain = SableBridge.connectedChainIds(parent, level.getGameTime());
            helper.assertTrue(chain.contains(parent.getUniqueId()) && chain.contains(child.getUniqueId())
                            && chain.size() == 2,
                    "Nested sublevels must resolve to one unique connected chain: " + chain);
            final long tick = level.getGameTime();
            helper.assertTrue(ShipTickBudget.grant(parent, tick, 1.0d, 0.75d) == 0.75d,
                    "First connected-body impulse should receive its requested budget");
            helper.assertTrue(ShipTickBudget.grant(parent, tick, 1.0d, 0.75d) == 0.25d,
                    "A second traversal must receive only the remaining shared budget");
            helper.assertTrue(ShipTickBudget.grant(child, tick, 1.0d, 0.75d) == 0.75d,
                    "A distinct nested body must retain its own budget entry");
        } finally {
            remove(level, child);
            remove(level, parent);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 160, batch = "shipLifecycle")
    public static void fusionThrusterWorksOnRotatedMovingShip(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos base = skyBase(helper, 240);
        buildFusionPanel(level, base, 5, 3);
        final BlockPos masterPos = base.offset(1, 1, 0);
        final var master = (com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity)
                level.getBlockEntity(masterPos);
        master.fluidHandler().fill(new net.neoforged.neoforge.fluids.FluidStack(
                com.stonytark.magnetization.registry.MagFluids.HELIUM_3.get(), 16_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        master.energyBuffer().receiveEnergy(2_000_000, false);
        final List<BlockPos> blocks = panelBlocks(base, 5, 3);
        final ServerSubLevel ship = assemble(level, masterPos, blocks);
        try {
            final Quaterniond rotation = new Quaterniond().rotateY(Math.PI / 2.0d);
            final var container = SubLevelContainer.getContainer(level);
            container.physicsSystem().getPipeline().teleport(ship,
                    new Vector3d(base.getX() + 0.5d, 245.0d, base.getZ() + 0.5d), rotation);
            final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            handle.addLinearAndAngularVelocity(new Vector3d(0.25d, 0.0d, 0.0d), new Vector3d());
            final Vector3d before = handle.getLinearVelocity(new Vector3d());
            helper.runAfterDelay(20L, () -> {
                final Vector3d after = handle.getLinearVelocity(new Vector3d());
                remove(level, ship);
                final double beforeHorizontal = Math.hypot(before.x(), before.z());
                final double afterHorizontal = Math.hypot(after.x(), after.z());
                helper.assertTrue(afterHorizontal > beforeHorizontal + 0.01d,
                        "Rotated Fusion Thruster must add thrust while the ship is already moving; before="
                                + before + " after=" + after);
                helper.succeed();
            });
            return;
        } finally {
            // The delayed assertion owns cleanup after the ship has had time to tick.
        }
    }

    // RailgunHandler scans every registered emitter in the shared ServerLevel.
    // Isolate this arc from the other lifecycle fixtures so it cannot cross-pair.
    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgun")
    public static void railgunWorksOnRotatedMovingShip(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos rail = skyBase(helper, 240);
        // Leave clearance for the one-block rotated collider between the rails;
        // a one-block gap makes Rapier pin it against a copper rail while still
        // reporting the velocity injected by the launcher.
        final BlockPos sibling = rail.offset(3, 0, 0);
        // Keep the fixture inside the horizontal channel while Sable settles its
        // rotated bounds; ships normally have a deck/launcher beneath them.
        for (int x = 0; x <= 3; x++) for (int z = 1; z <= 6; z++) {
            level.setBlock(rail.offset(x, -1, -z), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }

        final BlockPos shipOrigin = rail.offset(1, 0, -2);
        place(level, List.of(shipOrigin), List.of(Blocks.IRON_BLOCK.defaultBlockState()));
        final ServerSubLevel ship = assemble(level, shipOrigin, List.of(shipOrigin));
        try {
            final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            final var container = SubLevelContainer.getContainer(level);
            container.physicsSystem().getPipeline().teleport(ship,
                    new Vector3d(shipOrigin.getX() + 0.5d, 240.5d, shipOrigin.getZ() + 0.5d),
                    new Quaterniond().rotateY(Math.PI / 2.0d));
            // Seed motion explicitly in world NORTH so the fixture does not
            // depend on the sign convention of the quarter-turn quaternion.
            handle.addLinearAndAngularVelocity(new Vector3d(0.0d, 0.0d, -0.2d), new Vector3d());
            // Let Sable publish the teleported body's rotated world bounds before
            // arming the global rail scanner. Re-seat it afterward because gravity
            // would otherwise let the one-block fixture fall out of the channel.
            helper.runAfterDelay(10L, () -> {
                container.physicsSystem().getPipeline().teleport(ship,
                        new Vector3d(shipOrigin.getX() + 0.5d, 240.5d, shipOrigin.getZ() + 0.5d),
                        new Quaterniond().rotateY(Math.PI / 2.0d));
                handle.addLinearAndAngularVelocity(new Vector3d(0.0d, 0.0d, -0.2d), new Vector3d());
                helper.runAfterDelay(12L, () -> {
                    buildRailgunRail(level, rail, net.minecraft.core.Direction.NORTH);
                    buildRailgunRail(level, sibling, net.minecraft.core.Direction.NORTH);
                    powerRailgun(level, rail);
                    powerRailgun(level, sibling);
                    if (!(level.getBlockEntity(rail) instanceof RailgunEmitterBlockEntity master)) {
                        remove(level, ship);
                        helper.fail("Railgun master emitter was not created");
                        return;
                    }
                    monitorRailgunShip(helper, level, container, ship, handle, master, rail, sibling);
                });
            });
            return;
        } catch (final Throwable t) {
            remove(level, ship);
            throw t;
        }
    }

    /**
     * Observe the ship every tick from rail activation through the muzzle exit.
     * A one-shot velocity read is insufficient here: the original defect removed
     * the Sable sub-level at the end of the launch, after the railgun had already
     * applied some force. This trace proves that the body remains registered,
     * retains forward physics velocity across multiple samples, and survives
     * after the launch trace completes.
     */
    private static void monitorRailgunShip(final GameTestHelper helper,
                                           final net.minecraft.server.level.ServerLevel level,
                                           final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container,
                                           final ServerSubLevel ship,
                                           final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle,
                                           final RailgunEmitterBlockEntity master,
                                           final BlockPos rail,
                                           final BlockPos sibling) {
        final boolean[] removedDuringTrace = {false};
        final boolean[] sawLaunching = {false};
        final int[] samples = {0};
        final int[] maxLaunchTicks = {0};
        final int[] forwardSamples = {0};
        final double[] maxForwardSpeed = {0.0d};
        final Runnable[] monitor = new Runnable[1];
        monitor[0] = () -> {
            samples[0]++;
            final RailgunEmitterBlockEntity.ArcState state = master.arcState();
            if (state == RailgunEmitterBlockEntity.ArcState.LAUNCHING) sawLaunching[0] = true;
            maxLaunchTicks[0] = Math.max(maxLaunchTicks[0], master.launchTicks());

            final boolean registered = !ship.isRemoved()
                    && container.getSubLevel(ship.getUniqueId()) == ship;
            if (!registered) {
                removedDuringTrace[0] = true;
            } else {
                final Vector3d velocity = handle.isValid()
                        ? handle.getLinearVelocity(new Vector3d())
                        : new Vector3d(ship.latestLinearVelocity);
                final double forwardSpeed = -velocity.z();
                maxForwardSpeed[0] = Math.max(maxForwardSpeed[0], forwardSpeed);
                if (forwardSpeed > 0.5d) forwardSamples[0]++;
            }

            if (samples[0] < 50) {
                // GameTest removes the runnable that just executed after the
                // callback returns. Use a fresh wrapper so scheduling the next
                // sample cannot be removed along with the current one.
                helper.runAfterDelay(1L, () -> monitor[0].run());
                return;
            }

            final boolean survivedAfterExit = !removedDuringTrace[0]
                    && !ship.isRemoved()
                    && container.getSubLevel(ship.getUniqueId()) == ship;
            remove(level, ship);
            clear(level, rail, net.minecraft.core.Direction.NORTH);
            clear(level, sibling, net.minecraft.core.Direction.NORTH);
            for (int x = 0; x <= 3; x++) for (int z = 1; z <= 6; z++) {
                level.setBlock(rail.offset(x, -1, -z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            helper.assertTrue(samples[0] == 50, "Railgun ship monitor did not complete");
            helper.assertTrue(sawLaunching[0] && maxLaunchTicks[0] > 0,
                    "Railgun never entered a live launching state");
            helper.assertTrue(forwardSamples[0] >= 3 && maxForwardSpeed[0] > 0.5d,
                    "Railgun ship did not retain forward physics velocity across samples; samples="
                            + forwardSamples[0] + " maxForwardSpeed=" + maxForwardSpeed[0]);
            helper.assertTrue(survivedAfterExit,
                    "Railgun launch removed the ship sub-level after muzzle exit");
            helper.succeed();
        };
        monitor[0].run();
    }

    private static void place(final net.minecraft.server.level.ServerLevel level, final List<BlockPos> positions,
                               final List<net.minecraft.world.level.block.state.BlockState> states) {
        for (int i = 0; i < positions.size(); i++) level.setBlock(positions.get(i), states.get(i), Block.UPDATE_ALL);
    }

    private static ServerSubLevel assemble(final net.minecraft.server.level.ServerLevel level, final BlockPos anchor,
                                           final List<BlockPos> blocks) {
        final BlockPos min = blocks.stream().reduce((a, b) -> new BlockPos(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()))).orElse(anchor);
        final BlockPos max = blocks.stream().reduce((a, b) -> new BlockPos(
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))).orElse(anchor);
        return SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks,
                new BoundingBox3i(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1));
    }

    private static void remove(final net.minecraft.server.level.ServerLevel level, final ServerSubLevel ship) {
        if (ship == null || ship.isRemoved()) return;
        final var container = SubLevelContainer.getContainer(level);
        if (container != null) container.removeSubLevel(ship,
                dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
    }

    private static BlockPos skyBase(final GameTestHelper helper, final int y) {
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        return new BlockPos(abs.getX(), y, abs.getZ());
    }

    private static List<BlockPos> panelBlocks(final BlockPos base, final int width, final int height) {
        final List<BlockPos> out = new ArrayList<>();
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) out.add(base.offset(x, y, 0));
        return out;
    }

    private static void buildFusionPanel(final net.minecraft.server.level.ServerLevel level, final BlockPos base,
                                         final int width, final int height) {
        final var interior = MagBlocks.FUSION_THRUSTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            final boolean inside = x > 0 && x < width - 1 && y > 0 && y < height - 1;
            level.setBlock(base.offset(x, y, 0), inside ? interior : MagBlocks.TOKAMAK_COIL.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void buildRailgunRail(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter,
                                         final net.minecraft.core.Direction facing) {
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, facing), Block.UPDATE_ALL);
        for (int i = 1; i <= 6; i++) level.setBlock(emitter.relative(facing, i), Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void powerRailgun(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter) {
        level.setBlock(emitter.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(emitter) instanceof
                com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity railgun) {
            railgun.energyBuffer().receiveEnergy(1_000_000, false);
        }
    }

    private static void clear(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter,
                              final net.minecraft.core.Direction facing) {
        for (int i = 0; i <= 6; i++) level.setBlock(emitter.relative(facing, i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(emitter.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
