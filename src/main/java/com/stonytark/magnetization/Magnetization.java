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
import com.stonytark.magnetization.registry.MagTriggers;
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
        com.stonytark.magnetization.config.MagConfigMigration.migrateLegacyServerWorldgen();
        MagBlocks.REGISTER.register(modBus);
        // ArmorMaterials must register before items that reference them.
        MagArmorMaterials.REGISTER.register(modBus);
        MagItems.REGISTER.register(modBus);
        com.stonytark.magnetization.registry.MagFluids.FLUID_TYPES.register(modBus);
        com.stonytark.magnetization.registry.MagFluids.FLUIDS.register(modBus);
        MagBlockEntities.REGISTER.register(modBus);
        com.stonytark.magnetization.registry.MagEntities.REGISTER.register(modBus);
        MagCreativeTab.REGISTER.register(modBus);
        MagEffects.EFFECTS.register(modBus);
        MagEffects.POTIONS.register(modBus);
        MagParticles.REGISTER.register(modBus);
        MagBiomeModifiers.REGISTER.register(modBus);
        com.stonytark.magnetization.registry.MagConditions.REGISTER.register(modBus);
        com.stonytark.magnetization.registry.MagLootModifiers.REGISTER.register(modBus);
        MagDataComponents.REGISTER.register(modBus);
        MagMenus.REGISTER.register(modBus);
        MagTriggers.REGISTER.register(modBus);
        com.stonytark.magnetization.registry.MagFeatures.REGISTER.register(modBus);
        com.stonytark.magnetization.registry.MagFeatures.PROCESSOR_REGISTER.register(modBus);

        // SERVER spec — per-world admin/balance settings (guiLimits, debug, command
        // permissions). Loads at world-start; not editable from the title screen.
        modContainer.registerConfig(ModConfig.Type.SERVER, MagConfig.SPEC);
        // COMMON spec — a single global file editable from the main menu BEFORE a
        // world is created. Holds the player-facing + worldgen-baked settings so a
        // single player can set biome rarity/gen correctly up front, and so the
        // toggles are loaded early (before FMLCommonSetup region registration).
        modContainer.registerConfig(ModConfig.Type.COMMON, MagConfig.COMMON_SPEC);

        modBus.addListener(com.stonytark.magnetization.registry.MagEntities::onAttributes);
        modBus.addListener(Magnetization::onCommonSetup);
        modBus.addListener(Magnetization::onRegisterCapabilities);
        modBus.addListener(Magnetization::onRegisterPayloads);
        modBus.addListener(Magnetization::onConfigLoading);
        modBus.addListener(Magnetization::onConfigReloading);
        modBus.addListener(com.stonytark.magnetization.network.CommonConfigSync::onConfigReload);
        NeoForge.EVENT_BUS.addListener(MagCommands::onRegister);
        NeoForge.EVENT_BUS.addListener(Magnetization::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(Magnetization::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(Magnetization::onServerStopped);

        if (ModList.get().isLoaded("railways")) {
            com.stonytark.magnetization.compat.steamrails.MagSteamRailsCompat.wire(NeoForge.EVENT_BUS);
        }

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

        // AeroPortals reconstructs Sable sublevels in the destination dimension.
        // Follow its transfer event so derived caches and absolute railgun-remote
        // bindings follow the moved ship. The compat class is never resolved when
        // the optional mod is absent.
        if (ModList.get().isLoaded("aeroportals")) {
            com.stonytark.magnetization.compat.aeroportals.MagAeroPortalsCompat.wire(NeoForge.EVENT_BUS);
        }

        // Ore Excavation posts a start event after constructing its material
        // group.  Add our ore/geode tag there so its configured veinminer can
        // reach Magnetization deposits without becoming a hard dependency.
        // Config values are not populated until after mod construction. The
        // listener applies the config gate when Ore Excavation posts its first
        // excavation event, so this optional API is still wired safely here.
        if (ModList.get().isLoaded("oreexcavation")) {
            com.stonytark.magnetization.compat.oreexcavation.MagOreExcavationCompat.wire(NeoForge.EVENT_BUS);
        }

        // Client-only: light up the "Config" button on the Mods list with NeoForge's
        // built-in auto-generated config screen. The guard keeps the client-side
        // ConfigurationScreen / IConfigScreenFactory classes from being touched
        // on a dedicated server, where they don't exist.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.stonytark.magnetization.client.MagClientConfig.registerConfigScreen(modContainer);
        }
    }

    private static void onAddReloadListeners(final net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(new com.stonytark.magnetization.content.gas.GasExcitationProfiles(event));
    }

    /**
     * TerraBlender region registration. Runs once, post-registry-flush, so the
     * biome resource key is resolvable. TerraBlender is a hard dep — see
     * {@code neoforge.mods.toml} — so no presence guard is needed here.
     */
    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Region registration is unconditional. The biome toggles
            // (anomalyBiomeEnabled, petrifiedForestEnabled) now live in the COMMON
            // config so they CAN be edited from the title screen before world
            // creation — but we still don't gate region registration on them here:
            // COMMON-config load ordering relative to this event isn't guaranteed,
            // and registering the region unconditionally is harmless. The biome's
            // *placement* rarity is driven by anomalyBiomeRarity/petrifiedForestRarity
            // (read by the Region at registration). Runtime effects (chaos field,
            // compass scramble, emitter strength bonus) gate on the config check
            // independently via AnomalyBiome.enabled() / PetrifiedForestRegion at
            // tick time, so disabling the toggle still suppresses gameplay impact —
            // the biome just shows up as a quiet visual variant.
            Regions.register(new AnomalyRegion());
            Regions.register(new PetrifiedForestRegion());
            // Custom surface blocks for the two custom biomes — runs whether or
            // not the region was registered, so /place biome still produces a
            // visually-distinct surface.
            SurfaceRuleManager.addSurfaceRules(
                    SurfaceRuleManager.RuleCategory.OVERWORLD,
                    MOD_ID,
                    MagSurfaceRules.overworld());
            // Confirm registration fires — user has reported the anomaly biome
            // still looks like normal grass-and-trees despite three surface
            // rule rewrites. If this log line appears at startup, registration
            // happened; if the surface still doesn't visually change, the
            // problem is downstream (rule structure, biome key mismatch, or
            // TerraBlender merge ordering vs vanilla).
            org.slf4j.LoggerFactory.getLogger(MOD_ID)
                    .info("Surface rules registered for OVERWORLD category (anomaly + petrified_forest)");

            // Patchouli discovers books before COMMON config is guaranteed to
            // be loaded. Re-apply its master here after mod setup as a safety
            // net so a disabled manual is absent from Patchouli's registry.
            com.stonytark.magnetization.compat.patchouli.MagPatchouliCompat.applyMasterToggle();

            // Just Enough Resources integration — register the synchronized
            // all-ore/natural-resource catalog directly against JER's live API.
            // The presence gate keeps MagJerPlugin and its optional imports
            // unloaded when JER is not installed.
            if (ModList.get().isLoaded("jeresources")
                    && com.stonytark.magnetization.config.MagConfig.justEnoughResourcesCompatEnabled()) {
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
        event.registerBlockEntity(cap, MagBlockEntities.ELECTROMAGNET.get(),       (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.DIPOLE_ELECTROMAGNET.get(), (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MAGNETIC_ANCHOR.get(),     (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.REPULSOR_COIL.get(),       (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.TRACTOR_BEAM.get(),        (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MAGNETIC_EXCAVATOR.get(),  (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.STRUCTURAL_INDUCER.get(),  (be, side) -> disabled(be) ? null : be.getEnergyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.INDUCTION_PAD.get(),       (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.KINETIC_COIL.get(),        (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.TOKAMAK_CONTROLLER.get(),  (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MHD_JET.get(),             (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MICRO_THRUSTER.get(),      (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.ION_THRUSTER.get(),        (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.FUSION_THRUSTER.get(),     (be, side) -> disabled(be) ? null : be.energyBuffer());
        // A formed Fusion Thruster is one multiblock: cables may connect to any
        // Tokamak-Coil perimeter block and still feed the deterministic master's
        // buffer. Standalone coils and coils used by a Tokamak return no FE cap.
        event.registerBlock(cap, (level, pos, state, blockEntity, side) ->
                        com.stonytark.magnetization.content.jet.FusionThrusterBlockEntity
                                .energyBufferFromFrame(level, pos),
                MagBlocks.TOKAMAK_COIL.get());
        event.registerBlockEntity(cap, MagBlockEntities.RAILGUN_EMITTER.get(),     (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.MAGNETIC_ITEM_FRAME.get(), (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.GYROSTABILIZER.get(),      (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.ELECTROLYZER.get(),        (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(cap, MagBlockEntities.GAS_EXCITER.get(),         (be, side) -> disabled(be) ? null : be.energyBuffer());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.MICRO_THRUSTER.get(), (be, side) -> disabled(be) ? null : be.fluidHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.ION_THRUSTER.get(), (be, side) -> disabled(be) ? null : be.fluidHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.FUSION_THRUSTER.get(), (be, side) -> disabled(be) ? null : be.fluidHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.MHD_JET.get(), (be, side) -> disabled(be) ? null : be.fluidHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.ELECTROLYZER.get(), (be, side) -> disabled(be) ? null : be.fluidHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.AIR_SEPARATOR.get(), (be, side) -> disabled(be) ? null : be.fluidHandler(side));
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.GAS_VENT.get(), (be, side) -> disabled(be) ? null : be.fluidHandler());
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                MagBlockEntities.PROXY_GAS_CLOUD.get(), (be, side) -> be.isSource() ? be.fluidHandler() : null);

        // Item-handler caps so hoppers / Create automation can feed every item-fuel
        // machine (insert gated by each slot's canPlaceItem; only spent buckets extract
        // — see MachineFuelItemHandler). The Fusion Thruster auto-drains each panel
        // block's own bucket slot into the shared tank, so a per-block wrapper feeds the
        // whole multiblock via any interior cell. Each provider returns null while the
        // "Hopper Fuel Intake" toggle is off, so the toggle takes effect live (the cap
        // simply stops resolving) without needing a re-register.
        final net.neoforged.neoforge.capabilities.BlockCapability<net.neoforged.neoforge.items.IItemHandler, net.minecraft.core.Direction> items
                = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        event.registerBlockEntity(items, MagBlockEntities.TOKAMAK_CONTROLLER.get(),
                (be, side) -> !disabled(be) && com.stonytark.magnetization.config.MagConfig.hopperFuelIntake()
                        ? new com.stonytark.magnetization.content.MachineFuelItemHandler(be.fuelContainer()) : null);
        event.registerBlockEntity(items, MagBlockEntities.HOMOPOLAR_MOTOR.get(),
                (be, side) -> !disabled(be) && com.stonytark.magnetization.config.MagConfig.hopperFuelIntake()
                        ? new com.stonytark.magnetization.content.MachineFuelItemHandler(be.magnetContainer()) : null);
        event.registerBlockEntity(items, MagBlockEntities.MHD_JET.get(),
                (be, side) -> !disabled(be) && com.stonytark.magnetization.config.MagConfig.hopperFuelIntake()
                        ? new com.stonytark.magnetization.content.MachineFuelItemHandler(be.magnetContainer()) : null);
        event.registerBlockEntity(items, MagBlockEntities.MICRO_THRUSTER.get(),
                (be, side) -> !disabled(be) && com.stonytark.magnetization.config.MagConfig.hopperFuelIntake()
                        ? new com.stonytark.magnetization.content.MachineFuelItemHandler(be.bucketContainer()) : null);
        event.registerBlockEntity(items, MagBlockEntities.ION_THRUSTER.get(),
                (be, side) -> !disabled(be) && com.stonytark.magnetization.config.MagConfig.hopperFuelIntake()
                        ? new com.stonytark.magnetization.content.MachineFuelItemHandler(be.bucketContainer()) : null);
        event.registerBlockEntity(items, MagBlockEntities.AIR_SEPARATOR.get(),
                (be, side) -> disabled(be) ? null : be.itemHandler());
        event.registerBlockEntity(items, MagBlockEntities.FUSION_THRUSTER.get(),
                (be, side) -> !disabled(be) && com.stonytark.magnetization.config.MagConfig.hopperFuelIntake()
                ? new com.stonytark.magnetization.content.MachineFuelItemHandler(be.bucketContainer()) : null);
    }

    private static boolean disabled(final net.minecraft.world.level.block.entity.BlockEntity be) {
        return com.stonytark.magnetization.config.MagConfig.isBlockDisabled(be.getBlockState());
    }

    /** Wire the use-curio packet so clients can fire grapple/repulsor-gun from
     *  charm slots via the configurable keybinds. Registration is server-side
     *  too because the payload handler lives on both. The payload class lives
     *  outside the {@code .client} package so this dispatch doesn't load
     *  {@code KeyMapping} on dedicated servers. */
    private static void onRegisterPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        final net.neoforged.neoforge.network.registration.PayloadRegistrar reg =
                event.registrar(MOD_ID).versioned("1");
        com.stonytark.magnetization.network.UseCurioPayload.register(reg);
        com.stonytark.magnetization.network.CommonConfigSyncPayload.register(reg);
    }

    private static void onConfigLoading(final net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        MagConfig.validateRelationships();
        com.stonytark.magnetization.compat.patchouli.MagPatchouliCompat.applyMasterToggle();
    }

    private static void onConfigReloading(final net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        MagConfig.validateRelationships();
        com.stonytark.magnetization.compat.patchouli.MagPatchouliCompat.applyMasterToggle();
    }

    /** Drop the per-level ship-state caches when a dimension unloads, so we don't
     *  leak ShipMagneticState across world restarts. */
    private static void onLevelUnload(final net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
            com.stonytark.magnetization.physics.ShipMagneticRegistry.onLevelUnload(server);
            com.stonytark.magnetization.content.fluid.GasExcitation.onLevelUnload(server);
            com.stonytark.magnetization.physics.FieldApplicator.onLevelUnload(server);
            com.stonytark.magnetization.physics.InventorySink.onLevelUnload(server);
            com.stonytark.magnetization.physics.PerformanceDiagnostics.onLevelUnload(server);
            if (ModList.get().isLoaded("railways")) {
                com.stonytark.magnetization.compat.steamrails.MagSteamRailsCompat.onLevelUnload(server);
            }
        }
    }

    /** Drop cross-session static caches on server stop so nothing leaks into the
     *  next world loaded in the same client session (e.g. the Sable connected-chain
     *  cache — see SableBridge). */
    private static void onServerStopped(final net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        com.stonytark.magnetization.physics.SableBridge.onServerStopped();
    }

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
