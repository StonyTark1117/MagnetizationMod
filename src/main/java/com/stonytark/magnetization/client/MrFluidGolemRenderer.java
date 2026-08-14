package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.MrFluidGolem;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * The soft state uses complete UV-correct entity-texture frames generated from
 * the same tinted-water frames and cadence as MR armor. Hardening swaps the
 * single model pass to the existing rigid texture. This avoids z-fighting and
 * prevents atlas-sized water tiles from being stretched across arbitrary body
 * panels.
 */
public final class MrFluidGolemRenderer extends IronGolemRenderer {

    private static final int FRAMES = 16;
    private static final int FRAME_TIME = 3;
    private static final ResourceLocation[] FLUID_FRAMES = java.util.stream.IntStream.range(0, FRAMES)
            .mapToObj(frame -> ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID,
                    "textures/entity/mr_fluid_golem_" + frame + ".png"))
            .toArray(ResourceLocation[]::new);
    private static final ResourceLocation HARDENED =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/mr_fluid_golem_hardened.png");

    public MrFluidGolemRenderer(final EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(final IronGolem entity) {
        if (entity instanceof MrFluidGolem golem && golem.isHardened()) return HARDENED;
        return FLUID_FRAMES[(entity.tickCount / FRAME_TIME) % FRAMES];
    }
}
