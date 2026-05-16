package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.client.screen.EmitterScreen;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Wires the directional emitter beam renderer to its block entity types and
 * forces class-load on the client effect subscribers (their {@code @EventBusSubscriber}
 * annotation alone won't trigger load if there are no annotated event methods left).
 * Mod-bus event, client-only.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class MagClientRegistration {

    private MagClientRegistration() {}

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MagBlockEntities.TRACTOR_BEAM.get(), BeamEmitterRenderer::new);
        event.registerBlockEntityRenderer(MagBlockEntities.KINETIC_ELECTROMAGNET.get(), KineticElectromagnetRenderer::new);
        event.registerBlockEntityRenderer(MagBlockEntities.MAGNETIC_EXCAVATOR.get(), ExcavatorPreviewRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Touch each subscriber class so its static init runs and registers with
            // ActiveEmitterScanner. Without this the wire() blocks never fire.
            ClientEmitterEffects.touch();
            EmitterHumSound.touch();
            CompassPropertyHooks.install();
        });
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(MagMenus.EMITTER.get(), EmitterScreen::new);
    }
}
