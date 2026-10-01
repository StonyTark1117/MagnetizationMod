package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.shaft.MagneticShaftBlockEntity;
import com.stonytark.magnetization.content.shaft.MagneticShaftNetwork;
import com.stonytark.magnetization.network.FieldInspectionPayload;
import com.stonytark.magnetization.network.FieldInspectionRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import java.util.Locale;

/** Hold sneak with Engineer's Goggles to inspect the ship under the crosshair. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class FieldInspectionOverlay {
    private static int requestTicks;
    private static boolean wearing() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.options.hideGui && GogglesItem.isWearingGoggles(mc.player);
    }
    private static boolean inspecting() { return wearing() && Minecraft.getInstance().player.isShiftKeyDown() && Minecraft.getInstance().screen == null; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!inspecting()) { FieldInspectionPayload.clear(); requestTicks = 0; return; }
        if (requestTicks++ % 5 == 0) PacketDistributor.sendToServer(new FieldInspectionRequestPayload());
    }
    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Magnetization.id("field_inspection"), (gui, delta) -> {
            if (!inspecting()) return;
            var mc = Minecraft.getInstance();
            var p = FieldInspectionPayload.latest();
            int y = 64;
            if (p == null || p.ship() == null || !FieldInspectionPayload.fresh() || !p.dimension().equals(mc.level.dimension().location())) {
                gui.drawString(mc.font, Component.translatable("inspection.magnetization.aim"), 12, y, 0xBBDDEE);
                return;
            }
            var data = p.snapshot();
            int limited = data.limited();
            Component[] lines = {
                    Component.translatable("inspection.magnetization.ship", p.ship().toString().substring(0, 8)),
                    Component.translatable("inspection.magnetization.force", vector(data.force())),
                    Component.translatable("inspection.magnetization.torque", vector(data.torque())),
                    Component.translatable("inspection.magnetization.sources", data.sources().size() + data.omitted(), limited),
                    Component.translatable("inspection.magnetization.legend")
            };
            for (Component line : lines) {
                gui.fill(8, y - 2, Math.min(gui.guiWidth() - 8, 16 + mc.font.width(line)), y + 10, 0xB0101820);
                gui.drawString(mc.font, line, 12, y, 0xDCEFF7); y += 13;
            }
            if (data.omitted() > 0) gui.drawString(mc.font, Component.translatable("inspection.magnetization.omitted", data.omitted()), 12, y, 0xFFCC66);
        });
    }
    private static String vector(Vec3 v) { return String.format(Locale.ROOT, "(%+.3g, %+.3g, %+.3g)", v.x, v.y, v.z); }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !wearing()) return;
        var mc = Minecraft.getInstance();
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        var vertices = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        Matrix4f matrix = pose.last().pose();
        for (var shaft : MagneticShaftNetwork.loadedShafts(mc.level)) {
            Vec3 center = shaft.worldCenter().subtract(camera);
            if (center.lengthSqr() > 96 * 96) continue;
            if (shaft.status() == MagneticShaftBlockEntity.Status.SOURCE && shaft.getSpeed() != 0) sphere(vertices, matrix, center, shaft.transmissionRange());
            if (shaft.transmitter() != null && mc.level.hasChunkAt(shaft.transmitter())
                    && mc.level.getBlockEntity(shaft.transmitter()) instanceof MagneticShaftBlockEntity source) {
                line(vertices, matrix, center, source.worldCenter().subtract(camera), shaft.isOverStressed() ? 0xFFFF9966 : 0xFF66DDCC);
                arrow(vertices, matrix, source.worldCenter().subtract(camera), shaft.worldCenter().subtract(source.worldCenter()), 0xFF66DDCC, 1.5);
            }
        }
        var p = FieldInspectionPayload.latest();
        if (inspecting() && p != null && p.ship() != null && FieldInspectionPayload.fresh() && p.dimension().equals(mc.level.dimension().location())) {
            for (var source : p.snapshot().sources()) {
                line(vertices, matrix, source.origin().subtract(camera), p.center().subtract(camera), source.capped() ? 0x807F7F7F : 0x807BA6CF);
                arrow(vertices, matrix, source.origin().subtract(camera), source.applied(), 0xFF66DDFF, arrowLength(source.applied()));
                if (source.capped()) arrow(vertices, matrix, source.origin().subtract(camera), source.requested(), 0xFFEE8866, arrowLength(source.requested()));
            }
            arrow(vertices, matrix, p.center().subtract(camera), p.snapshot().force(), 0xFF55FF88, arrowLength(p.snapshot().force()));
            arrow(vertices, matrix, p.center().subtract(camera), p.snapshot().torque(), 0xFFFFCC55, arrowLength(p.snapshot().torque()));
        }
        pose.popPose(); buffers.endBatch(RenderType.lines());
    }
    private static double arrowLength(Vec3 force) { return Math.min(5, .5 + Math.log1p(force.length())); }
    private static void arrow(VertexConsumer b, Matrix4f m, Vec3 origin, Vec3 vector, int color, double length) {
        if (vector.lengthSqr() < 1e-12) return;
        Vec3 axis = vector.normalize(), tip = origin.add(axis.scale(length));
        Vec3 perpendicular = axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0,1,0) : new Vec3(1,0,0)).normalize().scale(.18);
        Vec3 back = tip.subtract(axis.scale(.35));
        line(b,m,origin,tip,color); line(b,m,tip,back.add(perpendicular),color); line(b,m,tip,back.subtract(perpendicular),color);
    }
    private static void sphere(VertexConsumer b, Matrix4f m, Vec3 center, double radius) {
        for (int plane = 0; plane < 3; plane++) for (int i=0; i<48; i++) {
            double a = i*Math.PI/24, next=(i+1)*Math.PI/24;
            double x = Math.cos(a)*radius, y = Math.sin(a)*radius, nx = Math.cos(next)*radius, ny = Math.sin(next)*radius;
            Vec3 p = plane == 0 ? new Vec3(x,y,0) : plane == 1 ? new Vec3(x,0,y) : new Vec3(0,x,y);
            Vec3 q = plane == 0 ? new Vec3(nx,ny,0) : plane == 1 ? new Vec3(nx,0,ny) : new Vec3(0,nx,ny);
            line(b,m,center.add(p),center.add(q),0x9066DDCC);
        }
    }
    private static void line(VertexConsumer b, Matrix4f m, Vec3 from, Vec3 to, int color) {
        Vec3 direction = to.subtract(from).normalize();
        b.addVertex(m,(float)from.x,(float)from.y,(float)from.z).setColor(color).setNormal((float)direction.x,(float)direction.y,(float)direction.z);
        b.addVertex(m,(float)to.x,(float)to.y,(float)to.z).setColor(color).setNormal((float)direction.x,(float)direction.y,(float)direction.z);
    }
}
