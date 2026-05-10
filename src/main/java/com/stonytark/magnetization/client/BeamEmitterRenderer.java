package com.stonytark.magnetization.client;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Translucent ribbon along the field's axis for DIRECTIONAL emitters. The ribbon
 * is composed of two cross-hatched quads (rotated 90° around the axis) so it
 * looks volumetric from any angle. Color: red = repel, blue = attract.
 */
public final class BeamEmitterRenderer<T extends AbstractEmitterBlockEntity> implements BlockEntityRenderer<T> {

    /** Ribbon half-width, in blocks. */
    private static final float HALF_WIDTH = 0.18f;

    public BeamEmitterRenderer(final BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(
            final T be, final float partialTick, final PoseStack poseStack,
            final MultiBufferSource buffers, final int packedLight, final int packedOverlay
    ) {
        final MagneticField field = be.currentField();
        if (field == null) return;
        if (field.shape() != MagneticField.Shape.DIRECTIONAL) return;

        final Vec3 axis = field.axis().normalize();
        final double length = field.range();
        final boolean repel = field.polarity().sign() > 0;
        final int r = repel ? 255 : 60;
        final int g = 60;
        final int b = repel ? 80 : 255;
        final int aNear = 200;
        final int aFar = 30; // fade alpha out at the far end

        // Build two perpendicular vectors orthogonal to the axis.
        final Vec3 up = Math.abs(axis.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        final Vec3 perp1 = axis.cross(up).normalize();
        final Vec3 perp2 = axis.cross(perp1).normalize();

        final VertexConsumer buf = buffers.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        final Matrix4f m = poseStack.last().pose();

        drawRibbon(buf, m, axis, perp1, length, r, g, b, aNear, aFar);
        drawRibbon(buf, m, axis, perp2, length, r, g, b, aNear, aFar);

        poseStack.popPose();
    }

    private static void drawRibbon(
            final VertexConsumer buf, final Matrix4f m,
            final Vec3 axis, final Vec3 perp, final double length,
            final int r, final int g, final int b, final int aNear, final int aFar
    ) {
        final float ax = (float) axis.x, ay = (float) axis.y, az = (float) axis.z;
        final float L = (float) length;
        final float px = (float) perp.x * HALF_WIDTH;
        final float py = (float) perp.y * HALF_WIDTH;
        final float pz = (float) perp.z * HALF_WIDTH;

        // Quad: emitter origin (near, full alpha) → far end (faded). Two triangles.
        // Vertices: nearLeft, nearRight, farRight, farLeft (CCW from above).
        // RenderType.lightning() takes (pos, color); no UV.
        addVertex(buf, m, -px, -py, -pz, r, g, b, aNear);
        addVertex(buf, m,  px,  py,  pz, r, g, b, aNear);
        addVertex(buf, m, ax * L + px, ay * L + py, az * L + pz, r, g, b, aFar);
        addVertex(buf, m, ax * L - px, ay * L - py, az * L - pz, r, g, b, aFar);
    }

    private static void addVertex(
            final VertexConsumer buf, final Matrix4f m,
            final float x, final float y, final float z,
            final int r, final int g, final int b, final int a
    ) {
        buf.addVertex(m, x, y, z).setColor(r, g, b, a);
    }

    @Override
    public boolean shouldRenderOffScreen(final T be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
