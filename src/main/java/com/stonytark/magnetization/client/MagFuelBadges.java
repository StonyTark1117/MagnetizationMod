package com.stonytark.magnetization.client;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.FusionFuelInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

import java.util.Map;

/**
 * Draws the isotope/element marking (H / D / T / He³ / He / Ne / Ar / Kr / Xe / Rn / Li / Ga)
 * over every fuel item icon.
 *
 * <p>Item decorations render through the same {@code renderItemDecorations} path the
 * vanilla inventory uses, so the badge shows up unchanged in the player inventory, in
 * machine GUIs, and in JEI / REI / EMI ingredient lists — the places the 1.3 audit
 * flagged as unable to tell one bucket or cell from another.
 *
 * <p>The badge is drawn in the <b>top-left</b> at half scale: the stack count owns the
 * bottom-right and the durability bar owns the bottom edge, so neither collides. Text
 * (not hue) is the identifying channel; the colour is a redundant second cue.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID, value = Dist.CLIENT)
public final class MagFuelBadges {

    /** Half-size text — a 3-glyph badge ("He³") then fits inside the 16px slot. */
    private static final float SCALE = 0.5f;

    private MagFuelBadges() {}

    @SubscribeEvent
    public static void onRegisterDecorations(final RegisterItemDecorationsEvent event) {
        for (final Map.Entry<net.minecraft.world.item.Item, FusionFuelInfo.Badge> e
                : FusionFuelInfo.allBadges().entrySet()) {
            final FusionFuelInfo.Badge badge = e.getValue();
            event.register(e.getKey(), (gui, font, stack, x, y) -> {
                draw(gui, font, badge, x, y);
                return false;  // pose is pushed/popped; GL render state is untouched
            });
        }
    }

    private static void draw(final GuiGraphics gui, final net.minecraft.client.gui.Font font,
                             final FusionFuelInfo.Badge badge, final int x, final int y) {
        final var pose = gui.pose();
        pose.pushPose();
        // Above the item model (z=150 is where vanilla puts the stack count) so the
        // badge is never buried inside the 3D bucket/cell render.
        pose.translate(x + 1.0f, y + 1.0f, 200.0f);
        pose.scale(SCALE, SCALE, 1.0f);
        gui.drawString(font, badge.text(), 0, 0, badge.argb(), true);
        pose.popPose();
    }
}
