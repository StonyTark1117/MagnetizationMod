package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.content.fluid.GalliumRegistry;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world integration tests for behaviour that can't be unit-tested without
 * a real {@link net.minecraft.server.level.ServerLevel}.
 *
 * <p>Run via {@code ./gradlew runGameTestServer}. The
 * {@code neoforge.enabledGameTestNamespaces} system property (configured in
 * {@code build.gradle}) gates discovery; only {@code magnetization}-namespaced
 * tests run by default.
 *
 * <p>Tests share the {@code magnetization:empty} 3×3×3 air template — the
 * structure exists only to give the framework a workspace; the tests place
 * blocks programmatically. {@link PrefixGameTestTemplate}{@code (false)} stops
 * NeoForge from prepending the namespace to the template name.
 *
 * <p><b>Scope</b>: these cover lifecycle + per-tick logic that runs without a
 * Sable sub-level. The Sable-dependent scenarios (multi-ship impulse,
 * excavator block-breaking, magnetic-switch proximity) need programmatic
 * contraption assembly and are deferred — see the test punchlist.
 */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagGameTests {

    /** Template path only — the framework prepends the namespace from the
     *  containing {@link GameTestHolder}, and {@link PrefixGameTestTemplate}
     *  {@code (false)} suppresses the class-name prefix. */
    private static final String EMPTY_TEMPLATE = "empty";

    private static final java.util.concurrent.atomic.AtomicInteger CURIO_SOUND_EVENTS =
            new java.util.concurrent.atomic.AtomicInteger();

    private MagGameTests() {}

    /** Capture the actual server sound event emitted by the shared item path. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void captureCurioActivationSound(
            final net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition event) {
        if (event.getSource() == net.minecraft.sounds.SoundSource.PLAYERS) {
            CURIO_SOUND_EVENTS.incrementAndGet();
        }
    }

    private static int drainPerTickFromConfig() {
        try { return com.stonytark.magnetization.config.MagConfig.EMITTER_ENERGY_DRAIN_PER_TICK.get(); }
        catch (final Throwable t) { return 10; }
    }

    /**
     * Force the emitter power-source config back to its production defaults
     * (either-or power: redstone OR energy runs an emitter). Every test that
     * relies on the default power semantics calls this FIRST so it is immune to
     * config drift — the {@code run/config/magnetization-common.toml} persists
     * across runs and can carry a stale {@code requireRedstoneAndEnergy = true}
     * (left by {@link #requireBothRedstoneAndEnergyGate} or a dev session). A
     * stale {@code true} makes a single-source emitter never power up, so the
     * field/drain/ship tests silently went red despite the features being fine
     * in-world. Forcing defaults here (the values ARE the canonical defaults, so
     * no restore is needed) makes those tests hermetic. See the matching note on
     * {@link #requireBothRedstoneAndEnergyGate}, which runs in its own batch so
     * its deliberate mutation can't bleed into these.
     */
    private static void forceDefaultEmitterPower() {
        com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(false);
        com.stonytark.magnetization.config.MagConfig.ALLOW_REDSTONE_POWER.set(true);
        com.stonytark.magnetization.config.MagConfig.ALLOW_ENERGY_POWER.set(true);
    }

    /** Reset and inspect one criterion without depending on advancement visibility or parent completion. */
    private static void revokeAdvancement(final net.minecraft.server.level.ServerPlayer player,
                                          final String path) {
        final var holder = player.server.getAdvancements().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, path));
        if (holder == null) throw new IllegalStateException("Missing advancement magnetization:" + path);
        final var progress = player.getAdvancements().getOrStartProgress(holder);
        for (final String criterion : progress.getCompletedCriteria()) {
            player.getAdvancements().revoke(holder, criterion);
        }
    }

    private static boolean criterionDone(final net.minecraft.server.level.ServerPlayer player,
                                         final String path, final String criterion) {
        final var holder = player.server.getAdvancements().get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder)
                .getCriterion(criterion) != null
                && player.getAdvancements().getOrStartProgress(holder).getCriterion(criterion).isDone();
    }

    private static net.minecraft.server.level.ServerPlayer headlessAdvancementPlayer(
            final net.minecraft.server.level.ServerLevel level, final String name) {
        final var player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name),
                net.minecraft.server.level.ClientInformation.createDefault());
        final var connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                level.getServer(), connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(final net.minecraft.network.protocol.Packet<?> packet) {
                // Headless GameTest player: advancement/Curios sync has no client.
            }
        };
        player.getAdvancements().reload(level.getServer().getAdvancements());
        level.addFreshEntity(player);
        return player;
    }

    /**
     * Placing an emitter in-world registers it with {@link EmitterRegistry};
     * breaking it unregisters. Catches regressions in the BE.onLoad /
     * setRemoved hooks that unit tests (which can't drive a real chunk-load
     * cycle) miss.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void emitterRegistersAndUnregisters(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.ELECTROMAGNET.get());

        // Run the assertions on a delayed tick so onLoad has fired.
        helper.runAfterDelay(2L, () -> {
            final int afterPlace = EmitterRegistry.size(helper.getLevel());
            helper.assertTrue(afterPlace >= 1,
                    "EmitterRegistry should track the placed electromagnet; size=" + afterPlace);
            final BlockPos absolutePos = helper.absolutePos(pos);
            helper.assertTrue(EmitterRegistry.snapshot(helper.getLevel()).contains(absolutePos),
                    "EmitterRegistry snapshot should include the emitter's world pos");

            helper.setBlock(pos, Blocks.AIR);
            helper.runAfterDelay(2L, () -> {
                helper.assertTrue(!EmitterRegistry.snapshot(helper.getLevel()).contains(absolutePos),
                        "EmitterRegistry should drop the pos after the block is broken");
                helper.succeed();
            });
        });
    }

    /**
     * A powered emitter with energy in its buffer drains energy each tick.
     * Exercises {@link AbstractEmitterBlockEntity#tickEmitter}'s power-source
     * resolution + drain in a real tick cycle — the bit unit tests can't reach
     * because there's no real {@code ServerLevel} to drive {@code serverTick}.
     */
    /**
     * A magnetostrictive sensor emits an analog redstone signal when a living
     * entity moves within range. Regression guard for the bug where it read
     * {@code getDeltaMovement()} (≈0 for players server-side) instead of
     * {@code getKnownMovement()} — which made it appear to do nothing. Uses a
     * mob (whose getKnownMovement == getDeltaMovement) with motion re-applied
     * each tick so the sensor samples a non-zero speed regardless of drag/timing.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "sensorMovement")
    public static void sensorEmitsRedstoneOnMovement(final GameTestHelper helper) {
        final BlockPos sensorPos = new BlockPos(1, 1, 1);
        helper.setBlock(sensorPos, MagBlocks.MAGNETOSTRICTIVE_SENSOR.get());

        // Spawn a cow two blocks away (well within the 8-block range), AI/gravity
        // off so it stays put except for the velocity we inject.
        final net.minecraft.world.entity.animal.Cow cow =
                helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(3, 1, 1));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        final com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity sensor =
                (com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity)
                        helper.getBlockEntity(sensorPos);

        // Re-apply horizontal motion every tick for the first 9 ticks so that,
        // whatever tick the sensor's 2-tick scan lands on, getKnownMovement is
        // non-zero and above the move threshold.
        for (long t = 1; t <= 9; t++) {
            helper.runAfterDelay(t, () -> {
                cow.setDeltaMovement(0.3, 0.0, 0.0);
                com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity.serverTick(
                        helper.getLevel(), helper.absolutePos(sensorPos), sensor.getBlockState(), sensor);
            });
        }

        helper.runAfterDelay(10L, () -> {
            final BlockEntity be = helper.getBlockEntity(sensorPos);
            if (!(be instanceof com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity)) {
                helper.fail("Expected a MagnetostrictiveSensorBlockEntity at " + sensorPos + ", got " + be);
                return;
            }
            helper.assertTrue(sensor.getSignal() > 0,
                    "Sensor should emit redstone for a moving entity in range; signal=" + sensor.getSignal());
            helper.assertTrue(helper.getBlockState(sensorPos)
                            .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED),
                    "Sensor block should be POWERED while emitting");
            helper.succeed();
        });
    }

    /**
     * Lenz braking detects a conductor pad the ship flies <em>over</em>, not just
     * blocks its own hull overlaps. Regression guard for the bug where the scan
     * used only the ship's bounding box, so a ship gliding above a ground copper
     * pad induced nothing and never slowed. The fix scans {@code BELOW_REACH}
     * blocks below the hull; this test calls the scan directly with a hull box
     * floating above placed copper and asserts the pad is counted — and that a
     * hull beyond reach counts nothing.
     *
     * <p>Scope: this proves the conductor-detection scan (the part that was
     * broken). It does not assemble a real Sable ship, so it does not exercise
     * the velocity-drag application — that still needs in-world testing.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void lenzCountsConductorPadBelowHull(final GameTestHelper helper) {
        // A two-block copper pad on the floor.
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.COPPER_BLOCK);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.COPPER_BLOCK);

        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos pad = helper.absolutePos(new BlockPos(1, 1, 1));
        final double cx = pad.getX();
        final double cy = pad.getY();
        final double cz = pad.getZ();

        // Hull box hovering 3 blocks above the pad — within BELOW_REACH (3), so
        // the downward scan (floor(minY) - 3) reaches the copper at cy.
        final dev.ryanhcode.sable.companion.math.BoundingBox3d overPad =
                new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                        cx, cy + 3, cz, cx + 1, cy + 4, cz + 2);
        final int counted = com.stonytark.magnetization.content.effect.LenzBrakingHandler
                .countOverlappingConductors(level, overPad);
        helper.assertTrue(counted >= 2,
                "Lenz scan should detect the copper pad 3 blocks below the hull; counted=" + counted);

        // Hull box 5 blocks up — beyond BELOW_REACH, so the scan stops above the
        // copper and counts nothing. Confirms the reach is bounded, not infinite.
        final dev.ryanhcode.sable.companion.math.BoundingBox3d tooHigh =
                new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                        cx, cy + 5, cz, cx + 1, cy + 6, cz + 2);
        final int countedHigh = com.stonytark.magnetization.content.effect.LenzBrakingHandler
                .countOverlappingConductors(level, tooHigh);
        helper.assertTrue(countedHigh == 0,
                "Lenz scan should not reach a pad beyond BELOW_REACH; counted=" + countedHigh);

        helper.succeed();
    }

    /**
     * End-to-end Lenz braking on a real Sable ship. Two single-block iron ships
     * get the same downward velocity; one falls right beside a copper wall, the
     * other in open air. After they fall, the ship next to the conductor must be
     * moving downward measurably slower — eddy-current drag opposing its motion.
     *
     * <p>This is the test the conductor-scan unit test could not be: it assembles
     * real ships via {@link dev.ryanhcode.sable.api.SubLevelAssemblyHelper}, lets
     * the live {@code LevelTickEvent} handler run, and reads the physics body's
     * velocity — proving drag is actually applied to a ship, not just that the
     * scan finds conductors. Uses a side wall (not a floor pad) to also prove the
     * uniform {@code CONDUCTOR_REACH} brakes a ship flying past a wall.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void lenzBrakesFallingShipBesideCopperWall(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos baseA = helper.absolutePos(new BlockPos(1, 2, 1));
        final BlockPos baseB = baseA.offset(12, 0, 0);
        // Drop the ships in open sky (well above any terrain) so the fall window is
        // never cut short by hitting the ground — that would zero both velocities
        // and erase the difference we're measuring. High Y is in a loaded chunk.
        final BlockPos skyA = new BlockPos(baseA.getX(), 240, baseA.getZ());
        final BlockPos skyB = new BlockPos(baseB.getX(), 240, baseB.getZ());

        helper.runAfterDelay(2L, () -> {
            final dev.ryanhcode.sable.sublevel.ServerSubLevel shipA =
                    assembleSingleBlockShip(level, baseA, Blocks.IRON_BLOCK);
            final dev.ryanhcode.sable.sublevel.ServerSubLevel shipB =
                    assembleSingleBlockShip(level, baseB, Blocks.IRON_BLOCK);
            teleportShip(level, shipA, skyA);
            teleportShip(level, shipB, skyB);

            // Copper wall hugging ship A's east face up in the sky, spanning its
            // fall path. Well over the conductor cap (8) so A gets near-maximal drag.
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -8; dy <= 1; dy++) {
                    level.setBlock(skyA.offset(1, dy, dz),
                            Blocks.COPPER_BLOCK.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }

            helper.runAfterDelay(2L, () -> {
                final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle hA =
                        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(shipA);
                final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle hB =
                        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(shipB);
                if (hA == null || hB == null) {
                    removeShip(level, shipA);   // don't leak the assembled ships on the null path
                    removeShip(level, shipB);
                    helper.fail("Could not obtain physics handles for the assembled ships");
                    return;
                }
                // Same downward kick to both, well above lenzMinSpeed (0.04).
                hA.addLinearAndAngularVelocity(new org.joml.Vector3d(0, -1.5, 0), new org.joml.Vector3d());
                hB.addLinearAndAngularVelocity(new org.joml.Vector3d(0, -1.5, 0), new org.joml.Vector3d());

                helper.runAfterDelay(24L, () -> {
                    final org.joml.Vector3d vA = hA.getLinearVelocity(new org.joml.Vector3d());
                    final org.joml.Vector3d vB = hB.getLinearVelocity(new org.joml.Vector3d());
                    // Both fall (negative y). A is braked, so its downward speed is
                    // smaller → vA.y is the less-negative (greater) of the two.
                    final boolean braked = vA.y > vB.y + 0.1;
                    removeShip(level, shipA);
                    removeShip(level, shipB);
                    helper.assertTrue(braked,
                            "Ship beside the copper wall should fall slower (Lenz drag): "
                                    + "vA.y=" + vA.y + " vB.y=" + vB.y);
                    helper.succeed();
                });
            });
        });
    }

    /** Place a single block at {@code pos}, assemble it into a Sable ship, and
     *  teleport the ship back onto that world position so callers can stage
     *  conductors around it. Mirrors Sable's own AssemblyTest setup. */
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel assembleSingleBlockShip(
            final net.minecraft.server.level.ServerLevel level,
            final BlockPos pos,
            final net.minecraft.world.level.block.Block block) {
        level.setBlock(pos, block.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        final dev.ryanhcode.sable.companion.math.BoundingBox3i bounds =
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                        pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                        level, pos, java.util.List.of(pos), bounds);
        final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        container.physicsSystem().getPipeline().teleport(ship,
                new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                new org.joml.Quaterniond());
        return ship;
    }

    /** Remove an assembled ship from the world. Ship tests MUST call this before
     *  succeeding: leftover sublevels pile up across the suite, bloating every
     *  world autosave, and Sable pauses physics on each save — which throttles the
     *  remaining physics tests to a crawl. Mirrors Sable's own AssemblyTest. */
    private static void removeShip(final net.minecraft.server.level.ServerLevel level,
                                   final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer c =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (c != null && ship != null) {
            c.removeSubLevel(ship, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
    }

    /** Teleport an already-assembled ship to a new world position (identity orientation). */
    private static void teleportShip(final net.minecraft.server.level.ServerLevel level,
                                     final dev.ryanhcode.sable.sublevel.ServerSubLevel ship,
                                     final BlockPos pos) {
        final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        container.physicsSystem().getPipeline().teleport(ship,
                new org.joml.Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                new org.joml.Quaterniond());
    }

    /**
     * #80 — Barkhausen Generator emits a jittering analog redstone signal while a
     * magnet block touches it, and 0 with no magnet. Signal is {@code random(0..15)}
     * every 2 ticks, so we sample many ticks and assert the magnetized generator
     * produced a non-zero reading (and toggled POWERED) at least once, while a bare
     * generator with no adjacent magnet stays flat at 0.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void barkhausenJittersWithAdjacentMagnet(final GameTestHelper helper) {
        final BlockPos withMagnet = new BlockPos(1, 1, 1);
        final BlockPos noMagnet = new BlockPos(1, 1, 2);
        helper.setBlock(withMagnet, MagBlocks.BARKHAUSEN.get());
        helper.setBlock(new BlockPos(1, 1, 0), MagBlocks.PERMANENT_MAGNET.get()); // touches the first generator
        helper.setBlock(noMagnet, MagBlocks.BARKHAUSEN.get());                     // no magnet anywhere near

        final com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity beMag =
                (com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity) helper.getBlockEntity(withMagnet);
        final com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity beBare =
                (com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity) helper.getBlockEntity(noMagnet);

        final int[] maxMagSignal = {0};
        final boolean[] sawPowered = {false};
        final int[] maxBareSignal = {0};
        for (long t = 1; t <= 40; t++) {
            helper.runAfterDelay(t, () -> {
                com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity.serverTick(
                        helper.getLevel(), helper.absolutePos(withMagnet), beMag.getBlockState(), beMag);
                com.stonytark.magnetization.content.sensor.BarkhausenBlockEntity.serverTick(
                        helper.getLevel(), helper.absolutePos(noMagnet), beBare.getBlockState(), beBare);
                maxMagSignal[0] = Math.max(maxMagSignal[0], beMag.getSignal());
                maxBareSignal[0] = Math.max(maxBareSignal[0], beBare.getSignal());
                if (helper.getBlockState(withMagnet)
                        .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                    sawPowered[0] = true;
                }
            });
        }
        helper.runAfterDelay(42L, () -> {
            helper.assertTrue(maxMagSignal[0] > 0,
                    "Magnetized Barkhausen should emit a non-zero signal across the sample window; max="
                            + maxMagSignal[0]);
            helper.assertTrue(sawPowered[0], "Magnetized Barkhausen should toggle POWERED true at least once");
            helper.assertTrue(maxBareSignal[0] == 0,
                    "Barkhausen with no adjacent magnet must stay at 0; max=" + maxBareSignal[0]);
            helper.succeed();
        });
    }

    /**
     * #85 — Magnetic anvil dampener detection. The break-chance override keys off
     * {@link com.stonytark.magnetization.content.AnvilDampenerHandler#hasAdjacentDampener}:
     * an anvil with a dampener magnet orthogonally adjacent has its break chance
     * forced to 0. We test that pure check directly (no anvil GUI needed) and
     * sanity-check the per-metal config defaults.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void anvilDampenerDetectedWhenMagnetAdjacent(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos anvil = new BlockPos(1, 1, 1);
        helper.setBlock(anvil, MagBlocks.MAGNETITE_ANVIL.get());
        final BlockPos anvilAbs = helper.absolutePos(anvil);

        helper.assertTrue(!com.stonytark.magnetization.content.AnvilDampenerHandler.hasAdjacentDampener(level, anvilAbs),
                "No dampener adjacent yet → should be false");

        helper.setBlock(new BlockPos(1, 1, 0), MagBlocks.MAGNETITE_BLOCK.get()); // magnetite block is a dampener
        helper.assertTrue(com.stonytark.magnetization.content.AnvilDampenerHandler.hasAdjacentDampener(level, anvilAbs),
                "Magnetite block adjacent → dampener should be detected");

        // Per-metal defaults: titanomagnetite never breaks; magnetite has a real chance.
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.anvilBreakTitanomagnetite() == 0.0f,
                "Titanomagnetite anvil break chance should default to 0");
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.anvilBreakMagnetite() > 0.0f,
                "Magnetite anvil break chance should default above 0");
        helper.succeed();
    }

    /**
     * #90 — Tokamak generates FE when its 8-coil ring is complete and a Deuterium
     * Cell is loaded. Builds the ring, loads fuel via the controller's fuel
     * container, ticks, and asserts the buffer charges and the block lights.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void tokamakGeneratesWithRingAndFuel(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, MagBlocks.TOKAMAK_CONTROLLER.get());
        // 8-coil ring on the controller's Y layer.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                helper.setBlock(controller.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
            }
        }
        final com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity be =
                (com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity) helper.getBlockEntity(controller);
        be.fuelContainer().setItem(0,
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.DEUTERIUM_CELL.get()));

        helper.assertTrue(com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity
                        .isRingFormed(helper.getLevel(), helper.absolutePos(controller)),
                "Ring of 8 coils should read as formed");

        helper.succeedWhen(() -> {
            com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(controller), be.getBlockState(), be);
            helper.assertTrue(be.energyBuffer().getEnergyStored() > 0,
                    "Tokamak should charge its buffer with a ring + fuel; FE=" + be.energyBuffer().getEnergyStored());
            helper.assertTrue(helper.getBlockState(controller)
                            .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
                    "Tokamak should be LIT while fusing");
        });
    }

    /**
     * #122 — Electrolyzer converts water + FE into hydrogen; with no FE it idles.
     * Fills the water tank and energy buffer directly, ticks, and asserts hydrogen
     * accumulated while water drained; a second unit with water but no FE makes none.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void electrolyzerMakesHydrogen(final GameTestHelper helper) {
        final BlockPos powered = new BlockPos(1, 1, 1);
        final BlockPos starved = new BlockPos(1, 1, 3);
        helper.setBlock(powered, MagBlocks.ELECTROLYZER.get());
        helper.setBlock(starved, MagBlocks.ELECTROLYZER.get());

        final com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity poweredBe =
                (com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity) helper.getBlockEntity(powered);
        final com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity starvedBe =
                (com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity) helper.getBlockEntity(starved);

        final net.neoforged.neoforge.fluids.FluidStack water =
                new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 4000);
        poweredBe.fluidHandler().fill(water.copy(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        poweredBe.energyBuffer().receiveEnergy(8000, false);    // fuel
        starvedBe.fluidHandler().fill(water.copy(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        // starvedBe gets NO energy — negative control.

        final int startWater = poweredBe.waterAmount();
        helper.succeedWhen(() -> {
            com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(powered), poweredBe.getBlockState(), poweredBe);
            com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(starved), starvedBe.getBlockState(), starvedBe);
            helper.assertTrue(poweredBe.hydrogenAmount() > 0,
                    "Electrolyzer with water + FE should produce hydrogen; got " + poweredBe.hydrogenAmount());
            helper.assertTrue(poweredBe.waterAmount() < startWater,
                    "Electrolyzer should consume water while running");
            helper.assertTrue(starvedBe.hydrogenAmount() == 0,
                    "Electrolyzer with no FE should produce no hydrogen; got " + starvedBe.hydrogenAmount());
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fusionGasesRise(final GameTestHelper helper) {
        final BlockPos[] sources = {
                new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(1, 1, 2)
        };
        final net.minecraft.world.level.block.Block[] gases = {
                MagBlocks.HYDROGEN_BLOCK.get(), MagBlocks.TRITIUM_BLOCK.get(), MagBlocks.HELIUM_3_BLOCK.get()
        };
        for (int i = 0; i < sources.length; i++) {
            helper.setBlock(sources[i], gases[i]);
            helper.setBlock(sources[i].above(2), Blocks.GLASS);
            scheduleFluidTick(helper, sources[i]);
        }
        helper.runAfterDelay(40L, () -> {
            for (int i = 0; i < sources.length; i++) {
                helper.assertTrue(helper.getBlockState(sources[i].above()).is(gases[i]),
                        "Gas should rise into the space below the ceiling: " + gases[i]);
                helper.assertTrue(!helper.getBlockState(sources[i].below()).is(gases[i]),
                        "Gas must not flow downward: " + gases[i]);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fusionGasSourceRisesWithoutPyramid(final GameTestHelper helper) {
        final BlockPos source = new BlockPos(1, 1, 1);
        final net.minecraft.world.level.block.Block gas = MagBlocks.HYDROGEN_BLOCK.get();
        helper.setBlock(source, gas);
        scheduleFluidTick(helper, source);
        tickFluidNow(helper, source);
        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(helper.getBlockState(source).getFluidState().isEmpty(),
                    "An unobstructed gas source must leave its old position");
            boolean sourceMovedUp = false;
            for (int y = 2; y <= 20; y++) {
                if (helper.getBlockState(new BlockPos(source.getX(), y, source.getZ()))
                        .getFluidState().isSource()) {
                    sourceMovedUp = true;
                    break;
                }
            }
            helper.assertTrue(sourceMovedUp, "An unobstructed gas source should float upward as a source");
            helper.assertTrue(helper.getBlockState(source.below()).getFluidState().isEmpty(),
                    "An unobstructed gas source must not flow downward");
            for (final net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                helper.assertTrue(helper.getBlockState(source.relative(direction)).getFluidState().isEmpty(),
                        "An unobstructed gas source must not spread sideways: " + direction);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fusionGasesDespawnAtSkyLimit(final GameTestHelper helper) {
        final BlockPos structureOrigin = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos skyLimit = new BlockPos(structureOrigin.getX(),
                helper.getLevel().getMaxBuildHeight() - 1, structureOrigin.getZ());
        final net.minecraft.world.level.block.Block[] gases = {
                MagBlocks.HYDROGEN_BLOCK.get(), MagBlocks.TRITIUM_BLOCK.get(), MagBlocks.HELIUM_3_BLOCK.get()
        };
        for (int i = 0; i < gases.length; i++) {
            final BlockPos pos = skyLimit.offset(i, 0, 0);
            helper.getLevel().setBlock(pos, gases[i].defaultBlockState(), 3);
            scheduleFluidTick(helper.getLevel(), pos);
            tickFluidNow(helper.getLevel(), pos);
        }
        helper.runAfterDelay(20L, () -> {
            for (int i = 0; i < gases.length; i++) {
                helper.assertTrue(helper.getLevel().getBlockState(skyLimit.offset(i, 0, 0)).isAir(),
                        "Gas should despawn at the sky limit: " + gases[i]);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 180)
    public static void fusionGasCeilingFlowDoesNotFillSky(final GameTestHelper helper) {
        final BlockPos source = new BlockPos(0, 1, 1);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 3; y <= 100; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.GLASS);
        helper.setBlock(source, MagBlocks.HYDROGEN_BLOCK.get());
        scheduleFluidTick(helper, source);
        helper.runAfterDelay(120L, () -> {
            int waterfallCells = 0;
            for (int y = 2; y <= 20; y++) {
                for (int x = 0; x <= 2; x++) {
                    for (int z = 0; z <= 2; z++) {
                        if (helper.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
                            continue;
                        }
                        waterfallCells++;
                        // The supported cell at (1,1,1) has three open edges.
                        // Each may make one vertical waterfall, but gas may not
                        // occupy the interior sky or another horizontal ring.
                        final boolean expectedColumn = (x == 2 && z == 1)
                                || (x == 1 && z == 0)
                                || (x == 1 && z == 2);
                        helper.assertTrue(expectedColumn,
                                "Ceiling flow escaped its edge waterfalls at " + x + "," + y + "," + z);
                    }
                }
            }
            helper.assertTrue(waterfallCells > 0,
                    "Gas should form bounded upward waterfalls at the ceiling perimeter");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 140)
    public static void fusionGasFollowsCeilingAndTurnsUpAtEdge(final GameTestHelper helper) {
        final BlockPos source = new BlockPos(0, 1, 1);
        helper.setBlock(source, MagBlocks.HYDROGEN_BLOCK.get());
        // The source is blocked immediately. Flow crosses the supported west cell,
        // then turns upward at the unsupported edge, all within the 3x3 template.
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.AIR);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 3; y <= 100; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        scheduleFluidTick(helper, source);
        // Seed the initial production-fluid ticks synchronously through the
        // minimum asserted waterfall height. A
        // clean, heavily loaded GameTest server can defer the scheduled tick
        // queue long enough that the fixed observation window sees only the
        // source even though the flow implementation is correct.
        tickFluidNow(helper, source);
        tickFluidNow(helper, new BlockPos(1, 1, 1));
        tickFluidNow(helper, new BlockPos(2, 1, 1));
        for (int y = 2; y <= 8; y++) {
            tickFluidNow(helper, new BlockPos(2, y, 1));
        }
        final int[] previousWaterfallTop = {0};
        for (long tick = 30L; tick <= 100L; tick += 10L) {
            final long checkTick = tick;
            helper.runAfterDelay(checkTick, () -> {
                int waterfallTop = 0;
                for (int y = 1; y <= 100; y++) {
                    if (!helper.getBlockState(new BlockPos(2, y, 1)).getFluidState().isEmpty()) {
                        waterfallTop = y;
                    }
                }
                helper.assertTrue(waterfallTop >= previousWaterfallTop[0],
                        "Waterfall retreated between checks at tick " + checkTick
                                + "; previous top=" + previousWaterfallTop[0]
                                + ", current top=" + waterfallTop);
                for (int y = 1; y <= waterfallTop; y++) {
                    helper.assertTrue(!helper.getBlockState(new BlockPos(2, y, 1))
                                    .getFluidState().isEmpty(),
                            "Waterfall column has a transient gap at tick " + checkTick + ", Y=" + y);
                    helper.assertTrue(helper.getBlockState(new BlockPos(2, y, 0))
                                    .getFluidState().isEmpty()
                                    && helper.getBlockState(new BlockPos(2, y, 2))
                                    .getFluidState().isEmpty(),
                            "Waterfall transiently fans sideways at tick " + checkTick + ", Y=" + y);
                }
                previousWaterfallTop[0] = waterfallTop;
            });
        }
        helper.runAfterDelay(100L, () -> {
            helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1))
                            .is(MagBlocks.HYDROGEN_BLOCK.get()),
                    "Gas should flow horizontally beneath a supporting ceiling; got "
                            + helper.getBlockState(new BlockPos(1, 1, 1))
                            + ", source=" + helper.getBlockState(source));
            boolean waterfall = false;
            int waterfallY = -1;
            for (int y = 2; y <= 100; y++) {
                if (!helper.getBlockState(new BlockPos(2, y, 1)).getFluidState().isEmpty()) {
                    waterfall = true;
                    waterfallY = y;
                }
            }
            helper.assertTrue(waterfall,
                    "Gas should spill upward at the ceiling edge instead of stopping; "
                            + "supported=" + helper.getBlockState(new BlockPos(1, 1, 1))
                            + ", edge=" + helper.getBlockState(new BlockPos(2, 1, 1))
                            + ", edgeAbove=" + helper.getBlockState(new BlockPos(2, 2, 1))
                            + ", edgeNext=" + helper.getBlockState(new BlockPos(2, 3, 1)));
            helper.assertTrue(waterfallY >= 9,
                    "Gas waterfall should keep moving upward beyond the edge; found at Y=" + waterfallY);
            for (int y = 1; y <= waterfallY; y++) {
                helper.assertTrue(!helper.getBlockState(new BlockPos(2, y, 1)).getFluidState().isEmpty(),
                        "Gas waterfall must be continuous; missing column cell at Y=" + y);
            }
            for (int y = 1; y <= 100; y++) {
                helper.assertTrue(helper.getBlockState(new BlockPos(2, y, 0)).getFluidState().isEmpty()
                                && helper.getBlockState(new BlockPos(2, y, 2)).getFluidState().isEmpty(),
                        "Waterfall must not fan sideways into open air at Y=" + y);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 260)
    public static void fusionGasWaterfallRetractsWhenFeedIsRemoved(final GameTestHelper helper) {
        final BlockPos source = new BlockPos(0, 1, 1);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                for (int y = 3; y <= 100; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(new BlockPos(0, 2, 1), Blocks.GLASS);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.GLASS);
        helper.setBlock(source, MagBlocks.HYDROGEN_BLOCK.get());
        scheduleFluidTick(helper, source);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    int waterfallTop = 0;
                    for (int y = 1; y <= 30; y++) {
                        if (!helper.getBlockState(new BlockPos(2, y, 1)).getFluidState().isEmpty()) {
                            waterfallTop = y;
                        }
                    }
                    helper.assertTrue(waterfallTop >= 3,
                            "Precondition failed: expected a developed waterfall before removing its source; top="
                                    + waterfallTop);
                })
                .thenExecute(() -> helper.setBlock(source, Blocks.AIR))
                .thenWaitUntil(() -> {
                    for (int x = 0; x <= 2; x++) {
                        for (int y = 1; y <= 30; y++) {
                            for (int z = 0; z <= 2; z++) {
                                helper.assertTrue(helper.getBlockState(new BlockPos(x, y, z))
                                                .getFluidState().isEmpty(),
                                        "An unpowered waterfall left orphaned gas at "
                                                + x + "," + y + "," + z);
                            }
                        }
                    }
                })
                .thenSucceed();
    }

    /** GameTest's direct block placement is not guaranteed to enqueue a liquid
     *  block's first scheduled tick. Seed that one production tick explicitly;
     *  every flowing cell created by the gas schedules its own follow-up normally. */
    private static void scheduleFluidTick(final GameTestHelper helper, final BlockPos relativePos) {
        scheduleFluidTick(helper.getLevel(), helper.absolutePos(relativePos));
    }

    private static void tickFluidNow(final GameTestHelper helper, final BlockPos relativePos) {
        tickFluidNow(helper.getLevel(), helper.absolutePos(relativePos));
    }

    private static void tickFluidNow(final net.minecraft.server.level.ServerLevel level,
                                     final BlockPos absolutePos) {
        final net.minecraft.world.level.material.FluidState fluid = level.getFluidState(absolutePos);
        if (!fluid.isEmpty()) {
            fluid.tick(level, absolutePos);
        }
    }

    private static void scheduleFluidTick(final net.minecraft.server.level.ServerLevel level,
                                          final BlockPos absolutePos) {
        final net.minecraft.world.level.material.FluidState fluid = level.getFluidState(absolutePos);
        if (!fluid.isEmpty()) {
            level.scheduleTick(absolutePos, fluid.getType(), 1);
        }
    }

    /**
     * #91 — MR Fluid hardens to a solid block when inside an active magnetic field.
     * Places an MR-fluid source beside a redstone-powered electromagnet and asserts
     * the fluid cell becomes {@code hardened_mr_fluid} once the field reaches it.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void mrFluidHardensInField(final GameTestHelper helper) {
        forceDefaultEmitterPower();                                              // config-drift guard
        helper.setBlock(new BlockPos(1, 1, 1), MagBlocks.ELECTROMAGNET.get());
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.REDSTONE_BLOCK);            // powers the electromagnet
        final BlockPos fluid = new BlockPos(2, 1, 1);
        helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);                     // floor so the source stays put
        helper.setBlock(fluid, MagBlocks.MR_FLUID_BLOCK.get());

        helper.runAfterDelay(40L, () -> {
            final net.minecraft.world.level.block.Block here = helper.getBlockState(fluid).getBlock();
            helper.assertTrue(here == MagBlocks.HARDENED_MR_FLUID.get(),
                    "MR fluid in an active field should harden to hardened_mr_fluid; got "
                            + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(here));
            helper.succeed();
        });
    }

    /**
     * #99 — Conductive fluids carry redstone like dust, with 1-level attenuation
     * per cell; deuterium oxide does not (negative control). A redstone block feeds
     * a 2-cell gallium chain (far cell must read powered) and a 2-cell deuterium
     * chain (far cell must have no power property at all).
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80)
    public static void conductiveFluidsCarryRedstone(final GameTestHelper helper) {
        // Gallium chain: redstone(0,1,0) → gallium(1,1,0) → gallium(2,1,0)
        for (int x = 0; x <= 2; x++) helper.setBlock(new BlockPos(x, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 0), MagBlocks.GALLIUM_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 0), MagBlocks.GALLIUM_BLOCK.get());
        // Deuterium chain (control): redstone(0,1,2) → d2o(1,1,2) → d2o(2,1,2)
        for (int x = 0; x <= 2; x++) helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 2), MagBlocks.DEUTERIUM_OXIDE_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 2), MagBlocks.DEUTERIUM_OXIDE_BLOCK.get());
        // Place the sources last so the conduction network recomputes with them present.
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(4L, () -> {
            final net.minecraft.world.level.block.state.BlockState galliumFar = helper.getBlockState(new BlockPos(2, 1, 0));
            helper.assertTrue(galliumFar.hasProperty(com.stonytark.magnetization.content.fluid.FluidRedstone.POWER)
                            && galliumFar.getValue(com.stonytark.magnetization.content.fluid.FluidRedstone.POWER) > 0,
                    "Gallium 2 cells from a redstone source should be powered; state=" + galliumFar);

            final net.minecraft.world.level.block.state.BlockState d2oFar = helper.getBlockState(new BlockPos(2, 1, 2));
            helper.assertTrue(!d2oFar.hasProperty(com.stonytark.magnetization.content.fluid.FluidRedstone.POWER),
                    "Deuterium oxide must NOT conduct (no signal_power property); state=" + d2oFar);
            helper.succeed();
        });
    }

    /**
     * #79 (GUI) — the sensor's range knob, driven through the shared emitter menu.
     * Constructs an {@link com.stonytark.magnetization.menu.EmitterMenu} with only
     * the range cap on a real sensor and clicks the +/- buttons, asserting the
     * per-block override moves and clamps to {@code [1, sensorMaxRange]}. Verifies
     * the menu↔BlockEntity range wiring without rendering a screen.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void sensorRangeGuiClampsToConfig(final GameTestHelper helper) {
        final BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, MagBlocks.MAGNETOSTRICTIVE_SENSOR.get());
        final BlockPos pos = helper.absolutePos(rel);
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);

        final com.stonytark.magnetization.menu.EmitterMenu menu =
                new com.stonytark.magnetization.menu.EmitterMenu(1, player.getInventory(),
                        net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos), pos,
                        com.stonytark.magnetization.menu.EmitterMenu.CAP_RANGE);

        final com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity be =
                (com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity) helper.getBlockEntity(rel);
        final int max = com.stonytark.magnetization.config.MagConfig.sensorMaxRange();

        // One increase from the untouched default (8) → 9.
        menu.clickMenuButton(player, com.stonytark.magnetization.menu.EmitterMenu.BUTTON_RANGE_INC);
        helper.assertTrue(be.getRangeOverride() == be.defaultRangeBlocks() + 1,
                "First +1 should step off the default; override=" + be.getRangeOverride());

        // Spam increase → clamps at the admin max.
        for (int i = 0; i < 100; i++) menu.clickMenuButton(player, com.stonytark.magnetization.menu.EmitterMenu.BUTTON_RANGE_INC);
        helper.assertTrue(be.getRangeOverride() == max,
                "Range should clamp up to sensorMaxRange (" + max + "); override=" + be.getRangeOverride());

        // Spam decrease → clamps at the 1-block floor.
        for (int i = 0; i < 100; i++) menu.clickMenuButton(player, com.stonytark.magnetization.menu.EmitterMenu.BUTTON_RANGE_DEC);
        helper.assertTrue(be.getRangeOverride() == 1,
                "Range should clamp down to the 1-block floor; override=" + be.getRangeOverride());
        helper.assertTrue(be.effectiveRange() == 1.0,
                "effectiveRange should follow the override; got " + be.effectiveRange());
        helper.succeed();
    }

    /**
     * #81 — Kinetic Coil generates FE when a magnetic ship moves past it within
     * range above the speed threshold. Assembles a ship beside the coil, gives it
     * a steady drift, and asserts the coil's buffer charges.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void kineticCoilGeneratesFromPassingShip(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos coil = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        level.setBlock(coil, MagBlocks.KINETIC_COIL.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        helper.runAfterDelay(2L, () -> {
            final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                    assembleSingleBlockShip(level, helper.absolutePos(new BlockPos(1, 1, 1)), Blocks.IRON_BLOCK);
            teleportShip(level, ship, coil.offset(2, 0, 0)); // 2 blocks away, inside RANGE (4)
            helper.runAfterDelay(2L, () -> {
                final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle h =
                        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
                if (h == null) { removeShip(level, ship); helper.fail("no ship handle"); return; }
                h.addLinearAndAngularVelocity(new org.joml.Vector3d(0, 0, 0.15), new org.joml.Vector3d()); // drift past, > MIN_SPEED
                helper.succeedWhen(() -> {
                    final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(coil);
                    if (!(be instanceof com.stonytark.magnetization.content.induction.KineticCoilBlockEntity kc)) {
                        removeShip(level, ship); helper.fail("no coil BE"); return;
                    }
                    com.stonytark.magnetization.content.induction.KineticCoilBlockEntity.serverTick(
                            level, coil, kc.getBlockState(), kc);
                    final int fe = kc.energyBuffer().getEnergyStored();
                    helper.assertTrue(fe > 0,
                            "Kinetic coil should generate FE from a passing ship; FE=" + fe);
                    removeShip(level, ship);
                });
            });
        });
    }

    /**
     * #123 — Fusion Thruster panel forms: a 5×3×1 panel (Tokamak-Coil ring + 3
     * NORTH-facing interior cells) validates, reports interiorCount==3 and the
     * deterministic master (min by y,x,z), and — being off-ship — never lights.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void fusionThrusterPanelFormsAndFires(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos base = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());     // open sky, no arena clamp
        buildFusionPanel(level, base);

        final BlockPos start = base.offset(1, 1, 0);                          // an interior cell
        final FusionThrusterPanelResult r = validateFusionPanel(level, start);
        helper.assertTrue(r.valid, "5×3 panel should validate; got invalid");
        helper.assertTrue(r.interiorCount == 3, "interiorCount should be 3; got " + r.interiorCount);
        helper.assertTrue(start.equals(r.master),
                "master should be the min-(y,x,z) interior " + start + "; got " + r.master);

        if (!(level.getBlockEntity(start) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity master)) {
            helper.fail("no fusion master BE at " + start); return;
        }
        final net.minecraft.server.level.ServerPlayer nearby = headlessAdvancementPlayer(level, "fusion-advancement");
        nearby.teleportTo(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
        revokeAdvancement(nearby, "formed_fusion_thruster");
        // Drive the master's tick directly: this open-sky panel isn't in a gametest
        // ticking region, so call serverTick explicitly (deterministic) to run the
        // real validate → cache → tank-scale path the world ticker runs in-world.
        com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(
                level, start, level.getBlockState(start), master);

        helper.assertTrue(master.formed() && master.interiorCount() == 3,
                "master should validate the panel; formed=" + master.formed()
                        + " interiorCount=" + master.interiorCount());
        helper.assertTrue(criterionDone(nearby, "formed_fusion_thruster", "formed"),
                "Forming a real Fusion Thruster panel should award its advancement criterion");
        nearby.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        // Tank capacity scales with the interior-block count (per-cell × 3 here).
        final int expected = com.stonytark.magnetization.config.MagConfig.fusionThrusterTank() * 3;
        final int cap = master.fluidHandler().getTankCapacity(0);
        helper.assertTrue(cap == expected,
                "Panel fuel tank should scale to per-cell × 3 interiors = " + expected + "; got " + cap);

        // Off-ship: the master never fires, so no interior is LIT.
        boolean anyLit = false;
        for (int x = 1; x <= 3; x++) {
            final net.minecraft.world.level.block.state.BlockState s = level.getBlockState(base.offset(x, 1, 0));
            anyLit |= s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                    && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        }
        helper.assertTrue(!anyLit, "Off-ship fusion thruster must stay dark (no host to thrust)");
        clearFusionPanel(level, base);
        helper.succeed();
    }

    /**
     * Build-preview diagnostic: a valid panel reports its deterministic master,
     * facing, dimensions, and complete frame; removing one coil identifies that
     * exact edge as invalid without mutating the world.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "fusionPreview")
    public static void fusionPanelPreviewIdentifiesInvalidFrame(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 240, abs.getZ());
        buildFusionPanel(level, base);
        final BlockPos start = base.offset(1, 1, 0);
        final var valid = com.stonytark.magnetization.content.jet.FusionThrusterPanel.preview(
                level, start, net.minecraft.core.Direction.NORTH, 10);
        helper.assertTrue(valid.valid(), "Preview should accept the complete panel");
        helper.assertTrue(valid.master().equals(start), "Preview master should be " + start
                + "; got " + valid.master());
        helper.assertTrue(valid.panelWidth() == 5 && valid.panelHeight() == 3,
                "Preview dimensions should be 5x3; got " + valid.panelWidth() + "x" + valid.panelHeight());
        final BlockPos missing = base.offset(0, 0, 0);
        level.setBlock(missing, Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        final var broken = com.stonytark.magnetization.content.jet.FusionThrusterPanel.preview(
                level, start, net.minecraft.core.Direction.NORTH, 10);
        helper.assertTrue(!broken.valid(), "Preview should reject a missing frame edge");
        helper.assertTrue(broken.invalidEdge().contains(missing),
                "Preview should mark missing frame edge " + missing + "; got " + broken.invalidEdge());
        clearFusionPanel(level, base);
        helper.succeed();
    }

    /**
     * #123 — Fusion Thruster thrusts a ship: build the panel, pre-load the master's
     * Helium-3 tank + FE (NBT carries through assembly), assemble the whole panel
     * into a Sable ship in open sky, tick, and assert the rigid body gained velocity
     * opposite the exhaust-facing panel face (FACING = NORTH, thrust = SOUTH).
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "shipAccel")
    public static void fusionThrusterPanelThrustsShip(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 240, abs.getZ());
        buildFusionPanel(level, base);

        // Pre-load the master interior (min y,x,z) with Helium-3 + FE before assembly.
        final BlockPos masterPos = base.offset(1, 1, 0);
        if (!(level.getBlockEntity(masterPos)
                instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity master)) {
            helper.fail("no fusion thruster BE at master pos"); return;
        }
        master.fluidHandler().fill(new net.neoforged.neoforge.fluids.FluidStack(
                com.stonytark.magnetization.registry.MagFluids.HELIUM_3.get(), 16_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        master.energyBuffer().receiveEnergy(2_000_000, false);

        // Assemble the whole 5×3 panel into one ship.
        final java.util.List<BlockPos> all = new java.util.ArrayList<>();
        for (int x = 0; x <= 4; x++) for (int y = 0; y <= 2; y++) all.add(base.offset(x, y, 0));
        final dev.ryanhcode.sable.companion.math.BoundingBox3i bounds =
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                        base.getX(), base.getY(), base.getZ(),
                        base.getX() + 5, base.getY() + 3, base.getZ() + 1);
        final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, masterPos, all, bounds);
        teleportShip(level, ship, new BlockPos(base.getX(), 245, base.getZ()));

        // Short window (like the railgun ship test): long enough for the thruster to
        // fire, short enough that gravity doesn't drop the ship out of the loaded
        // region (which invalidates the handle and zeroes the reading).
        helper.runAfterDelay(20L, () -> {
            final dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle h =
                    dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            if (h == null) { removeShip(level, ship); helper.fail("no ship handle"); return; }
            final org.joml.Vector3dc v = h.getLinearVelocity();
            final double vx = v.x(), vy = v.y(), vz = v.z();
            final net.minecraft.world.phys.Vec3 expectedWorld = ship.logicalPose().transformNormal(
                    new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0)).normalize();
            final double reactionSpeed = vx * expectedWorld.x + vy * expectedWorld.y + vz * expectedWorld.z;
            removeShip(level, ship);
            // FACING=NORTH is the exhaust direction. Transform local SOUTH through
            // the assembled ship pose and require positive velocity along it; this
            // catches a sign reversal even when Sable rotates local X/Z in world space.
            helper.assertTrue(reactionSpeed > 0.02,
                    "Fusion thruster should accelerate opposite its exhaust face; reactionSpeed="
                            + reactionSpeed + " expectedWorld=" + expectedWorld
                            + " v=(" + vx + "," + vy + "," + vz + ")");
            helper.succeed();
        });
    }

    /** Build a 5(x)×3(y)×1(z) fusion panel at {@code base}: 3 NORTH interior cells
     *  in the middle row, Tokamak-Coil ring around them, all sharing base.z. */
    private static void buildFusionPanel(final net.minecraft.server.level.ServerLevel level, final BlockPos base) {
        final net.minecraft.world.level.block.state.BlockState interior =
                MagBlocks.FUSION_THRUSTER.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.NORTH);
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 2; y++) {
                final boolean isInterior = (y == 1 && x >= 1 && x <= 3);
                level.setBlock(base.offset(x, y, 0),
                        isInterior ? interior : MagBlocks.TOKAMAK_COIL.get().defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
    }

    private static void clearFusionPanel(final net.minecraft.server.level.ServerLevel level, final BlockPos base) {
        for (int x = 0; x <= 4; x++) for (int y = 0; y <= 2; y++) {
            level.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /** Put a test stack into an actual Curios slot without bypassing the
     * capability lookup used by {@code UseCurioPayload}. */
    private static boolean placeCurio(final net.minecraft.server.level.ServerPlayer player,
                                      final net.minecraft.world.item.ItemStack stack) {
        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
        final var handler = player.getCapability(
                top.theillusivec4.curios.api.CuriosCapability.INVENTORY);
        if (handler == null || handler.getCurios() == null) return false;
        for (final var entry : handler.getCurios().entrySet()) {
            final var stacks = entry.getValue().getStacks();
            if (stacks.getSlots() <= 0) continue;
            stacks.setStackInSlot(0, stack);
            return true;
        }
        return false;
    }

    /** Small struct mirroring the panel validator's result for the gametest. */
    private record FusionThrusterPanelResult(boolean valid, int interiorCount, BlockPos master) {}

    private static FusionThrusterPanelResult validateFusionPanel(
            final net.minecraft.server.level.ServerLevel level, final BlockPos start) {
        final com.stonytark.magnetization.content.jet.FusionThrusterPanel.Result r =
                com.stonytark.magnetization.content.jet.FusionThrusterPanel.validate(
                        level, start, net.minecraft.core.Direction.NORTH, 10);
        return new FusionThrusterPanelResult(r.valid(), r.interiorCount(), r.master());
    }

    /**
     * #124 — Railgun walks its rail: an emitter + a 6-block copper rail reports
     * railLength==6 via the handler's pure walk.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "railgunLengthLimit")
    public static void railgunDetectsRailLength(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos emitter = new BlockPos(abs.getX(), 240, abs.getZ());
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.NORTH),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        for (int i = 1; i <= 6; i++) {
            level.setBlock(emitter.relative(net.minecraft.core.Direction.NORTH, i),
                    Blocks.COPPER_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        final boolean priorLimitEnabled = MagConfig.RAILGUN_LENGTH_LIMIT_ENABLED.get();
        final int priorMaxLength = MagConfig.RAILGUN_MAX_LENGTH.get();
        final int unlimitedLength;
        final int limitedLength;
        try {
            MagConfig.RAILGUN_LENGTH_LIMIT_ENABLED.set(false);
            unlimitedLength = com.stonytark.magnetization.content.railgun.RailgunHandler.walkRail(
                    level, emitter, net.minecraft.core.Direction.NORTH);
            MagConfig.RAILGUN_MAX_LENGTH.set(4);
            MagConfig.RAILGUN_LENGTH_LIMIT_ENABLED.set(true);
            limitedLength = com.stonytark.magnetization.content.railgun.RailgunHandler.walkRail(
                    level, emitter, net.minecraft.core.Direction.NORTH);
        } finally {
            MagConfig.RAILGUN_LENGTH_LIMIT_ENABLED.set(priorLimitEnabled);
            MagConfig.RAILGUN_MAX_LENGTH.set(priorMaxLength);
            // Clean up the sky blocks.
            level.setBlock(emitter, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            for (int i = 1; i <= 6; i++) {
                level.setBlock(emitter.relative(net.minecraft.core.Direction.NORTH, i),
                        Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
        helper.assertTrue(unlimitedLength == 6,
                "Disabled optional limit should detect all 6 rail blocks; got " + unlimitedLength);
        helper.assertTrue(limitedLength == 4,
                "Enabled optional limit should cap a 6-block rail at 4 effective blocks; got " + limitedLength);
        helper.succeed();
    }

    /**
     * #124 — Railgun arc accelerates a magnetic target down the channel. Two
     * parallel powered copper rails with a magnetic entity (an arrow — in
     * {@code #magnetization:magnetizable}) between them auto-launch it along
     * FACING (NORTH = −Z), exponential in rail length.
     *
     * <p>This uses the handler's ENTITY branch deliberately: entities are pushed
     * in world space with the SAME {@code forceBase·effL^exp} force formula as
     * ships, but have no Sable sub-level assembly/teleport race — so the test is
     * deterministic. (The ship/{@code applyWorldImpulse} branch is exercised
     * in-world; under heavy concurrent assembly the gametest harness intermittently
     * throws a Sable "No sub-level" on its physics thread, which no mod-side
     * try/catch can intercept, so it isn't asserted here.)
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void railgunArcAcceleratesEntity(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 240, abs.getZ());
        final BlockPos other = base.offset(2, 0, 0);                          // gap 2 on X
        buildRailgunRail(level, base);
        buildRailgunRail(level, other);
        powerRailgun(level, base);
        powerRailgun(level, other);
        final net.minecraft.server.level.ServerPlayer nearby = headlessAdvancementPlayer(level, "railgun-advancement");
        nearby.teleportTo(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5);
        revokeAdvancement(nearby, "completed_railgun");
        revokeAdvancement(nearby, "fired_railgun");

        // A magnetic arrow in the channel: between the rails (x+1), 2 blocks down-rail.
        final net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(level,
                        base.getX() + 1.5, base.getY() + 0.5, base.getZ() - 2.5,
                        net.minecraft.world.item.ItemStack.EMPTY, (net.minecraft.world.item.ItemStack) null);
        arrow.setNoGravity(true);   // isolate the railgun's horizontal push from fall
        arrow.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(arrow);

        helper.runAfterDelay(6L, () -> {
            final net.minecraft.world.phys.Vec3 dm = arrow.getDeltaMovement();
            arrow.discard();
            clearRailgunRail(level, base);
            clearRailgunRail(level, other);
            // Entity push is world-space along FACING (−Z), so we CAN assert the
            // axis + sign: the arrow should be launched strongly down the rail.
            helper.assertTrue(dm.z() < -1.0,
                    "Railgun should accelerate the magnetic entity down the rail (−Z); v=" + dm);
            helper.assertTrue(criterionDone(nearby, "completed_railgun", "completed"),
                    "Completing a real Railgun should award its advancement criterion");
            helper.assertTrue(criterionDone(nearby, "fired_railgun", "fired"),
                    "Firing a real Railgun should award its advancement criterion");
            nearby.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    /**
     * #124 — One remote pairs BOTH sibling rails: inserting a remote into one
     * emitter's GUI slot puts that emitter AND its handler-resolved sibling into
     * manual mode.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void railgunRemotePairsBothRails(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 240, abs.getZ());
        final BlockPos other = base.offset(2, 0, 0);
        buildRailgunRail(level, base);
        buildRailgunRail(level, other);
        powerRailgun(level, base);
        powerRailgun(level, other);

        if (!(level.getBlockEntity(base) instanceof com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity beA)) {
            helper.fail("no emitter A"); return;
        }
        beA.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));

        helper.runAfterDelay(10L, () -> {
            final com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity beB =
                    (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity) level.getBlockEntity(other);
            final boolean both = beA.manualMode() && beB != null && beB.manualMode();
            clearRailgunRail(level, base);
            clearRailgunRail(level, other);
            helper.assertTrue(both, "One remote should pair both sibling rails into manual mode");
            helper.succeed();
        });
    }

    /** Both emitter views describe one arc, except for their independently fed
     * FE buffers. The GUI switch must also update the complete pair even when
     * clicked from the non-owner emitter. */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "railgunPairedDisplay")
    public static void railgunPairedDisplaysShareArcDataButNotEnergy(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 236, abs.getZ());
        final BlockPos other = base.offset(2, 0, 0);
        buildRailgunRail(level, base);
        buildRailgunRail(level, other);
        // The effective paired length is the shorter rail, regardless of which
        // emitter the registry happens to process first.
        level.setBlock(other.relative(net.minecraft.core.Direction.NORTH, 5), Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(other.relative(net.minecraft.core.Direction.NORTH, 6), Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        final var beA = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity)
                level.getBlockEntity(base);
        final var beB = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity)
                level.getBlockEntity(other);
        if (beA == null || beB == null) { helper.fail("paired emitters were not created"); return; }
        beA.energyBuffer().receiveEnergy(1_234, false);
        beB.energyBuffer().receiveEnergy(5_678, false);
        // B is not the lower-BlockPos arc owner; pairing from it exercises the
        // former one-sided manual-mode synchronization bug.
        beB.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));

        helper.runAfterDelay(10L, () -> {
            final net.minecraft.world.entity.player.Player player =
                    helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
            final com.stonytark.magnetization.menu.MachineMenu menu =
                    new com.stonytark.magnetization.menu.MachineMenu(1, player.getInventory(),
                            net.minecraft.world.inventory.ContainerLevelAccess.create(level, other), other,
                            com.stonytark.magnetization.menu.MachineMenu.Kind.RAILGUN, beB.remoteContainer());
            final boolean clickAccepted = menu.clickMenuButton(player,
                    com.stonytark.magnetization.menu.MachineMenu.BUTTON_RAILGUN_BLOCK_BREAKING);

            final boolean sharedLength = beA.guiStat1() == 4 && beB.guiStat1() == 4;
            final boolean sharedMode = beA.manualMode() && beB.manualMode();
            final boolean sharedBreaking = !beA.breaksBlocks() && !beB.breaksBlocks();
            final boolean sharedPacked = beA.guiStat2() == beB.guiStat2();
            final boolean independentEnergy = beA.guiEnergyStored() == 1_234
                    && beB.guiEnergyStored() == 5_678;
            clearRailgunRail(level, base);
            clearRailgunRail(level, other);

            helper.assertTrue(clickAccepted, "Railgun GUI block-breaking button was rejected");
            helper.assertTrue(sharedLength, "Paired GUIs must show effective length 4; A="
                    + beA.guiStat1() + " B=" + beB.guiStat1());
            helper.assertTrue(sharedMode, "Manual mode must mirror from the non-owner emitter");
            helper.assertTrue(sharedBreaking, "Block-breaking switch must update both emitter GUIs");
            helper.assertTrue(sharedPacked, "Paired HUD/GUI arc data must be identical");
            helper.assertTrue(independentEnergy, "Each emitter must retain its own FE reading; A="
                    + beA.guiEnergyStored() + " B=" + beB.guiEnergyStored());
            helper.succeed();
        });
    }

    /**
     * #124 (R7 coverage) — Three collinear parallel rails DISSIPATE the arc. With
     * gaps of 8 (≤ maxGap 12), each OUTER rail sees only the middle (count 1) while
     * the middle sees both — the asymmetric case the findSibling partner re-scan
     * guards. All three must go IDLE and no target is accelerated. Own batch: the
     * RailgunHandler is a global level scanner, so a concurrent test's emitter could
     * otherwise pair across the shared level.
     */
    // NOTE: a 3-collinear-rail DISSIPATION gametest was attempted (R7) but removed.
    // RailgunHandler is a global LevelTickEvent scanner and gametests share one
    // ServerLevel, so a NEGATIVE "the arc must NOT fire" assertion is inherently
    // flaky here (registration/snapshot timing + cross-arc bleed) — the same trap
    // documented for `railgunSingleRailDoesNotLaunch`. The >2-rail dissipation logic
    // (RailgunHandler.findSibling double-scan) stays covered by adversarial code
    // review; if it ever needs an automated guard, unit-test scanSiblings/findSibling
    // directly against a mocked snapshot rather than via the live tick handler.

    /**
     * #124 (R7 coverage) — Auto-fire pulse cycle: an entering target drives
     * IDLE→LAUNCHING (acceleration), then on leaving the channel the arc enters
     * COOLDOWN and the BE tick decays it back to IDLE (re-armed). Guards both the
     * COOLDOWN entry and the cooldown re-arm.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "railgunCooldown")
    public static void railgunCyclesThroughCooldownToIdle(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 240, abs.getZ());
        final BlockPos other = base.offset(2, 0, 0);
        buildRailgunRail(level, base);
        buildRailgunRail(level, other);
        powerRailgun(level, base);
        powerRailgun(level, other);

        final net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(level,
                        base.getX() + 1.5, base.getY() + 0.5, base.getZ() - 2.5,
                        net.minecraft.world.item.ItemStack.EMPTY, (net.minecraft.world.item.ItemStack) null);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(arrow);

        final var master = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity) level.getBlockEntity(base);
        helper.runAfterDelay(14L, () -> {
            // By now the arrow has launched out of the channel and the arc has cooled.
            final boolean launched = arrow.getDeltaMovement().z() < -0.5;
            final boolean engaged = master != null
                    && master.arcState() != com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ArcState.IDLE;
            helper.assertTrue(launched, "Auto arc should have launched the target; v=" + arrow.getDeltaMovement());
            helper.assertTrue(engaged, "Arc should have left IDLE (LAUNCHING/COOLDOWN) after firing");
            arrow.discard();
            helper.runAfterDelay(80L, () -> {
                final boolean rearmed = master != null
                        && master.arcState() == com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ArcState.IDLE
                        && master.cooldownTicks() == 0;
                clearRailgunRail(level, base);
                clearRailgunRail(level, other);
                helper.assertTrue(rearmed, "Arc should cool down and re-arm to IDLE");
                helper.succeed();
            });
        });
    }

    /**
     * #124 (R7 coverage) — Manual workflow: a paired remote puts the arc in manual
     * mode, an entering target is HELD (not launched), and firing the bound remote
     * transitions HOLDING→LAUNCHING so the target then accelerates.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100, batch = "railgunHold")
    public static void railgunManualHoldThenRemoteFire(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        // Keep this manual-state regression outside the shared y=240 machine
        // fixtures; neighboring electromagnets otherwise add unrelated drift
        // to the arrow before the remote is fired.
        final BlockPos base = new BlockPos(abs.getX(), 300, abs.getZ());
        final BlockPos other = base.offset(2, 0, 0);
        buildRailgunRail(level, base);
        buildRailgunRail(level, other);
        powerRailgun(level, base);
        powerRailgun(level, other);

        final var master = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity) level.getBlockEntity(base);
        if (master == null) { helper.fail("no master emitter"); return; }
        master.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));

        final net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(level,
                        base.getX() + 1.5, base.getY() + 0.5, base.getZ() - 2.5,
                        net.minecraft.world.item.ItemStack.EMPTY, (net.minecraft.world.item.ItemStack) null);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(arrow);

        helper.runAfterDelay(12L, () -> {
            final boolean holding = master.arcState() == com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ArcState.HOLDING;
            final boolean notLaunchedYet = arrow.getDeltaMovement().z() > -0.4;
            helper.assertTrue(holding, "Manual arc should HOLD a target, not auto-launch; state=" + master.arcState());
            helper.assertTrue(notLaunchedYet, "Held target must not be launched before firing; v=" + arrow.getDeltaMovement());
            master.requestFire();   // the bound remote's fire signal
            helper.runAfterDelay(26L, () -> {
                final boolean launched = arrow.getDeltaMovement().z() < -0.4;
                arrow.discard();
                clearRailgunRail(level, base);
                clearRailgunRail(level, other);
                helper.assertTrue(launched, "Firing the remote should launch the held target; v=" + arrow.getDeltaMovement());
                helper.succeed();
            });
        });
    }

    /**
     * Audit #1 — the FULL manual player workflow end-to-end: pair a remote, TAKE IT
     * OUT of the slot (into a hand), confirm the arc STAYS in manual mode and HOLDS a
     * target, then fire it by invoking the real {@link RailgunRemoteItem#use} with the
     * bound remote in a mock player's hand. Previously manual mode was tied to slot
     * occupancy, so removing the remote to fire it dropped the arc to auto/IDLE and the
     * headline boarding-then-launch feature was unreachable without commands.
     */
    // Own batch: the RailgunHandler is a GLOBAL level scanner and gametests share one
    // level, so two paired-rail arcs at the same Y in the same batch can cross-pair.
    // A unique batch runs this in isolation (batches run sequentially).
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 220, batch = "railgunManualPersist")
    public static void railgunManualModeSurvivesRemoteRemovalAndFiresFromHand(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 240, abs.getZ());
        final BlockPos other = base.offset(2, 0, 0);
        buildRailgunRail(level, base);
        buildRailgunRail(level, other);
        powerRailgun(level, base);
        powerRailgun(level, other);

        final var master = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity) level.getBlockEntity(base);
        if (master == null) { helper.fail("no master emitter"); return; }

        // Pair the remote, then capture the now-bound stack and EMPTY the slot — i.e.
        // the player took the remote out to hold it for firing.
        master.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));
        final net.minecraft.world.item.ItemStack boundRemote = master.remoteContainer().getItem(0).copy();
        master.remoteContainer().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);

        helper.runAfterDelay(6L, () -> {
            // The fix: manual mode PERSISTS with an empty slot (the remote is in hand now).
            helper.assertTrue(master.manualMode(),
                    "Manual mode must persist after the remote leaves the slot");

            final net.minecraft.world.entity.projectile.Arrow arrow =
                    new net.minecraft.world.entity.projectile.Arrow(level,
                            base.getX() + 1.5, base.getY() + 0.5, base.getZ() - 2.5,
                            net.minecraft.world.item.ItemStack.EMPTY, (net.minecraft.world.item.ItemStack) null);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(0, 0, 0);
            level.addFreshEntity(arrow);

            helper.runAfterDelay(12L, () -> {
                helper.assertTrue(master.arcState() == com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ArcState.HOLDING,
                        "Manual arc should HOLD the target after the remote was removed; state=" + master.arcState());

                // Fire via the REAL item use(), remote in a mock player's hand.
                final net.minecraft.world.entity.player.Player player =
                        helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, boundRemote);
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()
                        .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);

                helper.runAfterDelay(26L, () -> {
                    final boolean launched = arrow.getDeltaMovement().z() < -0.4;
                    arrow.discard();
                    helper.assertTrue(launched,
                            "Firing the bound remote from hand should launch the held target; v=" + arrow.getDeltaMovement());

                    // Finish the real user workflow with sneak-use while the
                    // reachable rail is transitioning out of LAUNCHING.
                    player.setShiftKeyDown(true);
                    com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()
                            .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
                    helper.assertTrue(com.stonytark.magnetization.content.railgun.RailgunRemoteItem
                                    .boundPos(boundRemote) == null,
                            "Sneak-use should clear the held remote's binding after launch");
                    helper.assertTrue(!master.manualMode(),
                            "Sneak-use should return the reachable arc to automatic mode");
                    // The mock player exists only to exercise Item#use. Remove it
                    // before checking re-arm so it cannot become a fresh automatic
                    // railgun target after cooldown expires.
                    player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);

                    helper.runAfterDelay(8L, () -> {
                        helper.assertTrue(master.arcState()
                                        == com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ArcState.COOLDOWN,
                                "Launched arc should enter COOLDOWN after its target leaves; state="
                                        + master.arcState());
                        helper.succeedWhen(() -> {
                            // These isolated rails live well above the tiny test
                            // structure. The global railgun handler still sees them,
                            // but GameTest does not consistently schedule their BE
                            // ticker; drive the real production cooldown tick here.
                            com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.serverTick(
                                    level, base, level.getBlockState(base), master);
                            final boolean rearmed = master.arcState()
                                    == com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ArcState.IDLE
                                    && master.cooldownTicks() == 0;
                            helper.assertTrue(rearmed,
                                    "Manual arc should cool down and re-arm after fire/unbind; state="
                                            + master.arcState() + " cooldown=" + master.cooldownTicks());
                            clearRailgunRail(level, base);
                            clearRailgunRail(level, other);
                        });
                    });
                });
            });
        });
    }

    /**
     * Audit #8 — a bound remote can always be un-paired locally, even when the rail it
     * points at is gone. The old sneak-use path required the emitter block entity to
     * still exist in the same dimension, so a remote bound to a demolished railgun kept
     * its binding forever and every use silently passed with no message.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "railgunUnbind")
    public static void railgunRemoteUnbindsFromAMissingRailgun(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos base = new BlockPos(abs.getX(), 244, abs.getZ());
        level.setBlock(base, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        final var be = (com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity) level.getBlockEntity(base);
        if (be == null) { helper.fail("no emitter"); return; }
        be.remoteContainer().setItem(0, new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()));
        final net.minecraft.world.item.ItemStack bound = be.remoteContainer().getItem(0).copy();
        helper.assertTrue(com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundPos(bound) != null,
                "Inserting a remote should bind it");
        helper.assertTrue(com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundDim(bound) != null,
                "Binding must record the dimension for the tooltip / cross-dimension check");

        // Demolish the railgun, then sneak-use the orphaned remote.
        level.setBlock(base, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        final net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bound);
        com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get()
                .use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);

        helper.assertTrue(com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundPos(bound) == null,
                "Sneak-use must clear the binding even when the bound railgun no longer exists");
        helper.succeed();
    }

    /**
     * #124 (R7 coverage) — Block-breaking safety carve-outs: the railgun smashes a
     * plain obstructing block but spares its own rails, the emitter controls, and
     * bedrock. Calls breakIfObstructing directly for a deterministic check.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "railgunBreak")
    public static void railgunSparesRailsAndBedrockWhenBreaking(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos b = new BlockPos(abs.getX(), 240, abs.getZ());
        final BlockPos stone = b;
        final BlockPos rail = b.offset(2, 0, 0);
        final BlockPos bedrock = b.offset(4, 0, 0);
        final BlockPos emitter = b.offset(6, 0, 0);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(rail, Blocks.COPPER_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL); // #railgun_rails
        level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        final boolean priorDrops = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                .set(false, level.getServer());
        final boolean stoneBroke;
        final boolean railSpared;
        final boolean bedrockSpared;
        final boolean emitterSpared;
        try {
            stoneBroke = com.stonytark.magnetization.content.railgun.RailgunHandler.breakIfObstructing(level, stone);
            railSpared = !com.stonytark.magnetization.content.railgun.RailgunHandler.breakIfObstructing(level, rail);
            bedrockSpared = !com.stonytark.magnetization.content.railgun.RailgunHandler.breakIfObstructing(level, bedrock);
            emitterSpared = !com.stonytark.magnetization.content.railgun.RailgunHandler.breakIfObstructing(level, emitter);
        } finally {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(priorDrops, level.getServer());
        }

        final boolean stoneGone = level.getBlockState(stone).isAir();
        final boolean railStays = level.getBlockState(rail).is(Blocks.COPPER_BLOCK);
        final boolean bedrockStays = level.getBlockState(bedrock).is(Blocks.BEDROCK);
        final boolean emitterStays = level.getBlockState(emitter).is(MagBlocks.RAILGUN_EMITTER.get());

        level.setBlock(stone, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(rail, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(bedrock, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(emitter, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(b).inflate(12.0d)).forEach(net.minecraft.world.entity.Entity::discard);

        helper.assertTrue(stoneBroke && stoneGone, "Railgun should break a plain obstructing block");
        helper.assertTrue(railSpared && railStays, "Railgun must never break its own rails");
        helper.assertTrue(bedrockSpared && bedrockStays, "Railgun must not break bedrock");
        helper.assertTrue(emitterSpared && emitterStays, "Railgun must not break its emitter controls");
        helper.succeed();
    }

    /** A launched ship's high-speed sweep clears every ordinary obstruction it
     * can cross this tick, while the per-arc switch and railgun immunity remain
     * effective. This directly covers the path routine called by the Sable ship
     * acceleration branch. */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "railgunBreakPath")
    public static void railgunShipSweepBreaksPathAndHonorsToggle(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos b = new BlockPos(abs.getX(), 232, abs.getZ());
        final BlockPos farStone = b.offset(0, 0, 1);
        final BlockPos rail = b.offset(0, 0, 2);
        final BlockPos emitter = b.offset(0, 0, 3);
        final BlockPos disabledStone = b;
        level.setBlock(farStone, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(rail, Blocks.COPPER_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        final boolean priorGlobal = MagConfig.RAILGUN_BREAKS_BLOCKS.get();
        final boolean priorDrops = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS);
        final int broken;
        final int disabledBroken;
        try {
            MagConfig.RAILGUN_BREAKS_BLOCKS.set(true);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(false, level.getServer());
            // The ship occupies z=4; at 80 blocks/s it can cross four blocks in
            // one tick. The old one-slab implementation never reached farStone.
            final var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                    b.getX() + 0.1d, b.getY() + 0.1d, b.getZ() + 4.1d,
                    b.getX() + 0.9d, b.getY() + 0.9d, b.getZ() + 4.9d);
            broken = com.stonytark.magnetization.content.railgun.RailgunHandler.breakPathAhead(
                    level, bounds, net.minecraft.core.Direction.NORTH, 80.0d, true);
            level.setBlock(disabledStone, Blocks.STONE.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            disabledBroken = com.stonytark.magnetization.content.railgun.RailgunHandler.breakPathAhead(
                    level, bounds, net.minecraft.core.Direction.NORTH, 80.0d, false);
        } finally {
            MagConfig.RAILGUN_BREAKS_BLOCKS.set(priorGlobal);
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS)
                    .set(priorDrops, level.getServer());
        }

        final boolean stoneGone = level.getBlockState(farStone).isAir();
        final boolean railStays = level.getBlockState(rail).is(Blocks.COPPER_BLOCK);
        final boolean emitterStays = level.getBlockState(emitter).is(MagBlocks.RAILGUN_EMITTER.get());
        final boolean disabledStays = level.getBlockState(disabledStone).is(Blocks.STONE);
        level.setBlock(farStone, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(rail, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(emitter, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(disabledStone, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        // These fixtures live high above the tiny test arena. Do not leave the
        // intentionally generated stone drop falling into unloaded chunks after
        // the test has completed; that can stall unrelated later GameTests.
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(b).inflate(12.0d)).forEach(net.minecraft.world.entity.Entity::discard);

        helper.assertTrue(broken == 1 && stoneGone,
                "Ship sweep should break the distant path obstruction; broken=" + broken);
        helper.assertTrue(railStays, "Ship sweep must not break railgun rails");
        helper.assertTrue(emitterStays, "Ship sweep must not break railgun emitters");
        helper.assertTrue(disabledBroken == 0 && disabledStays,
                "Per-arc GUI switch must completely disable path breaking");
        helper.succeed();
    }

    /** Non-breaking launches use vanilla's swept collision solver before each
     * Sable step. A high-speed body must stop at a wall and at the ground while
     * leaving both collision blocks intact; unobstructed movement is unchanged. */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "railgunCollisionGuard")
    public static void railgunNonBreakingSweepStopsAtWorldCollision(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos origin = new BlockPos(abs.getX(), 236, abs.getZ());
        final BlockPos wall = origin.offset(0, 0, 3);
        final BlockPos ground = origin.below();
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(ground, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        final var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                origin.getX() + 0.1d, origin.getY() + 0.1d, origin.getZ() + 0.1d,
                origin.getX() + 0.9d, origin.getY() + 0.9d, origin.getZ() + 0.9d);
        final net.minecraft.world.phys.Vec3 wallRequest = new net.minecraft.world.phys.Vec3(0.0d, 0.0d, 5.0d);
        final net.minecraft.world.phys.Vec3 groundRequest = new net.minecraft.world.phys.Vec3(0.0d, -3.0d, 0.0d);
        final net.minecraft.world.phys.Vec3 openRequest = new net.minecraft.world.phys.Vec3(1.0d, 0.0d, 0.0d);
        final net.minecraft.world.phys.Vec3 safeWall =
                com.stonytark.magnetization.content.railgun.RailgunHandler.collisionSafeMovement(
                        level, bounds, wallRequest);
        final net.minecraft.world.phys.Vec3 safeGround =
                com.stonytark.magnetization.content.railgun.RailgunHandler.collisionSafeMovement(
                        level, bounds, groundRequest);
        final net.minecraft.world.phys.Vec3 safeOpen =
                com.stonytark.magnetization.content.railgun.RailgunHandler.collisionSafeMovement(
                        level, bounds, openRequest);

        final boolean wallStays = level.getBlockState(wall).is(Blocks.STONE);
        final boolean groundStays = level.getBlockState(ground).is(Blocks.STONE);
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(ground, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        helper.assertTrue(safeWall.z > 1.9d && safeWall.z < 2.2d,
                "Non-breaking sweep should stop at the wall; safe movement=" + safeWall);
        helper.assertTrue(safeGround.y > -0.2d && safeGround.y <= 0.0d,
                "Non-breaking sweep should stop above the ground; safe movement=" + safeGround);
        helper.assertTrue(safeOpen.distanceToSqr(openRequest) <= 1.0E-12d,
                "Collision guard must not reduce unobstructed launch movement; safe movement=" + safeOpen);
        helper.assertTrue(wallStays && groundStays,
                "Non-breaking collision guard must preserve every collided block");
        helper.succeed();
    }

    /** Build an emitter (FACING NORTH) + a 6-block copper rail at {@code emitter}. */
    private static void buildRailgunRail(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter) {
        level.setBlock(emitter, MagBlocks.RAILGUN_EMITTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.NORTH),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        for (int i = 1; i <= 6; i++) {
            level.setBlock(emitter.relative(net.minecraft.core.Direction.NORTH, i),
                    Blocks.COPPER_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    private static void powerRailgun(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter) {
        if (level.getBlockEntity(emitter) instanceof com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity be) {
            be.energyBuffer().receiveEnergy(1_000_000, false);   // FE powers it (either-or)
        }
    }

    private static void clearRailgunRail(final net.minecraft.server.level.ServerLevel level, final BlockPos emitter) {
        level.setBlock(emitter, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        for (int i = 1; i <= 6; i++) {
            level.setBlock(emitter.relative(net.minecraft.core.Direction.NORTH, i),
                    Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    /**
     * #125 (C6) — A magnet-slot machine burns its magnet down and stops; with the
     * legacy config the magnet is never consumed. Uses a tiny burn time (config
     * mutated in an isolated batch) so it completes within the test window.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "configMutating")
    public static void magnetSlotBurnsDownAndStops(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.HOMOPOLAR_MOTOR.get());
        if (!(helper.getBlockEntity(pos)
                instanceof com.stonytark.magnetization.content.motor.HomopolarMotorBlockEntity motor)) {
            helper.fail("no motor BE"); return;
        }
        // Shrink the burn so one magnet is gone in a few producing ticks.
        com.stonytark.magnetization.config.MagConfig.MAGNET_BURN_TICKS_BASE.set(5);
        com.stonytark.magnetization.config.MagConfig.MAGNET_BURN_TICKS_PER_POTENCY.set(0);
        com.stonytark.magnetization.config.MagConfig.MAGNET_SLOT_CONSUMES_FUEL.set(true);
        motor.magnetContainer().setItem(0,
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get()));

        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(motor.magnetContainer().getItem(0).isEmpty(),
                    "Magnet should be consumed once its burn time elapses (consume on)");
            // Legacy: with consumption off, a magnet is never consumed.
            com.stonytark.magnetization.config.MagConfig.MAGNET_SLOT_CONSUMES_FUEL.set(false);
            motor.magnetContainer().setItem(0,
                    new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get()));
            helper.runAfterDelay(20L, () -> {
                final boolean stillThere = !motor.magnetContainer().getItem(0).isEmpty();
                // Restore defaults for the rest of the batch.
                com.stonytark.magnetization.config.MagConfig.MAGNET_BURN_TICKS_BASE.set(1200);
                com.stonytark.magnetization.config.MagConfig.MAGNET_BURN_TICKS_PER_POTENCY.set(400);
                com.stonytark.magnetization.config.MagConfig.MAGNET_SLOT_CONSUMES_FUEL.set(true);
                helper.assertTrue(stillThere, "Legacy mode (consume off) must NOT consume the magnet");
                helper.succeed();
            });
        });
    }

    /**
     * #125 (C3) — The tokamak burns each cell tier for its configured duration: a
     * Helium-3 cell sets a longer burn than a Deuterium cell. Drives the controller
     * with a formed ring and reads the burn time the auto-feed assigns per tier.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void tokamakBurnsEachCellTier(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(1, 1, 1);
        helper.setBlock(controller, MagBlocks.TOKAMAK_CONTROLLER.get());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                helper.setBlock(controller.offset(dx, 0, dz), MagBlocks.TOKAMAK_COIL.get());
            }
        }
        final com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity be =
                (com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity) helper.getBlockEntity(controller);
        be.fuelContainer().setItem(0,
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.HELIUM_3_CELL.get()));

        // Block-entity ticking can start later than structure placement on a clean,
        // heavily loaded GameTest world. Wait for the real auto-feed/fusion result
        // instead of assuming that it always occurs within exactly three ticks.
        helper.succeedWhen(() -> {
            com.stonytark.magnetization.content.tokamak.TokamakControllerBlockEntity.serverTick(
                    helper.getLevel(), helper.absolutePos(controller), be.getBlockState(), be);
            helper.assertTrue(be.currentTier() == 2,
                    "Tokamak should be on the Helium-3 tier; got " + be.currentTier());
            helper.assertTrue(be.energyBuffer().getEnergyStored() > 0,
                    "Tokamak should fuse a Helium-3 cell; FE=" + be.energyBuffer().getEnergyStored());
        });
    }

    /**
     * #82 — Halbach Array: aligned same-polarity magnets raise a powered emitter's
     * effective strength tier; an adjacent hematite block steps it down. Staged on
     * one electromagnet: baseline MEDIUM → +magnets STRONG → swap to hematite WEAK.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void halbachBoostsAndHematiteDampens(final GameTestHelper helper) {
        // Drive the pure strength-modifier functions directly with blocks we place
        // at the arena centre (all six neighbours are in-arena air): deterministic,
        // synchronous, and immune to other GameTest arenas' fields.
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        final com.stonytark.magnetization.api.MagneticStrength MED = com.stonytark.magnetization.api.MagneticStrength.MEDIUM;
        final com.stonytark.magnetization.api.MagneticPolarity SOUTH = com.stonytark.magnetization.api.MagneticPolarity.SOUTH;

        // Baseline: no aligned magnets adjacent → unchanged.
        helper.assertTrue(com.stonytark.magnetization.content.HalbachArray.boostedStrength(level, pos, SOUTH, MED) == MED,
                "No aligned magnets → strength should stay MEDIUM");

        // Two aligned (SOUTH) magnets adjacent → +1 tier → STRONG.
        helper.setBlock(new BlockPos(1, 1, 0), MagBlocks.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(com.stonytark.magnetization.content.permanent.PermanentMagnetBlock.POLARITY, SOUTH));
        helper.setBlock(new BlockPos(1, 1, 2), MagBlocks.PERMANENT_MAGNET.get().defaultBlockState()
                .setValue(com.stonytark.magnetization.content.permanent.PermanentMagnetBlock.POLARITY, SOUTH));
        helper.assertTrue(com.stonytark.magnetization.content.HalbachArray.boostedStrength(level, pos, SOUTH, MED)
                        == com.stonytark.magnetization.api.MagneticStrength.STRONG,
                "Two aligned magnets should boost MEDIUM→STRONG");

        // A hematite block adjacent steps strength DOWN one tier → WEAK.
        helper.setBlock(new BlockPos(0, 1, 1), MagBlocks.HEMATITE_BLOCK.get());
        helper.assertTrue(com.stonytark.magnetization.content.hematite.HematiteBlock.dampenedStrength(level, pos, MED)
                        == com.stonytark.magnetization.api.MagneticStrength.WEAK,
                "Adjacent hematite should dampen MEDIUM→WEAK");
        helper.succeed();
    }

    /**
     * #83 — Diamagnetism flips a ship's field response: a ship containing a
     * diamagnetic block is repelled by a field that ATTRACTS an ordinary ferrous
     * ship. Two ships at the same offset from a powered electromagnet end up moving
     * in opposite directions along the emitter axis.
     */
    // Own batch: this test ticks a powered ELECTROMAGNET at y=240 whose field
    // (range ~8) would otherwise reach a sibling ship test's hull in the shared
    // default batch (gametest arenas sit only a few blocks apart).
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "ship_diamag")
    public static void diamagneticShipRepelledWhileFerrousAttracted(final GameTestHelper helper) {
        forceDefaultEmitterPower();                                              // config-drift guard
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // NeoForge can allocate a GameTest arena millions of blocks from origin.
        // Sable rejects physics bodies at those coordinates as "enormous local
        // sub-level collision bounds", leaving valid ships stationary. This test
        // owns its batch, so stage its temporary physics fixture at a known-safe,
        // otherwise-unused coordinate and remove every world block in finally.
        final BlockPos a = new BlockPos(0, 1, 0);
        final BlockPos em = new BlockPos(0, 240, 0);
        level.setBlock(em, MagBlocks.ELECTROMAGNET.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(em.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        helper.runAfterDelay(3L, () -> {
            final dev.ryanhcode.sable.sublevel.ServerSubLevel dia =
                    assembleSingleBlockShip(level, a, MagBlocks.DIAMAGNETIC_BLOCK.get());
            final dev.ryanhcode.sable.sublevel.ServerSubLevel iron =
                    assembleSingleBlockShip(level, a.offset(0, 0, 6), Blocks.IRON_BLOCK);
            teleportShip(level, dia, em.offset(4, 0, 0));       // +X of the emitter
            teleportShip(level, iron, em.offset(4, 0, 6));      // +X too, different Z

            helper.runAfterDelay(24L, () -> {
                // removeShip in finally: a transient null handle (of() can return null)
                // would otherwise throw before cleanup and leak both sub-levels, which
                // pauses Sable physics and throttles the rest of the suite.
                final var hDia = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(dia);
                final var hIron = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(iron);
                try {
                    if (hDia == null || hIron == null) { helper.fail("no ship handle"); return; }
                    final org.joml.Vector3d vDia = hDia.getLinearVelocity(new org.joml.Vector3d());
                    final org.joml.Vector3d vIron = hIron.getLinearVelocity(new org.joml.Vector3d());
                    helper.assertTrue(vDia.x > 0.0 && vIron.x < 0.0,
                            "Diamagnetic ship should be pushed away (+X) while ferrous is pulled in (-X): "
                                    + "dia.x=" + vDia.x + " iron.x=" + vIron.x);
                } finally {
                    removeShip(level, dia);
                    removeShip(level, iron);
                    level.removeBlock(em, false);
                    level.removeBlock(em.below(), false);
                }
                // GameTest success can synchronously tear down the arena. Clean
                // Sable plots first, otherwise the finally block races teardown
                // and intermittently throws "No sub-level at ...".
                helper.succeed();
            });
        });
    }

    /**
     * Dipole Electromagnet — the two poles are built correctly: a NORTH field offset
     * to the +FACING end and a SOUTH field to the -FACING end, and at a shared sample
     * point above the block the two poles push in OPPOSITE directions. Deterministic
     * (reads the BE's public pole builders + FieldApplicator.forceAt), no ship physics.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void dipoleProducesTwoOppositePoles(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MagBlocks.DIPOLE_ELECTROMAGNET.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                        net.minecraft.core.Direction.UP));
        final com.stonytark.magnetization.content.dipole.DipoleElectromagnetBlockEntity be =
                (com.stonytark.magnetization.content.dipole.DipoleElectromagnetBlockEntity) helper.getBlockEntity(pos);
        final net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
        final com.stonytark.magnetization.api.MagneticField north = be.northPoleField(state);
        final com.stonytark.magnetization.api.MagneticField south = be.southPoleField(state);

        helper.assertTrue(north.polarity() == com.stonytark.magnetization.api.MagneticPolarity.NORTH
                        && south.polarity() == com.stonytark.magnetization.api.MagneticPolarity.SOUTH,
                "North pole must be NORTH and south pole SOUTH");

        final net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pos));
        helper.assertTrue(north.origin().y > center.y && south.origin().y < center.y,
                "North origin above centre, south below; n=" + north.origin() + " s=" + south.origin());
        final double sep = north.origin().distanceTo(south.origin());
        final double expected = 2.0 * com.stonytark.magnetization.config.MagConfig.dipolePoleOffset();
        helper.assertTrue(Math.abs(sep - expected) < 0.01,
                "Pole separation should be 2*offset=" + expected + "; got " + sep);

        // Shared point above the block: north pole repels (+Y), south pole attracts (−Y).
        final net.minecraft.world.phys.Vec3 p = center.add(0, 3, 0);
        final double fN = com.stonytark.magnetization.physics.FieldApplicator.forceAt(north, p).y;
        final double fS = com.stonytark.magnetization.physics.FieldApplicator.forceAt(south, p).y;
        helper.assertTrue(fN > 0 && fS < 0,
                "The two poles must push oppositely at a shared point; north.y=" + fN + " south.y=" + fS);
        helper.succeed();
    }

    /**
     * Dipole orientation matrix: every placement direction must move the NORTH
     * pole to the +FACING side and the SOUTH pole to the -FACING side. The
     * force sampled outside either pole must rotate with that same axis rather
     * than remaining fixed in world-up or world-north.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40, batch = "dipoleOrientation")
    public static void dipoleRotatesBothPoleForcesAcrossAllFacings(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MagBlocks.DIPOLE_ELECTROMAGNET.get().defaultBlockState());
        final var be = (com.stonytark.magnetization.content.dipole.DipoleElectromagnetBlockEntity)
                helper.getBlockEntity(pos);
        final Vec3 center = Vec3.atCenterOf(helper.absolutePos(pos));

        for (final net.minecraft.core.Direction facing : net.minecraft.core.Direction.values()) {
            final var state = helper.getBlockState(pos).setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, facing);
            final Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
            final var north = be.northPoleField(state);
            final var south = be.southPoleField(state);

            helper.assertTrue(north.origin().subtract(center).dot(axis) > 0.0d,
                    "NORTH pole must be on +" + facing + "; origin=" + north.origin());
            helper.assertTrue(south.origin().subtract(center).dot(axis) < 0.0d,
                    "SOUTH pole must be on -" + facing + "; origin=" + south.origin());

            final Vec3 northForce = com.stonytark.magnetization.physics.FieldApplicator.forceAt(
                    north, center.add(axis.scale(3.0d)));
            final Vec3 southForce = com.stonytark.magnetization.physics.FieldApplicator.forceAt(
                    south, center.subtract(axis.scale(3.0d)));
            helper.assertTrue(northForce.dot(axis) > 0.0d,
                    "NORTH pole force must point outward along +" + facing + "; force=" + northForce);
            helper.assertTrue(southForce.dot(axis) > 0.0d,
                    "SOUTH pole force must point inward along +" + facing + "; force=" + southForce);
            helper.assertTrue(northForce.cross(axis).lengthSqr() < 1.0e-9
                            && southForce.cross(axis).lengthSqr() < 1.0e-9,
                    "Pole forces must stay parallel to the rotated axis for " + facing
                            + "; north=" + northForce + " south=" + southForce);
        }
        helper.succeed();
    }

    /**
     * Dipole Electromagnet — in-world dipole signature on ships: a ferrous ship
     * beyond the +FACING (NORTH) end is REPELLED outward while a ferrous ship beyond
     * the -FACING (SOUTH) end is ATTRACTED inward, so BOTH drift the same world
     * direction (+X for an EAST-facing block). A single (monopole) field would push
     * the two ships in OPPOSITE directions — so "both +X" is the dipole's signature.
     *
     * <p>Isolation (regression guard): the block's field range is overridden to a
     * SMALL 5 blocks (the electromagnet default is 128), and the test runs in its own
     * batch. Together these stop the powered emitter — parked at y=240 like every
     * "sky" ship test — from bleeding into a neighbouring arena's test (a 128-range
     * field at y=240 previously disturbed the railgun manual-hold test).
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "ship_dipole")
    public static void dipolePushesFerrousShipsWithDipoleSignature(final GameTestHelper helper) {
        forceDefaultEmitterPower();                                              // config-drift guard
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // Keep Sable physics inside its reliable coordinate envelope; GameTest
        // arena allocation itself is allowed to wander millions of blocks out.
        final BlockPos a = new BlockPos(0, 1, 0);
        final BlockPos em = new BlockPos(0, 240, 0);
        level.setBlock(em, MagBlocks.DIPOLE_ELECTROMAGNET.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,
                                net.minecraft.core.Direction.EAST),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        // Clamp the reach so the field covers the two nearby ships (±4) but can't
        // touch adjacent gametest arenas.
        if (level.getBlockEntity(em) instanceof com.stonytark.magnetization.content.dipole.DipoleElectromagnetBlockEntity dbe) {
            dbe.setRangeOverride(5);
        }
        level.setBlock(em.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        helper.runAfterDelay(3L, () -> {
            final dev.ryanhcode.sable.sublevel.ServerSubLevel shipN =
                    assembleSingleBlockShip(level, a, Blocks.IRON_BLOCK);
            final dev.ryanhcode.sable.sublevel.ServerSubLevel shipS =
                    assembleSingleBlockShip(level, a.offset(0, 0, 6), Blocks.IRON_BLOCK);
            teleportShip(level, shipN, em.offset(4, 0, 0));     // beyond the +X (NORTH) pole
            teleportShip(level, shipS, em.offset(-4, 0, 0));    // beyond the -X (SOUTH) pole

            helper.runAfterDelay(24L, () -> {
                final var hN = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(shipN);
                final var hS = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(shipS);
                try {
                    if (hN == null || hS == null) { helper.fail("no ship handle"); return; }
                    final org.joml.Vector3d vN = hN.getLinearVelocity(new org.joml.Vector3d());
                    final org.joml.Vector3d vS = hS.getLinearVelocity(new org.joml.Vector3d());
                    helper.assertTrue(vN.x > 0.0 && vS.x > 0.0,
                            "Both ferrous ships should drift +X — NORTH end repels outward, SOUTH end "
                                    + "pulls inward (a monopole would push them opposite ways): n.x="
                                    + vN.x + " s.x=" + vS.x);
                } finally {
                    removeShip(level, shipN);
                    removeShip(level, shipS);
                    level.removeBlock(em, false);
                    level.removeBlock(em.below(), false);
                }
                helper.succeed();
            });
        });
    }

    /**
     * #84 — With a Vector Core installed, the repulsion cone ALSO drags ships
     * caught in it toward the selected perpendicular direction (it is NOT an
     * on-ship thruster). Coil faces UP, so its cone points up; a ship placed in
     * that cone above the coil should gain velocity in the default thrust
     * direction (perpendicular to UP, index 0 = NORTH = −Z).
     */
    // Own batch: ticks a powered REPULSOR_COIL at y=240 whose cone/field would
    // otherwise reach a sibling ship test's hull in the shared default batch.
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160, batch = "ship_repulsor")
    public static void directionalRepulsorDragsConeShipInSelectedDir(final GameTestHelper helper) {
        forceDefaultEmitterPower();                                              // config-drift guard
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos coil = new BlockPos(a.getX(), 240, a.getZ());
        level.setBlock(coil, MagBlocks.REPULSOR_COIL.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, net.minecraft.core.Direction.UP),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(coil.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        final com.stonytark.magnetization.content.repulsor.RepulsorCoilBlockEntity rc =
                level.getBlockEntity(coil) instanceof com.stonytark.magnetization.content.repulsor.RepulsorCoilBlockEntity r ? r : null;
        if (rc == null) { helper.fail("No RepulsorCoilBlockEntity at " + coil); return; }
        rc.setVectorCore(true); // default thrust dir = perpendicular(UP)[0] = NORTH (−Z)
        helper.assertTrue(rc.thrustDirection() == net.minecraft.core.Direction.NORTH,
                "Default thrust direction for an UP-facing coil should be NORTH; got " + rc.thrustDirection());

        helper.runAfterDelay(3L, () -> {
            final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                    assembleSingleBlockShip(level, a, Blocks.IRON_BLOCK);
            teleportShip(level, ship, coil.offset(0, 2, 0)); // directly above → inside the upward cone
            helper.runAfterDelay(24L, () -> {
                final var h = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
                try {
                    if (h == null) { helper.fail("no ship handle"); return; }
                    final org.joml.Vector3d v = h.getLinearVelocity(new org.joml.Vector3d());
                    helper.assertTrue(v.z < -0.1,
                            "Vector-core repulsor should drag a ship in its cone toward NORTH (−Z); v.z=" + v.z);
                } finally {
                    removeShip(level, ship);   // always clean up, even on null handle / assert fail
                }
                helper.succeed();
            });
        });
    }

    /**
     * #93 — MR Fluid Golem hardens while inside a magnetic field. Spawns a golem,
     * confirms it is soft with no field, then places a magnet and asserts it reads
     * hardened after the field-check interval.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void mrGolemHardensInField(final GameTestHelper helper) {
        // Place the magnet at spawn so the golem is in a field from the start, then
        // assert it reads hardened after a field-check interval. (We don't assert a
        // "soft" baseline: GameTests share one level, so always-on magnets in
        // neighbouring arenas can leak into the global field registry — the robust
        // claim is "a field present → golem hardens".)
        final com.stonytark.magnetization.content.golem.MrFluidGolem golem =
                helper.spawn(com.stonytark.magnetization.registry.MagEntities.MR_FLUID_GOLEM.get(), new BlockPos(1, 1, 1));
        golem.setNoAi(true);
        helper.setBlock(new BlockPos(0, 1, 1), MagBlocks.PERMANENT_MAGNET.get());
        helper.runAfterDelay(14L, () -> {
            helper.assertTrue(golem.isHardened(), "Golem next to a magnet should harden");
            helper.succeed();
        });
    }

    /**
     * #103 — Gallium Lorentz current pushes entities floating in a powered gallium
     * source that's covered by a field. Asserts a stationary mob gains horizontal
     * velocity once the gallium is both signal-powered and in-field.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "galliumLorentzPlain")
    public static void galliumLorentzPushesEntity(final GameTestHelper helper) {
        galliumPushTest(helper, MagBlocks.GALLIUM_BLOCK.get());
    }

    /**
     * #104 — Mixed gallium carries the same Lorentz entity-push as plain gallium
     * (the second of its two abilities; the ferrofluid-style creep is a slow
     * block-spread covered by registry membership). Same assertion as #103.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120, batch = "galliumLorentzMixed")
    public static void mixedGalliumLorentzPushesEntity(final GameTestHelper helper) {
        galliumPushTest(helper, MagBlocks.MIXED_GALLIUM_BLOCK.get());
    }

    private static void galliumPushTest(final GameTestHelper helper, final net.minecraft.world.level.block.Block galliumBlock) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), galliumBlock);
        final BlockPos magnetRel = new BlockPos(1, 1, 2);
        helper.setBlock(magnetRel, MagBlocks.PERMANENT_MAGNET.get()); // field over the gallium

        // Wiring test. GalliumLorentzHandler drives its per-tick entity push when a
        // gallium cell is (a) a tracked Lorentz source, (b) carrying a redstone
        // current, and (c) in a magnetic field. We verify (a) and (c) hold for a
        // placed gallium cell under a magnet, then succeed. We deliberately omit the
        // redstone source and a floating entity: a powered gallium fluid cell ticking
        // in the shared GameTest arena spins the batch runner indefinitely (game
        // ticks never advance for the test). The redstone-current gating and the
        // actual entity push magnitude (drag-dependent inside a fluid) are validated
        // in-world. Drive the permanent magnet through its real production ticker
        // once so this wiring assertion does not depend on when the shared GameTest
        // server schedules a newly placed block entity.
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(GalliumRegistry.snapshot(level).contains(abs),
                "Placed gallium should register itself as a tracked Lorentz source in GalliumRegistry");
        final BlockPos magnetAbs = helper.absolutePos(magnetRel);
        if (!(level.getBlockEntity(magnetAbs)
                instanceof com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity magnet)) {
            helper.fail("No permanent magnet block entity at " + magnetAbs);
            return;
        }
        // This fixture is placed synchronously before the shared test level runs
        // the BE lifecycle. Registration itself has a dedicated onLoad/removal
        // test; seed the production registry here so nearestField can exercise its
        // actual discovery path deterministically.
        com.stonytark.magnetization.physics.EmitterRegistry.register(level, magnetAbs);
        com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity.serverTick(
                level, magnetAbs, level.getBlockState(magnetAbs), magnet);
        helper.assertTrue(MagneticFields.nearestField(level, net.minecraft.world.phys.Vec3.atCenterOf(abs)) != null,
                "Gallium under a permanent magnet should sit in a magnetic field");
        helper.succeed();
    }

    /**
     * #109 — Gallium freezes to solid near a cooling source and melts back when the
     * cooling is removed. (Gear stats and dye outputs are item/recipe data, not
     * behaviours — they're verified in JEI, not here.)
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 300)
    public static void galliumFreezesNearIceAndMeltsWhenRemoved(final GameTestHelper helper) {
        final BlockPos g = new BlockPos(1, 1, 1);
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(g, MagBlocks.GALLIUM_BLOCK.get());
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.ICE); // cooling source → schedules freeze

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockState(g).getBlock() == MagBlocks.SOLID_GALLIUM.get(),
                        "Gallium next to ice should freeze to solid_gallium; got "
                                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                                        helper.getBlockState(g).getBlock())))
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getBlockState(g).getBlock() == MagBlocks.GALLIUM_BLOCK.get(),
                        "Solid gallium should melt back to fluid once cooling is gone; got "
                                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                                        helper.getBlockState(g).getBlock())))
                .thenSucceed();
    }

    /**
     * #92 — MR Fluid Armor strongly mitigates damage while the wearer is in a
     * field. An MR-armored zombie and a bare zombie stand in the SAME field and
     * take the same generic hit; the armored one must lose far less health. Both
     * sharing one field makes the test immune to background fields leaking from
     * other GameTest arenas (the bare zombie has no MR pieces, so the field's
     * mitigation never applies to it).
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void mrArmorMitigatesDamageInField(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.world.entity.monster.Zombie armored =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 1, 0));
        armored.setNoAi(true);
        armored.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MR_LIQUID_HELMET.get()));
        armored.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MR_LIQUID_CHESTPLATE.get()));
        armored.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MR_LIQUID_LEGGINGS.get()));
        armored.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MR_LIQUID_BOOTS.get()));
        final net.minecraft.world.entity.monster.Zombie bare =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 1, 2));
        bare.setNoAi(true);
        helper.setBlock(new BlockPos(1, 1, 1), MagBlocks.PERMANENT_MAGNET.get()); // both zombies adjacent → in field

        helper.runAfterDelay(4L, () -> {
            armored.setHealth(armored.getMaxHealth());
            bare.setHealth(bare.getMaxHealth());
            armored.invulnerableTime = 0;
            bare.invulnerableTime = 0;
            armored.hurt(level.damageSources().generic(), 8f);
            bare.hurt(level.damageSources().generic(), 8f);
            final float armoredLost = armored.getMaxHealth() - armored.getHealth();
            final float bareLost = bare.getMaxHealth() - bare.getHealth();
            helper.assertTrue(bareLost > 0f, "Bare zombie should take some damage; got " + bareLost);
            helper.assertTrue(armoredLost < bareLost - 1.0f,
                    "MR-armored zombie in a field should lose far less than a bare one: armored=" + armoredLost + " bare=" + bareLost);
            helper.succeed();
        });
    }

    /**
     * #96 — MR Fluid tools barely wear (high max durability vs iron) and harden on
     * use (HARDENED_UNTIL stamped). Mines a block with the pickaxe and asserts the
     * stamp is set and durability is generous.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void mrToolBarelyWearsAndHardensOnUse(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.world.item.ItemStack pick =
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MR_FLUID_PICKAXE.get());
        helper.assertTrue(pick.getMaxDamage() > 250,
                "MR tool should have far more durability than iron (250); got " + pick.getMaxDamage());

        final net.minecraft.world.entity.monster.Zombie user =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        user.setNoAi(true);
        final BlockPos stone = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);

        pick.getItem().mineBlock(pick, level, Blocks.STONE.defaultBlockState(), stone, user);
        helper.assertTrue(pick.get(com.stonytark.magnetization.registry.MagDataComponents.HARDENED_UNTIL.get()) != null,
                "Mining with an MR tool should stamp HARDENED_UNTIL");
        helper.assertTrue(pick.getDamageValue() <= 1,
                "One mine should cost at most 1 durability; got " + pick.getDamageValue());
        helper.succeed();
    }

    /**
     * #97 — MR Fluid horse armor runs the same field-mitigation path on a horse
     * (the visual render layer is client-only and not covered here). Generic damage
     * to an armored horse is reduced in a field vs out of field.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void mrHorseArmorIsValidBardingOnTheMitigationPath(final GameTestHelper helper) {
        // The horse-barding piece routes through the SAME MrArmorHandler proven by
        // mrArmorMitigatesDamageInField (it's an MrFluidHorseArmorItem, which the
        // handler's isMrPiece recognises), and equips to the horse body slot
        // (AnimalArmorItem / EQUESTRIAN). Its fluid↔rigid look is a client render
        // layer, not headless-testable. We assert it's valid barding a horse
        // accepts and that it's the recognised MR class.
        final net.minecraft.world.item.ItemStack barding =
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MR_FLUID_HORSE_ARMOR.get());
        helper.assertTrue(barding.getItem() instanceof com.stonytark.magnetization.content.mrarmor.MrFluidHorseArmorItem,
                "MR horse armor should be an MrFluidHorseArmorItem (recognised by the MR mitigation handler)");
        helper.assertTrue(barding.getItem() instanceof net.minecraft.world.item.AnimalArmorItem,
                "MR horse armor should be an AnimalArmorItem (equips to the horse body slot)");
        final net.minecraft.world.entity.animal.horse.Horse horse =
                helper.spawn(net.minecraft.world.entity.EntityType.HORSE, new BlockPos(1, 1, 1));
        helper.assertTrue(horse.isBodyArmorItem(barding),
                "A horse should accept MR horse armor as body barding");
        helper.succeed();
    }

    /**
     * The ore-break residual field (seeded by {@code ExtraLirmSources#onBlockBreak}
     * and run through {@link com.stonytark.magnetization.physics.FieldApplicator}) honours
     * the {@code oreBreakAffectsArmor} config. We drive the exact path the config feeds —
     * {@code FieldApplicator.apply(level, field, affectsArmor, affectsItems)} — rather than
     * the break event + decay scheduler, so the result is deterministic.
     *
     * <p>A cow is NOT in {@code #magnetization:magnetizable} (only iron golems / projectiles
     * are), so it can be magnetized ONLY through worn metal armor. Wearing an iron helmet
     * (in {@code #magnetization:metal_armor}), the same field pulls it with the armor flag
     * on and leaves it motionless with the flag off — proving the toggle isolates the
     * ore→armor interaction without disabling any other susceptibility.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void oreBreakArmorToggleGatesArmorPull(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.world.entity.animal.Cow cow =
                helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(1, 1, 1));
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));

        final com.stonytark.magnetization.api.MagneticField field = strongOreBreakField(helper, new BlockPos(4, 1, 1));

        // Delay so the freshly-spawned cow is indexed for the AABB entity query.
        helper.runAfterDelay(3L, () -> {
            // affectsArmor = true → the armored cow is pulled (gains velocity).
            cow.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field, true, true);
            final double pulled = cow.getDeltaMovement().lengthSqr();
            helper.assertTrue(pulled > 1.0e-6,
                    "oreBreakAffectsArmor ON → the ore-break field should pull the armored cow; v^2=" + pulled);

            // affectsArmor = false → armor is exempt and the cow has no other
            // susceptibility, so it is never even a field candidate → exactly zero motion.
            cow.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field, false, true);
            final double exempt = cow.getDeltaMovement().lengthSqr();
            helper.assertTrue(exempt == 0.0,
                    "oreBreakAffectsArmor OFF → the ore-break field must NOT move the armored cow; v^2=" + exempt);
            helper.succeed();
        });
    }

    /**
     * Companion to {@link #oreBreakArmorToggleGatesArmorPull} for the {@code oreBreakAffectsItems}
     * config. A loose ferromagnetic item drop is pulled by the ore-break field with the item
     * flag on, and completely ignored with it off — while leaving armor/mobs/ships untouched.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void oreBreakItemsToggleGatesItemPull(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        final net.minecraft.world.entity.item.ItemEntity drop =
                new net.minecraft.world.entity.item.ItemEntity(level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
                        new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.FERROMAGNETIC_INGOT.get()));
        drop.setNoGravity(true);
        level.addFreshEntity(drop);

        final com.stonytark.magnetization.api.MagneticField field = strongOreBreakField(helper, new BlockPos(4, 1, 1));

        helper.runAfterDelay(3L, () -> {
            // affectsItems = true → the ferromagnetic drop is pulled.
            drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field, true, true);
            final double pulled = drop.getDeltaMovement().lengthSqr();
            helper.assertTrue(pulled > 1.0e-6,
                    "oreBreakAffectsItems ON → the ore-break field should pull the ferromagnetic drop; v^2=" + pulled);

            // affectsItems = false → item drops are filtered out of the field entirely.
            drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.stonytark.magnetization.physics.FieldApplicator.apply(level, field, true, false);
            final double exempt = drop.getDeltaMovement().lengthSqr();
            helper.assertTrue(exempt == 0.0,
                    "oreBreakAffectsItems OFF → the ore-break field must NOT move the item drop; v^2=" + exempt);
            // GameTests share one ServerLevel. Do not leave a magnetic item
            // fixture behind for later emitters to accelerate into an unloaded
            // chunk, which can stall the entire batch in chunk acquisition.
            drop.discard();
            helper.succeed();
        });
    }

    /** A STRONG omnidirectional field centred at {@code rel} (range 16) — stands in for the
     *  ore-break residual so the {@code affectsArmor}/{@code affectsItems} flags can be driven
     *  directly. SOUTH polarity attracts a default-NORTH target toward the origin. */
    private static com.stonytark.magnetization.api.MagneticField strongOreBreakField(
            final GameTestHelper helper, final BlockPos rel) {
        return new com.stonytark.magnetization.api.MagneticField(
                net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(rel)),
                new net.minecraft.world.phys.Vec3(0, 1, 0),
                com.stonytark.magnetization.api.MagneticPolarity.SOUTH,
                com.stonytark.magnetization.api.MagneticStrength.STRONG,
                com.stonytark.magnetization.api.MagneticField.Shape.OMNIDIRECTIONAL);
    }

    /**
     * The {@code requireRedstoneAndEnergy} config gates an emitter on BOTH a redstone
     * signal and buffered FE at once (instead of either-or). Drives the emitter BE's
     * power resolution directly — {@code setPowered} + {@code setEnergyForDebug} + a
     * manual {@code serverTick} — with the flag flipped on, then restores the default
     * and confirms either-or still powers it. Fully synchronous; a try/finally always
     * restores the shared-server config so sibling tests are unaffected.
     *
     * <p>Pinned to its own {@code "configMutating"} batch: it flips the GLOBAL
     * static {@code REQUIRE_REDSTONE_AND_ENERGY}, and GameTests in a batch tick
     * concurrently against one shared static config. Isolating it keeps its
     * deliberate mutation from bleeding into the default-batch field/emitter
     * tests (which {@link #forceDefaultEmitterPower} also defends, belt-and-braces).
     */
    /**
     * Analog Redstone Strength: the signal level scales the emitted force along a ramp
     * anchored at WEAK's force (signal 1) and EXTREME's force (signal 15), and does so
     * ONLY while redstone is the driver and only for a block whose toggle is on.
     *
     * <p>Pinned to its OWN batch, not the shared {@code "configMutating"} one: tests
     * within a batch tick concurrently, and {@code requireBothRedstoneAndEnergyGate}
     * flips {@code REQUIRE_REDSTONE_AND_ENERGY} globally — which would break the FE-only
     * case below. Batches run sequentially, so a private batch is real isolation.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 80, batch = "analogRedstone")
    public static void analogRedstoneScalesForce(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, MagBlocks.ELECTROMAGNET.get());
        final BlockPos pos = helper.absolutePos(rel);
        final AbstractEmitterBlockEntity be = (AbstractEmitterBlockEntity) helper.getBlockEntity(rel);
        final int cap = be.getEnergyBuffer().getMaxEnergyStored();

        final boolean priorAnalog =
                com.stonytark.magnetization.config.MagConfig.ANALOG_REDSTONE_ELECTROMAGNET.get();
        final boolean priorBoth =
                com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.get();
        try {
            forceDefaultEmitterPower();
            com.stonytark.magnetization.config.MagConfig.ANALOG_REDSTONE_ELECTROMAGNET.set(true);

            // (A) full signal → exactly EXTREME's force, the top of the ramp.
            be.setEnergyForDebug(0);
            be.setRedstoneLevel(15);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            final var atFull = be.currentField();
            helper.assertTrue(atFull != null, "signal 15 should produce a field");
            helper.assertTrue(Math.abs(atFull.force() - com.stonytark.magnetization.api.MagneticStrength.EXTREME.force()) < 1e-6,
                    "signal 15 must give EXTREME's force; got " + atFull.force());

            // (B) minimum signal → exactly WEAK's force, the bottom of the ramp.
            be.setRedstoneLevel(1);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            final var atMin = be.currentField();
            helper.assertTrue(atMin != null, "signal 1 should still produce a field");
            helper.assertTrue(Math.abs(atMin.force() - com.stonytark.magnetization.api.MagneticStrength.WEAK.force()) < 1e-6,
                    "signal 1 must give WEAK's force; got " + atMin.force());

            // (C) the ramp is monotonic across the dial.
            double previous = 0.0d;
            for (int s = 1; s <= 15; s++) {
                be.setRedstoneLevel(s);
                AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
                final var f = be.currentField();
                helper.assertTrue(f != null && f.force() > previous,
                        "force must rise with the signal; stalled at level " + s);
                previous = f.force();
            }

            // (D) signal 0 → dark, no field at all.
            be.setRedstoneLevel(0);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            helper.assertTrue(!be.isPowered() && be.currentField() == null,
                    "signal 0 must leave the emitter unpowered with no field");

            // (E) FE-only drive is NEVER throttled — there is no signal to read, so the
            // emitter must run at its full configured tier rather than at the floor.
            be.setEnergyForDebug(cap);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            final var onEnergy = be.currentField();
            helper.assertTrue(onEnergy != null && be.isPowered(),
                    "buffered FE alone should power the emitter");
            helper.assertTrue(!onEnergy.hasForceOverride(),
                    "an FE-driven emitter must not carry an analog force override");
            helper.assertTrue(Math.abs(onEnergy.force() - be.configuredStrength().force()) < 1e-6,
                    "FE-driven force must equal the configured tier; got " + onEnergy.force());

            // (F) toggle OFF → a weak signal gives full configured strength again, i.e.
            //     the historical behaviour every existing world relies on.
            com.stonytark.magnetization.config.MagConfig.ANALOG_REDSTONE_ELECTROMAGNET.set(false);
            be.setEnergyForDebug(0);
            be.setRedstoneLevel(1);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            final var legacy = be.currentField();
            helper.assertTrue(legacy != null && !legacy.hasForceOverride(),
                    "with the toggle off a partial signal must not throttle the field");
            helper.assertTrue(Math.abs(legacy.force() - be.configuredStrength().force()) < 1e-6,
                    "toggle off: force must equal the configured tier; got " + legacy.force());

            // (G) the legacy on/off entry point still means "full drive".
            be.setPowered(true);
            helper.assertTrue(be.getRedstoneLevel() == 15,
                    "setPowered(true) must store a full signal; got " + be.getRedstoneLevel());
            be.setPowered(false);
            helper.assertTrue(be.getRedstoneLevel() == 0,
                    "setPowered(false) must store no signal; got " + be.getRedstoneLevel());
        } finally {
            com.stonytark.magnetization.config.MagConfig.ANALOG_REDSTONE_ELECTROMAGNET.set(priorAnalog);
            com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(priorBoth);
        }
        helper.succeed();
    }

    /**
     * A pre-analog save carries only the boolean {@code Powered} tag. It must load as a
     * FULL signal, otherwise every powered emitter in an existing world would quietly
     * come back at the bottom of the ramp the first time an admin enabled the feature.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void legacyPoweredNbtLoadsAsFullSignal(final GameTestHelper helper) {
        final BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, MagBlocks.ELECTROMAGNET.get());
        final AbstractEmitterBlockEntity be = (AbstractEmitterBlockEntity) helper.getBlockEntity(rel);

        final net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putBoolean("Powered", true);   // no RedstoneLevel — a 1.3.0-era save
        be.loadCustomOnly(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(be.getRedstoneLevel() == 15,
                "a legacy Powered=true save must load as a full signal; got " + be.getRedstoneLevel());

        final net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
        off.putBoolean("Powered", false);
        be.loadCustomOnly(off, helper.getLevel().registryAccess());
        helper.assertTrue(be.getRedstoneLevel() == 0,
                "a legacy Powered=false save must load as no signal; got " + be.getRedstoneLevel());

        // And a modern save round-trips the exact level.
        be.setRedstoneLevel(7);
        final net.minecraft.nbt.CompoundTag saved =
                be.saveWithoutMetadata(helper.getLevel().registryAccess());
        be.setRedstoneLevel(0);
        be.loadCustomOnly(saved, helper.getLevel().registryAccess());
        helper.assertTrue(be.getRedstoneLevel() == 7,
                "a saved partial signal must round-trip; got " + be.getRedstoneLevel());
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "configMutating")
    public static void requireBothRedstoneAndEnergyGate(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, MagBlocks.ELECTROMAGNET.get());
        final BlockPos pos = helper.absolutePos(rel);
        final AbstractEmitterBlockEntity be = (AbstractEmitterBlockEntity) helper.getBlockEntity(rel);
        final int cap = be.getEnergyBuffer().getMaxEnergyStored();

        final boolean prior = com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.get();
        try {
            com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(true);

            // (A) require-both, full buffer but NO redstone → stays off AND burns no energy.
            be.setPowered(false);
            be.setEnergyForDebug(cap);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            helper.assertTrue(!be.isPowered(),
                    "require-both: full buffer but no redstone must NOT power the emitter");
            helper.assertTrue(be.getEnergyBuffer().getEnergyStored() == cap,
                    "require-both: with no redstone the buffer must not drain; got "
                            + be.getEnergyBuffer().getEnergyStored());

            // (B) require-both, redstone ON + energy → runs and drains.
            be.setPowered(true);
            be.setEnergyForDebug(cap);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            helper.assertTrue(be.isPowered(),
                    "require-both: redstone + buffered FE should power the emitter");
            helper.assertTrue(be.getEnergyBuffer().getEnergyStored() < cap,
                    "require-both: a running emitter should drain energy");

            // (C) require-both, redstone ON but EMPTY buffer → stays off.
            be.setPowered(true);
            be.setEnergyForDebug(0);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            helper.assertTrue(!be.isPowered(),
                    "require-both: redstone but no energy must NOT power the emitter");

            // (D) default either-or restored: energy alone (no redstone) powers it.
            com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(false);
            be.setPowered(false);
            be.setEnergyForDebug(cap);
            AbstractEmitterBlockEntity.serverTick(level, pos, be.getBlockState(), be);
            helper.assertTrue(be.isPowered(),
                    "default either-or: energy alone should power the emitter");
        } finally {
            com.stonytark.magnetization.config.MagConfig.REQUIRE_REDSTONE_AND_ENERGY.set(prior);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void emitterDrainsEnergyOverTicks(final GameTestHelper helper) {
        forceDefaultEmitterPower();                                              // config-drift guard
        // Guard against a configured drain of 0 or a tiny capacity — both would
        // make the post-tick assertion misleading. Bail with a clear message
        // so users don't chase a "test failed" alarm caused by their config.
        final int drainPerTick = drainPerTickFromConfig();
        if (drainPerTick <= 0) {
            helper.succeed(); // nothing to test; the feature is disabled
            return;
        }

        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.ELECTROMAGNET.get());

        helper.runAfterDelay(2L, () -> {
            final BlockEntity be = helper.getBlockEntity(pos);
            if (!(be instanceof AbstractEmitterBlockEntity emitter)) {
                helper.fail("Expected an AbstractEmitterBlockEntity at " + pos + ", got " + be);
                return;
            }
            // Fill to ~half capacity so we have headroom for both the drain
            // assertion and the "still some left" assertion later.
            final int capacity = emitter.getEnergyBuffer().getMaxEnergyStored();
            final int initial = capacity / 2;
            emitter.setEnergyForDebug(initial);
            helper.assertTrue(emitter.getEnergyBuffer().getEnergyStored() == initial,
                    "setEnergyForDebug should populate the buffer; got "
                            + emitter.getEnergyBuffer().getEnergyStored());

            // 40 ticks later the buffer should have decreased — the per-tick
            // drain comes from MagConfig.EMITTER_ENERGY_DRAIN_PER_TICK (default
            // 10 FE/tick). Don't assert exact magnitude; configs can change.
            helper.succeedWhen(() -> {
                AbstractEmitterBlockEntity.serverTick(
                        helper.getLevel(), helper.absolutePos(pos), emitter.getBlockState(), emitter);
                final int after = emitter.getEnergyBuffer().getEnergyStored();
                helper.assertTrue(after < initial,
                        "Buffer should drain while the emitter ticks; initial=" + initial
                                + " after=" + after);
            });
        });
    }

    /**
     * #112 — The Gallium Golem is an iron-golem palette-swap (so it behaves like
     * one) but, being soft gallium, is weaker: lower max health and no knockback
     * resistance. We assert the type relationship and the tuned attributes. The
     * warm-biome melt and warm-damage softening are biome-temperature dependent
     * (the GameTest arena biome is not guaranteed warm) and the shatter loot is a
     * loot table — those are verified in-world.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void galliumGolemIsAWeakerIronGolem(final GameTestHelper helper) {
        final com.stonytark.magnetization.content.golem.GalliumGolem golem =
                helper.spawn(com.stonytark.magnetization.registry.MagEntities.GALLIUM_GOLEM.get(), new BlockPos(1, 1, 1));
        helper.assertTrue(golem instanceof net.minecraft.world.entity.animal.IronGolem,
                "Gallium golem should be an IronGolem subclass (behaves like one)");
        final double maxHealth = golem.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        helper.assertTrue(maxHealth > 0 && maxHealth < 100.0,
                "Gallium golem should be weaker than an iron golem (max health < 100); got " + maxHealth);
        final double knockback = golem.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(knockback == 0.0,
                "Soft gallium golem should have no knockback resistance; got " + knockback);
        helper.succeed();
    }

    /**
     * #112 — Mixed gallium's "dual ability": it registers as BOTH a plain
     * ferrofluid source (so FerrofluidCreepHandler creeps it toward magnets) AND a
     * Lorentz source in GalliumRegistry (the entity push). Both registrations
     * happen synchronously in onPlace, so we assert membership in both registries.
     * The live creep + push behaviours are covered by #104 / in-world.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void mixedGalliumRegistersForBothCreepAndLorentz(final GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), MagBlocks.MIXED_GALLIUM_BLOCK.get());
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(GalliumRegistry.snapshot(helper.getLevel()).contains(abs),
                "Mixed gallium should register as a Lorentz source (GalliumRegistry)");
        helper.assertTrue(com.stonytark.magnetization.content.fluid.FerrofluidSourceRegistry
                        .snapshot(helper.getLevel()).contains(abs),
                "Mixed gallium should also register as a ferrofluid creep source (FerrofluidSourceRegistry)");
        helper.succeed();
    }

    /**
     * #78 — Soft-disabled content is not just hidden but uncraftable: the recipe
     * strip ({@link com.stonytark.magnetization.content.DisabledContentRecipes},
     * run at server start) removes recipes producing disabled items. The induction
     * pad is disabled by default, so its recipe must be gone, while an always-on
     * item (electromagnet) keeps its recipe as a control.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void disabledContentRecipeIsStripped(final GameTestHelper helper) {
        final net.minecraft.world.item.crafting.RecipeManager recipes =
                helper.getLevel().getServer().getRecipeManager();
        final net.minecraft.resources.ResourceLocation pad =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "induction_pad");
        final net.minecraft.resources.ResourceLocation electromagnet =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "electromagnet");
        helper.assertTrue(recipes.byKey(electromagnet).isPresent(),
                "Enabled content (electromagnet) should keep its crafting recipe");
        helper.assertTrue(recipes.byKey(pad).isEmpty(),
                "Disabled-by-default induction pad should have NO crafting recipe (stripped)");
        helper.succeed();
    }

    /**
     * #91 — When a hardened MR-fluid bridge reverts (field removed), a cell that
     * was FLOWING must NOT come back as a fluid source (that would duplicate
     * fluid). Place an MR source plus an adjacent flowing cell, harden both in a
     * field, remove the field, and assert the flowing cell does not revert to a
     * source.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 200)
    public static void mrFluidRevertDoesNotTurnFlowingIntoSource(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        // Source cell + an explicitly-flowing cell beside it.
        helper.setBlock(new BlockPos(1, 2, 1), MagBlocks.MR_FLUID_BLOCK.get());
        level.setBlock(helper.absolutePos(new BlockPos(2, 2, 1)),
                MagBlocks.MR_FLUID_BLOCK.get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        // Magnet beside the source → field over the body.
        helper.setBlock(new BlockPos(0, 2, 1), MagBlocks.PERMANENT_MAGNET.get());

        final BlockPos flowingAbs = helper.absolutePos(new BlockPos(2, 2, 1));
        helper.runAfterDelay(20L, () -> {
            // Both cells should have hardened.
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 1)).is(MagBlocks.HARDENED_MR_FLUID.get()),
                    "Flowing MR-fluid cell should harden in a field");
            helper.assertTrue(!helper.getBlockState(new BlockPos(2, 2, 1))
                            .getValue(com.stonytark.magnetization.content.fluid.HardenedMrFluidBlock.SOURCE),
                    "Hardened flowing cell should record SOURCE=false");
            // Remove the field.
            helper.setBlock(new BlockPos(0, 2, 1), Blocks.AIR);
            helper.runAfterDelay(20L, () -> {
                final net.minecraft.world.level.block.state.BlockState reverted = level.getBlockState(flowingAbs);
                final boolean isSource = reverted.is(MagBlocks.MR_FLUID_BLOCK.get())
                        && reverted.getFluidState().isSource();
                helper.assertTrue(!isSource,
                        "A reverted FLOWING cell must not become an MR-fluid source; got "
                                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(reverted.getBlock())
                                + " source=" + reverted.getFluidState().isSource());
                helper.succeed();
            });
        });
    }

    /**
     * #125 — The Feature-C fuel-balance ladder (C0) holds at the configured
     * defaults, verified deterministically WITHOUT physics (the ship-thrust
     * comparisons are in-world-tested by the user; the ORDERING the rebalance
     * enforces is config/formula-driven and asserted here):
     *  • magnet burn time: titanomagnetite block > its ingot > a bare weak ore;
     *  • MHD conductivity: Liquid Lithium > Mixed Gallium > Gallium > 0;
     *  • micro-thruster magnetized-ferrofluid bonus > 1.0;
     *  • fusion fluid strength: Helium-3 > Tritium > Deuterium Oxide > Hydrogen;
     *  • tokamak: D-T out-generates D-D per tick, and He-3 burns longest.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 1)
    public static void fuelBalanceOrdering(final GameTestHelper helper) {
        // Magnet burn: quantity (block ×9) + strength compound → block ≫ ingot > ore.
        final int blockBurn = com.stonytark.magnetization.content.MagneticMaterials.magnetBurnTicks(
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.TITANOMAGNETITE_BLOCK.get()));
        final int ingotBurn = com.stonytark.magnetization.content.MagneticMaterials.magnetBurnTicks(
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.TITANOMAGNETITE_INGOT.get()));
        final int oreBurn = com.stonytark.magnetization.content.MagneticMaterials.magnetBurnTicks(
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.HEMATITE_ORE.get()));
        helper.assertTrue(blockBurn > ingotBurn,
                "Storage block must burn longer than its ingot; block=" + blockBurn + " ingot=" + ingotBurn);
        helper.assertTrue(ingotBurn > oreBurn,
                "A strong ingot must burn longer than a weak ore; ingot=" + ingotBurn + " ore=" + oreBurn);

        // MHD conductive working fluid ordering.
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.mhdConductivityLiquidLithium()
                        > com.stonytark.magnetization.config.MagConfig.mhdConductivityMixedGallium()
                && com.stonytark.magnetization.config.MagConfig.mhdConductivityMixedGallium()
                        > com.stonytark.magnetization.config.MagConfig.mhdConductivityGallium()
                && com.stonytark.magnetization.config.MagConfig.mhdConductivityGallium() > 0.0,
                "MHD conductivity must rank Liquid Lithium > Mixed Gallium > Gallium > 0");

        // Micro-thruster magnetized bonus.
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.microThrusterMagnetizedMult() > 1.0,
                "Magnetized ferrofluid must out-thrust plain; mult="
                        + com.stonytark.magnetization.config.MagConfig.microThrusterMagnetizedMult());

        // Fusion fluid strength ladder.
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.fusionThrusterFluidMultHelium3()
                        > com.stonytark.magnetization.config.MagConfig.fusionThrusterFluidMultTritium()
                && com.stonytark.magnetization.config.MagConfig.fusionThrusterFluidMultTritium()
                        > com.stonytark.magnetization.config.MagConfig.fusionThrusterFluidMultDeuteriumOxide()
                && com.stonytark.magnetization.config.MagConfig.fusionThrusterFluidMultDeuteriumOxide()
                        > com.stonytark.magnetization.config.MagConfig.fusionThrusterFluidMultHydrogen(),
                "Fusion fluid strength must rank He-3 > Tritium > Deuterium Oxide > Hydrogen");

        // Tokamak tiers: D-T = raw power (most FE/tick); He-3 = longest burn.
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.tokamakGenPerTickTritium()
                        > com.stonytark.magnetization.config.MagConfig.tokamakGenPerTick(),
                "D-T must out-generate D-D per tick");
        helper.assertTrue(com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksHelium3()
                        > com.stonytark.magnetization.config.MagConfig.tokamakBurnTicksPerCell()
                && com.stonytark.magnetization.config.MagConfig.tokamakOutputRateHelium3()
                        > com.stonytark.magnetization.config.MagConfig.tokamakOutputRate(),
                "He-3 must burn longest and push the highest output rate");
        helper.succeed();
    }

    /**
     * Pyrrhotite becomes heat-activated next to a heat source (a magma block →
     * KINDLED) and stays cold with no source. This is the regression guard for the
     * {@code gameTime - Long.MIN_VALUE} rescan-overflow bug: before the fix the
     * scan never ran and the ore never observed heat (never magnetic).
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void pyrrhotiteMagnetizesNearHeat(final GameTestHelper helper) {
        final BlockPos hot = new BlockPos(1, 1, 1);
        final BlockPos cold = new BlockPos(1, 1, 4);
        helper.setBlock(hot, MagBlocks.PYRRHOTITE_BLOCK.get());
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.MAGMA_BLOCK);   // adjacent heat
        helper.setBlock(cold, MagBlocks.PYRRHOTITE_BLOCK.get());      // no heat anywhere near

        final com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity hotBe =
                (com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity) helper.getBlockEntity(hot);
        final com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity coldBe =
                (com.stonytark.magnetization.content.pyrrhotite.PyrrhotiteBlockEntity) helper.getBlockEntity(cold);

        helper.runAfterDelay(20L, () -> {
            AbstractEmitterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(hot),
                    hotBe.getBlockState(), hotBe);
            AbstractEmitterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(cold),
                    coldBe.getBlockState(), coldBe);
            helper.assertTrue(hotBe.observedHeat() != com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE,
                    "Pyrrhotite beside a magma block should observe heat; got " + hotBe.observedHeat());
            helper.assertTrue(coldBe.observedHeat() == com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE,
                    "Pyrrhotite with no heat source must stay cold; got " + coldBe.observedHeat());
            helper.succeed();
        });
    }

    /**
     * Fusion Thruster panel fuel is a single SHARED tank: filling a non-master
     * interior's fluid handler pools into the master's tank (so a pipe on any cell
     * feeds the whole panel). Drives serverTick directly (the open-sky panel isn't
     * in a gametest ticking region) so each interior caches the master first.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void fusionThrusterSharesOneTankAcrossInteriors(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos base = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        buildFusionPanel(level, base);

        // Tick each interior so they all resolve + cache the same master.
        for (int x = 1; x <= 3; x++) {
            final BlockPos p = base.offset(x, 1, 0);
            if (level.getBlockEntity(p) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity be) {
                com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(level, p, level.getBlockState(p), be);
            }
        }
        final BlockPos cornerPos = base.offset(3, 1, 0);   // a NON-master interior
        final BlockPos masterPos = base.offset(1, 1, 0);   // the deterministic master
        if (!(level.getBlockEntity(cornerPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity corner)
                || !(level.getBlockEntity(masterPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity master)) {
            helper.fail("missing fusion BEs"); return;
        }
        // Fill the CORNER's handler — it must pool into the MASTER's shared tank.
        corner.fluidHandler().fill(new net.neoforged.neoforge.fluids.FluidStack(
                        com.stonytark.magnetization.registry.MagFluids.HELIUM_3.get(), 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);

        final int masterMb = master.fluidHandler().getFluidInTank(0).getAmount();
        helper.assertTrue(masterMb == 1000,
                "Fuel piped into a non-master interior should pool in the master tank; master mB=" + masterMb);
        clearFusionPanel(level, base);
        helper.succeed();
    }

    /**
     * Companion to the shared-tank test: FE cabled to a non-master interior must
     * pool in the master's buffer (only the master drains FE to fire). Without the
     * energy forwarding, that FE would be stranded and the engine never powers
     * unless the player happens to cable the invisible min-corner master block.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void fusionThrusterSharesOneEnergyBufferAcrossInteriors(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos base = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        buildFusionPanel(level, base);
        for (int x = 1; x <= 3; x++) {
            final BlockPos p = base.offset(x, 1, 0);
            if (level.getBlockEntity(p) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity be) {
                com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(level, p, level.getBlockState(p), be);
            }
        }
        final BlockPos cornerPos = base.offset(3, 1, 0);   // a NON-master interior
        final BlockPos masterPos = base.offset(1, 1, 0);   // the deterministic master
        if (!(level.getBlockEntity(cornerPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity corner)
                || !(level.getBlockEntity(masterPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity master)) {
            helper.fail("missing fusion BEs"); return;
        }
        // Cable FE into the CORNER's buffer — it must pool into the MASTER's buffer.
        corner.energyBuffer().receiveEnergy(5000, false);
        final int masterFe = master.energyBuffer().getEnergyStored();
        helper.assertTrue(masterFe == 5000,
                "FE cabled into a non-master interior should pool in the master buffer; master FE=" + masterFe);
        helper.assertTrue(corner.guiEnergyStored() == 5000,
                "A non-master Fusion Thruster HUD should show the shared master FE; HUD FE="
                        + corner.guiEnergyStored());
        corner.energyBuffer().receiveEnergy(2500, false);
        helper.assertTrue(corner.guiEnergyStored() == 7500,
                "The non-master Fusion Thruster HUD should update when shared FE changes; HUD FE="
                        + corner.guiEnergyStored());
        final net.minecraft.nbt.CompoundTag clientTag = corner.getUpdateTag(level.registryAccess());
        helper.assertTrue(clientTag.getBoolean("Formed") && clientTag.contains("Master")
                        && BlockPos.of(clientTag.getLong("Master")).equals(masterPos),
                "A non-master client update must include formed-panel master metadata");
        clearFusionPanel(level, base);
        helper.succeed();
    }

    /**
     * Every Tokamak Coil in a complete Fusion Thruster perimeter exposes the
     * panel master's shared FE capability. This includes diagonal corner coils;
     * an unrelated standalone coil must remain passive and expose no energy cap.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void fusionThrusterAcceptsEnergyThroughEveryFrameCoil(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos base = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        buildFusionPanel(level, base);

        int inserted = 0;
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 2; y++) {
                if (y == 1 && x >= 1 && x <= 3) continue;
                final BlockPos framePos = base.offset(x, y, 0);
                final net.neoforged.neoforge.energy.IEnergyStorage frameEnergy = level.getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                        framePos, null);
                if (frameEnergy == null) {
                    clearFusionPanel(level, base);
                    helper.fail("formed Fusion Thruster frame coil has no FE capability at " + framePos);
                    return;
                }
                inserted += frameEnergy.receiveEnergy(1000, false);
            }
        }

        final BlockPos masterPos = base.offset(1, 1, 0);
        if (!(level.getBlockEntity(masterPos)
                instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity master)) {
            clearFusionPanel(level, base);
            helper.fail("missing Fusion Thruster master BE");
            return;
        }
        helper.assertTrue(inserted == 12_000 && master.energyBuffer().getEnergyStored() == 12_000,
                "all 12 frame coils should feed the shared master buffer; inserted=" + inserted
                        + " master FE=" + master.energyBuffer().getEnergyStored());

        final BlockPos standalone = base.offset(6, 0, 0);
        level.setBlock(standalone, MagBlocks.TOKAMAK_COIL.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        final net.neoforged.neoforge.energy.IEnergyStorage unrelated = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                standalone, null);
        helper.assertTrue(unrelated == null,
                "a standalone Tokamak Coil must not expose Fusion Thruster FE storage");

        level.setBlock(standalone, Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        clearFusionPanel(level, base);
        helper.succeed();
    }

    // ── Hopper fuel intake (GitHub #3): every item-fuel machine exposes an
    //    ItemHandler so hoppers / Create automation can feed it. ──

    /**
     * The Tokamak's fuel slot accepts a fusion cell through its item-handler
     * capability (hopper insert), rejects a non-fuel item, and refuses to hand the
     * cell back out — so a hopper below can't siphon fuel or reset the burn.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void tokamakFuelIntakeAcceptsCellsAndBlocksTheft(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.TOKAMAK_CONTROLLER.get());
        final net.neoforged.neoforge.items.IItemHandler handler = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "Tokamak should expose an item handler for hoppers");

        final net.minecraft.world.item.ItemStack cell =
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.DEUTERIUM_CELL.get());
        helper.assertTrue(handler.insertItem(0, cell, false).isEmpty(),
                "A deuterium cell should be fully accepted");
        helper.assertTrue(handler.getStackInSlot(0).is(com.stonytark.magnetization.registry.MagItems.DEUTERIUM_CELL.get()),
                "The cell should land in the fuel slot");

        final net.minecraft.world.item.ItemStack stone = new net.minecraft.world.item.ItemStack(Blocks.STONE);
        helper.assertTrue(handler.insertItem(0, stone, false).getCount() == 1,
                "A non-fuel item must be rejected by the fuel slot");

        helper.assertTrue(handler.extractItem(0, 64, false).isEmpty(),
                "Valid fuel must never be extractable (no hopper theft / burn refresh)");
        helper.succeed();
    }

    /**
     * The Homopolar Motor's magnet slot accepts a potency magnet through its
     * item-handler capability and won't surrender a live magnet back to a hopper.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void motorMagnetIntakeAcceptsMagnet(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.HOMOPOLAR_MOTOR.get());
        final net.neoforged.neoforge.items.IItemHandler handler = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "Motor should expose an item handler for hoppers");

        final net.minecraft.world.item.ItemStack magnet =
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get());
        helper.assertTrue(handler.insertItem(0, magnet, false).isEmpty(),
                "A potency magnet should be accepted into the motor");
        helper.assertTrue(handler.getStackInSlot(0).is(com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get()),
                "The magnet should land in the motor slot");

        helper.assertTrue(handler.extractItem(0, 64, false).isEmpty(),
                "A live magnet must not be extractable");
        helper.succeed();
    }

    /**
     * The MHD jet has the same automation boundary as the motor, but its
     * magnet is consumed by the propulsion path. Exercise the real block
     * capability rather than the right-click setter so a hopper can actually
     * supply the jet.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void mhdJetMagnetIntakeAcceptsMagnet(final GameTestHelper helper) {
        com.stonytark.magnetization.config.MagConfig.HOPPER_FUEL_INTAKE.set(true);
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.MHD_JET.get());
        final net.neoforged.neoforge.items.IItemHandler handler = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "MHD Jet should expose an item handler for hoppers");

        final net.minecraft.world.item.ItemStack magnet =
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get());
        helper.assertTrue(handler.insertItem(0, magnet, false).isEmpty(),
                "A potency magnet should be accepted into the MHD jet");
        helper.assertTrue(handler.getStackInSlot(0).is(com.stonytark.magnetization.registry.MagItems.MAGNETITE_INGOT.get()),
                "The magnet should land in the MHD jet slot");
        helper.assertTrue(handler.extractItem(0, 64, false).isEmpty(),
                "A live MHD magnet must not be extractable");
        helper.succeed();
    }

    /**
     * A formed Fusion panel must accept a fuel bucket through the item handler
     * on a non-master interior. The next real machine tick drains that slot
     * into the master's shared tank, which is the path hopper automation uses.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60)
    public static void fusionNonMasterItemIntakeForwardsToMaster(final GameTestHelper helper) {
        com.stonytark.magnetization.config.MagConfig.HOPPER_FUEL_INTAKE.set(true);
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos base = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        buildFusionPanel(level, base);
        for (int x = 1; x <= 3; x++) {
            final BlockPos p = base.offset(x, 1, 0);
            if (level.getBlockEntity(p) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity be) {
                com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(
                        level, p, level.getBlockState(p), be);
            }
        }

        final BlockPos cornerPos = base.offset(3, 1, 0);
        final BlockPos masterPos = base.offset(1, 1, 0);
        if (!(level.getBlockEntity(cornerPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity corner)
                || !(level.getBlockEntity(masterPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity master)) {
            helper.fail("missing fusion BEs"); return;
        }
        final net.neoforged.neoforge.items.IItemHandler handler = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, cornerPos, null);
        helper.assertTrue(handler != null, "Fusion non-master should expose an item handler");
        final net.minecraft.world.item.ItemStack fuel = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.HELIUM_3_BUCKET.get());
        helper.assertTrue(handler.insertItem(0, fuel, false).isEmpty(),
                "A helium-3 bucket should be accepted on a non-master interior");

        com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(
                level, cornerPos, level.getBlockState(cornerPos), corner);
        helper.assertTrue(master.fluidHandler().getFluidInTank(0).getAmount() == 1000,
                "Non-master automation should forward fuel to the master tank");
        helper.assertTrue(corner.bucketContainer().getItem(0).is(net.minecraft.world.item.Items.BUCKET),
                "The tick should leave an empty bucket in the non-master slot");
        clearFusionPanel(level, base);
        helper.succeed();
    }

    /**
     * Capability invalidation is observable when a panel's deterministic master
     * changes: a cached fluid capability must stop writing to the removed
     * master's tank and resolve the new one after the next validation scan.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void fusionCapabilityFollowsChangedPanelMaster(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos base = new BlockPos(helper.absolutePos(new BlockPos(1, 1, 1)).getX(), 240,
                helper.absolutePos(new BlockPos(1, 1, 1)).getZ());
        buildFusionPanel(level, base);
        for (int x = 1; x <= 3; x++) {
            final BlockPos p = base.offset(x, 1, 0);
            if (level.getBlockEntity(p) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity be) {
                com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(
                        level, p, level.getBlockState(p), be);
            }
        }
        final BlockPos cornerPos = base.offset(3, 1, 0);
        final BlockPos oldMasterPos = base.offset(1, 1, 0);
        if (!(level.getBlockEntity(cornerPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity corner)
                || !(level.getBlockEntity(oldMasterPos) instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity oldMaster)) {
            helper.fail("missing initial fusion BEs"); return;
        }
        final net.neoforged.neoforge.fluids.capability.IFluidHandler before = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, cornerPos, null);
        helper.assertTrue(before != null, "Fusion non-master should expose a fluid capability");
        before.fill(new net.neoforged.neoforge.fluids.FluidStack(
                com.stonytark.magnetization.registry.MagFluids.HELIUM_3.get(), 1000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(oldMaster.fluidHandler().getFluidInTank(0).getAmount() == 1000,
                "Initial capability should write to the initial master");

        // Replacing the old master with a coil leaves a valid, smaller panel
        // whose interior is x=2..3, so x=2 becomes the new deterministic master.
        level.setBlock(oldMasterPos, MagBlocks.TOKAMAK_COIL.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        helper.runAfterDelay(21L, () -> {
            com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity.serverTick(
                    level, cornerPos, level.getBlockState(cornerPos), corner);
            final BlockPos newMasterPos = base.offset(2, 1, 0);
            if (!(level.getBlockEntity(newMasterPos)
                    instanceof com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity newMaster)) {
                helper.fail("new fusion master BE missing"); return;
            }
            final net.neoforged.neoforge.fluids.capability.IFluidHandler after = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, cornerPos, null);
            helper.assertTrue(after != null, "Fusion capability should remain available after master change");
            after.fill(new net.neoforged.neoforge.fluids.FluidStack(
                    com.stonytark.magnetization.registry.MagFluids.HELIUM_3.get(), 1000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(newMaster.fluidHandler().getFluidInTank(0).getAmount() == 1000,
                    "Invalidated capability should write to the new master");
            helper.assertTrue(oldMaster.fluidHandler().getFluidInTank(0).getAmount() == 1000,
                    "A stale capability must not continue writing to the old master");
            clearFusionPanel(level, base);
            helper.succeed();
        });
    }

    /**
     * Drives the registered serverbound Curios payload with a real
     * {@link net.minecraft.server.level.ServerPlayer} and the real Curios
     * inventory capability. The same shared activation path must stamp the
     * actual charm stack, emit the player sound, and arm the normal cooldown;
     * a second packet is rejected by that cooldown rather than firing again.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "curiosActivation")
    public static void curioRepulsorPayloadActivatesRealCharm(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "curio-repulsor-test"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            protected net.minecraft.world.item.ItemCooldowns createItemCooldowns() {
                // ServerItemCooldowns sends packets through connection; this
                // headless test player has no client, but still needs the real
                // cooldown map and timing semantics.
                return new net.minecraft.world.item.ItemCooldowns();
            }
        };
        final BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, 300.0, pos.getZ() + 0.5);
        // Keep this payload test focused on Curios dispatch. Looking straight
        // up prevents the repulsor's optional magnetic-emitter recoil branch
        // from depending on a GameTest player's absent network connection.
        player.setXRot(-90.0F);
        final net.minecraft.world.item.ItemStack gun = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.REPULSOR_GUN.get());
        helper.assertTrue(placeCurio(player, gun), "The mock server player should have a Curios slot");

        CURIO_SOUND_EVENTS.set(0);
        final com.stonytark.magnetization.network.UseCurioPayload payload =
                new com.stonytark.magnetization.network.UseCurioPayload(
                        com.stonytark.magnetization.network.UseCurioPayload.Kind.REPULSOR_GUN);
        final boolean originalMaster = com.stonytark.magnetization.config.MagConfig.CURIOS_COMPAT_ENABLED.get();
        try {
            com.stonytark.magnetization.config.MagConfig.CURIOS_COMPAT_ENABLED.set(false);
            com.stonytark.magnetization.network.UseCurioPayload.handleServerbound(payload, player);
            helper.assertTrue(gun.get(com.stonytark.magnetization.registry.MagDataComponents.FIRED_AT.get()) == null
                            && CURIO_SOUND_EVENTS.get() == 0,
                    "Curios master switch did not suppress serverbound item activation");

            com.stonytark.magnetization.config.MagConfig.CURIOS_COMPAT_ENABLED.set(true);
            com.stonytark.magnetization.network.UseCurioPayload.handleServerbound(payload, player);
            final Long firstStamp = gun.get(com.stonytark.magnetization.registry.MagDataComponents.FIRED_AT.get());
            final int firstSounds = CURIO_SOUND_EVENTS.get();
            helper.assertTrue(firstStamp != null,
                    "Serverbound Curios activation should stamp FIRED_AT on the charm-slot stack");
            helper.assertTrue(player.getCooldowns().isOnCooldown(
                            com.stonytark.magnetization.registry.MagItems.REPULSOR_GUN.get()),
                    "Repulsor activation should arm its normal cooldown");
            helper.assertTrue(firstSounds > 0,
                    "Serverbound Curios activation should emit the same player sound as hand use");

            com.stonytark.magnetization.network.UseCurioPayload.handleServerbound(payload, player);
            helper.assertTrue(java.util.Objects.equals(firstStamp,
                            gun.get(com.stonytark.magnetization.registry.MagDataComponents.FIRED_AT.get())),
                    "Cooldown spam must not rewrite the source-stack fire component");
            helper.assertTrue(CURIO_SOUND_EVENTS.get() == firstSounds,
                    "Cooldown spam must not emit a second activation sound");
            helper.succeed();
        } finally {
            com.stonytark.magnetization.config.MagConfig.CURIOS_COMPAT_ENABLED.set(originalMaster);
            player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
    }

    /**
     * The grapple variant uses a real Curios stack and a real serverbound
     * payload, then verifies logout cleanup removes the UUID-keyed sustained
     * pull. This catches both the source-stack/component regression and stale
     * activation state surviving a disconnect.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 60, batch = "curiosActivation")
    public static void curioGrapplePayloadClearsOnLogout(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.server.level.ServerPlayer player = new net.minecraft.server.level.ServerPlayer(
                level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "curio-grapple-test"),
                net.minecraft.server.level.ClientInformation.createDefault());
        final BlockPos playerPos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5);
        final net.minecraft.world.entity.animal.Cow target = helper.spawn(
                net.minecraft.world.entity.EntityType.COW, new BlockPos(5, 1, 1));
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.stonytark.magnetization.registry.MagEffects.MAGNETIZED, 200));
        final net.minecraft.world.item.ItemStack grapple = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.MAGNETIC_GRAPPLE.get());
        helper.assertTrue(placeCurio(player, grapple), "The mock server player should have a Curios slot");

        CURIO_SOUND_EVENTS.set(0);
        final com.stonytark.magnetization.network.UseCurioPayload payload =
                new com.stonytark.magnetization.network.UseCurioPayload(
                        com.stonytark.magnetization.network.UseCurioPayload.Kind.GRAPPLE);
        com.stonytark.magnetization.network.UseCurioPayload.handleServerbound(payload, player);
        helper.assertTrue(com.stonytark.magnetization.content.item.GrappleTickHandler.isPulling(player),
                "Serverbound Curios grapple should start the real sustained pull");
        helper.assertTrue(grapple.get(com.stonytark.magnetization.registry.MagDataComponents.FIRED_AT.get()) != null,
                "Grapple activation should stamp FIRED_AT on the Curios source stack");
        helper.assertTrue(CURIO_SOUND_EVENTS.get() > 0,
                "Grapple Curios activation should emit its normal player sound");

        com.stonytark.magnetization.content.item.PlayerStateCleanupHandler.onLogout(
                new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
        helper.assertTrue(!com.stonytark.magnetization.content.item.GrappleTickHandler.isPulling(player),
                "Logout cleanup must remove the active grapple for that player UUID");
        target.discard();
        player.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        helper.succeed();
    }

    /**
     * Bucket-fed machines (here the Micro Thruster): a full fuel bucket inserts but
     * can't be siphoned back; once the machine has drained it to a plain empty
     * bucket, a hopper CAN pull that empty for recirculation — and it can't be
     * re-inserted as fuel. This exercises the wrapper's "spent-only extraction" rule.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40)
    public static void bucketMachineRecirculatesEmptyBucket(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.MICRO_THRUSTER.get());
        final net.neoforged.neoforge.items.IItemHandler handler = helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "Micro Thruster should expose an item handler");

        final net.minecraft.world.item.ItemStack fuel =
                new net.minecraft.world.item.ItemStack(com.stonytark.magnetization.registry.MagItems.FERROFLUID_BUCKET.get());
        helper.assertTrue(handler.insertItem(0, fuel, false).isEmpty(),
                "A ferrofluid bucket should be accepted as fuel");
        helper.assertTrue(handler.extractItem(0, 1, false).isEmpty(),
                "A full fuel bucket must not be extractable (no theft)");

        final com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity be =
                (com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity) helper.getBlockEntity(pos);
        // Drive the actual server tick: the machine consumes the inserted fuel
        // bucket and replaces it with a plain empty bucket.
        com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity.serverTick(
                helper.getLevel(), helper.absolutePos(pos), helper.getLevel().getBlockState(helper.absolutePos(pos)), be);

        final net.minecraft.world.item.ItemStack pulled = handler.extractItem(0, 1, false);
        helper.assertTrue(pulled.is(net.minecraft.world.item.Items.BUCKET),
                "An emptied bucket should be extractable for recirculation; got " + pulled);
        helper.assertTrue(handler.insertItem(0,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET), false).getCount() == 1,
                "An empty bucket must not be insertable as fuel");
        helper.succeed();
    }

    /**
     * Regression guard for the Magnetic Switch (GitHub #4): a switch in the open
     * world reads a rising redstone signal when a Sable contraption comes within
     * range. This covers the scan/refactor path that the on-ship fix shares. (The
     * on-ship promotion — a switch mounted on a ship detecting OTHER ships via its
     * host pose — mirrors the gyrostabilizer sable$tick pattern and is confirmed
     * in-world, like every other sable$tick behaviour in this suite.)
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void magneticSwitchDetectsNearbyShip(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos switchRel = new BlockPos(1, 2, 1);
        final BlockPos sky = new BlockPos(helper.absolutePos(switchRel).getX(), 240, helper.absolutePos(switchRel).getZ());
        final BlockPos shipBase = helper.absolutePos(switchRel).offset(3, 0, 0);
        final BlockPos shipSky = new BlockPos(shipBase.getX(), 240, shipBase.getZ());

        level.setBlock(sky, MagBlocks.MAGNETIC_SWITCH.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        helper.runAfterDelay(2L, () -> {
            final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                    assembleSingleBlockShip(level, shipBase, Blocks.IRON_BLOCK);
            teleportShip(level, ship, shipSky);   // park it 3 blocks from the switch

            helper.runAfterDelay(10L, () -> {
                final com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity be =
                        (com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity) level.getBlockEntity(sky);
                // Drive the throttled world ticker a few times so the scan runs.
                for (int i = 0; i < 8; i++) {
                    com.stonytark.magnetization.content.switchblock.MagneticSwitchBlockEntity.serverTick(
                            level, sky, level.getBlockState(sky), be);
                }
                final int signal = be.signal();
                removeShip(level, ship);
                helper.assertTrue(signal > 0,
                        "A switch should sense a contraption 3 blocks away; signal=" + signal);
                helper.succeed();
            });
        });
    }

    /**
     * The "Hopper Fuel Intake" config toggle (Machine Tuning, default on) gates the
     * item-handler capability: off, a machine exposes no handler so hoppers can't
     * feed it; on, it does. Runs in the config-mutating batch and restores the
     * default in a finally.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 40, batch = "configMutating")
    public static void hopperIntakeToggleGatesItemHandler(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.TOKAMAK_CONTROLLER.get());
        final BlockPos abs = helper.absolutePos(pos);
        try {
            com.stonytark.magnetization.config.MagConfig.HOPPER_FUEL_INTAKE.set(false);
            helper.getLevel().invalidateCapabilities(abs);
            helper.assertTrue(helper.getLevel().getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, abs, null) == null,
                    "With Hopper Fuel Intake off, a machine must expose no item handler");

            com.stonytark.magnetization.config.MagConfig.HOPPER_FUEL_INTAKE.set(true);
            helper.getLevel().invalidateCapabilities(abs);
            helper.assertTrue(helper.getLevel().getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, abs, null) != null,
                    "With Hopper Fuel Intake on, a machine must expose an item handler");
        } finally {
            com.stonytark.magnetization.config.MagConfig.HOPPER_FUEL_INTAKE.set(true);
        }
        helper.succeed();
    }

    /**
     * Audit #8 — machine fuel/fluid bar denominators are SERVER-authoritative: the BE
     * exposes the tank/burn max via guiStat4 (computed from the server's config), which
     * the menu syncs so a multiplayer client with a different COMMON config still draws
     * the right fill. Asserts the exposed max matches the server config value.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 20)
    public static void machineBarMaxIsServerAuthoritative(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MagBlocks.MICRO_THRUSTER.get());
        final com.stonytark.magnetization.menu.MachineGuiData be =
                (com.stonytark.magnetization.menu.MachineGuiData) helper.getBlockEntity(pos);
        helper.assertTrue(be.guiStat4() == com.stonytark.magnetization.config.MagConfig.microThrusterTank(),
                "Micro Thruster bar max should equal the server-config tank; got " + be.guiStat4());
        final com.stonytark.magnetization.menu.MachineDisplayData display = be.displayData();
        helper.assertTrue(display.capacity() == com.stonytark.magnetization.config.MagConfig.microThrusterTank(),
                "Named display capacity must use the server-config tank; got " + display.capacity());
        helper.assertTrue(display.current() == be.guiStat1() && display.auxiliary() == be.guiStat2(),
                "Named display snapshot must preserve current/auxiliary values");
        helper.assertTrue(display.status() == com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE,
                "An empty, unpowered thruster should report IDLE; got " + display.status());
        helper.succeed();
    }

    /**
     * Audit #9 — GUI/HUD status requirements must describe the actual machine.
     * Generators do not require FE input, and a thruster containing fuel/power
     * but not mounted on a ship is not actively firing.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 20)
    public static void machineStatusRequirementsMatchOperation(final GameTestHelper helper) {
        final BlockPos motorPos = new BlockPos(1, 1, 1);
        helper.setBlock(motorPos, MagBlocks.HOMOPOLAR_MOTOR.get());
        final com.stonytark.magnetization.menu.MachineGuiData motor =
                (com.stonytark.magnetization.menu.MachineGuiData) helper.getBlockEntity(motorPos);
        helper.assertTrue(motor.guiEnergyStored() < 0,
                "Homopolar Motor must not expose an FE input bar; it is a generator");
        helper.assertTrue(motor.displayData().status()
                        == com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE,
                "Empty Homopolar Motor should be idle");
        final net.minecraft.network.chat.Component motorIdle =
                com.stonytark.magnetization.menu.MachineGuiData.statusLine(
                        com.stonytark.magnetization.menu.MachineMenu.Kind.MOTOR,
                        com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE);
        helper.assertTrue(motorIdle.getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                        && contents.getKey().equals("tooltip.magnetization.machine_idle_motor"),
                "Homopolar status must use the magnet-only idle requirement");

        final BlockPos thrusterPos = new BlockPos(2, 1, 1);
        helper.setBlock(thrusterPos, MagBlocks.MICRO_THRUSTER.get());
        if (!(helper.getBlockEntity(thrusterPos)
                instanceof com.stonytark.magnetization.content.jet.MicroThrusterBlockEntity thruster)) {
            helper.fail("no micro-thruster BE");
            return;
        }
        thruster.fluidHandler().fill(new net.neoforged.neoforge.fluids.FluidStack(
                MagFluids.FERROFLUID.get(), 1_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        thruster.energyBuffer().receiveEnergy(1_000, false);
        helper.assertTrue(thruster.displayData().status()
                        == com.stonytark.magnetization.menu.MachineDisplayData.Status.IDLE,
                "A fueled and powered thruster without a ship must remain idle");
        helper.succeed();
    }

    /**
     * Redstone controls thrust by default without becoming a hidden FE source;
     * the propulsion config can explicitly restore redstone as the power source.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 20, batch = "thrustRedstoneControl")
    public static void thrustRedstoneControlDefaultsToInhibit(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos thrust = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos signal = thrust.east();
        final boolean prior = MagConfig.ALLOW_REDSTONE_THRUST_POWER.get();
        final boolean priorCompat = MagConfig.ALLOW_REDSTONE_POWER.get();
        try {
            MagConfig.ALLOW_REDSTONE_THRUST_POWER.set(false);
            MagConfig.ALLOW_REDSTONE_POWER.set(false);
            helper.assertTrue(com.stonytark.magnetization.content.jet.ThrustControl.canRun(
                            level, thrust, true, true),
                    "An unpowered FE/propellant thrust machine should run normally");
            level.setBlock(signal, Blocks.REDSTONE_BLOCK.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
            helper.assertTrue(!com.stonytark.magnetization.content.jet.ThrustControl.canRun(
                            level, thrust, true, true),
                    "A redstone signal should stop resource-powered thrust by default");

            MagConfig.ALLOW_REDSTONE_THRUST_POWER.set(true);
            helper.assertTrue(com.stonytark.magnetization.content.jet.ThrustControl.canRun(
                            level, thrust, true, false),
                    "The propulsion redstone-power setting should replace FE input even when compat redstone power is disabled");
            MagConfig.ALLOW_REDSTONE_THRUST_POWER.set(false);
            helper.assertTrue(!com.stonytark.magnetization.content.jet.ThrustControl.canRun(
                            level, thrust, true, true),
                    "The propulsion setting should control thrust independently of compat redstone power");
            helper.assertTrue(!com.stonytark.magnetization.content.jet.ThrustControl.passiveThrustAllowed(
                            level, thrust),
                    "A redstone signal should stop passive thrust such as a Solar Sail");
        } finally {
            MagConfig.ALLOW_REDSTONE_THRUST_POWER.set(prior);
            MagConfig.ALLOW_REDSTONE_POWER.set(priorCompat);
            level.setBlock(signal, Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        helper.succeed();
    }

    /**
     * Persistent migration framework semantics: a migration version runs once per
     * chunk, a restart-safe completion record suppresses the second attempt, and
     * an explicit version bump is allowed exactly once for that same chunk.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 20)
    public static void versionedChunkMigrationRunsOncePerVersion(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(
                helper.absolutePos(new BlockPos(1, 1, 1)));
        final java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        // GameTest worlds can be reused between launches; scope the synthetic
        // id to this test chunk so an older run's completion record cannot make
        // this run's one-chunk count ambiguous.
        final String id = "gametest_versioned_migration_" + chunk.toLong();
        helper.assertTrue(com.stonytark.magnetization.worldgen.WorldChunkMigrations.apply(
                        level, id, 1, chunk, runs::incrementAndGet),
                "First migration version should run");
        helper.assertTrue(!com.stonytark.magnetization.worldgen.WorldChunkMigrations.apply(
                        level, id, 1, chunk, runs::incrementAndGet),
                "The same migration version must not run twice");
        helper.assertTrue(com.stonytark.magnetization.worldgen.WorldChunkMigrations.apply(
                        level, id, 2, chunk, runs::incrementAndGet),
                "An explicit migration version bump should run once");
        helper.assertTrue(runs.get() == 2,
                "Migration action should run exactly once per applied version; runs=" + runs.get());
        helper.assertTrue(com.stonytark.magnetization.worldgen.WorldChunkMigrations.version(level, id) == 2
                        && com.stonytark.magnetization.worldgen.WorldChunkMigrations.completedCount(level, id) == 1,
                "SavedData should retain version 2 for one chunk");
        helper.succeed();
    }

    /**
     * Optional AeroPortals integration: move an actual serialized Sable ship to
     * the Nether, then prove its UUID, derived magnetic state, railgun BE state,
     * and installed remote binding all followed the reconstructed sublevel.
     * Run with {@code ./gradlew runAeroPortalsGameTestServer}; the normal minimal
     * suite treats an absent optional mod as not applicable.
     */
    public static void aeroPortalsRetainsMagneticShipState(final GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("aeroportals")) {
            helper.succeed();
            return;
        }

        final net.minecraft.server.level.ServerLevel src = helper.getLevel();
        final net.minecraft.server.level.ServerLevel dst = src.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (dst == null) { helper.fail("AeroPortals compat test requires the Nether"); return; }

        final BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        final BlockPos bearingWorldPos = origin.south();
        final java.util.List<BlockPos> blocks = java.util.List.of(
                origin, origin.east(), origin.east(2), origin.east(3), bearingWorldPos);
        src.setBlock(origin, MagBlocks.POLARITY_INVERTER.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        src.setBlock(origin.east(), MagBlocks.MAGNETITE_BLOCK.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        src.setBlock(origin.east(2), MagBlocks.RAILGUN_EMITTER.get().defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        src.setBlock(origin.east(3), Blocks.CHEST.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) src.getBlockEntity(origin.east(3)))
                .setItem(0, new net.minecraft.world.item.ItemStack(
                        com.stonytark.magnetization.registry.MagItems.HELIUM_3_CELL.get(), 5));
        src.setBlock(bearingWorldPos,
                dev.simulated_team.simulated.index.SimBlocks.SWIVEL_BEARING.getDefaultState()
                        .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING,
                                net.minecraft.core.Direction.SOUTH)
                        .setValue(dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock.ASSEMBLED,
                                true),
                net.minecraft.world.level.block.Block.UPDATE_ALL);

        final var bounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 4, origin.getY() + 1, origin.getZ() + 2);
        final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(src, origin, blocks, bounds);
        if (ship == null) { helper.fail("Could not assemble AeroPortals compatibility ship"); return; }

        final java.util.UUID uuid = ship.getUniqueId();
        final var before = com.stonytark.magnetization.physics.ShipMagneticRegistry.get(src, ship);
        final com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity oldEmitter =
                findRailgunEmitter(ship);
        if (oldEmitter == null) {
            removeShip(src, ship);
            helper.fail("Assembled ship lost its railgun emitter before transfer");
            return;
        }
        final BlockPos childOrigin = origin.offset(0, 0, 8);
        src.setBlock(childOrigin,
                dev.simulated_team.simulated.index.SimBlocks.SWIVEL_BEARING_LINK_BLOCK.getDefaultState()
                        .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING,
                                net.minecraft.core.Direction.SOUTH),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        final var childBounds = new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                childOrigin.getX(), childOrigin.getY(), childOrigin.getZ(),
                childOrigin.getX() + 1, childOrigin.getY() + 1, childOrigin.getZ() + 1);
        final dev.ryanhcode.sable.sublevel.ServerSubLevel child =
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                        src, childOrigin, java.util.List.of(childOrigin), childBounds);
        if (child == null) {
            removeShip(src, ship);
            helper.fail("Could not assemble swivel-attached child ship");
            return;
        }
        final java.util.UUID childUuid = child.getUniqueId();
        final var oldBearing = findSwivelBearing(ship);
        if (oldBearing == null) {
            removeShip(src, child);
            removeShip(src, ship);
            helper.fail("Could not install Simulated swivel bearing on compatibility ship");
            return;
        }
        final BlockPos platePos = findSwivelPlatePos(child);
        if (platePos == null) {
            removeShip(src, child);
            removeShip(src, ship);
            helper.fail("Could not locate assembled Simulated swivel plate");
            return;
        }
        oldBearing.setPlatePos(platePos);
        oldBearing.setSubLevelID(childUuid);
        oldBearing.reattachConstraint(child, true);
        helper.assertTrue(hasSwivelConstraint(oldBearing),
                "Pre-transfer Simulated swivel constraint was not created");
        oldEmitter.setManualMode(true);
        oldEmitter.setRailLength(7);
        final net.minecraft.world.item.ItemStack remote = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get());
        com.stonytark.magnetization.content.railgun.RailgunRemoteItem.bind(
                remote, oldEmitter, src.dimension());
        oldEmitter.remoteContainer().setItem(0, remote);
        // NeoForge's mock connection rejects AeroPortals' custom sync payloads.
        // A no-client fixture still participates in the real transfer hook.
        final net.minecraft.server.level.ServerPlayer player =
                AeroPortalsGameTestSupport.addHeadlessOnlinePlayer(src, "aeroportals-remote");
        final net.minecraft.world.item.ItemStack heldRemote = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get());
        com.stonytark.magnetization.content.railgun.RailgunRemoteItem.bind(
                heldRemote, oldEmitter, src.dimension());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, heldRemote);
        final net.minecraft.world.item.ItemStack inventoryRemote = new net.minecraft.world.item.ItemStack(
                com.stonytark.magnetization.registry.MagItems.RAILGUN_REMOTE.get());
        com.stonytark.magnetization.content.railgun.RailgunRemoteItem.bind(
                inventoryRemote, oldEmitter, src.dimension());
        player.getInventory().setItem(0, inventoryRemote);

        final net.minecraft.world.phys.Vec3 destination = new net.minecraft.world.phys.Vec3(
                origin.getX() / 8.0 + 0.5, 160.0, origin.getZ() / 8.0 + 0.5);
        com.breakinblocks.aeroportals.portal.PortalTeleport.teleportToDimension(
                src, ship, dst, destination, true, "magnetization:gametest");

        // teleportToDimension posts SubLevelTransferEvent synchronously. Assert
        // before the distant destination chunk can unload and move the rebuilt
        // sublevel into Sable's holding map.
        ((Runnable) () -> {
            try {
            final var srcContainer = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(src);
            final dev.ryanhcode.sable.sublevel.ServerSubLevel moved =
                    com.stonytark.magnetization.compat.aeroportals.MagAeroPortalsCompat.consumeRecentTransfer(uuid);
            if (srcContainer != null && srcContainer.getSubLevel(uuid) != null) {
                helper.fail("AeroPortals left ship " + uuid + " registered in its source dimension");
                return;
            }
            if (moved == null) {
                helper.fail("AeroPortals did not reconstruct ship " + uuid + " in the Nether");
                return;
            }

            final var after = com.stonytark.magnetization.physics.ShipMagneticRegistry.get(dst, moved);
            final var newEmitter = findRailgunEmitter(moved);
            if (newEmitter == null) {
                helper.fail("Railgun emitter did not survive AeroPortals transfer");
                return;
            }
            final var movedBearing = findSwivelBearing(moved);
            if (movedBearing == null) {
                helper.fail("Simulated swivel bearing did not survive AeroPortals transfer");
                return;
            }
            final var movedChest = findChest(moved);
            if (movedChest == null) {
                helper.fail("Inventory chest did not survive AeroPortals transfer");
                return;
            }
            final net.minecraft.world.item.ItemStack movedRemote = newEmitter.remoteContainer().getItem(0);
            final boolean bindingRetained = dst.dimension().equals(
                    com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundDim(movedRemote))
                    && newEmitter.getBlockPos().equals(
                    com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundPos(movedRemote));
            final boolean beStateRetained = newEmitter.manualMode() && newEmitter.railLength() == 7;
            // AeroPortals may immediately park this far-away ship in Sable's
            // holding map. That object cannot be passed to removeSubLevel; this
            // profile has its own disposable world, so shutdown owns cleanup.

            helper.assertTrue(before.equals(after),
                    "Magnetic ship state changed across AeroPortals transfer: " + before + " -> " + after);
            helper.assertTrue(beStateRetained,
                    "Railgun BE state was not retained (manual=" + newEmitter.manualMode()
                            + ", length=" + newEmitter.railLength() + ")");
            helper.assertTrue(bindingRetained,
                    "Installed remote did not follow the moved railgun; pos="
                            + com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundPos(movedRemote)
                            + " dim=" + com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundDim(movedRemote)
                            + " expected=" + newEmitter.getBlockPos() + "@" + dst.dimension().location());
            helper.assertTrue(movedChest.getItem(0).is(
                                    com.stonytark.magnetization.registry.MagItems.HELIUM_3_CELL.get())
                            && movedChest.getItem(0).getCount() == 5,
                    "Ship inventory contents changed during AeroPortals transfer");
            helper.assertTrue(childUuid.equals(movedBearing.getSubLevelID()),
                    "Swivel bearing lost its attached child UUID during transfer");
            helper.assertTrue(!platePos.equals(movedBearing.getPlatePos()),
                    "Swivel plate plot position was not remapped during transfer");
            helper.assertTrue(hasSwivelConstraint(movedBearing),
                    "Swivel constraint was not reattached after AeroPortals reconstruction");
            helper.assertTrue(dst.dimension().equals(
                            com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundDim(player.getMainHandItem()))
                            && newEmitter.getBlockPos().equals(
                            com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundPos(player.getMainHandItem())),
                    "Player-held remote did not follow the moved railgun");
            final net.minecraft.world.item.ItemStack movedInventoryRemote = player.getInventory().getItem(0);
            helper.assertTrue(dst.dimension().equals(
                            com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundDim(movedInventoryRemote))
                            && newEmitter.getBlockPos().equals(
                            com.stonytark.magnetization.content.railgun.RailgunRemoteItem.boundPos(movedInventoryRemote)),
                    "Inventory remote did not follow the moved railgun");
            helper.succeed();
            } finally {
                AeroPortalsGameTestSupport.removeHeadlessOnlinePlayer(src, player);
            }
        }).run();
    }

    private static com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity findRailgunEmitter(
            final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity railgun) {
                    return railgun;
                }
            }
        }
        return null;
    }

    private static dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity
            findSwivelBearing(final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof
                        dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity bearing) {
                    return bearing;
                }
            }
        }
        return null;
    }

    private static net.minecraft.world.level.block.entity.ChestBlockEntity findChest(
            final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) return chest;
            }
        }
        return null;
    }

    private static BlockPos findSwivelPlatePos(
            final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        for (final var holder : ship.getPlot().getLoadedChunks()) {
            for (final BlockEntity blockEntity : holder.getChunk().getBlockEntities().values()) {
                if (blockEntity instanceof
                        dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity) {
                    return blockEntity.getBlockPos();
                }
            }
        }
        return null;
    }

    private static boolean hasSwivelConstraint(final Object bearing) {
        try {
            final java.lang.reflect.Field handle = bearing.getClass().getDeclaredField("handle");
            handle.setAccessible(true);
            return handle.get(bearing) != null;
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to inspect Simulated swivel constraint", exception);
        }
    }
}
