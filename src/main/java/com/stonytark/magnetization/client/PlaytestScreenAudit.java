package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Emits an automation boundary only in explicitly enabled disposable playtest clients. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class PlaytestScreenAudit {
    private PlaytestScreenAudit() {}

    @SubscribeEvent
    public static void onOpening(final ScreenEvent.Opening event) {
        if (!Boolean.getBoolean("magnetization.playtest") || event.getNewScreen() == null) return;
        if (event.getNewScreen().getClass().getName()
                .startsWith("com.stonytark.magnetization.client.screen.")) {
            org.slf4j.LoggerFactory.getLogger("magnetization/PlaytestScreenAudit")
                    .info("PLAYTEST_SCREEN_OPEN {}",
                    event.getNewScreen().getClass().getName());
        }
    }
}
