package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.compat.ponder.PonderSceneCatalog;
import com.stonytark.magnetization.gametest.LifecyclePresentationAudit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.stonytark.magnetization.gametest.LifecyclePresentationAudit.check;

/** Plays the shipping scenes through ordinary screen ticks and captures real world rendering. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class LifecyclePresentationAuditClient {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/validation-client");
    private static int ticks, state, row, viewAge, sceneIndex, previousTime;
    private static boolean done, finalCaptured;
    private static String requested;
    private static final List<PonderSceneCatalog.Scene> SCENES = new ArrayList<>();
    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        String phase = System.getProperty("magnetization.audit.validation", "");
        if (phase.isEmpty() || done) return;
        var mc = Minecraft.getInstance(); var control = LifecyclePresentationAudit.control();
        if (mc.player == null || mc.level == null || mc.gameMode == null || !Files.exists(control.resolve("server-ready"))) return;
        try {
            ticks++;
            if (state == 0 && ticks > 60) {
                check(LifecyclePresentationAudit.bookPresent() == !phase.equals("disabled"), "Client book registration mismatch");
                if (phase.equals("disabled") || phase.equals("gift-off")) {
                    check(mc.player.getInventory().items.stream().noneMatch(LifecyclePresentationAudit::isManual), "Unexpected gifted book");
                    capture(mc, "login-" + phase); clientDone(); state = 8; return;
                }
                int slot = -1;
                for (int i = 0; i < 9; i++) if (LifecyclePresentationAudit.isManual(mc.player.getInventory().getItem(i))) slot = i;
                check(slot >= 0, "Gift missing from synchronized hotbar"); mc.player.getInventory().selected = slot;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); state = 1; ticks = 0;
            } else if (state == 1 && ticks > 25) {
                check(mc.screen != null && mc.screen.getClass().getSimpleName().equals("GuiBookLanding"), "Gift did not open native manual");
                capture(mc, "manual-" + phase);
                LOG.info("VALIDATION_MANUAL_OPEN_PASS phase={}", phase); mc.setScreen(null);
                if (!phase.equals("initial")) { clientDone(); state = 8; return; }
                SCENES.addAll(PonderSceneCatalog.coreScenes()); SCENES.addAll(PonderSceneCatalog.optionalScenes());
                mc.options.hideGui = true; requestView(0, false); state = 2;
            } else if (state == 2) {
                if (!Files.exists(control.resolve("view-ready")) || !Files.readString(control.resolve("view-ready")).equals(requested)) return;
                if (++viewAge < 60) return;
                var pos = new BlockPos(0, 100, row * 4);
                var anchor = (dev.silvergold.simulatedcoasters.track.anchor.CoasterAnchorpointBlockEntity) mc.level.getBlockEntity(pos);
                check(anchor != null && anchor.hasAnchorPeer(pos.east(12)), "Rendered anchor curve did not synchronize");
                LOG.info("VALIDATION_RENDER_DIAGNOSTIC backend={} visualization={} curves={}",
                        dev.engine_room.flywheel.api.backend.BackendManager.currentBackend(),
                        dev.engine_room.flywheel.api.visualization.VisualizationManager.supportsVisualization(mc.level),
                        anchor.getAnchorPeerCurvesView().values().stream().map(c -> "primary=" + c.isPrimary() + " length=" + c.getLength() + " starts=" + c.starts).toList());
                var manager = dev.engine_room.flywheel.api.visualization.VisualizationManager.get(mc.level);
                if (manager != null) {
                    var storage = manager.blockEntities().getClass().getMethod("getStorage").invoke(manager.blockEntities());
                    var visual = storage.getClass().getMethod("visualAtPos", long.class).invoke(storage, pos.asLong());
                    if (visual != null) {
                        var field = visual.getClass().getDeclaredField("visuals"); field.setAccessible(true);
                        LOG.info("VALIDATION_RENDER_VISUAL class={} children={}", visual.getClass().getName(), ((java.util.List<?>) field.get(visual)).size());
                    } else LOG.info("VALIDATION_RENDER_VISUAL absent total={}", manager.blockEntities().visualCount());
                }
                check(anchor.getAnchorPeerCurvesView().values().stream().anyMatch(com.simibubi.create.content.trains.track.BezierConnection::isPrimary), "Fixture lost its primary render curve");
                check(anchor.getAnchorPeerCurvesView().values().stream().anyMatch(dev.silvergold.simulatedcoasters.track.CoasterPeerCurveHandleLengths::isCoaster),
                        "Fixture did not synchronize a renderable coaster curve");
                if (row < LifecyclePresentationAudit.STYLES.length) {
                    check(anchor.getPeerCurveBeamDiffuseRgb(pos.east(12)) == 0x4422AA
                            && anchor.getPeerCurveRailDiffuseRgb(pos.east(12)) == 0xDDDDAA, "Independent track colors did not synchronize");
                    capture(mc, "style-" + LifecyclePresentationAudit.STYLES[row]);
                    LOG.info("VALIDATION_STYLE_PASS style={} beam=4422aa rail=ddddaa curves={}", LifecyclePresentationAudit.STYLES[row], anchor.getAnchorPeerCurvesView().size());
                    requestView(++row, false);
                } else {
                    boolean field = requested.endsWith("true");
                    check(com.stonytark.magnetization.compat.coastersmagnetized.MagCoastersMagnetizedCompat.clientFieldPowersAnchor(pos) == field,
                            "Field power packet did not reflect transition");
                    boolean renderedPower = net.antopfr.coastersmagnetized.magnet.MagnetizedAnchors.isPoweredForRender(mc.level, pos);
                    check(renderedPower == field, "Native powered render predicate mismatch");
                    var variant = net.antopfr.coastersmagnetized.client.MagneticTrackModels.variantFor(anchor, pos.east(12));
                    check(variant != null, "Missing native magnetic track model");
                    String mode = row == LifecyclePresentationAudit.STYLES.length ? "magnet" : "brake";
                    capture(mc, mode + "-" + (field ? "field-on" : "field-off") + "-" + viewCycle);
                    check(variant.centerBeam().modelLocation().getPath().contains("powered") == field, "Wrong powered model variant");
                    LOG.info("VALIDATION_POWER_VISUAL_PASS mode={} field={} model={}", mode, field, variant.centerBeam().modelLocation());
                    if (viewCycle < 3) { viewCycle++; requestView(row, viewCycle % 2 == 1); }
                    else if (row == LifecyclePresentationAudit.STYLES.length) { row++; viewCycle = 0; requestView(row, false); }
                    else { mc.options.hideGui = false; openScene(mc); state = 3; }
                }
            } else if (state == 3) {
                check(mc.screen instanceof AuditPonderUI, "Ponder playback screen closed unexpectedly");
                var ui = (AuditPonderUI) mc.screen; var scene = ui.getActiveScene();
                check(scene.getId().equals(ResourceLocation.parse("magnetization:" + SCENES.get(sceneIndex).id())), "Wrong scene played");
                check(scene.getCurrentTime() >= previousTime, "Playback moved backwards");
                if (previousTime < 65 && scene.getCurrentTime() >= 65) capture(mc, "ponder-" + SCENES.get(sceneIndex).id() + "-first");
                if (!finalCaptured && scene.getCurrentTime() >= scene.getTotalTime() - 50) {
                    switch (SCENES.get(sceneIndex).id()) {
                        case "gas_exciter", "gas_vent" -> check(scene.getWorld().getBlockState(new BlockPos(3, 1, 2))
                                .is(net.minecraft.world.level.block.Blocks.PINK_STAINED_GLASS), "Gas diagram volume missing");
                        case "ion_thruster" -> {
                            check(scene.getWorld().getBlockState(new BlockPos(1, 1, 1)).is(net.minecraft.world.level.block.Blocks.WHITE_STAINED_GLASS), "Helium marker missing");
                            check(scene.getWorld().getBlockState(new BlockPos(2, 1, 1)).is(net.minecraft.world.level.block.Blocks.PURPLE_STAINED_GLASS), "Xenon marker missing");
                            check(scene.getWorld().getBlockState(new BlockPos(3, 1, 1)).is(net.minecraft.world.level.block.Blocks.LIME_STAINED_GLASS), "Radon marker missing");
                        }
                        default -> { }
                    }
                    capture(mc, "ponder-" + SCENES.get(sceneIndex).id() + "-last"); finalCaptured = true;
                }
                previousTime = scene.getCurrentTime();
                if (scene.isFinished()) {
                    check(previousTime >= scene.getTotalTime(), "Scene finished before total time");
                    LOG.info("VALIDATION_PONDER_PASS id={} elapsed={} total={} title={}", scene.getId(), previousTime, scene.getTotalTime(), scene.getTitle());
                    if (++sceneIndex < SCENES.size()) openScene(mc);
                    else { mc.setScreen(null); clientDone(); state = 4; }
                }
            } else if (state == 4 && Files.exists(control.resolve("package-off"))) {
                if (LifecyclePresentationAudit.bookPresent()) return;
                LOG.info("VALIDATION_CLIENT_PACKAGE_OFF_PASS registry=absent");
                Files.writeString(control.resolve("client-package-off"), "pass"); state = 5;
            } else if (state == 5 && Files.exists(control.resolve("package-on"))) {
                check(!LifecyclePresentationAudit.bookPresent(), "Book unexpectedly returned without client restart");
                LOG.info("VALIDATION_CLIENT_PACKAGE_ON_PASS registry=absent restart=required");
                Files.writeString(control.resolve("client-package-on"), "pass"); state = 8;
            }
            if (state == 8) { done = true; LOG.info("VALIDATION_CLIENT_PASS phase={}", phase); }
        } catch (Throwable error) { done = true; LOG.error("VALIDATION_CLIENT_FAILED", error); }
    }
    private static int viewCycle;
    private static void requestView(int nextRow, boolean field) throws Exception {
        requested = nextRow + ":" + field; viewAge = 0;
        Files.writeString(LifecyclePresentationAudit.control().resolve("view-request"), requested);
    }
    private static void clientDone() throws Exception {
        Files.writeString(LifecyclePresentationAudit.control().resolve("client-done"), "pass");
        String phase = System.getProperty("magnetization.audit.validation");
        if (!phase.equals("initial")) { done = true; LOG.info("VALIDATION_CLIENT_PASS phase={}", phase); }
    }
    private static void openScene(Minecraft mc) {
        var definition = SCENES.get(sceneIndex);
        var compiled = net.createmod.ponder.foundation.PonderIndex.getSceneAccess()
                .compile(ResourceLocation.parse(definition.targets().getFirst())).stream()
                .filter(scene -> scene.getId().equals(ResourceLocation.parse("magnetization:" + definition.id()))).toList();
        check(compiled.size() == 1, "Expected one advertised scene for " + definition.id());
        mc.setScreen(new AuditPonderUI(new ArrayList<>(compiled))); previousTime = 0; finalCaptured = false;
        LOG.info("VALIDATION_PONDER_START id={} duration={}", definition.id(), compiled.getFirst().getTotalTime());
    }
    private static final class AuditPonderUI extends net.createmod.ponder.foundation.ui.PonderUI {
        AuditPonderUI(List<net.createmod.ponder.foundation.PonderScene> scenes) { super(scenes); setComfyReadingEnabled(false); }
    }
    private static void capture(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "validation-" + name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("VALIDATION_CAPTURE {} {}", name, message.getString()));
    }
}
