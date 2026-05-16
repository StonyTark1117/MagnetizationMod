package com.stonytark.magnetization;

import com.stonytark.magnetization.command.MagCommands;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagArmorMaterials;
import com.stonytark.magnetization.registry.MagBiomeModifiers;
import com.stonytark.magnetization.registry.MagBlockEntities;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagCreativeTab;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.registry.MagItems;
import com.stonytark.magnetization.registry.MagMenus;
import com.stonytark.magnetization.registry.MagParticles;
import com.stonytark.magnetization.worldgen.AnomalyRegion;
import com.stonytark.magnetization.worldgen.MagSurfaceRules;
import com.stonytark.magnetization.worldgen.PetrifiedForestRegion;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

@Mod(Magnetization.MOD_ID)
public final class Magnetization {

    public static final String MOD_ID = "magnetization";

    public Magnetization(final IEventBus modBus, final ModContainer modContainer) {
        MagBlocks.REGISTER.register(modBus);
        // ArmorMaterials must register before items that reference them.
        MagArmorMaterials.REGISTER.register(modBus);
        MagItems.REGISTER.register(modBus);
        MagBlockEntities.REGISTER.register(modBus);
        MagCreativeTab.REGISTER.register(modBus);
        MagEffects.EFFECTS.register(modBus);
        MagEffects.POTIONS.register(modBus);
        MagParticles.REGISTER.register(modBus);
        MagBiomeModifiers.REGISTER.register(modBus);
        MagDataComponents.REGISTER.register(modBus);
        MagMenus.REGISTER.register(modBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, MagConfig.SPEC);

        modBus.addListener(Magnetization::onCommonSetup);
        modBus.addListener(Magnetization::onRegisterCapabilities);
        NeoForge.EVENT_BUS.addListener(MagCommands::onRegister);
        NeoForge.EVENT_BUS.addListener(Magnetization::onLevelUnload);

        // Curios — register the Field Compass and Magnetic Grapple as curios
        // so they work from a charm slot. Guarded so the Curios imports don't
        // resolve when Curios isn't installed.
        if (ModList.get().isLoaded("curios")) {
            com.stonytark.magnetization.compat.curios.MagCurioCompat.wire(modBus);
        }

        // Alex's Caves — optional swap between our Magnetized effect and AC's
        // Magnetizing effect, controlled by config. Guarded so the AC imports
        // and BuiltInRegistries lookups don't fire when AC isn't installed.
        if (ModList.get().isLoaded("alexscaves")) {
            com.stonytark.magnetization.compat.alexscaves.MagAlexsCavesCompat.wire(modBus);
        }

        // Client-only: light up the "Config" button on the Mods list with NeoForge's
        // built-in auto-generated config screen. The guard keeps the client-side
        // ConfigurationScreen / IConfigScreenFactory classes from being touched
        // on a dedicated server, where they don't exist.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.stonytark.magnetization.client.MagClientConfig.registerConfigScreen(modContainer);
        }
    }

    /**
     * TerraBlender region registration. Runs once, post-registry-flush, so the
     * biome resource key is resolvable. TerraBlender is a hard dep — see
     * {@code neoforge.mods.toml} — so no presence guard is needed here.
     */
    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (anomalyBiomeEnabled()) {
                Regions.register(new AnomalyRegion());
            }
            if (petrifiedForestEnabled()) {
                Regions.register(new PetrifiedForestRegion());
            }
            // Custom surface blocks for the two custom biomes — runs whether or
            // not the region was registered, so /place biome still produces a
            // visually-distinct surface.
            SurfaceRuleManager.addSurfaceRules(
                    SurfaceRuleManager.RuleCategory.OVERWORLD,
                    MOD_ID,
                    MagSurfaceRules.overworld());

            // Just Enough Resources integration — register magnetite ore
            // distributions directly via JERAPI.getInstance(). We avoid JER's
            // @JERPlugin annotation route because the 1.6.0.17 NeoForge build
            // scans for the wrong type and never finds the plugin. The
            // isLoaded check keeps MagJerPlugin (and its JER imports)
            // unloaded when JER isn't installed.
            if (ModList.get().isLoaded("jeresources")) {
                com.stonytark.magnetization.compat.jer.MagJerPlugin.register();
            }
        });
    }

    /** Register the {@code FORGE_ENERGY} capability on every redstone-powered
     *  emitter so any FE-providing mod (Create: Crafts & Additions, Mekanism,
     *  Thermal, IE generators, AE2…) can push energy into the buffer. The
     *  capability resolves to the BE's internal one-way buffer; external
     *  extraction returns 0 by design. The KineticElectromagnet is omitted
     *  here because it's already powered by Create kinetics — no point in
     *  exposing FE on it. */
    private static void onRegisterCapabilities(final net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        final net.neoforged.neoforge.capabilities.BlockCapability<net.neoforged.neoforge.energy.IEnergyStorage, net.minecraft.core.Direction> cap
                = net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK;
        event.registerBlockEntity(cap, MagBlockEntities.ELECTROMAGNET.get(),       (be, side) -> be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MAGNETIC_ANCHOR.get(),     (be, side) -> be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.REPULSOR_COIL.get(),       (be, side) -> be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.TRACTOR_BEAM.get(),        (be, side) -> be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MAGNETIC_EXCAVATOR.get(),  (be, side) -> be.getEnergyBuffer());
    }

    /** Drop the per-level ship-state caches when a dimension unloads, so we don't
     *  leak ShipMagneticState across world restarts. */
    private static void onLevelUnload(final net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
            com.stonytark.magnetization.physics.ShipMagneticRegistry.onLevelUnload(server);
        }
    }

    private static boolean anomalyBiomeEnabled() {
        try { return MagConfig.ANOMALY_BIOME_ENABLED.get(); } catch (Throwable t) { return false; }
    }

    private static boolean petrifiedForestEnabled() {
        try { return MagConfig.PETRIFIED_FOREST_ENABLED.get(); } catch (Throwable t) { return false; }
    }

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
