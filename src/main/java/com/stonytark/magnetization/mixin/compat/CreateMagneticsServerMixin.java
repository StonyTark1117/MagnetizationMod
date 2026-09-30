package com.stonytark.magnetization.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** The plugin isolates client-only sound members before JVM verification. */
@Pseudo
@Mixin(targets = "com.koudesuk.create_magnetics.block.kinetic.KineticMagnetBlockEntity", remap = false)
public abstract class CreateMagneticsServerMixin {}
