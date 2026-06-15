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
    public static void onRegisterClientExtensions(final net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        // Ferrofluid renders as the vanilla water textures tinted near-black.
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");
            }
            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFF14141C; // opaque near-black
            }
        }, com.stonytark.magnetization.registry.MagFluids.FERROFLUID_TYPE.get());

        // MR fluid: water textures tinted iron-grey.
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");
            }
            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFF5A5A64; // iron grey
            }
        }, com.stonytark.magnetization.registry.MagFluids.MR_FLUID_TYPE.get());

        // Deuterium oxide (heavy water): water textures tinted a darker blue.
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");
            }
            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFF1E3A8A; // darker blue
            }
        }, com.stonytark.magnetization.registry.MagFluids.DEUTERIUM_OXIDE_TYPE.get());

        // Gallium: water textures tinted bright silvery metal.
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");
            }
            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFFC2C8D2; // bright silver
            }
        }, com.stonytark.magnetization.registry.MagFluids.GALLIUM_TYPE.get());

        // Mixed gallium: dark steel-blue (gallium silver darkened by ferrofluid).
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still");
            }
            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow");
            }
            @Override
            public int getTintColor() {
                return 0xFF4A5570; // dark steel blue
            }
        }, com.stonytark.magnetization.registry.MagFluids.MIXED_GALLIUM_TYPE.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MagBlockEntities.TRACTOR_BEAM.get(), BeamEmitterRenderer::new);
        event.registerBlockEntityRenderer(MagBlockEntities.KINETIC_ELECTROMAGNET.get(), KineticElectromagnetRenderer::new);
        event.registerBlockEntityRenderer(MagBlockEntities.MAGNETIC_EXCAVATOR.get(), ExcavatorPreviewRenderer::new);
        event.registerBlockEntityRenderer(MagBlockEntities.MAGNETIC_ITEM_FRAME.get(), MagneticItemFrameRenderer::new);
        event.registerEntityRenderer(com.stonytark.magnetization.registry.MagEntities.MR_FLUID_GOLEM.get(),
                MrFluidGolemRenderer::new);
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
            registerArmorHardenedProperty();
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

    /** {@code magnetization:hardened} = 1 while an MR Liquid Armor piece is within
     *  its post-hit hardened window (see {@code MrArmorHandler}); the model
     *  {@code overrides} swap to the rigid-plate icon. */
    @SuppressWarnings("deprecation")
    private static void registerArmorHardenedProperty() {
        final net.minecraft.resources.ResourceLocation hardened =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("magnetization", "hardened");
        final net.minecraft.client.renderer.item.ClampedItemPropertyFunction fn = (stack, level, entity, seed) -> {
            if (level == null) return 0.0f;
            final Long until = stack.get(com.stonytark.magnetization.registry.MagDataComponents.HARDENED_UNTIL.get());
            return (until != null && level.getGameTime() < until) ? 1.0f : 0.0f;
        };
        for (final var armor : new net.neoforged.neoforge.registries.DeferredItem[]{
                com.stonytark.magnetization.registry.MagItems.MR_LIQUID_HELMET,
                com.stonytark.magnetization.registry.MagItems.MR_LIQUID_CHESTPLATE,
                com.stonytark.magnetization.registry.MagItems.MR_LIQUID_LEGGINGS,
                com.stonytark.magnetization.registry.MagItems.MR_LIQUID_BOOTS,
                com.stonytark.magnetization.registry.MagItems.MR_FLUID_SWORD,
                com.stonytark.magnetization.registry.MagItems.MR_FLUID_PICKAXE,
                com.stonytark.magnetization.registry.MagItems.MR_FLUID_AXE,
                com.stonytark.magnetization.registry.MagItems.MR_FLUID_SHOVEL,
                com.stonytark.magnetization.registry.MagItems.MR_FLUID_HOE}) {
            net.minecraft.client.renderer.item.ItemProperties.register(
                    (net.minecraft.world.item.Item) armor.get(), hardened, fn);
        }
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(final RegisterMenuScreensEvent event) {
        event.register(MagMenus.EMITTER.get(), EmitterScreen::new);
        event.register(MagMenus.MACHINE.get(),
                com.stonytark.magnetization.client.screen.MachineScreen::new);
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
                addMrArmorLayer((LivingEntityRenderer) living, event);
            }
        }
        // Armor stands — vanilla elytra works on them, so ours should too.
        EntityRenderer<?> stand = event.getRenderer(EntityType.ARMOR_STAND);
        if (stand instanceof LivingEntityRenderer<?, ?> living) {
            ((LivingEntityRenderer) living).addLayer(
                    new MagneticElytraLayer<>((LivingEntityRenderer) living, event.getEntityModels()));
            addMrArmorLayer((LivingEntityRenderer) living, event);
        }
        // Horses — animated MR fluid barding (mirror of the player MR armor layer).
        EntityRenderer<?> horse = event.getRenderer(EntityType.HORSE);
        if (horse instanceof LivingEntityRenderer<?, ?> living
                && living.getModel() instanceof net.minecraft.client.model.HorseModel) {
            ((LivingEntityRenderer) living).addLayer(
                    new MrFluidHorseArmorLayer((LivingEntityRenderer) living, event.getEntityModels()));
        }
    }

    /** Add the animated Magnetorheological-armor layer to a humanoid renderer.
     *  Guards on the parent model being a {@link net.minecraft.client.model.HumanoidModel}
     *  (the layer copies humanoid pose onto its armor models). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addMrArmorLayer(final LivingEntityRenderer living,
                                        final EntityRenderersEvent.AddLayers event) {
        if (living.getModel() instanceof net.minecraft.client.model.HumanoidModel) {
            living.addLayer(new MrLiquidArmorLayer(living, event.getEntityModels()));
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
