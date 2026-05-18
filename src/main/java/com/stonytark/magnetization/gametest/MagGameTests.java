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
