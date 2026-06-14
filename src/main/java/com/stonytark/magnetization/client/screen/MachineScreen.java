package com.stonytark.magnetization.client.screen;

import com.stonytark.magnetization.menu.MachineGuiData;
import com.stonytark.magnetization.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the shared {@link MachineMenu}. Drawn entirely with {@code g.fill}
 * (no texture) at the menu's exact slot coordinates — same approach + dark
 * coloration as {@code EmitterScreen}, so slots align and the GUI matches the
 * rest of the mod. Shows a power bar (machines with FE) and a fuel/fluid bar
 * (tokamak fuel, thruster ferrofluid), plus per-kind stat text.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    // Vertical bars on the right edge of the pane.
    private static final int ENERGY_X = 156, BAR_Y = 18, BAR_W = 12, BAR_H = 54;
    private static final int FLUID_X = 138;
    // Max values for the secondary bar (mirror the BEs).
    private static final int TOKAMAK_MAX_BURN = 19_200; // 4 cells × 4800
    private static final int THRUSTER_TANK = 8_000;

    public MachineScreen(final MachineMenu menu, final Inventory inv, final Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partial, final int mx, final int my) {
        // Dark neutral panel (matches EmitterScreen).
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0202020);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF3A3A3A);

        // Input slot recess (tinted per kind so it reads as magnet / fuel / bucket).
        final int tint = switch (menu.kind()) {
            case TOKAMAK -> 0xFF2B2416;
            case THRUSTER -> 0xFF16242B;
            default -> 0xFF1B1B1B;
        };
        drawSlotRecess(g, leftPos + MachineMenu.INPUT_X, topPos + MachineMenu.INPUT_Y, tint);

        // Player inventory + hotbar recesses (exact menu coords).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotRecess(g, leftPos + 8 + col * 18, topPos + 84 + row * 18, 0xFF1B1B1B);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotRecess(g, leftPos + 8 + col * 18, topPos + 142, 0xFF1B1B1B);
        }

        // Power bar (any machine reporting energy).
        if (menu.energyStored() >= 0) {
            drawBar(g, leftPos + ENERGY_X, topPos + BAR_Y, menu.energyStored() / (float) menu.energyMax(), 0xFFE0AC4A);
        }
        // Secondary fuel/fluid bar.
        switch (menu.kind()) {
            case TOKAMAK -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat1() / (float) TOKAMAK_MAX_BURN, 0xFFE05A2A); }
            case THRUSTER -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat1() / (float) THRUSTER_TANK, 0xFF3AC0E0); }
            default -> { }
        }
    }

    private static void drawSlotRecess(final GuiGraphics g, final int x, final int y, final int colour) {
        g.fill(x - 1, y - 1, x + 17, y + 17, colour);
        g.fill(x - 1, y - 1, x + 17, y, 0xFF101010);
        g.fill(x - 1, y + 16, x + 17, y + 17, 0xFF606060);
        g.fill(x - 1, y - 1, x, y + 17, 0xFF101010);
        g.fill(x + 16, y - 1, x + 17, y + 17, 0xFF606060);
    }

    /** Vertical bar with a dark well + colour fill (bottom-up). */
    private static void drawBar(final GuiGraphics g, final int x, final int y, final float frac, final int colour) {
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF101010); // well
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF202020);                  // empty interior
        final int filled = (int) (Math.max(0f, Math.min(1f, frac)) * BAR_H);
        if (filled > 0) g.fill(x, y + (BAR_H - filled), x + BAR_W, y + BAR_H, colour);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mx, final int my) {
        g.drawString(font, title, 8, 6, 0xE0E0E0, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xA0A0A0, false);

        final List<Component> lines = new ArrayList<>();
        switch (menu.kind()) {
            case TOKAMAK -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_fuel", menu.stat1() / 20));
                lines.add(Component.translatable("tooltip.magnetization.gui_output", Math.max(0, menu.stat2())));
            }
            case THRUSTER -> lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.stat1())));
            case MOTOR -> {
                lines.add(MachineGuiData.magnetStatusLine(menu.getSlot(0).getItem()));
                lines.add(Component.translatable("tooltip.magnetization.gui_rpm", Math.max(0, menu.stat1())));
            }
            case JET -> lines.add(MachineGuiData.magnetStatusLine(menu.getSlot(0).getItem()));
        }
        int ly = 22;
        for (final Component c : lines) {
            g.drawString(font, c, 8, ly, 0xC0C0C0, false);
            ly += 11;
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mx, final int my, final float partial) {
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
        // Bar hover tooltips.
        if (menu.energyStored() >= 0 && inBar(mx, my, ENERGY_X)) {
            g.renderTooltip(font, Component.translatable("tooltip.magnetization.gui_energy",
                    menu.energyStored(), menu.energyMax()), mx, my);
        }
        if ((menu.kind() == MachineMenu.Kind.TOKAMAK || menu.kind() == MachineMenu.Kind.THRUSTER)
                && menu.stat1() >= 0 && inBar(mx, my, FLUID_X)) {
            final Component t = menu.kind() == MachineMenu.Kind.TOKAMAK
                    ? Component.translatable("tooltip.magnetization.gui_fuel", menu.stat1() / 20)
                    : Component.translatable("tooltip.magnetization.gui_fluid", menu.stat1());
            g.renderTooltip(font, t, mx, my);
        }
    }

    private boolean inBar(final int mx, final int my, final int barX) {
        return mx >= leftPos + barX && mx < leftPos + barX + BAR_W
                && my >= topPos + BAR_Y && my < topPos + BAR_Y + BAR_H;
    }
}
