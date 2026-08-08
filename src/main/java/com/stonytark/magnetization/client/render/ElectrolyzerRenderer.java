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
 * Renders the two fluids held inside the Electrolyzer's basin as translucent
 * surfaces. Hydrogen occupies the volume above the remaining water, making the
 * consumed water visibly turn into output instead of simply lowering the level.
 */
public class ElectrolyzerRenderer implements BlockEntityRenderer<ElectrolyzerBlockEntity> {

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation HYDROGEN_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final int WATER_COLOR = 0xCC3A6AE0; // translucent water blue
    private static final int HYDROGEN_COLOR = 0xCC9FD8FF; // translucent hydrogen aqua

    public ElectrolyzerRenderer(final BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(final ElectrolyzerBlockEntity be, final float partialTick, final PoseStack pose,
                       final MultiBufferSource buffers, final int packedLight, final int packedOverlay) {
        final float min = 3f / 16f, max = 13f / 16f;     // basin inner walls
        final float yBase = 4f / 16f, yTop = 13.5f / 16f; // basin floor → near rim
        final float water = Math.min(1f, Math.max(0f, be.waterFillFraction()));
        final float hydrogen = Math.min(1f, Math.max(0f, be.hydrogenFillFraction()));
        final float total = Math.min(1f, water + hydrogen);
        if (total <= 0.001f) return;

        final TextureAtlasSprite waterSprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER_STILL);
        final TextureAtlasSprite hydrogenSprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(HYDROGEN_STILL);
        final VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        final PoseStack.Pose p = pose.last();
        if (water > 0.001f) renderSurface(vc, p, waterSprite, yBase + (yTop - yBase) * water,
                WATER_COLOR, min, max, packedLight, packedOverlay);
        if (hydrogen > 0.001f) renderSurface(vc, p, hydrogenSprite, yBase + (yTop - yBase) * total,
                HYDROGEN_COLOR, min, max, packedLight, packedOverlay);
    }

    private static void renderSurface(final VertexConsumer vc, final PoseStack.Pose p,
                                      final TextureAtlasSprite sprite, final float y, final int color,
                                      final float min, final float max, final int light, final int overlay) {
        final float u0 = sprite.getU0(), u1 = sprite.getU1();
        final float v0 = sprite.getV0(), v1 = sprite.getV1();
        vertex(vc, p, min, y, min, u0, v0, color, light, overlay);
        vertex(vc, p, min, y, max, u0, v1, color, light, overlay);
        vertex(vc, p, max, y, max, u1, v1, color, light, overlay);
        vertex(vc, p, max, y, min, u1, v0, color, light, overlay);
    }

    private static void vertex(final VertexConsumer vc, final PoseStack.Pose p, final float x, final float y,
                               final float z, final float u, final float v, final int color,
                               final int light, final int overlay) {
        vc.addVertex(p, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(p, 0f, 1f, 0f);
    }
}
