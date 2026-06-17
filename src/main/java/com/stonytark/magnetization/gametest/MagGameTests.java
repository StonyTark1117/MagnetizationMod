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
