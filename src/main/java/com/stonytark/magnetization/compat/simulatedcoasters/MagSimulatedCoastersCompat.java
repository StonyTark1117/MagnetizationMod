package com.stonytark.magnetization.compat.simulatedcoasters;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.neoforged.fml.ModList;

/** Optional Create: Coasters Simulated bridge, isolated behind its mod-list guard. */
public final class MagSimulatedCoastersCompat {
    public static final String MOD_ID = "simulatedcoasters";

    private MagSimulatedCoastersCompat() {}

    /** True only for an assembled coaster-cart physics sublevel. The optional
     *  class is resolved only after FML confirms that its owning mod is loaded. */
    public static boolean isCoasterCart(final ServerSubLevel subLevel) {
        return ModList.get().isLoaded(MOD_ID)
                && dev.silvergold.simulatedcoasters.track.cart.CoasterCartResolve.isCartSubLevel(subLevel);
    }

    public static boolean receivesMagneticFields(final ServerSubLevel subLevel) {
        return !isCoasterCart(subLevel)
                || com.stonytark.magnetization.config.MagConfig.simulatedCoastersFieldReaction();
    }

    public static boolean structuralInducerCanAdopt(final ServerSubLevel subLevel) {
        return com.stonytark.magnetization.config.MagConfig.simulatedCoastersStructuralInducer()
                && isCoasterCart(subLevel);
    }
}
