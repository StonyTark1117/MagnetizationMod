package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidBlock;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Placed magnetized ferrofluid can't be shown by the WTHIT/Jade/TOP hover
 * overlays — the crosshair pick passes straight through fluids to the block
 * behind. This small HUD does a fluid-inclusive raytrace from the player's eye
 * and, when it lands on magnetized ferrofluid, shows that fluid's pole just
 * above the hotbar. Plain ferrofluid (no pole) shows nothing.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class FerrofluidPolarityHud {

    private static final double REACH = 6.0d;
    private static final int HOTBAR_OFFSET = 72;
    private static final float SCALE = 0.85f;

    private FerrofluidPolarityHud() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(final RegisterGuiLayersEvent event) {
        event.registerAboveAll(Magnetization.id("ferrofluid_polarity_hud"), FerrofluidPolarityHud::render);
    }

    private static void render(final GuiGraphics graphics, final DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        final LocalPlayer player = mc.player;
        final Level level = mc.level;

        final Vec3 eye = player.getEyePosition(delta.getGameTimeDeltaPartialTick(true));
        final Vec3 end = eye.add(player.getViewVector(1.0f).scale(REACH));
        final BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;

        final BlockState state = level.getBlockState(hit.getBlockPos());
        if (!state.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get())
                || !state.hasProperty(MagnetizedFerrofluidBlock.POLARITY)) {
            return;
        }
        final MagneticPolarity pole = state.getValue(MagnetizedFerrofluidBlock.POLARITY);
        final ChatFormatting colour = pole == MagneticPolarity.NORTH ? ChatFormatting.RED : ChatFormatting.AQUA;
        final Component line = Component.translatable("tooltip.magnetization.ferrofluid_magnetized",
                Component.translatable("tooltip.magnetization.polarity." + pole.name().toLowerCase())
                        .withStyle(colour));

        final Font font = mc.font;
        final int sw = graphics.guiWidth();
        final int sh = graphics.guiHeight();
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1.0f);
        final int tw = font.width(line);
        final int x = (int) ((sw / 2.0f) / SCALE) - tw / 2;
        final int y = (int) ((sh - HOTBAR_OFFSET) / SCALE);
        graphics.drawString(font, line, x, y, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }
}
