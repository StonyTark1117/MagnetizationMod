package com.stonytark.magnetization.compat.coastersmagnetized;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.MagneticFields;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/** Dependency-free facade for the optional Coasters: Magnetized bridge. */
public final class MagCoastersMagnetizedCompat {
    public static final String MOD_ID = "coastersmagnetized";

    private MagCoastersMagnetizedCompat() {}

    public static void wire(final IEventBus eventBus) {
        if (loaded()) MagCoastersMagnetizedLoadedCompat.wire(eventBus);
    }

    /** Used by the addon's server-side acceleration predicate. */
    public static boolean fieldPowersAnchor(final ServerLevel level, final BlockPos pos) {
        return loaded() && MagConfig.coastersMagnetizedFieldPowerEnabled()
                && MagneticFields.isInField(level, pos);
    }

    /** Used by the addon's client renderer after the server-authoritative sync. */
    public static boolean clientFieldPowersAnchor(final BlockPos pos) {
        return loaded() && MagCoastersMagnetizedLoadedCompat.clientFieldPowersAnchor(pos);
    }

    public static void updateClientFieldPower(final BlockPos pos, final boolean powered) {
        if (loaded()) MagCoastersMagnetizedLoadedCompat.updateClientFieldPower(pos, powered);
    }

    public static void clearClientFieldPower() {
        if (loaded()) MagCoastersMagnetizedLoadedCompat.clearClientFieldPower();
    }

    private static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
