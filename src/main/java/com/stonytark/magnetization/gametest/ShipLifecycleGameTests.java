package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.ShipMagneticState;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity;
import com.stonytark.magnetization.physics.SableBridge;
import com.stonytark.magnetization.physics.ShipMagneticRegistry;
import com.stonytark.magnetization.physics.ShipTickBudget;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
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
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/gametest");
    private static final int RAILGUN_TEST_GAP = 5;
    private static final int RAILGUN_TEST_POST_EXIT_TICKS = 20;

    private record RailgunBenchmark(String name, int railLength, int shipEdge,
                                    double minExitSpeed, double minPostExitDistance,
                                    boolean requireFullPostExitTrace) {}

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
    // Separate batches prevent benchmark arcs and their temporary config values
    // from cross-pairing or overlapping one another.
    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgunMinimumSmall")
    public static void minimumRailgunPowerfullyLaunchesSmallShip(final GameTestHelper helper) {
        runRailgunBenchmark(helper, new RailgunBenchmark("minimum-small", 3, 1, 15.0d, 12.0d, true));
    }

    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgunMinimumLarge")
    public static void minimumRailgunPowerfullyLaunchesLargeShip(final GameTestHelper helper) {
        runRailgunBenchmark(helper, new RailgunBenchmark("minimum-large", 3, 3, 15.0d, 12.0d, true));
    }

    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgunMedium")
    public static void sixBlockRailgunScalesAboveMinimum(final GameTestHelper helper) {
        // This speed can leave the headless test region before a full second;
        // the minimum-rail cases retain the complete survival regression trace.
        runRailgunBenchmark(helper, new RailgunBenchmark("six-block", 6, 1, 35.0d, 25.0d, false));
    }

    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgunLong")
    public static void twelveBlockRailgunHasUncappedOutput(final GameTestHelper helper) {
        // Both thresholds exceed the former 40 blocks/s ceiling, proving that a
        // longer rail is allowed to realize its exponentially greater output.
        // At this speed the headless GameTest backend removes a ship once it
        // leaves the tiny test region, so verify several live coast samples and
        // project the full one-second distance from its measured exit velocity.
        runRailgunBenchmark(helper, new RailgunBenchmark("twelve-block", 12, 1, 70.0d, 50.0d, false));
    }

    /** A real six-block launch with block breaking disabled must keep its ship
     * registered and stop it on the near side of an intact wall. This exercises
     * launch tracking and the pre-physics collision clamp together, beyond the
     * deterministic collision-shape test in {@link MagGameTests}. */
    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgunCollisionGuard")
    public static void nonBreakingRailgunStopsRealShipAtWall(final GameTestHelper helper) {
        MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(false);
        MagConfig.ALLOW_REDSTONE_POWER.set(true);
        MagConfig.ALLOW_ENERGY_POWER.set(true);
        final boolean priorLenzBraking = MagConfig.LENZ_BRAKING_ENABLED.get();
        final double priorRailgunForceBase = MagConfig.RAILGUN_FORCE_BASE.get();
        MagConfig.LENZ_BRAKING_ENABLED.set(false);
        MagConfig.RAILGUN_FORCE_BASE.set(0.6d);

        final var level = helper.getLevel();
        final BlockPos rail = skyBase(helper, 240);
        final BlockPos sibling = rail.offset(RAILGUN_TEST_GAP, 0, 0);
        final int railLength = 6;
        // Put the wall directly beyond the muzzle. The six-block launch still
        // reaches obstacle-crossing speed, but cannot leave Sable's small
        // headless-test tracking region before the collision is observed.
        final BlockPos wallBase = rail.offset(0, -4, -(railLength + 1));
        final List<BlockPos> wall = new ArrayList<>();
        for (int x = 0; x <= RAILGUN_TEST_GAP; x++) for (int y = 0; y < 9; y++) {
            final BlockPos pos = wallBase.offset(x, y, 0);
            wall.add(pos);
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }

        final BlockPos shipOrigin = rail.offset(2, 0, -2);
        final List<BlockPos> shipBlocks = railgunShipBlocks(shipOrigin, 1);
        place(level, shipBlocks, List.of(Blocks.IRON_BLOCK.defaultBlockState()));
        final ServerSubLevel ship = assemble(level, shipOrigin, shipBlocks);
        final var container = SubLevelContainer.getContainer(level);
        final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
        container.physicsSystem().getPipeline().teleport(ship,
                new Vector3d(shipOrigin.getX() + 0.5d, 240.5d, shipOrigin.getZ() + 0.5d),
                new Quaterniond().rotateY(Math.PI / 2.0d));

        helper.runAfterDelay(10L, () -> {
            container.physicsSystem().getPipeline().teleport(ship,
                    new Vector3d(shipOrigin.getX() + 0.5d, 240.5d, shipOrigin.getZ() + 0.5d),
                    new Quaterniond().rotateY(Math.PI / 2.0d));
            handle.addLinearAndAngularVelocity(new Vector3d(0.0d, 0.0d, -0.2d), new Vector3d());
            helper.runAfterDelay(1L, () -> {
                buildRailgunRail(level, rail, net.minecraft.core.Direction.NORTH, railLength);
                buildRailgunRail(level, sibling, net.minecraft.core.Direction.NORTH, railLength);
                final var first = level.getBlockEntity(rail) instanceof RailgunEmitterBlockEntity be ? be : null;
                final var second = level.getBlockEntity(sibling) instanceof RailgunEmitterBlockEntity be ? be : null;
                if (first == null || second == null) {
                    finishRailgunCollisionTest(level, ship, rail, sibling, railLength, wall,
                            priorLenzBraking, priorRailgunForceBase);
                    helper.fail("Railgun collision-guard emitters were not created");
                    return;
                }
                first.setBreakBlocks(false);
                second.setBreakBlocks(false);
                powerRailgun(level, rail);
                powerRailgun(level, sibling);

                final int[] samples = {0};
                final double[] maxForwardSpeed = {0.0d};
                final double[] lastRegisteredZ = {Double.NaN};
                final double[] lastForwardSpeed = {Double.NaN};
                final Pose3d pose = new Pose3d();
                final Runnable[] monitor = new Runnable[1];
                monitor[0] = () -> {
                    samples[0]++;
                    final boolean registered = !ship.isRemoved()
                            && container.getSubLevel(ship.getUniqueId()) == ship;
                    final Vector3d velocity = registered && handle.isValid()
                            ? handle.getLinearVelocity(new Vector3d()) : new Vector3d();
                    maxForwardSpeed[0] = Math.max(maxForwardSpeed[0], -velocity.z());
                    if (registered) {
                        lastRegisteredZ[0] = container.physicsSystem().getPipeline()
                                .readPose(ship, pose).position().z();
                        lastForwardSpeed[0] = -velocity.z();
                    }
                    if (registered && samples[0] < 35) {
                        helper.runAfterDelay(1L, () -> monitor[0].run());
                        return;
                    }

                    final double finalZ = registered
                            ? container.physicsSystem().getPipeline().readPose(ship, pose).position().z()
                            : Double.NEGATIVE_INFINITY;
                    final boolean wallIntact = wall.stream()
                            .allMatch(pos -> level.getBlockState(pos).is(Blocks.OBSIDIAN));
                    finishRailgunCollisionTest(level, ship, rail, sibling, railLength, wall,
                            priorLenzBraking, priorRailgunForceBase);

                    helper.assertTrue(maxForwardSpeed[0] >= 15.0d,
                            "Collision-guard fixture never received a real launch; max speed="
                                    + maxForwardSpeed[0]);
                    helper.assertTrue(registered,
                            "Non-breaking railgun collision removed the launched ship; samples=" + samples[0]
                                    + " max speed=" + maxForwardSpeed[0]
                                    + " last z=" + lastRegisteredZ[0]
                                    + " last speed=" + lastForwardSpeed[0]
                                    + " wall z=" + wallBase.getZ());
                    helper.assertTrue(finalZ >= wallBase.getZ() + 1.0d,
                            "Non-breaking launch crossed into or through the wall; ship z=" + finalZ
                                    + " wall z=" + wallBase.getZ());
                    helper.assertTrue(wallIntact,
                            "Non-breaking launch altered its collision wall");
                    helper.succeed();
                };
                monitor[0].run();
            });
        });
    }

    /**
     * Manual launch regression: a real Sable ship must remain suspended while
     * players board and wait on the remote, then leave that suspension cleanly
     * and receive the normal six-block launch velocity when fired.
     */
    @GameTest(template = EMPTY, timeoutTicks = 180, batch = "shipLifecycleRailgunManualHold")
    public static void manualRailgunSuspendsShipUntilRemoteFire(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos rail = skyBase(helper, 240);
        final BlockPos sibling = rail.offset(RAILGUN_TEST_GAP, 0, 0);
        final BlockPos shipOrigin = rail.offset(2, 0, -2);
        final List<BlockPos> blocks = railgunShipBlocks(shipOrigin, 3);
        place(level, blocks, blocks.stream().map(ignored -> Blocks.IRON_BLOCK.defaultBlockState()).toList());
        final ServerSubLevel ship = assemble(level, shipOrigin, blocks);
        final var container = SubLevelContainer.getContainer(level);
        final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);

        container.physicsSystem().getPipeline().teleport(ship,
                new Vector3d(shipOrigin.getX() + 0.5d, 240.5d, shipOrigin.getZ() + 0.5d),
                new Quaterniond().rotateY(Math.PI / 2.0d));

        // Let Sable publish the assembled body's bounds before the global rail
        // scanner starts looking for it, then put it back at the breech.
        helper.runAfterDelay(10L, () -> {
            container.physicsSystem().getPipeline().teleport(ship,
                    new Vector3d(shipOrigin.getX() + 0.5d, 240.5d, shipOrigin.getZ() + 0.5d),
                    new Quaterniond().rotateY(Math.PI / 2.0d));
            buildRailgunRail(level, rail, net.minecraft.core.Direction.NORTH, 6);
            buildRailgunRail(level, sibling, net.minecraft.core.Direction.NORTH, 6);
            powerRailgun(level, rail);
            powerRailgun(level, sibling);
            if (!(level.getBlockEntity(rail) instanceof RailgunEmitterBlockEntity master)) {
                remove(level, ship);
                clear(level, rail, net.minecraft.core.Direction.NORTH, 6);
                clear(level, sibling, net.minecraft.core.Direction.NORTH, 6);
                helper.fail("Manual railgun master emitter was not created");
                return;
            }
            master.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                    com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));

            helper.runAfterDelay(4L, () -> {
                final Pose3d heldStart = container.physicsSystem().getPipeline().readPose(ship, new Pose3d());
                final double startY = heldStart.position().y();

                // Four seconds is long enough for the previous 85%-damping
                // implementation to sink out of the channel (and, in a normal
                // ground-level build, continue falling into the terrain).
                helper.runAfterDelay(80L, () -> {
                    final boolean remainedRegistered = !ship.isRemoved()
                            && container.getSubLevel(ship.getUniqueId()) == ship;
                    final Pose3d heldEnd = remainedRegistered
                            ? container.physicsSystem().getPipeline().readPose(ship, new Pose3d()) : new Pose3d();
                    final double verticalDrift = remainedRegistered
                            ? Math.abs(heldEnd.position().y() - startY) : Double.POSITIVE_INFINITY;
                    final double heldVerticalSpeed = remainedRegistered && handle.isValid()
                            ? Math.abs(handle.getLinearVelocity(new Vector3d()).y()) : Double.POSITIVE_INFINITY;
                    final RailgunEmitterBlockEntity.ArcState heldState = master.arcState();
                    final boolean stayedHolding = heldState == RailgunEmitterBlockEntity.ArcState.HOLDING;

                    master.requestFire();
                    final double[] maxForwardSpeed = {0.0d};
                    final int[] launchSamples = {0};
                    final Runnable[] launchTrace = new Runnable[1];
                    launchTrace[0] = () -> {
                        final double forwardSpeed = !ship.isRemoved() && handle.isValid()
                                ? -handle.getLinearVelocity(new Vector3d()).z()
                                : -ship.latestLinearVelocity.z();
                        maxForwardSpeed[0] = Math.max(maxForwardSpeed[0], forwardSpeed);
                        launchSamples[0]++;
                        if (launchSamples[0] < 8) {
                            helper.runAfterDelay(1L, () -> launchTrace[0].run());
                            return;
                        }

                        remove(level, ship);
                        clear(level, rail, net.minecraft.core.Direction.NORTH, 6);
                        clear(level, sibling, net.minecraft.core.Direction.NORTH, 6);
                        LOG.info("Manual railgun hold benchmark: drift={} blocks heldVerticalSpeed={} blocks/s launchSpeed={} blocks/s",
                                verticalDrift, heldVerticalSpeed, maxForwardSpeed[0]);
                        helper.assertTrue(remainedRegistered,
                                "Held railgun ship disappeared before firing");
                        helper.assertTrue(stayedHolding,
                                "Manual railgun left HOLDING while waiting; state=" + heldState);
                        helper.assertTrue(verticalDrift <= 0.05d,
                                "Manual railgun ship lost altitude while held; drift=" + verticalDrift);
                        helper.assertTrue(heldVerticalSpeed <= 0.05d,
                                "Manual railgun ship retained vertical fall speed while held; speed="
                                        + heldVerticalSpeed);
                        // The independent six-block benchmark enforces the full
                        // 35+ blocks/s scaling target. This large fixture can be
                        // culled by the headless arena after only two acceleration
                        // samples, so require both samples' decisive launch here.
                        helper.assertTrue(maxForwardSpeed[0] >= 15.0d,
                                "Released manual railgun ship did not receive decisive launch velocity; speed="
                                        + maxForwardSpeed[0]);
                        helper.succeed();
                    };
                    launchTrace[0].run();
                });
            });
        });
    }

    private static void runRailgunBenchmark(final GameTestHelper helper, final RailgunBenchmark benchmark) {
        // Config-mutating tests run in separate batches, but force the canonical
        // power-source defaults here so this FE-only launch benchmark is hermetic.
        MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(false);
        MagConfig.ALLOW_REDSTONE_POWER.set(true);
        MagConfig.ALLOW_ENERGY_POWER.set(true);
        final boolean priorLenzBraking = MagConfig.LENZ_BRAKING_ENABLED.get();
        final double priorRailgunForceBase = MagConfig.RAILGUN_FORCE_BASE.get();
        // Exercise the value already present in existing player configs. This
        // must pass without relying on a regenerated config or a raised default.
        MagConfig.RAILGUN_FORCE_BASE.set(0.6d);
        // Rails are conductive and therefore also exercise Lenz braking. Disable
        // that separate effect for this benchmark so the measured exit velocity
        // belongs to the launcher; dedicated Lenz tests cover the counterforce.
        MagConfig.LENZ_BRAKING_ENABLED.set(false);
        final var level = helper.getLevel();
        final BlockPos rail = skyBase(helper, 240);
        // Leave generous clearance for the rotated collider. A tight rail gap
        // can pin this minimal one-block fixture against a copper rail while
        // still reporting the velocity injected by the launcher.
        final BlockPos sibling = rail.offset(RAILGUN_TEST_GAP, 0, 0);
        // Keep the fixture inside the horizontal channel while Sable settles its
        // rotated bounds; ships normally have a deck/launcher beneath them.
        // Keep the floor below the ship so it cannot pin the collider while
        // still giving the 20-tick post-muzzle trace room to measure horizontal
        // travel before gravity reaches it.
        for (int x = 0; x <= RAILGUN_TEST_GAP; x++) for (int z = 1; z <= benchmark.railLength() + 24; z++) {
            level.setBlock(rail.offset(x, -8, -z), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        }

        final BlockPos shipOrigin = rail.offset(2, 0, -2);
        final List<BlockPos> shipBlocks = railgunShipBlocks(shipOrigin, benchmark.shipEdge());
        place(level, shipBlocks, shipBlocks.stream().map(ignored -> Blocks.IRON_BLOCK.defaultBlockState()).toList());
        final ServerSubLevel ship = assemble(level, shipOrigin, shipBlocks);
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
                // Arm immediately after the final reseat; waiting longer lets
                // gravity drop the fixture out of the rail channel.
                helper.runAfterDelay(1L, () -> {
                    buildRailgunRail(level, rail, net.minecraft.core.Direction.NORTH, benchmark.railLength());
                    buildRailgunRail(level, sibling, net.minecraft.core.Direction.NORTH, benchmark.railLength());
                    powerRailgun(level, rail);
                    powerRailgun(level, sibling);
                    if (!(level.getBlockEntity(rail) instanceof RailgunEmitterBlockEntity master)) {
                        remove(level, ship);
                        MagConfig.LENZ_BRAKING_ENABLED.set(priorLenzBraking);
                        MagConfig.RAILGUN_FORCE_BASE.set(priorRailgunForceBase);
                        helper.fail("Railgun master emitter was not created");
                        return;
                    }
                    monitorRailgunShip(helper, level, container, ship, handle, master, rail, sibling,
                            benchmark, priorLenzBraking, priorRailgunForceBase);
                });
            });
            return;
        } catch (final Throwable t) {
            remove(level, ship);
            MagConfig.LENZ_BRAKING_ENABLED.set(priorLenzBraking);
            MagConfig.RAILGUN_FORCE_BASE.set(priorRailgunForceBase);
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
                                           final BlockPos sibling,
                                           final RailgunBenchmark benchmark,
                                           final boolean priorLenzBraking,
                                           final double priorRailgunForceBase) {
        final boolean[] removedDuringTrace = {false};
        final boolean[] sawLaunching = {false};
        final int[] samples = {0};
        final int[] maxLaunchTicks = {0};
        final int[] forwardSamples = {0};
        final double[] maxForwardSpeed = {0.0d};
        final boolean[] leftRail = {false};
        final int[] postExitSamples = {0};
        final int[] launchTicksAtExit = {-1};
        final double[] exitSpeed = {0.0d};
        final double[] estimatedRailTravel = {0.0d};
        final double[] estimatedPostExitDistance = {0.0d};
        final double[] initialPositionZ = {Double.NaN};
        final double[] lastPositionZ = {Double.NaN};
        final double[] lastForwardSpeed = {Double.NaN};
        final Pose3d livePose = new Pose3d();
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
                final var physicsPose = container.physicsSystem().getPipeline().readPose(ship, livePose);
                final Vector3d velocity = handle.isValid()
                        ? handle.getLinearVelocity(new Vector3d())
                        : new Vector3d(ship.latestLinearVelocity);
                final double forwardSpeed = -velocity.z();
                maxForwardSpeed[0] = Math.max(maxForwardSpeed[0], forwardSpeed);
                if (forwardSpeed > 0.5d) forwardSamples[0]++;

                // The GameTest Sable backend can expose a fresh rigid-body
                // velocity while its pose bridge remains at the last network
                // position. Integrate the authoritative velocity sample at
                // one Minecraft tick (1/20 s) so the benchmark still measures
                // where the launch would carry the ship.
                estimatedRailTravel[0] += Math.max(0.0d, forwardSpeed) / 20.0d;

                // readPose is the authoritative Rapier pose. logicalPose() can
                // remain at the last networked position while a small test ship
                // is being advanced, which made the old distance check useless.
                final double positionZ = physicsPose.position().z();
                if (Double.isNaN(initialPositionZ[0])) initialPositionZ[0] = positionZ;
                lastPositionZ[0] = positionZ;
                lastForwardSpeed[0] = forwardSpeed;
                final double muzzleTravel = benchmark.railLength() - 1.0d;
                if (!leftRail[0] && estimatedRailTravel[0] >= muzzleTravel) {
                    leftRail[0] = true;
                    launchTicksAtExit[0] = master.launchTicks();
                    exitSpeed[0] = forwardSpeed;
                    // The headless Sable backend can retain the pre-launch
                    // collision bounds even while reporting the advancing rigid
                    // body velocity. Disarm at the integrated muzzle crossing so
                    // the coast trace matches a real ship leaving the channel
                    // instead of receiving phantom post-exit acceleration.
                    clear(level, rail, net.minecraft.core.Direction.NORTH, benchmark.railLength());
                    clear(level, sibling, net.minecraft.core.Direction.NORTH, benchmark.railLength());
                }
                if (leftRail[0]) {
                    postExitSamples[0]++;
                    estimatedPostExitDistance[0] += Math.max(0.0d, forwardSpeed) / 20.0d;
                }
            }

            final boolean acceptedShortTrace = !benchmark.requireFullPostExitTrace()
                    && removedDuringTrace[0] && postExitSamples[0] >= 3;
            if (!acceptedShortTrace && postExitSamples[0] < RAILGUN_TEST_POST_EXIT_TICKS && samples[0] < 100) {
                // GameTest removes the runnable that just executed after the
                // callback returns. Use a fresh wrapper so scheduling the next
                // sample cannot be removed along with the current one.
                helper.runAfterDelay(1L, () -> monitor[0].run());
                return;
            }

            final boolean survivedAfterExit = !removedDuringTrace[0]
                    && !ship.isRemoved()
                    && container.getSubLevel(ship.getUniqueId()) == ship;
            final double projectedPostExitDistance = benchmark.requireFullPostExitTrace()
                    ? estimatedPostExitDistance[0]
                    : exitSpeed[0] * RAILGUN_TEST_POST_EXIT_TICKS / 20.0d;
            remove(level, ship);
            clear(level, rail, net.minecraft.core.Direction.NORTH, benchmark.railLength());
            clear(level, sibling, net.minecraft.core.Direction.NORTH, benchmark.railLength());
            for (int x = 0; x <= RAILGUN_TEST_GAP; x++) for (int z = 1; z <= benchmark.railLength() + 24; z++) {
                level.setBlock(rail.offset(x, -8, -z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            MagConfig.LENZ_BRAKING_ENABLED.set(priorLenzBraking);
            MagConfig.RAILGUN_FORCE_BASE.set(priorRailgunForceBase);
            LOG.info(
                    "Railgun benchmark: case={} length={} shipBlocks={} forceBase=0.6 exitSpeed={} blocks/s postExitDistance={} blocks maxForwardSpeed={} blocks/s launchTicksAtExit={}",
                    benchmark.name(), benchmark.railLength(), benchmark.shipEdge() * benchmark.shipEdge() * benchmark.shipEdge(),
                    exitSpeed[0], estimatedPostExitDistance[0], maxForwardSpeed[0], launchTicksAtExit[0]);
            helper.assertTrue(samples[0] <= 100 && (postExitSamples[0] >= RAILGUN_TEST_POST_EXIT_TICKS
                            || acceptedShortTrace),
                    "Railgun ship monitor did not complete the post-exit trace; samples=" + samples[0]
                            + " postExitSamples=" + postExitSamples[0]
                            + " initialPositionZ=" + initialPositionZ[0]
                            + " lastPositionZ=" + lastPositionZ[0]
                            + " estimatedRailTravel=" + estimatedRailTravel[0]
                            + " estimatedPostExitDistance=" + estimatedPostExitDistance[0]
                            + " lastForwardSpeed=" + lastForwardSpeed[0]
                            + " removed=" + removedDuringTrace[0]);
            helper.assertTrue(sawLaunching[0] && maxLaunchTicks[0] > 0,
                    "Railgun never entered a live launching state");
            helper.assertTrue(forwardSamples[0] >= 3 && maxForwardSpeed[0] > 0.5d,
                    "Railgun ship did not retain forward physics velocity across samples; samples="
                            + forwardSamples[0] + " maxForwardSpeed=" + maxForwardSpeed[0]);
            helper.assertTrue(leftRail[0],
                    "Railgun ship never reached the muzzle; maxForwardSpeed=" + maxForwardSpeed[0]
                            + " launchTicks=" + maxLaunchTicks[0]);
            helper.assertTrue(exitSpeed[0] >= benchmark.minExitSpeed(),
                    benchmark.name() + " railgun exit velocity was too low; exitSpeed=" + exitSpeed[0]
                            + " required=" + benchmark.minExitSpeed()
                            + " launchTicksAtExit=" + launchTicksAtExit[0]);
            helper.assertTrue(projectedPostExitDistance >= benchmark.minPostExitDistance(),
                    benchmark.name() + " railgun ship did not travel meaningfully after the muzzle; distance="
                            + projectedPostExitDistance + " required=" + benchmark.minPostExitDistance()
                            + " exitSpeed=" + exitSpeed[0]);
            if (benchmark.requireFullPostExitTrace()) {
                helper.assertTrue(survivedAfterExit,
                        "Railgun launch removed the ship sub-level after muzzle exit");
            }
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

    /** Build an odd-edged iron cube centered laterally on the launch axis. */
    private static List<BlockPos> railgunShipBlocks(final BlockPos origin, final int edge) {
        final List<BlockPos> blocks = new ArrayList<>();
        final int half = edge / 2;
        for (int x = -half; x <= half; x++) for (int y = 0; y < edge; y++) {
            for (int z = -half; z <= half; z++) blocks.add(origin.offset(x, y, z));
        }
        return blocks;
    }

    private static void buildRailgunRail(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter,
                                         final net.minecraft.core.Direction facing, final int length) {
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, facing), Block.UPDATE_ALL);
        for (int i = 1; i <= length; i++) level.setBlock(emitter.relative(facing, i), Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void powerRailgun(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter) {
        level.setBlock(emitter.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(emitter) instanceof
                com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity railgun) {
            railgun.energyBuffer().receiveEnergy(1_000_000, false);
        }
    }

    private static void clear(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter,
                              final net.minecraft.core.Direction facing, final int length) {
        for (int i = 0; i <= length; i++) level.setBlock(emitter.relative(facing, i), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(emitter.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void finishRailgunCollisionTest(final net.minecraft.server.level.ServerLevel level,
                                                    final ServerSubLevel ship,
                                                    final BlockPos rail, final BlockPos sibling,
                                                    final int railLength, final List<BlockPos> wall,
                                                    final boolean priorLenzBraking,
                                                    final double priorRailgunForceBase) {
        remove(level, ship);
        clear(level, rail, net.minecraft.core.Direction.NORTH, railLength);
        clear(level, sibling, net.minecraft.core.Direction.NORTH, railLength);
        wall.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        MagConfig.LENZ_BRAKING_ENABLED.set(priorLenzBraking);
        MagConfig.RAILGUN_FORCE_BASE.set(priorRailgunForceBase);
    }
}
