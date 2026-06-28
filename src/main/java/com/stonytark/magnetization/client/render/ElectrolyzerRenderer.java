package com.stonytark.magnetization.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.stonytark.magnetization.content.electrolyzer.ElectrolyzerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Renders the water held inside the Electrolyzer's basin as a translucent surface
 * quad scaled to the fill level — the "cauldron showing the fluid inside" look.
 */
public class ElectrolyzerRenderer implements BlockEntityRenderer<ElectrolyzerBlockEntity> {

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final int WATER_COLOR = 0xCC3A6AE0; // translucent water blue

    public ElectrolyzerRenderer(final BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(final ElectrolyzerBlockEntity be, final float partialTick, final PoseStack pose,
                       final MultiBufferSource buffers, final int packedLight, final int packedOverlay) {
        final float frac = be.waterFillFraction();
        if (frac <= 0.001f) return;

        final TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER_STILL);

        final float min = 3f / 16f, max = 13f / 16f;     // basin inner walls
        final float yBase = 4f / 16f, yTop = 13.5f / 16f; // basin floor → near rim
        final float y = yBase + (yTop - yBase) * Math.min(1f, frac);

        final float u0 = sprite.getU0(), u1 = sprite.getU1();
        final float v0 = sprite.getV0(), v1 = sprite.getV1();

        final VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        final PoseStack.Pose p = pose.last();
        vertex(vc, p, min, y, min, u0, v0, packedLight, packedOverlay);
        vertex(vc, p, min, y, max, u0, v1, packedLight, packedOverlay);
        vertex(vc, p, max, y, max, u1, v1, packedLight, packedOverlay);
        vertex(vc, p, max, y, min, u1, v0, packedLight, packedOverlay);
    }

    private static void vertex(final VertexConsumer vc, final PoseStack.Pose p, final float x, final float y,
                               final float z, final float u, final float v, final int light, final int overlay) {
        vc.addVertex(p, x, y, z)
                .setColor(WATER_COLOR)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(p, 0f, 1f, 0f);
    }
}
