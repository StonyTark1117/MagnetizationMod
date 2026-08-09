package com.stonytark.magnetization.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Small text-layout helpers shared by the custom block screens. */
final class GuiTextLayout {
    private static final String ELLIPSIS = "…";

    private GuiTextLayout() { }

    static void drawClipped(final GuiGraphics graphics, final Font font, final Component text,
                            final int x, final int y, final int maxWidth, final int colour) {
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        final int headWidth = Math.max(0, maxWidth - font.width(ELLIPSIS));
        final FormattedText clipped = FormattedText.composite(
                font.substrByWidth(text, headWidth),
                FormattedText.of(ELLIPSIS, text.getStyle()));
        graphics.drawString(font, Language.getInstance().getVisualOrder(clipped), x, y, colour, false);
    }

    static void renderTooltipIfClipped(final GuiGraphics graphics, final Font font, final Component text,
                                       final int x, final int y, final int maxWidth,
                                       final int mouseX, final int mouseY) {
        if (font.width(text) <= maxWidth) return;
        if (mouseX >= x && mouseX < x + maxWidth
                && mouseY >= y && mouseY < y + font.lineHeight) {
            graphics.renderTooltip(font, text, mouseX, mouseY);
        }
    }
}
