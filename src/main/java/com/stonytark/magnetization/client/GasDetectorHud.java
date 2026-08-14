package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.item.GasDetectorScanner;
import com.stonytark.magnetization.network.GasDetectorStatusPayload;
import com.stonytark.magnetization.network.GasDetectorStatusRequestPayload;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** Detailed inspection overlay opened by right-clicking the gas detector. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class GasDetectorHud {
    private static final int HOTBAR_OFFSET = 85;
    private static final int SYNC_INTERVAL_TICKS = 5;
    private static boolean expanded;
    private static int lastRequestTick = Integer.MIN_VALUE;

    private GasDetectorHud() {}

    @SubscribeEvent
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (!event.getEntity().level().isClientSide
                || MagConfig.isItemDisabled(event.getItemStack())
                || !event.getItemStack().is(MagItems.GAS_DETECTOR.get())) return;
        expanded = !expanded;
        if (expanded) {
            GasDetectorStatusPayload.clearClientSnapshot();
            requestSnapshot();
        }
        else GasDetectorStatusPayload.clearClientSnapshot();
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            expanded = false;
            GasDetectorStatusPayload.clearClientSnapshot();
            return;
        }
        if (!expanded) return;
        if (!holdingDetector(player)) {
            expanded = false;
            GasDetectorStatusPayload.clearClientSnapshot();
            return;
        }
        if (player.tickCount - lastRequestTick >= SYNC_INTERVAL_TICKS) requestSnapshot();
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(Magnetization.id("gas_detector_hud"), GasDetectorHud::render);
    }

    private static void render(final GuiGraphics graphics, final DeltaTracker delta) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (!expanded || player == null || minecraft.options.hideGui || minecraft.screen != null
                || (minecraft.getDebugOverlay() != null && minecraft.getDebugOverlay().showDebugScreen())) return;
        if (!holdingDetector(player)) {
            expanded = false;
            GasDetectorStatusPayload.clearClientSnapshot();
            return;
        }

        final GasDetectorScanner.Reading reading = GasDetectorScanner.nearest(player.level(), player.blockPosition());
        final GasDetectorStatusPayload exposure = GasDetectorStatusPayload.latest();
        final int x = graphics.guiWidth() / 2;
        final int y = graphics.guiHeight() - HOTBAR_OFFSET;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.75f, 0.75f, 1f);
        if (!reading.found()) {
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable("hud.magnetization.gas_detector.none")
                            .withStyle(ChatFormatting.DARK_GRAY), 0, 0, 0xFFFFFFFF);
            drawExposure(graphics, minecraft.font, exposure, reading, 10);
        } else {
            final Component gas = new net.neoforged.neoforge.fluids.FluidStack(reading.fluid(), 1).getHoverName();
            final Component title = Component.translatable("hud.magnetization.gas_detector.title", gas)
                    .withStyle(reading.dangerous() ? ChatFormatting.RED : ChatFormatting.AQUA);
            final Component status = Component.translatable("hud.magnetization.gas_detector.status",
                    Component.translatable("hud.magnetization.gas_detector.status." + reading.statusKey()));
            final double dx = reading.position().getX() + 0.5 - player.getX();
            final double dz = reading.position().getZ() + 0.5 - player.getZ();
            final double bearing = (Math.toDegrees(Math.atan2(-dx, dz)) + 360.0) % 360.0;
            final Component location = Component.translatable("hud.magnetization.gas_detector.location",
                    cardinal(bearing), String.format("%.0f°", bearing), String.format("%.1fm", reading.distance()));
            graphics.drawCenteredString(minecraft.font, title, 0, 0, 0xFFFFFFFF);
            graphics.drawCenteredString(minecraft.font, status, 0, 10, 0xFFFFFFFF);
            graphics.drawCenteredString(minecraft.font, location, 0, 20, 0xFFFFFFFF);
            drawExposure(graphics, minecraft.font, exposure, reading, 30);
            if (reading.dangerous()) {
                graphics.drawCenteredString(minecraft.font,
                        Component.translatable("hud.magnetization.gas_detector.danger")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 0, 50, 0xFFFFFFFF);
            }
        }
        graphics.pose().popPose();
    }

    private static boolean holdingDetector(final LocalPlayer player) {
        final ItemStack main = player.getMainHandItem();
        final ItemStack off = player.getOffhandItem();
        return (!MagConfig.isItemDisabled(main) && main.is(MagItems.GAS_DETECTOR.get()))
                || (!MagConfig.isItemDisabled(off) && off.is(MagItems.GAS_DETECTOR.get()));
    }

    private static void requestSnapshot() {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !holdingDetector(player)) return;
        lastRequestTick = player.tickCount;
        PacketDistributor.sendToServer(new GasDetectorStatusRequestPayload());
    }

    private static void drawExposure(final GuiGraphics graphics, final Font font,
                                     final GasDetectorStatusPayload exposure,
                                     final GasDetectorScanner.Reading reading, final int y) {
        if (exposure == null) {
            graphics.drawCenteredString(font,
                    Component.translatable("hud.magnetization.gas_detector.syncing")
                            .withStyle(ChatFormatting.DARK_GRAY), 0, y, 0xFFFFFFFF);
            return;
        }

        final long percent = Math.round(exposure.dose() * 100.0d / exposure.threshold());
        final ChatFormatting doseColour = exposure.dose() >= exposure.threshold()
                ? ChatFormatting.RED : exposure.dose() > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
        graphics.drawCenteredString(font,
                Component.translatable("hud.magnetization.gas_detector.exposure",
                        exposure.dose(), exposure.threshold(), percent).withStyle(doseColour),
                0, y, 0xFFFFFFFF);

        final Component safety;
        if (!exposure.radiationEnabled()) {
            safety = Component.translatable("hud.magnetization.gas_detector.safety.disabled")
                    .withStyle(ChatFormatting.DARK_GRAY);
        } else if (exposure.exposed()) {
            safety = Component.translatable("hud.magnetization.gas_detector.safety.exposed",
                            metres(exposure.distanceToSafety()))
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        } else if (reading.dangerous()) {
            safety = Component.translatable("hud.magnetization.gas_detector.safety.radon_clearance",
                            metres(reading.distance()))
                    .withStyle(ChatFormatting.GREEN);
        } else if (exposure.dose() > 0) {
            safety = Component.translatable("hud.magnetization.gas_detector.safety.recovering",
                            exposure.recoveryPerTick()).withStyle(ChatFormatting.YELLOW);
        } else {
            safety = Component.translatable("hud.magnetization.gas_detector.safety.clear")
                    .withStyle(ChatFormatting.GREEN);
        }
        graphics.drawCenteredString(font, safety, 0, y + 10, 0xFFFFFFFF);
    }

    private static String metres(final double distance) {
        return String.format(Locale.ROOT, "%.1fm", Math.max(0.1d, distance));
    }

    private static String cardinal(final double degrees) {
        final String[] rose = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return rose[(int) Math.floor(((degrees + 22.5) % 360.0) / 45.0)];
    }
}
