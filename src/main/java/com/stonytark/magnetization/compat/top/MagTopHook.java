package com.stonytark.magnetization.compat.top;

import com.stonytark.magnetization.Magnetization;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * Soft-dependency entry point for The One Probe. This class deliberately does
 * not import any {@code mcjty.theoneprobe.api} type — it only reflects on
 * {@link ModList} to check whether TOP is installed and, if so, calls into
 * {@link MagTopRegistration} (which holds the actual API references). When TOP
 * isn't installed, {@code MagTopRegistration} is never class-loaded so its
 * imports never resolve.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagTopHook {

    private MagTopHook() {}

    @SubscribeEvent
    public static void onEnqueueIMC(final InterModEnqueueEvent event) {
        if (!ModList.get().isLoaded("theoneprobe")) return;
        MagTopRegistration.send();
    }
}
