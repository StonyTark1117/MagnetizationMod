package com.stonytark.magnetization.client.screen;

import com.stonytark.magnetization.config.MagConfig;
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

    /** Denominator for the secondary fuel/fluid bar, read live from config (and,
     *  for the tokamak, the synced current fuel tier) so the bar fill stays
     *  accurate when an admin retunes a tank size or fuel burn time. */
    private int fluidBarMax() {
        return switch (menu.kind()) {
            case TOKAMAK -> tokamakTierBurn(menu.stat3());
            case THRUSTER -> MagConfig.microThrusterTank();
            // Fusion tank is per-cell × interior count (stat2) — bars scale with the panel.
            case FUSION_THRUSTER -> MagConfig.fusionThrusterTank() * Math.max(1, menu.stat2());
            case JET -> MagConfig.mhdJetTank();
            case ELECTROLYZER -> MagConfig.electrolyzerHydrogenTank();
            default -> 1;
        };
    }

    /** Full burn ticks of the tokamak's currently-loaded cell tier (0=D-D/1=D-T/2=He³). */
    private static int tokamakTierBurn(final int tier) {
        return switch (tier) {
            case 1 -> MagConfig.tokamakBurnTicksTritium();
            case 2 -> MagConfig.tokamakBurnTicksHelium3();
            default -> MagConfig.tokamakBurnTicksPerCell();
        };
    }

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
            case THRUSTER, FUSION_THRUSTER, JET -> 0xFF16242B;
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
        // Secondary fuel/fluid bar (denominator from config / synced tier).
        final float barMax = Math.max(1f, fluidBarMax());
        switch (menu.kind()) {
            case TOKAMAK -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat1() / barMax, 0xFFE05A2A); }
            case THRUSTER -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat1() / barMax, 0xFF3AC0E0); }
            case FUSION_THRUSTER -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat1() / barMax, 0xFF7ADCE0); }
            case JET -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat1() / barMax, 0xFFB0C0FF); }
            case ELECTROLYZER -> { if (menu.stat2() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.stat2() / barMax, 0xFFDDE6FF); }
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
                final String[] tiers = {"dd", "dt", "he3"};
                lines.add(Component.translatable("tooltip.magnetization.gui_tokamak_tier_"
                        + tiers[Math.min(2, Math.max(0, menu.stat3()))]));
                lines.add(Component.translatable("tooltip.magnetization.gui_fuel", menu.stat1() / 20));
                lines.add(Component.translatable("tooltip.magnetization.gui_output", Math.max(0, menu.stat2())));
            }
            case THRUSTER -> lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.stat1())));
            case FUSION_THRUSTER -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.stat1())));
                lines.add(Component.translatable("tooltip.magnetization.gui_fusion_size", Math.max(0, menu.stat2())));
            }
            case MOTOR -> {
                lines.add(MachineGuiData.magnetStatusLine(menu.getSlot(0).getItem()));
                lines.add(Component.translatable("tooltip.magnetization.gui_rpm", Math.max(0, menu.stat1())));
                if (menu.stat2() > 0) lines.add(Component.translatable("tooltip.magnetization.gui_magnet_burn", menu.stat2() / 20));
            }
            case JET -> {
                lines.add(MachineGuiData.magnetStatusLine(menu.getSlot(0).getItem()));
                lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.stat1())));
                if (menu.stat2() > 0) lines.add(Component.translatable("tooltip.magnetization.gui_magnet_burn", menu.stat2() / 20));
            }
            case ELECTROLYZER -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_water", Math.max(0, menu.stat1())));
                lines.add(Component.translatable("tooltip.magnetization.gui_hydrogen", Math.max(0, menu.stat2())));
            }
            case RAILGUN -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_rail_length", Math.max(0, menu.stat1())));
                final int packed = Math.max(0, menu.stat2());
                final boolean manual = (packed & 16) != 0;
                final String[] states = {"idle", "holding", "launching", "cooldown"};
                lines.add(Component.translatable(manual
                        ? "tooltip.magnetization.gui_railgun_manual" : "tooltip.magnetization.gui_railgun_auto"));
                lines.add(Component.translatable("tooltip.magnetization.gui_railgun_state_"
                        + states[Math.min(states.length - 1, packed & 15)]));
            }
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
        if ((menu.kind() == MachineMenu.Kind.TOKAMAK || menu.kind() == MachineMenu.Kind.THRUSTER
                || menu.kind() == MachineMenu.Kind.FUSION_THRUSTER || menu.kind() == MachineMenu.Kind.JET)
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
