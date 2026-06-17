package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.physics.EmitterRegistry;
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
                    helper.assertTrue(vA.y > vB.y + 0.1,
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
}
