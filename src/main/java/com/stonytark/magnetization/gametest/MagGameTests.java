package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.content.fluid.GalliumRegistry;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
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
public final class MagGameTests {

    /** Template path only — the framework prepends the namespace from the
     *  containing {@link GameTestHolder}, and {@link PrefixGameTestTemplate}
     *  {@code (false)} suppresses the class-name prefix. */
    private static final String EMPTY_TEMPLATE = "empty";

    private MagGameTests() {}

    private static int drainPerTickFromConfig() {
        try { return com.stonytark.magnetization.config.MagConfig.EMITTER_ENERGY_DRAIN_PER_TICK.get(); }
        catch (final Throwable t) { return 10; }
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
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void sensorEmitsRedstoneOnMovement(final GameTestHelper helper) {
        final BlockPos sensorPos = new BlockPos(1, 1, 1);
        helper.setBlock(sensorPos, MagBlocks.MAGNETOSTRICTIVE_SENSOR.get());

        // Spawn a cow two blocks away (well within the 8-block range), AI/gravity
        // off so it stays put except for the velocity we inject.
        final net.minecraft.world.entity.animal.Cow cow =
                helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(3, 1, 1));
        cow.setNoAi(true);
        cow.setNoGravity(true);

        // Re-apply horizontal motion every tick for the first 9 ticks so that,
        // whatever tick the sensor's 2-tick scan lands on, getKnownMovement is
        // non-zero and above the move threshold.
        for (long t = 1; t <= 9; t++) {
            helper.runAfterDelay(t, () -> cow.setDeltaMovement(0.3, 0.0, 0.0));
        }

        helper.runAfterDelay(10L, () -> {
            final BlockEntity be = helper.getBlockEntity(sensorPos);
            if (!(be instanceof com.stonytark.magnetization.content.sensor.MagnetostrictiveSensorBlockEntity sensor)) {
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
        for (long t = 2; t <= 40; t += 2) {
            helper.runAfterDelay(t, () -> {
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
                    "Magnetized Barkhausen should emit a non-zero signal across 20 samples; max=" + maxMagSignal[0]);
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

        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(be.energyBuffer().getEnergyStored() > 0,
                    "Tokamak should charge its buffer with a ring + fuel; FE=" + be.energyBuffer().getEnergyStored());
            helper.assertTrue(helper.getBlockState(controller)
                            .getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),
                    "Tokamak should be LIT while fusing");
            helper.succeed();
        });
    }

    /**
     * #91 — MR Fluid hardens to a solid block when inside an active magnetic field.
     * Places an MR-fluid source beside a redstone-powered electromagnet and asserts
     * the fluid cell becomes {@code hardened_mr_fluid} once the field reaches it.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void mrFluidHardensInField(final GameTestHelper helper) {
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
                if (h == null) { helper.fail("no ship handle"); return; }
                h.addLinearAndAngularVelocity(new org.joml.Vector3d(0, 0, 0.15), new org.joml.Vector3d()); // drift past, > MIN_SPEED
                helper.runAfterDelay(12L, () -> {
                    final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(coil);
                    if (!(be instanceof com.stonytark.magnetization.content.induction.KineticCoilBlockEntity kc)) {
                        helper.fail("no coil BE"); return;
                    }
                    final int fe = kc.energyBuffer().getEnergyStored();
                    removeShip(level, ship);
                    helper.assertTrue(fe > 0,
                            "Kinetic coil should generate FE from a passing ship; FE=" + fe);
                    helper.succeed();
                });
            });
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
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void diamagneticShipRepelledWhileFerrousAttracted(final GameTestHelper helper) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        final BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
        final BlockPos em = new BlockPos(a.getX(), 240, a.getZ());
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
                final org.joml.Vector3d vDia = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(dia)
                        .getLinearVelocity(new org.joml.Vector3d());
                final org.joml.Vector3d vIron = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(iron)
                        .getLinearVelocity(new org.joml.Vector3d());
                final boolean ok = vDia.x > 0.0 && vIron.x < 0.0;
                removeShip(level, dia);
                removeShip(level, iron);
                helper.assertTrue(ok,
                        "Diamagnetic ship should be pushed away (+X) while ferrous is pulled in (-X): "
                                + "dia.x=" + vDia.x + " iron.x=" + vIron.x);
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
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 160)
    public static void directionalRepulsorDragsConeShipInSelectedDir(final GameTestHelper helper) {
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
                final org.joml.Vector3d v = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship)
                        .getLinearVelocity(new org.joml.Vector3d());
                final boolean draggedNorth = v.z < -0.1;
                removeShip(level, ship);
                helper.assertTrue(draggedNorth,
                        "Vector-core repulsor should drag a ship in its cone toward NORTH (−Z); v.z=" + v.z);
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
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void galliumLorentzPushesEntity(final GameTestHelper helper) {
        galliumPushTest(helper, MagBlocks.GALLIUM_BLOCK.get());
    }

    /**
     * #104 — Mixed gallium carries the same Lorentz entity-push as plain gallium
     * (the second of its two abilities; the ferrofluid-style creep is a slow
     * block-spread covered by registry membership). Same assertion as #103.
     */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void mixedGalliumLorentzPushesEntity(final GameTestHelper helper) {
        galliumPushTest(helper, MagBlocks.MIXED_GALLIUM_BLOCK.get());
    }

    private static void galliumPushTest(final GameTestHelper helper, final net.minecraft.world.level.block.Block galliumBlock) {
        final net.minecraft.server.level.ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 1), galliumBlock);
        helper.setBlock(new BlockPos(1, 1, 2), MagBlocks.PERMANENT_MAGNET.get()); // field over the gallium

        // Wiring test. GalliumLorentzHandler drives its per-tick entity push when a
        // gallium cell is (a) a tracked Lorentz source, (b) carrying a redstone
        // current, and (c) in a magnetic field. We verify (a) and (c) hold for a
        // placed gallium cell under a magnet, then succeed. We deliberately omit the
        // redstone source and a floating entity: a powered gallium fluid cell ticking
        // in the shared GameTest arena spins the batch runner indefinitely (game
        // ticks never advance for the test). The redstone-current gating and the
        // actual entity push magnitude (drag-dependent inside a fluid) are validated
        // in-world. The short delay lets the magnet's BlockEntity#onLoad register its
        // field (registration is not synchronous with setBlock).
        final BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(GalliumRegistry.snapshot(level).contains(abs),
                "Placed gallium should register itself as a tracked Lorentz source in GalliumRegistry");
        helper.runAfterDelay(4L, () -> {
            helper.assertTrue(MagneticFields.nearestField(level, net.minecraft.world.phys.Vec3.atCenterOf(abs)) != null,
                    "Gallium under a permanent magnet should sit in a magnetic field");
            helper.succeed();
        });
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

        helper.runAfterDelay(50L, () -> {
            helper.assertTrue(helper.getBlockState(g).getBlock() == MagBlocks.SOLID_GALLIUM.get(),
                    "Gallium next to ice should freeze to solid_gallium; got "
                            + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(helper.getBlockState(g).getBlock()));
            helper.setBlock(new BlockPos(2, 1, 1), Blocks.AIR); // remove cooling → schedules melt
            helper.runAfterDelay(140L, () -> {
                helper.assertTrue(helper.getBlockState(g).getBlock() == MagBlocks.GALLIUM_BLOCK.get(),
                        "Solid gallium should melt back to fluid once cooling is gone; got "
                                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(helper.getBlockState(g).getBlock()));
                helper.succeed();
            });
        });
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

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void emitterDrainsEnergyOverTicks(final GameTestHelper helper) {
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
            helper.runAfterDelay(40L, () -> {
                final int after = emitter.getEnergyBuffer().getEnergyStored();
                helper.assertTrue(after < initial,
                        "Buffer should drain while the emitter ticks; initial=" + initial
                                + " after=" + after);
                helper.succeed();
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
}
