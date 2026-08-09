package com.stonytark.magnetization.mixin.compat;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagEffects;
import com.stonytark.magnetization.registry.MagFluids;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives Diesel Generators' existing fluid projectile meaningful behavior when
 * its tank contains either form of Ferrofluid. */
@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.content.tools.ChemicalSprayerProjectileEntity",
        remap = false)
public abstract class DieselChemicalSprayerMixin {

    @Inject(method = "onHitEntity", at = @At("TAIL"), require = 0)
    private void magnetization$applyFerrofluidMagnetization(final EntityHitResult hit,
                                                            final CallbackInfo ci) {
        if (!MagConfig.dieselGeneratorsFerrofluidSprayEnabled()) return;
        if (!(hit.getEntity() instanceof LivingEntity living)) return;
        final FluidStack stack = fluidStack();
        if (stack == null || stack.isEmpty()) return;
        if (!stack.is(MagFluids.FERROFLUID.get())
                && !stack.is(MagFluids.FERROFLUID_FLOWING.get())
                && !stack.is(MagFluids.MAGNETIZED_FERROFLUID.get())
                && !stack.is(MagFluids.MAGNETIZED_FERROFLUID_FLOWING.get())) return;
        living.addEffect(new MobEffectInstance(MagEffects.MAGNETIZED, 200, 0));
    }

    private FluidStack fluidStack() {
        try {
            final java.lang.reflect.Field field = getClass().getField("stack");
            final Object value = field.get(this);
            return value instanceof FluidStack fluid ? fluid : FluidStack.EMPTY;
        } catch (final ReflectiveOperationException | RuntimeException ignored) {
            return FluidStack.EMPTY;
        }
    }
}
