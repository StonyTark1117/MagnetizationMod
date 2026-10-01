package com.stonytark.magnetization.mixin.compat;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The published 26.08.307 orbit callback also assumes Deep Space exists.
 * Keep local ship physics and thrusters ticking when that dimension is absent. */
@Pseudo
@Mixin(targets = "dev.devce.rocketnautics.content.physics.SpaceTransitionHandler", remap = false)
public abstract class CosmonauticsMissingSpacePhysicsGuardMixin {
    private static final ResourceKey<Level> MAGNETIZATION$DEEP_SPACE = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.parse("rocketnautics:deep_space"));

    @Inject(method = "lambda$init$2", at = @At("HEAD"), cancellable = true, remap = false)
    private static void magnetization$skipOrbitWithoutDeepSpace(
            SubLevelPhysicsSystem physics, double timeStep, CallbackInfo ci) {
        if (physics.getLevel().getServer().getLevel(MAGNETIZATION$DEEP_SPACE) == null) ci.cancel();
    }
}
