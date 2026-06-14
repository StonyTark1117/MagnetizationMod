package com.stonytark.magnetization.client.screen;

import com.stonytark.magnetization.menu.HomopolarMotorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Homopolar Motor — a single magnet slot over a vanilla-style
 * container background, plus the player inventory.
 */
public class HomopolarMotorScreen extends AbstractContainerScreen<HomopolarMotorMenu> {

    private static final ResourceLocation BG = ResourceLocation.parse("textures/gui/container/generic_54.png");

    public HomopolarMotorScreen(final HomopolarMotorMenu menu, final Inventory inv, final Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick, final int mouseX, final int mouseY) {
        final int x = (width - imageWidth) / 2;
        final int y = (height - imageHeight) / 2;
        // Top strip (title + content area) and the player-inventory strip, cropped
        // from the generic chest texture — same stand-in approach as EmitterScreen.
        graphics.blit(BG, x, y, 0, 0, imageWidth, 90);
        graphics.blit(BG, x, y + 90, 0, 126, imageWidth, 96);
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
