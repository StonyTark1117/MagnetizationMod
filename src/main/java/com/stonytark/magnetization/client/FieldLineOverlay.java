package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticFieldSource;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.physics.EmitterRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Goggles-only world overlay: while the player wears Create's Engineer's
 * Goggles, every active emitter in the loaded level gets a polarity-tinted
 * horizontal ring drawn at its origin's y-plane, sized to the emitter's
 * effective range. Helps the player see how far each magnet reaches when
 * planning propulsion tracks or docking stations.
 *
 * <p>Cost: one {@link RenderType#lines()} batch per active emitter, drawn as
 * a 32-segment ring. {@link EmitterRegistry} bounds the iteration to loaded
 * emitter BEs only, so unloaded chunks contribute nothing.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class FieldLineOverlay {

    private static final int SEGMENTS = 32;

    private FieldLineOverlay() {}

    @SubscribeEvent
    public static void onRender(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!GogglesItem.isWearingGoggles(mc.player)) return;

        final PoseStack ps = event.getPoseStack();
        final Camera cam = event.getCamera();
        final Vec3 camPos = cam.getPosition();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final VertexConsumer buf = buffers.getBuffer(RenderType.lines());

        EmitterRegistry.forEach(mc.level, (level, pos) -> {
            final BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MagneticFieldSource src)) return;
            final MagneticField field = src.currentField();
            if (field == null || field.range() <= 0.5) return;

            final Vec3 origin = field.origin();
            ps.pushPose();
            ps.translate(origin.x - camPos.x, origin.y - camPos.y, origin.z - camPos.z);
            final int color = field.polarity() == MagneticPolarity.SOUTH ? 0x80_60_A0_E0 : 0x80_E0_60_60;
            // Shape-aware rendering. OMNIDIRECTIONAL gets the horizontal ring;
            // DIRECTIONAL and CONICAL get a single arrow along the field's axis
            // (otherwise a horizontal ring would mislead — a tractor beam aimed
            // sideways doesn't actually project a horizontal disc of force).
            switch (field.shape()) {
                case OMNIDIRECTIONAL -> drawRing(buf, ps.last().pose(), field.range(), color);
                case DIRECTIONAL, CONICAL -> drawArrow(buf, ps.last().pose(),
                        field.axis(), field.range(), color);
            }
            ps.popPose();
        });

        buffers.endBatch(RenderType.lines());
    }

    /** Single shaft + small arrowhead along the field's axis, length matching
     *  the field's range. Used for DIRECTIONAL / CONICAL shapes — drawing a
     *  horizontal ring on those would mislead the player about coverage. */
    private static void drawArrow(
            final VertexConsumer buf, final Matrix4f m, final Vec3 axis,
            final double length, final int argb
    ) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >>> 16) & 0xFF;
        final int g = (argb >>> 8) & 0xFF;
        final int b = argb & 0xFF;
        final Vec3 dir = axis.normalize();
        final float L = (float) length;
        final float tipX = (float) (dir.x * L);
        final float tipY = (float) (dir.y * L);
        final float tipZ = (float) (dir.z * L);
        // Shaft.
        buf.addVertex(m, 0, 0, 0).setColor(r, g, b, a).setNormal(0, 1, 0);
        buf.addVertex(m, tipX, tipY, tipZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        // Arrowhead — two short segments back from the tip on a perpendicular plane.
        final Vec3 up = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        final Vec3 perp = dir.cross(up).normalize().scale(0.5);
        final Vec3 backTip = dir.scale(L - 1.0);
        for (final Vec3 p : new Vec3[] { perp, perp.scale(-1) }) {
            final float bx = (float) (backTip.x + p.x);
            final float by = (float) (backTip.y + p.y);
            final float bz = (float) (backTip.z + p.z);
            buf.addVertex(m, tipX, tipY, tipZ).setColor(r, g, b, a).setNormal(0, 1, 0);
            buf.addVertex(m, bx, by, bz).setColor(r, g, b, a).setNormal(0, 1, 0);
        }
    }

    private static void drawRing(
            final VertexConsumer buf, final Matrix4f m, final double radius, final int argb
    ) {
        final int a = (argb >>> 24) & 0xFF;
        final int r = (argb >>> 16) & 0xFF;
        final int g = (argb >>> 8) & 0xFF;
        final int b = argb & 0xFF;
        for (int i = 0; i < SEGMENTS; i++) {
            final double t1 = 2.0 * Math.PI * i / SEGMENTS;
            final double t2 = 2.0 * Math.PI * (i + 1) / SEGMENTS;
            final float x1 = (float) (Math.cos(t1) * radius);
            final float z1 = (float) (Math.sin(t1) * radius);
            final float x2 = (float) (Math.cos(t2) * radius);
            final float z2 = (float) (Math.sin(t2) * radius);
            buf.addVertex(m, x1, 0, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
            buf.addVertex(m, x2, 0, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
        }
    }
}
