package com.stonytark.magnetization.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.golem.CustomGolemHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;

/** Textual Create-goggles readout for entity-backed golem field sources. */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class GolemGoggleHud {
    private GolemGoggleHud() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(Magnetization.id("golem_goggle_hud"), GolemGoggleHud::render);
    }

    private static void render(final GuiGraphics graphics, final DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen != null
                || !GogglesItem.isWearingGoggles(mc.player)
                || !(mc.hitResult instanceof EntityHitResult hit)
                || !CustomGolemHud.supports(hit.getEntity())) return;

        final ArrayList<Component> lines = new ArrayList<>();
        lines.add(hit.getEntity().getDisplayName().copy().withStyle(net.minecraft.ChatFormatting.BOLD));
        lines.addAll(CustomGolemHud.lines(hit.getEntity()));
        final int width = lines.stream().mapToInt(mc.font::width).max().orElse(0);
        final int preferredX = graphics.guiWidth() / 2 + 14;
        final int x = Math.max(6, Math.min(preferredX, graphics.guiWidth() - width - 6));
        final int y = graphics.guiHeight() / 2 + 12;
        graphics.fill(x - 4, y - 4, x + width + 4, y + lines.size() * 10 + 3, 0xB0101010);
        for (int line = 0; line < lines.size(); line++) {
            graphics.drawString(mc.font, lines.get(line), x, y + line * 10, 0xFFFFFFFF, true);
        }
    }
}
