package com.stonytark.magnetization.compat.top;

import mcjty.theoneprobe.api.ITheOneProbe;
import net.neoforged.fml.InterModComms;

import java.util.function.Function;

/**
 * Holds the actual {@code mcjty.theoneprobe.api} references. Loaded only when
 * {@link MagTopHook} confirms TOP is present.
 */
final class MagTopRegistration {

    private MagTopRegistration() {}

    static void send() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", () -> (Function<ITheOneProbe, Void>) probe -> {
            probe.registerProvider(EmitterProbeProvider.INSTANCE);
            probe.registerProvider(MachineProbeProvider.INSTANCE);
            return null;
        });
    }
}
