package com.stonytark.magnetization.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.effect.LightningRemnantMagnetism;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Tesla's unspawned dummy bolt bypasses NeoForge's entity-struck and join-level events. */
@Pseudo
@Mixin(targets = "com.github.alexmodguy.alexscaves.server.block.blockentity.TeslaBulbBlockEntity", remap = false)
public abstract class AlexsCavesTeslaLightningMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V"),
            require = 2)
    private static void magnetization$teslaStrike(final LivingEntity target, final ServerLevel level,
                                                 final LightningBolt bolt, final Operation<Void> original) {
        original.call(target, level, bolt);
        if (MagConfig.alexsCavesCompatEnabled()) {
            LightningRemnantMagnetism.onSyntheticLightningStrike(target, "alexscaves:tesla_bulb");
        }
    }
}
