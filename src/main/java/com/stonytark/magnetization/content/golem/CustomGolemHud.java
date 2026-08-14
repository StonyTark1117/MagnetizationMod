package com.stonytark.magnetization.content.golem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** One dispatch point used by Create goggles and optional HUD integrations. */
public final class CustomGolemHud {
    public static List<Component> lines(final Entity entity) {
        if (entity instanceof MagneticGolem magnetic) return MagneticGolemHud.lines(magnetic);
        if (entity instanceof GalliumGolem gallium) return GalliumGolemHud.lines(gallium);
        if (entity instanceof MrFluidGolem mrFluid) return MrFluidGolemHud.lines(mrFluid);
        return List.of();
    }

    public static boolean supports(final Entity entity) {
        return entity instanceof MagneticGolem
                || entity instanceof GalliumGolem
                || entity instanceof MrFluidGolem;
    }

    private CustomGolemHud() {}
}
