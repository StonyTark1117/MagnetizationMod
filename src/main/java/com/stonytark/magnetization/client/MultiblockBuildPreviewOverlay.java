package com.stonytark.magnetization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.jet.FusionThrusterPanel;
import com.stonytark.magnetization.content.railgun.RailgunEmitterBlock;
import com.stonytark.magnetization.content.tokamak.TokamakRingPreview;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Construction diagnostics for the three fixed-pattern machine multiblocks. While Create's goggles
 * (or a wrench) are active and the player looks at a relevant block, this draws
 * the required frame in-world and prints the same authoritative facts in a small
 * HUD: master cell, facing, invalid edge, and effective dimensions.
 *
 * <p>The overlay deliberately uses shapes as well as text: green = present,
 * red = wrong/missing, yellow = deterministic master. That makes a bad panel
 * visible from a distance and keeps the diagnostic useful for color-vision users.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class MultiblockBuildPreviewOverlay {

    private static final String WRENCH_TAG = "c:tools/wrench";
    private MultiblockBuildPreviewOverlay() {}

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !previewTool(mc.player.getMainHandItem(), mc.player.getOffhandItem(), mc.player)) return;
        final BlockPos hit = hitBlock(mc);
        if (hit == null) return;

        final PoseStack pose = event.getPoseStack();
        final Vec3 camera = event.getCamera().getPosition();
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        if (isFusionTarget(mc.level, hit)) {
            renderFusion(mc.level, hit, pose, camera, lines);
        } else if (isTokamakTarget(mc.level, hit)) {
            renderTokamak(mc.level, tokamakPreview(mc.level, hit), pose, camera, lines);
        } else {
            final BlockPos emitter = findRailgunEmitter(mc.level, hit);
            if (emitter != null) renderRailgun(mc.level, emitter, pose, camera, lines);
            else return;
        }
        buffers.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(Magnetization.id("multiblock_build_preview"),
                MultiblockBuildPreviewOverlay::renderHud);
    }

    private static void renderHud(final GuiGraphics graphics, final DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null
                || !previewTool(mc.player.getMainHandItem(), mc.player.getOffhandItem(), mc.player)) return;
        final BlockPos hit = hitBlock(mc);
        if (hit == null) return;

        final List<String> lines;
        if (isFusionTarget(mc.level, hit)) {
            final Direction facing = facingForFusion(mc.level, hit);
            final FusionThrusterPanel.Preview p = FusionThrusterPanel.preview(
                    mc.level, fusionInteriorTarget(mc.level, hit), facing, MagConfig.fusionThrusterMaxEdge());
            lines = fusionText(p);
        } else if (isTokamakTarget(mc.level, hit)) {
            lines = tokamakText(tokamakPreview(mc.level, hit));
        } else {
            final BlockPos emitter = findRailgunEmitter(mc.level, hit);
            if (emitter == null) return;
            lines = railgunText(railgunPreview(mc.level, emitter));
        }

        final Font font = mc.font;
        int width = 0;
        for (final String line : lines) width = Math.max(width, font.width(line));
        final int x = 8;
        final int y = 8;
        graphics.fill(x - 3, y - 3, x + width + 5, y + lines.size() * 10 + 3, 0xB0101018);
        for (int i = 0; i < lines.size(); i++) {
            final int colour = i == 0 ? 0xFFFFD866
                    : (lines.get(i).startsWith("Status: VALID") ? 0xFF70FF90
                    : lines.get(i).startsWith("Status: INVALID") ? 0xFFFF7070 : 0xFFE0E0E0);
            graphics.drawString(font, lines.get(i), x, y + i * 10, colour, true);
        }
    }

    private static boolean previewTool(final ItemStack main, final ItemStack off,
                                       final net.minecraft.client.player.LocalPlayer player) {
        return GogglesItem.isWearingGoggles(player) || isWrench(main) || isWrench(off);
    }

    private static boolean isWrench(final ItemStack stack) {
        return !stack.isEmpty() && stack.is(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.parse(WRENCH_TAG)));
    }

    private static BlockPos hitBlock(final Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return hit.getBlockPos();
    }

    private static boolean isFusionTarget(final Level level, final BlockPos hit) {
        if (level.getBlockState(hit).is(MagBlocks.FUSION_THRUSTER.get())) return true;
        if (!level.getBlockState(hit).is(MagBlocks.TOKAMAK_COIL.get())) return false;
        for (final Direction d : Direction.values()) {
            if (level.getBlockState(hit.relative(d)).is(MagBlocks.FUSION_THRUSTER.get())) return true;
        }
        return false;
    }

    private static BlockPos fusionInteriorTarget(final Level level, final BlockPos hit) {
        if (level.getBlockState(hit).is(MagBlocks.FUSION_THRUSTER.get())) return hit;
        for (final Direction d : Direction.values()) {
            final BlockPos p = hit.relative(d);
            if (level.getBlockState(p).is(MagBlocks.FUSION_THRUSTER.get())) return p;
        }
        return hit;
    }

    private static boolean isTokamakTarget(final Level level, final BlockPos hit) {
        return tokamakControllerTarget(level, hit) != null;
    }

    private static BlockPos tokamakControllerTarget(final Level level, final BlockPos hit) {
        return TokamakRingPreview.findController(level, hit, MagConfig.tokamakMaxEdge());
    }

    private record TokamakOverlayPreview(TokamakRingPreview.Preview target,
                                         TokamakRingPreview.Preview formed) {}

    private static TokamakOverlayPreview tokamakPreview(final Level level, final BlockPos hit) {
        final BlockPos controller = tokamakControllerTarget(level, hit);
        if (controller == null) throw new IllegalArgumentException("Tokamak target has no controller");
        final TokamakRingPreview.Preview formed = TokamakRingPreview.preview(level, controller);
        final TokamakRingPreview.Preview target;
        if (level.getBlockState(hit).is(MagBlocks.TOKAMAK_COIL.get())) {
            final int radius = Math.max(Math.abs(hit.getX() - controller.getX()),
                    Math.abs(hit.getZ() - controller.getZ()));
            target = TokamakRingPreview.previewExact(level, controller, radius * 2 + 1,
                    MagConfig.tokamakMaxEdge());
        } else {
            target = TokamakRingPreview.constructionPreview(level, controller,
                    MagConfig.tokamakMaxEdge());
        }
        return new TokamakOverlayPreview(target, formed);
    }

    private static Direction facingForFusion(final Level level, final BlockPos hit) {
        final BlockPos interior = fusionInteriorTarget(level, hit);
        final BlockState state = level.getBlockState(interior);
        return state.hasProperty(DirectionalBlock.FACING)
                ? state.getValue(DirectionalBlock.FACING) : Direction.NORTH;
    }

    private static void renderFusion(final Level level, final BlockPos hit, final PoseStack pose,
                                     final Vec3 camera, final VertexConsumer lines) {
        final BlockPos interior = fusionInteriorTarget(level, hit);
        final Direction facing = facingForFusion(level, hit);
        final FusionThrusterPanel.Preview p = FusionThrusterPanel.preview(
                level, interior, facing, MagConfig.fusionThrusterMaxEdge());
        for (final BlockPos pos : p.requiredFrame())
            drawCube(lines, pose, pos, camera, 0xFF70FF90);
        for (final BlockPos pos : p.invalidEdge())
            drawCube(lines, pose, pos, camera, 0xFFFF5050);
        if (p.master() != null) drawCube(lines, pose, p.master(), camera, 0xFFFFD866);
    }

    private static void renderTokamak(final Level level, final TokamakOverlayPreview preview,
                                      final PoseStack pose, final Vec3 camera,
                                      final VertexConsumer lines) {
        final TokamakRingPreview.Preview p = preview.target();
        for (final BlockPos pos : p.requiredFrame())
            drawCube(lines, pose, pos, camera, 0xFF70FF90);
        for (final BlockPos pos : p.invalidEdges())
            drawCube(lines, pose, pos, camera, 0xFFFF5050);
        for (final BlockPos pos : p.requiredCores())
            drawCube(lines, pose, pos, camera, 0xFF70D8FF);
        for (final BlockPos pos : p.invalidCores())
            drawCube(lines, pose, pos, camera, 0xFFFF5050);
        drawCube(lines, pose, p.controller(), camera, 0xFFFFD866);
    }

    private static List<String> fusionText(final FusionThrusterPanel.Preview p) {
        final String bad = p.invalidEdge().isEmpty() ? "none" : pos(p.invalidEdge().get(0));
        return List.of(
                "Fusion Thruster Preview",
                "Frame: " + p.requiredFrame().size() + " Tokamak Coils",
                "Master: " + (p.master() == null ? "none" : pos(p.master())),
                "Facing: " + p.facing().getName(),
                "Dimensions: " + p.panelWidth() + "x" + p.panelHeight()
                        + " (" + p.interiorWidth() + "x" + p.interiorHeight() + " interior)",
                "Invalid edge: " + bad,
                "Status: " + (p.valid() ? "VALID" : "INVALID"));
    }

    private static List<String> tokamakText(final TokamakOverlayPreview preview) {
        final TokamakRingPreview.Preview p = preview.target();
        final TokamakRingPreview.Preview formed = preview.formed();
        final String badCoil = p.invalidEdges().isEmpty() ? "none" : pos(p.invalidEdges().get(0));
        final String badCore = p.invalidCores().isEmpty() ? "none" : pos(p.invalidCores().get(0));
        final String active = formed.valid()
                ? formed.edge() + "x" + formed.edge() + " (×" + Math.max(1, formed.edge() - 2) + ")"
                : "none";
        return List.of(
                "Tokamak Preview",
                "Target ring: " + p.edge() + "x" + p.edge() + " (" + p.coilCount() + " coils)",
                "Core interior: " + (p.edge() - 2) + "x" + (p.edge() - 2)
                        + " (" + p.coreCount() + " cores)",
                "Performance when complete: ×" + Math.max(1, p.edge() - 2),
                "Active ring: " + active,
                "Master: " + pos(p.controller()),
                "Facing: horizontal ring",
                "Missing/wrong coil: " + badCoil,
                "Missing/wrong core: " + badCore,
                "Status: " + (p.valid() ? "VALID" : "INVALID"));
    }

    private record RailPreview(BlockPos emitter, BlockPos sibling, Direction facing,
                                int firstLength, int secondLength, int effectiveLength,
                                int gap, List<BlockPos> invalidEdges) {}

    private static RailPreview railgunPreview(final Level level, final BlockPos emitter) {
        final BlockState state = level.getBlockState(emitter);
        final Direction facing = state.getValue(RailgunEmitterBlock.FACING);
        final int first = clientRailLength(level, emitter, facing);
        BlockPos sibling = null;
        int gap = 0;
        for (final Direction d : Direction.values()) {
            if (d.getAxis() == facing.getAxis()) continue;
            for (int sign : new int[]{-1, 1}) {
                for (int g = 1; g <= MagConfig.railgunMaxGap(); g++) {
                    final BlockPos candidate = emitter.relative(d, sign * g);
                    final BlockState cs = level.getBlockState(candidate);
                    if (cs.is(MagBlocks.RAILGUN_EMITTER.get())
                            && cs.getValue(RailgunEmitterBlock.FACING) == facing) {
                        sibling = candidate;
                        gap = g;
                        break;
                    }
                }
                if (sibling != null) break;
            }
            if (sibling != null) break;
        }
        final int second = sibling == null ? 0 : clientRailLength(level, sibling, facing);
        final int effective = sibling == null ? first : Math.min(first, second);
        final List<BlockPos> invalid = new ArrayList<>();
        if (first < MagConfig.railgunMinLength()) invalid.add(emitter.relative(facing, first + 1));
        if (sibling != null && second < MagConfig.railgunMinLength()) {
            invalid.add(sibling.relative(facing, second + 1));
        }
        return new RailPreview(emitter, sibling, facing, first, second, effective, gap, List.copyOf(invalid));
    }

    private static int clientRailLength(final Level level, final BlockPos emitter, final Direction facing) {
        int length = 0;
        final int maxLength = MagConfig.railgunLengthLimitEnabled()
                ? MagConfig.railgunMaxLength() : Integer.MAX_VALUE;
        for (BlockPos p = emitter.relative(facing); length < maxLength
                && level.isInWorldBounds(p) && level.hasChunkAt(p);
             p = p.relative(facing)) {
            if (!level.getBlockState(p).is(MagTags.RAILGUN_RAILS)) break;
            length++;
        }
        return length;
    }

    private static BlockPos findRailgunEmitter(final Level level, final BlockPos hit) {
        if (level.getBlockState(hit).is(MagBlocks.RAILGUN_EMITTER.get())) return hit;
        if (!level.getBlockState(hit).is(MagTags.RAILGUN_RAILS)) return null;
        final int maxLength = MagConfig.railgunLengthLimitEnabled()
                ? MagConfig.railgunMaxLength() : Integer.MAX_VALUE;
        for (final Direction facing : Direction.values()) {
            for (int i = 1; i > 0 && i <= maxLength; i++) {
                final BlockPos candidate = hit.relative(facing.getOpposite(), i);
                if (!level.isInWorldBounds(candidate) || !level.hasChunkAt(candidate)) break;
                final BlockState state = level.getBlockState(candidate);
                if (state.is(MagBlocks.RAILGUN_EMITTER.get())
                        && state.getValue(RailgunEmitterBlock.FACING) == facing) return candidate;
                if (!state.is(MagTags.RAILGUN_RAILS)) break;
            }
        }
        return null;
    }

    private static void renderRailgun(final Level level, final BlockPos emitter, final PoseStack pose,
                                      final Vec3 camera, final VertexConsumer lines) {
        final RailPreview p = railgunPreview(level, emitter);
        drawCube(lines, pose, p.emitter(), camera, 0xFFFFD866);
        if (p.sibling() != null) drawCube(lines, pose, p.sibling(), camera, 0xFFFFD866);
        for (int i = 1; i <= p.firstLength(); i++)
            drawCube(lines, pose, emitter.relative(p.facing(), i), camera, 0xFF70FF90);
        if (p.sibling() != null) {
            for (int i = 1; i <= p.secondLength(); i++)
                drawCube(lines, pose, p.sibling().relative(p.facing(), i), camera, 0xFF70FF90);
        }
        for (final BlockPos pos : p.invalidEdges()) drawCube(lines, pose, pos, camera, 0xFFFF5050);
    }

    private static List<String> railgunText(final RailPreview p) {
        final String bad = p.invalidEdges().isEmpty() ? "none" : pos(p.invalidEdges().get(0));
        return List.of(
                "Railgun Preview",
                "Frame: 2 emitters + paired rail set",
                "Master: " + pos(p.emitter().compareTo(p.sibling() == null ? p.emitter() : p.sibling()) <= 0
                        ? p.emitter() : p.sibling()),
                "Facing: " + p.facing().getName(),
                "Dimensions: " + p.effectiveLength() + " length x " + p.gap() + " gap",
                "Rail lengths: " + p.firstLength() + (p.sibling() == null ? " / missing pair" : " / " + p.secondLength()),
                "Invalid edge: " + bad,
                "Status: " + (p.sibling() != null && p.effectiveLength() >= MagConfig.railgunMinLength()
                        ? "VALID" : "INVALID"));
    }

    private static String pos(final BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    private static void drawCube(final VertexConsumer out, final PoseStack pose, final BlockPos pos,
                                 final Vec3 camera, final int argb) {
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        final Matrix4f matrix = pose.last().pose();
        final int a = argb >>> 24, r = (argb >>> 16) & 0xFF, g = (argb >>> 8) & 0xFF, b = argb & 0xFF;
        final float[][] edges = {
                {0,0,0,1,0,0},{0,0,0,0,1,0},{0,0,0,0,0,1},
                {1,1,1,0,1,1},{1,1,1,1,0,1},{1,1,1,1,1,0},
                {1,0,0,1,1,0},{1,0,0,1,0,1},{0,1,0,1,1,0},
                {0,1,0,0,1,1},{0,0,1,1,0,1},{0,0,1,0,1,1}
        };
        for (final float[] e : edges) {
            out.addVertex(matrix, e[0], e[1], e[2]).setColor(r, g, b, a).setNormal(0, 1, 0);
            out.addVertex(matrix, e[3], e[4], e[5]).setColor(r, g, b, a).setNormal(0, 1, 0);
        }
        pose.popPose();
    }
}
