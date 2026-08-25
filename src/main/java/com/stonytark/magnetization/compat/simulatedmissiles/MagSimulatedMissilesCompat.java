package com.stonytark.magnetization.compat.simulatedmissiles;

import com.stonytark.magnetization.config.MagConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** Dependency-free facade for optional CBC Aeronautics Missiles EMP behavior. */
public final class MagSimulatedMissilesCompat {
    public static final String MOD_ID = "cbcaeronauticsmissiles";

    private MagSimulatedMissilesCompat() {}

    public static int disableGuidanceInPulse(final ServerLevel level, final Vec3 center,
                                             final double radius) {
        if (!loaded() || !MagConfig.simulatedMissilesGuidanceEmpEnabled()) return 0;
        return MagSimulatedMissilesLoadedCompat.disableGuidanceInPulse(level, center, radius);
    }

    /** Test/diagnostic query that remains false when the optional addon is absent. */
    public static boolean hasGuidanceComputer(final ServerSubLevel subLevel) {
        return loaded() && MagSimulatedMissilesLoadedCompat.hasGuidanceComputer(subLevel);
    }

    private static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
