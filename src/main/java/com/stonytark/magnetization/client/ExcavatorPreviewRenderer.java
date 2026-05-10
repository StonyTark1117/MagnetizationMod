package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.content.excavator.MagneticExcavatorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * While the player is wearing Create's Engineer's Goggles, this renders a
 * coloured wireframe along the excavator's pull column — the cells that would
 * be ripped out next time the emitter triggers. Polarity tints the outline
 * (red = repel/push, blue = attract/pull). Only fires when goggles are equipped
 * so it doesn't pollute the world for unaware players.
 */
public final class ExcavatorPreviewRenderer
        implements BlockEntityRenderer<MagneticExcavatorBlockEntity> {

    public ExcavatorPreviewRenderer(final BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(
            final MagneticExcavatorBlockEntity be, final float partialTick, final PoseStack poseStack,
            final MultiBufferSource buffers, final int packedLight, final int packedOverlay
    ) {
        final var mc = Minecraft.getInstance();
        if (mc.player == null || !GogglesItem.isWearingGoggles(mc.player)) return;

        final BlockState state = be.getBlockState();
        if (!state.hasProperty(DirectionalBlock.FACING)) return;
        final MagneticField field = be.currentField();
        if (field == null) return;

        final Direction facing = state.getValue(DirectionalBlock.FACING);
        final int columnLength = (int) Math.max(1, field.range());
        final boolean attract = field.polarity() == MagneticPolarity.SOUTH;
        // Light pastel so the outline is visible without overwhelming the column blocks.
        final int r = attract ?  60 : 220;
        final int g = attract ? 150 : 110;
        final int b = attract ? 230 :  60;
        final int a = 180;

        final VertexConsumer buf = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        // Translate to the cell adjacent to the emitter (offset 1 along facing).
        poseStack.translate(0.5 + facing.getStepX(), 0.5 + facing.getStepY(), 0.5 + facing.getStepZ());
        final Matrix4f m = poseStack.last().pose();

        for (int i = 0; i < columnLength; i++) {
            final float ox = facing.getStepX() * i;
            final float oy = facing.getStepY() * i;
            final float oz = facing.getStepZ() * i;
            drawCubeOutline(buf, m, ox, oy, oz, r, g, b, a);
        }

        poseStack.popPose();
    }

    /** Render the 12 edges of a unit cube whose lower-back-left corner is at
     *  ({@code cx-0.5}, {@code cy-0.5}, {@code cz-0.5}). RenderType.lines
     *  expects per-vertex (pos, color, normal); a constant normal is fine for
     *  unlit wireframe lines. */
    private static void drawCubeOutline(
            final VertexConsumer buf, final Matrix4f m,
            final float cx, final float cy, final float cz,
            final int r, final int g, final int b, final int a
    ) {
        final float h = 0.5f;
        final float[] xs = { cx - h, cx + h };
        final float[] ys = { cy - h, cy + h };
        final float[] zs = { cz - h, cz + h };

        // 4 edges along X
        for (int yi = 0; yi < 2; yi++) for (int zi = 0; zi < 2; zi++)
            edge(buf, m, xs[0], ys[yi], zs[zi], xs[1], ys[yi], zs[zi], r, g, b, a, 1, 0, 0);
        // 4 edges along Y
        for (int xi = 0; xi < 2; xi++) for (int zi = 0; zi < 2; zi++)
            edge(buf, m, xs[xi], ys[0], zs[zi], xs[xi], ys[1], zs[zi], r, g, b, a, 0, 1, 0);
        // 4 edges along Z
        for (int xi = 0; xi < 2; xi++) for (int yi = 0; yi < 2; yi++)
            edge(buf, m, xs[xi], ys[yi], zs[0], xs[xi], ys[yi], zs[1], r, g, b, a, 0, 0, 1);
    }

    private static void edge(
            final VertexConsumer buf, final Matrix4f m,
            final float x1, final float y1, final float z1,
            final float x2, final float y2, final float z2,
            final int r, final int g, final int b, final int a,
            final float nx, final float ny, final float nz
    ) {
        buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        buf.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(final MagneticExcavatorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
