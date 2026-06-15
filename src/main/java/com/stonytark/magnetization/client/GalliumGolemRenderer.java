package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders the Gallium Golem with the iron-golem model and a gallium palette-swap
 * texture.
 */
public final class GalliumGolemRenderer extends IronGolemRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/gallium_golem.png");

    public GalliumGolemRenderer(final EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(final IronGolem entity) {
        return TEXTURE;
    }
}
