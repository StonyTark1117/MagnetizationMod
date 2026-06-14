package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.client.screen.EmitterScreen;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
            registerGunFiredProperty();
        });
    }

    /** Number of ticks the muzzle stays in its glowing-model state after a shot. */
    private static final long GUN_GLOW_TICKS = 10L;

    /** Registers the {@code magnetization:fired} item-model property on both guns.
     *  It reads the {@link com.stonytark.magnetization.registry.MagDataComponents#FIRED_AT}
     *  stamp and returns 1 for {@link #GUN_GLOW_TICKS} ticks after a shot, which the
     *  model {@code overrides} use to swap to the glowing-muzzle variant. */
    @SuppressWarnings("deprecation")
    private static void registerGunFiredProperty() {
        final net.minecraft.resources.ResourceLocation fired =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("magnetization", "fired");
        final net.minecraft.client.renderer.item.ClampedItemPropertyFunction fn = (stack, level, entity, seed) -> {
            if (level == null) return 0.0f;
            final Long firedAt = stack.get(com.stonytark.magnetization.registry.MagDataComponents.FIRED_AT.get());
            if (firedAt == null) return 0.0f;
            final long dt = level.getGameTime() - firedAt;
            return (dt >= 0 && dt < GUN_GLOW_TICKS) ? 1.0f : 0.0f;
        };
        net.minecraft.client.renderer.item.ItemProperties.register(
                com.stonytark.magnetization.registry.MagItems.REPULSOR_GUN.get(), fired, fn);
        net.minecraft.client.renderer.item.ItemProperties.register(
                com.stonytark.magnetization.registry.MagItems.MAGNETIC_GRAPPLE.get(), fired, fn);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(MagMenus.EMITTER.get(), EmitterScreen::new);
    }

    /** Wires layer0 of the magnetic_elytra item model to the DYED_COLOR
     *  data component so dyeing the item in the crafting grid colours the
     *  inventory icon as well as the worn cape. Without this the held item
     *  stays its base tint even after dyeing. The cape tint itself is
     *  driven by {@link MagneticElytraLayer#resolveDyeTint}. */
    @SubscribeEvent
    public static void onRegisterItemColors(final net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return 0xFFFFFFFF;
            final net.minecraft.world.item.component.DyedItemColor dyed =
                    stack.get(net.minecraft.core.component.DataComponents.DYED_COLOR);
            if (dyed == null) return 0xFFFFFFFF;
            return 0xFF000000 | (dyed.rgb() & 0x00FFFFFF);
        }, com.stonytark.magnetization.registry.MagItems.MAGNETIC_ELYTRA.get());
    }

    /** Adds a {@link MagneticElytraLayer} to every humanoid renderer so the
     *  custom magnetic-elytra cape texture draws when worn. Vanilla's
     *  {@code ElytraLayer.shouldRender} only fires for {@code Items.ELYTRA};
     *  without this our subclassed item would render nothing. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAddLayers(final EntityRenderersEvent.AddLayers event) {
        // Player renderers (default + slim).
        for (PlayerSkin.Model model : event.getSkins()) {
            EntityRenderer<?> renderer = event.getSkin(model);
            if (renderer instanceof LivingEntityRenderer<?, ?> living) {
                ((LivingEntityRenderer) living).addLayer(
                        new MagneticElytraLayer<>((LivingEntityRenderer) living, event.getEntityModels()));
            }
        }
        // Armor stands — vanilla elytra works on them, so ours should too.
        EntityRenderer<?> stand = event.getRenderer(EntityType.ARMOR_STAND);
        if (stand instanceof LivingEntityRenderer<?, ?> living) {
            ((LivingEntityRenderer) living).addLayer(
                    new MagneticElytraLayer<>((LivingEntityRenderer) living, event.getEntityModels()));
        }
    }

    /** Defers mod-compass wrapping until after every mod's
     *  {@link FMLClientSetupEvent} has flushed — both Nature's Compass and
     *  Explorer's Compass register their own angle property inside that
     *  same event, and mod-bus ordering across mods isn't deterministic, so
     *  wrapping there can be silently overwritten. By the time the client
     *  player logs in, all client-setup work is done; one-shot guard inside
     *  {@link CompassPropertyHooks#installModCompasses} keeps this idempotent. */
    @SubscribeEvent
    public static void onClientLogin(final ClientPlayerNetworkEvent.LoggingIn event) {
        CompassPropertyHooks.installModCompasses();
    }
}
