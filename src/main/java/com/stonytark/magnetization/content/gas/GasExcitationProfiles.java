package com.stonytark.magnetization.content.gas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reloadable registry for data-driven proxy-gas excitation profiles. */
public final class GasExcitationProfiles extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<Fluid, GasExcitationProfile> BY_FLUID = Map.of();
    private final DynamicOps<JsonElement> ops;

    public GasExcitationProfiles(final net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        super(GSON, "magnetization/gas_excitation_profiles");
        ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE, event.getRegistryAccess()),
                event.getConditionContext());
    }

    public static Optional<GasExcitationProfile> find(final Fluid fluid) {
        return Optional.ofNullable(BY_FLUID.get(fluid));
    }

    public static boolean supports(final Fluid fluid) {
        return BY_FLUID.containsKey(fluid);
    }

    public static int size() {
        return BY_FLUID.size();
    }

    /**
     * Installs an in-memory profile for runtime GameTests that need to exercise
     * the generic vent API without placing an optional addon on the base test
     * classpath. This cannot be called in a production environment.
     */
    public static synchronized void registerGameTestProfile(final GasExcitationProfile profile) {
        if (!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) {
            throw new IllegalStateException("GameTest gas profiles are unavailable outside a development test run");
        }
        final Map<Fluid, GasExcitationProfile> updated = new LinkedHashMap<>(BY_FLUID);
        for (final ResourceLocation fluidId : profile.fluids()) {
            if (!BuiltInRegistries.FLUID.containsKey(fluidId)) {
                throw new IllegalArgumentException("Missing GameTest gas fluid " + fluidId);
            }
            final Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
            final GasExcitationProfile previous = updated.putIfAbsent(fluid, profile);
            if (previous != null && !previous.equals(profile)) {
                throw new IllegalStateException("GameTest gas fluid already has a different profile: " + fluidId);
            }
        }
        BY_FLUID = Map.copyOf(updated);
    }

    /** Validates exact fluid-ID ownership after conditions have been evaluated. */
    static Map<ResourceLocation, ResourceLocation> claimOwners(
            final Map<ResourceLocation, GasExcitationProfile> profiles) {
        final Map<ResourceLocation, ResourceLocation> owners = new LinkedHashMap<>();
        for (final var entry : profiles.entrySet()) {
            for (final ResourceLocation fluidId : entry.getValue().fluids()) {
                final ResourceLocation previous = owners.putIfAbsent(fluidId, entry.getKey());
                if (previous != null) {
                    throw new IllegalStateException("Gas excitation fluid " + fluidId
                            + " is claimed by both " + previous + " and " + entry.getKey());
                }
            }
        }
        return Map.copyOf(owners);
    }

    @Override
    protected void apply(final Map<ResourceLocation, JsonElement> resources,
                         final ResourceManager manager, final ProfilerFiller profiler) {
        final Map<ResourceLocation, GasExcitationProfile> profiles = new LinkedHashMap<>();
        final var conditionalCodec = ConditionalOps.createConditionalCodec(GasExcitationProfile.CODEC);
        for (final var entry : resources.entrySet()) {
            final Optional<GasExcitationProfile> decoded = conditionalCodec.parse(ops, entry.getValue())
                    .getOrThrow(message -> new IllegalStateException(
                            "Invalid gas excitation profile " + entry.getKey() + ": " + message));
            decoded.ifPresent(profile -> profiles.put(entry.getKey(), profile));
        }

        final Map<ResourceLocation, ResourceLocation> owners = claimOwners(profiles);
        final Map<Fluid, GasExcitationProfile> loaded = new LinkedHashMap<>();
        for (final var claim : owners.entrySet()) {
            final ResourceLocation fluidId = claim.getKey();
            final ResourceLocation profileId = claim.getValue();
            if (!BuiltInRegistries.FLUID.containsKey(fluidId)) {
                throw new IllegalStateException("Gas excitation profile " + profileId
                        + " references missing fluid " + fluidId);
            }
            loaded.put(BuiltInRegistries.FLUID.get(fluidId), profiles.get(profileId));
        }
        BY_FLUID = Map.copyOf(loaded);
        LOGGER.info("Loaded {} gas excitation fluid profile bindings", loaded.size());
    }
}
