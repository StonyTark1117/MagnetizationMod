package com.stonytark.magnetization.mixin;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;

@Mixin(value = RotationPropagator.class, remap = false)
public interface MagneticShaftRotationAccess {
    @Invoker("getPotentialNeighbourLocations")
    static List<BlockPos> magnetization$locations(KineticBlockEntity be) { throw new AssertionError(); }
    @Invoker("propagateMissingSource")
    static void magnetization$disconnect(KineticBlockEntity be) { throw new AssertionError(); }
    @Invoker("getRotationSpeedModifier")
    static float magnetization$ratio(KineticBlockEntity from, KineticBlockEntity to) { throw new AssertionError(); }
}
