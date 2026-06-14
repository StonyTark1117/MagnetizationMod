package com.stonytark.magnetization.client.screen;

import com.stonytark.magnetization.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the shared {@link MachineMenu}: a compact panel with one input slot,
 * an energy bar, and up to two stat lines whose labels depend on the machine kind
 * (e.g. the tokamak shows fuel runtime + current FE output).
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath("magnetization", "textures/gui/machine.png");
    // Energy bar interior (matches machine.png frame at 151,17 .. 165,75).
    private static final int BAR_X = 151, BAR_Y = 17, BAR_W = 14, BAR_H = 58;

    public MachineScreen(final MachineMenu menu, final Inventory inv, final Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partial, final int mx, final int my) {
        final int x = (width - imageWidth) / 2;
        final int y = (height - imageHeight) / 2;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight);
        // Energy fill (bottom-up), if this machine reports energy.
        if (menu.energyStored() >= 0) {
            final int filled = (int) ((long) menu.energyStored() * BAR_H / menu.energyMax());
            if (filled > 0) {
                g.fill(x + BAR_X, y + BAR_Y + (BAR_H - filled), x + BAR_X + BAR_W, y + BAR_Y + BAR_H,
                        0xFFE0AC4A);
            }
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mx, final int my, final float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        // Energy-bar hover tooltip.
        final int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        if (menu.energyStored() >= 0 && mx >= x + BAR_X && mx < x + BAR_X + BAR_W
                && my >= y + BAR_Y && my < y + BAR_Y + BAR_H) {
            g.renderTooltip(font, Component.translatable("tooltip.magnetization.gui_energy",
                    menu.energyStored(), menu.energyMax()), mx, my);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mx, final int my) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        // Stat lines below the input slot, labelled by machine kind.
        final List<Component> lines = new ArrayList<>();
        switch (menu.kind()) {
            case TOKAMAK -> {
                if (menu.stat1() >= 0) lines.add(Component.translatable("tooltip.magnetization.gui_fuel", menu.stat1() / 20));
                if (menu.stat2() >= 0) lines.add(Component.translatable("tooltip.magnetization.gui_output", menu.stat2()));
            }
            case THRUSTER -> {
                if (menu.stat1() >= 0) lines.add(Component.translatable("tooltip.magnetization.gui_fluid", menu.stat1()));
            }
            case MOTOR -> {
                if (menu.stat1() >= 0) lines.add(Component.translatable("tooltip.magnetization.gui_rpm", menu.stat1()));
            }
            case JET -> {
                if (menu.stat1() >= 0) lines.add(Component.translatable("tooltip.magnetization.gui_speed", menu.stat1()));
            }
        }
        int ly = 55;
        for (final Component c : lines) {
            g.drawString(font, c, 8, ly, 0x404040, false);
            ly += 10;
        }
    }
}
