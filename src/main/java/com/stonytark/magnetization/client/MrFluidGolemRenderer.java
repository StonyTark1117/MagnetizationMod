package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.MrFluidGolem;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Renders the MR Fluid Golem as an animated MR-fluid body which locks into a
 * rigid surface while hardened. The soft state is drawn from Minecraft's live
 * water-flow atlas sprite, which is also the source material used by the other
 * MR-fluid visuals. This deliberately avoids a second frame set or animation
 * clock just for the golem.
 */
public final class MrFluidGolemRenderer extends IronGolemRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/mr_fluid_golem.png");
    private static final ResourceLocation HARDENED =
            ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "textures/entity/mr_fluid_golem_hardened.png");

    public MrFluidGolemRenderer(final EntityRendererProvider.Context ctx) {
        super(ctx);
        addLayer(new FluidSurfaceLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(final IronGolem entity) {
        return (entity instanceof MrFluidGolem g && g.isHardened()) ? HARDENED : TEXTURE;
    }

    /**
     * A visible layer of the game's animated water-flow sprite over the
     * existing MR golem body. Hardening deliberately removes the moving layer.
     */
    private static final class FluidSurfaceLayer
            extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
        private static final int MR_FLUID_SURFACE_TINT = 0xFF60606B;

        private FluidSurfaceLayer(
                final RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
            super(parent);
        }

        @Override
        public void render(final PoseStack pose, final MultiBufferSource buffers,
                           final int packedLight, final IronGolem entity,
                           final float limbSwing, final float limbSwingAmount,
                           final float partialTicks, final float ageInTicks,
                           final float netHeadYaw, final float headPitch) {
            if (!(entity instanceof MrFluidGolem golem) || golem.isHardened()) return;

            final VertexConsumer surface = ModelBakery.WATER_FLOW.sprite().wrap(buffers.getBuffer(
                    RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)));

            pose.pushPose();
            // Keep the liquid skin just outside the base mesh to avoid z-fighting.
            pose.scale(1.012f, 1.012f, 1.012f);
            getParentModel().renderToBuffer(pose, surface, packedLight,
                    OverlayTexture.NO_OVERLAY, MR_FLUID_SURFACE_TINT);
            pose.popPose();
        }
    }
}
