package com.stonytark.magnetization.client;

import com.stonytark.magnetization.content.electromagnet.KineticElectromagnetBlock;
import com.stonytark.magnetization.content.electromagnet.KineticElectromagnetBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Visible feedback that the kinetic electromagnet is running: a glowing ring
 * floating just above the block, rotating around the block's chosen axis at
 * a rate proportional to the kinetic speed (RPM).
 *
 * <p>This is a deliberately lightweight stand-in for a full Flywheel
 * {@code Visual}. Flywheel integration would let the renderer batch with
 * Create's other rotating visuals, but requires a partial-model registration
 * and a {@code KineticBlockEntityVisual} subclass — substantially heavier than
 * what a simple visual cue justifies. The ring renders fine via the standard
 * BER pipeline.
 */
public final class KineticElectromagnetRenderer implements BlockEntityRenderer<KineticElectromagnetBlockEntity> {

    private static final int SEGMENTS = 16;
    private static final float RADIUS = 0.45f;

    public KineticElectromagnetRenderer(final BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(
            final KineticElectromagnetBlockEntity be, final float partialTick, final PoseStack ps,
            final MultiBufferSource buffers, final int packedLight, final int packedOverlay
    ) {
        final float speed = be.getSpeed();
        if (Math.abs(speed) < 0.5f) return;

        final Direction.Axis axis = be.getBlockState().getValue(KineticElectromagnetBlock.AXIS);

        // Angle: ramp by speed (deg/tick) over world time + partialTick. Game-time
        // is stable across saves and means every kinetic emag in a world rotates
        // in lockstep, which looks tidy when many are placed in a row.
        final long gameTime = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
        final float angle = ((gameTime + partialTick) * (speed * 0.5f)) % 360f;

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);

        // Orient so the ring lies in the plane perpendicular to the axis.
        switch (axis) {
            case X -> ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(90));
            case Z -> ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            default -> { /* Y: default orientation, ring lies horizontal */ }
        }
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));

        // Color tint: brighter when faster.
        final float intensity = Math.min(1.0f, Math.abs(speed) / 256f);
        final int r = (int) (140 + 100 * intensity);
        final int g = (int) (90 + 60 * intensity);
        final int b = (int) (30 + 40 * intensity);
        final int a = (int) (160 + 80 * intensity);

        final VertexConsumer buf = buffers.getBuffer(RenderType.lines());
        final Matrix4f m = ps.last().pose();

        // Draw a polygon outline approximating a ring.
        for (int i = 0; i < SEGMENTS; i++) {
            final double t0 = (i / (double) SEGMENTS) * Math.PI * 2;
            final double t1 = ((i + 1) / (double) SEGMENTS) * Math.PI * 2;
            final float x0 = (float) (Math.cos(t0) * RADIUS);
            final float z0 = (float) (Math.sin(t0) * RADIUS);
            final float x1 = (float) (Math.cos(t1) * RADIUS);
            final float z1 = (float) (Math.sin(t1) * RADIUS);
            buf.addVertex(m, x0, 0, z0).setColor(r, g, b, a).setNormal(0, 1, 0);
            buf.addVertex(m, x1, 0, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
        }

        ps.popPose();
    }
}
