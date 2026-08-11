package com.stonytark.magnetization.content.jet;

import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Shared redstone semantics for propulsion machines. */
public final class ThrustControl {

    private ThrustControl() {}

    /**
     * Whether a resource-powered thrust machine may run this tick.
     *
     * <p>By default, an external redstone signal is an inhibit/stop signal;
     * FE and propellant remain the machine's power source. The config opt-in
     * restores redstone as an alternate power source, so a signal can satisfy
     * the FE portion of the operating requirement.</p>
     */
    public static boolean canRun(final ServerLevel level, final BlockPos pos,
                                 final boolean propellantReady, final boolean feReady) {
        return canRunWithSignal(level.hasNeighborSignal(pos), propellantReady, feReady);
    }

    /** Same control rule for a multiblock whose redstone face may be any member. */
    public static boolean canRun(final ServerLevel level, final Iterable<BlockPos> positions,
                                 final boolean propellantReady, final boolean feReady) {
        boolean redstone = false;
        for (final BlockPos pos : positions) {
            if (level.hasNeighborSignal(pos)) {
                redstone = true;
                break;
            }
        }
        return canRunWithSignal(redstone, propellantReady, feReady);
    }

    private static boolean canRunWithSignal(final boolean redstone,
                                            final boolean propellantReady,
                                            final boolean feReady) {
        final boolean redstonePower = MagConfig.allowRedstoneThrustPower();
        if (redstone && !redstonePower) return false;
        return propellantReady && (feReady || (redstone && redstonePower));
    }

    /** Passive propulsion (such as the Solar Sail) is stopped by redstone. */
    public static boolean passiveThrustAllowed(final ServerLevel level, final BlockPos pos) {
        return !level.hasNeighborSignal(pos);
    }
}
