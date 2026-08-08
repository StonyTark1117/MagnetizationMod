package com.stonytark.magnetization.compat.simulatedcoasters;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.fml.ModList;

import java.util.Set;
import java.util.UUID;

/** Dependency-free facade for the optional Create: Coasters Simulated bridge. */
public final class MagSimulatedCoastersCompat {
    public static final String MOD_ID = "simulatedcoasters";

    private MagSimulatedCoastersCompat() {}

    public static boolean isCoasterCart(final ServerSubLevel subLevel) {
        return loaded() && MagSimulatedCoastersLoadedCompat.isCoasterCart(subLevel);
    }

    public static boolean receivesMagneticFields(final ServerSubLevel subLevel) {
        return !loaded() || MagSimulatedCoastersLoadedCompat.receivesMagneticFields(subLevel);
    }

    public static boolean structuralInducerCanAdopt(final ServerSubLevel subLevel) {
        return loaded() && MagSimulatedCoastersLoadedCompat.structuralInducerCanAdopt(subLevel);
    }

    public static boolean isRailEngaged(final ServerSubLevel cart) {
        return loaded() && MagSimulatedCoastersLoadedCompat.isRailEngaged(cart);
    }

    public static Set<UUID> coasterStructureIds(final ServerSubLevel seed, final long now) {
        return loaded() ? MagSimulatedCoastersLoadedCompat.coasterStructureIds(seed, now) : Set.of();
    }

    public static boolean isPartOfCoasterStructure(final ServerSubLevel subLevel) {
        return loaded() && MagSimulatedCoastersLoadedCompat.isPartOfCoasterStructure(subLevel);
    }

    private static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
