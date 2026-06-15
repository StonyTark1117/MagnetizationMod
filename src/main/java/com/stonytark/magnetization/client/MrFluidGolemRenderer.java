package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.MrFluidGolem;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders the MR Fluid Golem with the iron-golem model but an MR-fluid texture,
 * swapping to a brighter rigid texture while it's hardened (inside a field).
 */
public final class MrFluidGolemRenderer extends IronGolemRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/mr_fluid_golem.png");
    private static final ResourceLocation HARDENED =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/mr_fluid_golem_hardened.png");

    public MrFluidGolemRenderer(final EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(final IronGolem entity) {
        return (entity instanceof MrFluidGolem g && g.isHardened()) ? HARDENED : TEXTURE;
    }
}
