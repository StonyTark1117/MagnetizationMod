package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.gametest.AeMeteoriteRuntimeAudit;
import com.stonytark.magnetization.network.CosmicCompassTargetPayload;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;

import static com.stonytark.magnetization.gametest.AeMeteoriteRuntimeAudit.check;

/** Reads the real payload handler cache and renders the shipping item model. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class AeMeteoriteAuditClient {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("magnetization/ae-meteorite-client");
    private static String request = "", completed = "";
    private static int age;
    private static boolean failed;
    private AeMeteoriteAuditClient() {}

    @SubscribeEvent
    public static void tick(final ClientTickEvent.Post event) {
        final String phase = System.getProperty("magnetization.audit.aeMeteorite", "");
        if (phase.isEmpty() || failed) return;
        final var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            final var file = AeMeteoriteRuntimeAudit.control().resolve("stage");
            if (!Files.exists(file)) return;
            final String stage = Files.readString(file);
            if (!stage.startsWith(phase + "|") || stage.equals(completed)) return;
            if (!stage.equals(request)) { request = stage; age = 0; mc.setScreen(new CompassScreen(stage)); }
            if (++age < 70) return;
            final String[] parts = stage.split("\\|");
            final boolean hook = Boolean.parseBoolean(parts[3]);
            final float expectedAngle = Float.parseFloat(parts[4]);
            check(mc.player.getMainHandItem().is(MagItems.COSMIC_COMPASS.get()), "Cosmic Compass did not synchronize to the selected slot");
            final CosmicCompassTargetPayload received = receivedPacket();
            check(received != null, "No actual CosmicCompassTargetPayload arrived");
            check(received.dimension().equals(mc.level.dimension().location()), "Received target dimension mismatch");
            check(received.position().isPresent() == hook, "Received packet did not follow the live integration switch");
            check(!hook || received.position().orElseThrow().equals(AeMeteoriteRuntimeAudit.AE),
                    "Dead or wrong AE2 source selected by server");
            check(!hook || CosmicCompassTargetPayload.latestTarget(mc.level, mc.player.position(), 512) != null,
                    "Delivered target was not usable in this client level");
            final ItemStack compass = mc.player.getMainHandItem();
            final var property = ItemProperties.getProperty(compass, ResourceLocation.parse("magnetization:cosmic_angle"));
            check(property != null, "Shipping cosmic_angle property missing");
            final float angle = property.call(compass, mc.level, mc.player, 0);
            check(Math.abs(angle - expectedAngle) < 1e-6,
                    "Wrong target selection: expected=" + expectedAngle + " actual=" + angle + " player=" + mc.player.position());
            final var model = mc.getItemRenderer().getModel(compass, mc.level, mc.player, 0);
            final String expectedTexture = String.format(java.util.Locale.ROOT, "magnetization:item/cosmic_compass_%02d", (int) (expectedAngle * 32));
            final String texture = model.getParticleIcon().contents().name().toString();
            check(texture.equals(expectedTexture), "Actual rendered item model selected wrong needle frame: " + texture);
            final var quads = model.getQuads(null, null, net.minecraft.util.RandomSource.create(1L));
            check(!quads.isEmpty() && quads.stream().allMatch(q -> q.getSprite().contents().name().toString().equals(expectedTexture)),
                    "Rendered item quads did not use the selected needle texture");
            mc.getToasts().clear();
            final String capture = "ae-compass-" + phase + "-" + parts[1] + "-" + parts[2] + ".png";
            Screenshot.grab(mc.gameDirectory, capture, mc.getMainRenderTarget(), message -> LOG.info("AE_AUDIT_CAPTURE {} {}", capture, message.getString()));
            LOG.info("AE_AUDIT_CLIENT_PASS phase={} stage={} selection={} packetPos={} packetExpires={} clientTime={} angle={} texture={} quads={} pid={}",
                    phase, parts[1], parts[2], received.position(), received.expiresAtTick(), mc.level.getGameTime(), angle, texture, quads.size(), ProcessHandle.current().pid());
            Files.writeString(AeMeteoriteRuntimeAudit.control().resolve("client-" + phase + "-" + parts[1]), "pass");
            completed = stage;
        } catch (Throwable failure) {
            failed = true; LOG.error("AE_AUDIT_CLIENT_FAILED phase=" + phase, failure);
        }
    }

    /** Observation only: tests never call the handler or seed its cache. */
    private static CosmicCompassTargetPayload receivedPacket() throws ReflectiveOperationException {
        final var field = CosmicCompassTargetPayload.class.getDeclaredField("latest"); field.setAccessible(true);
        final Object snapshot = field.get(null);
        if (snapshot == null) return null;
        final var getter = snapshot.getClass().getDeclaredMethod("payload"); getter.setAccessible(true);
        return (CosmicCompassTargetPayload) getter.invoke(snapshot);
    }

    private static final class CompassScreen extends Screen {
        private final String[] stage;
        CompassScreen(final String value) { super(Component.literal("Cosmic Compass runtime audit")); stage = value.split("\\|"); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
            graphics.fill(0, 0, width, height, 0xE0182030);
            graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
            graphics.drawCenteredString(font, "Phase " + stage[0] + " / stage " + stage[1] + " / target " + stage[2], width / 2, 38, 0xB8D6FF);
            graphics.drawCenteredString(font, "AE2 hook " + stage[3] + " / expected angle " + stage[4], width / 2, 54, 0xB8D6FF);
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2f - 80, height / 2f - 60, 0);
            graphics.pose().scale(10, 10, 1);
            graphics.renderItem(minecraft.player.getMainHandItem(), 0, 0);
            graphics.pose().popPose();
            graphics.drawCenteredString(font, "Shipping Cosmic Compass model rendered with the connected player", width / 2, height - 35, 0xFFFFFF);
        }
    }
}
