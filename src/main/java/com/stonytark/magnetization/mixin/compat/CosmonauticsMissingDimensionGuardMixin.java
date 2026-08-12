package com.stonytark.magnetization.mixin.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cosmonautics 26.08.307 dereferences its Deep Space level from three event
 * handlers without checking whether the level exists. Normal worlds include
 * the dimension, but NeoForge GameTest servers and datapacks that intentionally
 * remove it do not. Keep the optional addon inert in that lifecycle instead of
 * allowing its observer bootstrap to crash the whole server.
 */
@Pseudo
@Mixin(targets = "dev.devce.rocketnautics.content.orbit.DeepSpaceData", remap = false)
public abstract class CosmonauticsMissingDimensionGuardMixin {
    private static final ResourceKey<Level> MAGNETIZATION$DEEP_SPACE = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("rocketnautics", "deep_space"));

    @Inject(method = "advanceUniverse", at = @At("HEAD"), cancellable = true, remap = false)
    private static void magnetization$skipTickWithoutDeepSpace(
            final ServerTickEvent.Post event, final CallbackInfo ci) {
        if (!magnetization$hasDeepSpace(event.getServer())) ci.cancel();
    }

    @Inject(method = "handlePlayerLogin", at = @At("HEAD"), cancellable = true, remap = false)
    private static void magnetization$skipLoginWithoutDeepSpace(
            final PlayerEvent.PlayerLoggedInEvent event, final CallbackInfo ci) {
        if (!magnetization$hasDeepSpace(event.getEntity().getServer())) ci.cancel();
    }

    @Inject(method = "handlePlayerLogout", at = @At("HEAD"), cancellable = true, remap = false)
    private static void magnetization$skipLogoutWithoutDeepSpace(
            final PlayerEvent.PlayerLoggedOutEvent event, final CallbackInfo ci) {
        if (!magnetization$hasDeepSpace(event.getEntity().getServer())) ci.cancel();
    }

    private static boolean magnetization$hasDeepSpace(final MinecraftServer server) {
        return server != null && server.getLevel(MAGNETIZATION$DEEP_SPACE) != null;
    }
}
