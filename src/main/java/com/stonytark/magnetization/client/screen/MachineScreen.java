package com.stonytark.magnetization.client.screen;

import com.stonytark.magnetization.menu.MachineGuiData;
import com.stonytark.magnetization.menu.MachineGuiLayout;
import com.stonytark.magnetization.menu.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int ENERGY_X = 156, BAR_Y = MachineGuiLayout.BAR_Y, BAR_W = 12,
            BAR_H = MachineGuiLayout.BAR_HEIGHT;
    private static final int FLUID_X = 138;
    private Button railgunBreakingButton;

    /** Denominator for the secondary fuel/fluid bar. Uses the server-authoritative
     *  value synced through the menu ({@code stat4}) so a multiplayer client whose
     *  COMMON config differs from the server (tank size / fuel burn time retuned)
     *  still draws the correct fill percentage — reading local client config here
     *  was the bug. Falls back to 1 when the machine reports no secondary bar. */
    private int fluidBarMax() {
        return menu.displayCapacity();
    }

    public MachineScreen(final MachineMenu menu, final Inventory inv, final Component title) {
        super(menu, inv, title);
        this.imageWidth = MachineGuiLayout.IMAGE_WIDTH;
        this.imageHeight = MachineGuiLayout.IMAGE_HEIGHT;
        this.inventoryLabelY = MachineGuiLayout.INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind() == MachineMenu.Kind.RAILGUN) {
            railgunBreakingButton = addRenderableWidget(Button.builder(railgunBreakingLabel(), button -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(
                            menu.containerId, MachineMenu.BUTTON_RAILGUN_BLOCK_BREAKING);
                }
            }).bounds(leftPos + 8, topPos + 55, 104, 14).build());
        }
    }

    private boolean railgunBreaksBlocks() {
        return (menu.displayAuxiliary()
                & com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.BREAK_BLOCKS_BIT) != 0;
    }

    private Component railgunBreakingLabel() {
        return Component.translatable(railgunBreaksBlocks()
                ? "gui.magnetization.railgun_break_blocks_on"
                : "gui.magnetization.railgun_break_blocks_off");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (railgunBreakingButton != null) railgunBreakingButton.setMessage(railgunBreakingLabel());
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partial, final int mx, final int my) {
        // Dark neutral panel (matches EmitterScreen).
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0202020);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF3A3A3A);

        // Input slot recess (tinted per kind so it reads as magnet / fuel / bucket).
        final int tint = switch (menu.kind()) {
            case TOKAMAK -> 0xFF2B2416;
            case THRUSTER, ION_THRUSTER, FUSION_THRUSTER, JET -> 0xFF16242B;
            default -> 0xFF1B1B1B;
        };
        drawSlotRecess(g, leftPos + MachineMenu.inputX(menu.kind()), topPos + MachineMenu.INPUT_Y, tint);

        // Player inventory + hotbar recesses (exact menu coords).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotRecess(g, leftPos + 8 + col * 18,
                        topPos + MachineGuiLayout.PLAYER_INVENTORY_Y + row * 18, 0xFF1B1B1B);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotRecess(g, leftPos + 8 + col * 18,
                    topPos + MachineGuiLayout.HOTBAR_Y, 0xFF1B1B1B);
        }

        // Power bar (any machine reporting energy).
        if (menu.energyStored() >= 0) {
            drawBar(g, leftPos + ENERGY_X, topPos + BAR_Y, menu.energyStored() / (float) menu.energyMax(), 0xFFE0AC4A);
        }
        // Secondary fuel/fluid bar (denominator from config / synced tier).
        final float barMax = Math.max(1f, fluidBarMax());
        switch (menu.kind()) {
            case TOKAMAK -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.displayCurrent() / barMax, 0xFFE05A2A); }
            case THRUSTER, ION_THRUSTER -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.displayCurrent() / barMax, 0xFF3AC0E0); }
            case FUSION_THRUSTER -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.displayCurrent() / barMax, 0xFF7ADCE0); }
            case JET -> { if (menu.stat1() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.displayCurrent() / barMax, 0xFFB0C0FF); }
            case ELECTROLYZER -> { if (menu.stat2() >= 0)
                drawBar(g, leftPos + FLUID_X, topPos + BAR_Y, menu.displayAuxiliary() / barMax, 0xFFDDE6FF); }
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
        GuiTextLayout.drawClipped(g, font, title, 8, 6, imageWidth - 16, 0xE0E0E0);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xA0A0A0, false);

        final List<Component> lines = readoutLines();
        int ly = MachineGuiLayout.READOUT_Y;
        for (final Component line : lines) {
            GuiTextLayout.drawClipped(g, font, line, 8, ly, imageWidth - 16, 0xC0C0C0);
            ly += MachineGuiLayout.READOUT_LINE_HEIGHT;
        }
    }

    private List<Component> readoutLines() {
        final List<Component> lines = new ArrayList<>();
        switch (menu.kind()) {
            case TOKAMAK -> {
                final String[] tiers = {"dd", "dt", "he3"};
                lines.add(MachineGuiData.tokamakRingScaleLine(menu.displayData()));
                lines.add(Component.translatable("tooltip.magnetization.gui_tokamak_tier_"
                        + tiers[Math.min(2, Math.max(0, menu.displayTier()))]));
                lines.add(Component.translatable("tooltip.magnetization.gui_tokamak_operation",
                        menu.displayCurrent() / 20, Math.max(0, menu.displayAuxiliary())));
            }
            case THRUSTER -> lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.displayCurrent())));
            case ION_THRUSTER -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.displayCurrent())));
                final String[] gases = {"helium", "neon", "argon", "krypton", "xenon", "radon", "external"};
                if (menu.displayTier() >= 0) lines.add(Component.translatable("tooltip.magnetization.gui_propellant",
                        Component.translatable("fluid_type.magnetization." + gases[Math.min(6, menu.displayTier())])));
            }
            case FUSION_THRUSTER -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.displayCurrent())));
                lines.add(Component.translatable("tooltip.magnetization.gui_fusion_size", Math.max(0, menu.displayAuxiliary())));
            }
            case MOTOR -> {
                lines.add(MachineGuiData.magnetStatusLine(menu.getSlot(0).getItem()));
                lines.add(Component.translatable("tooltip.magnetization.gui_rpm", Math.max(0, menu.displayCurrent())));
                if (menu.displayAuxiliary() > 0) lines.add(Component.translatable("tooltip.magnetization.gui_magnet_burn", menu.displayAuxiliary() / 20));
            }
            case JET -> {
                lines.add(MachineGuiData.magnetStatusLine(menu.getSlot(0).getItem()));
                lines.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, menu.displayCurrent())));
                if (menu.displayAuxiliary() > 0) lines.add(Component.translatable("tooltip.magnetization.gui_magnet_burn", menu.displayAuxiliary() / 20));
            }
            case ELECTROLYZER -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_water", Math.max(0, menu.displayCurrent())));
                lines.add(Component.translatable("tooltip.magnetization.gui_hydrogen", Math.max(0, menu.displayAuxiliary())));
            }
            case RAILGUN -> {
                lines.add(Component.translatable("tooltip.magnetization.gui_rail_length", Math.max(0, menu.displayCurrent())));
                final int packed = Math.max(0, menu.displayAuxiliary());
                final boolean manual = (packed
                        & com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.MANUAL_MODE_BIT) != 0;
                final String[] states = {"idle", "holding", "launching", "cooldown"};
                lines.add(Component.translatable(manual
                        ? "tooltip.magnetization.gui_railgun_manual" : "tooltip.magnetization.gui_railgun_auto"));
                lines.add(Component.translatable("tooltip.magnetization.gui_railgun_state_"
                        + states[Math.min(states.length - 1, packed
                        & com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ARC_STATE_MASK)]));
            }
        }
        // Railgun already has a precise arc-state row; its final machine-status
        // row was duplicate information and now makes room for the toggle.
        if (menu.kind() != MachineMenu.Kind.RAILGUN) {
            lines.add(MachineGuiData.statusLine(menu.kind(), menu.displayStatus()));
        }
        return lines;
    }

    @Override
    public void render(final GuiGraphics g, final int mx, final int my, final float partial) {
        super.render(g, mx, my, partial);
        // super.render already draws the hovered-slot item tooltip; don't call
        // renderTooltip again here or it double-draws (the translucent background
        // alpha-blends twice and darkens).
        // Bar hover tooltips.
        if (menu.energyStored() >= 0 && inBar(mx, my, ENERGY_X)) {
            g.renderTooltip(font, Component.translatable("tooltip.magnetization.gui_energy",
                    menu.energyStored(), menu.energyMax()), mx, my);
        }
        if ((menu.kind() == MachineMenu.Kind.TOKAMAK || menu.kind() == MachineMenu.Kind.THRUSTER
                || menu.kind() == MachineMenu.Kind.ION_THRUSTER
                || menu.kind() == MachineMenu.Kind.FUSION_THRUSTER || menu.kind() == MachineMenu.Kind.JET)
                && menu.displayCurrent() >= 0 && inBar(mx, my, FLUID_X)) {
            final Component t = menu.kind() == MachineMenu.Kind.TOKAMAK
                    ? Component.translatable("tooltip.magnetization.gui_fuel", menu.displayCurrent() / 20)
                    : Component.translatable("tooltip.magnetization.gui_fluid", menu.displayCurrent());
            g.renderTooltip(font, t, mx, my);
        }
        GuiTextLayout.renderTooltipIfClipped(g, font, title,
                leftPos + 8, topPos + 6, imageWidth - 16, mx, my);
        final List<Component> lines = readoutLines();
        for (int i = 0; i < lines.size(); i++) {
            GuiTextLayout.renderTooltipIfClipped(g, font, lines.get(i),
                    leftPos + 8, topPos + MachineGuiLayout.READOUT_Y
                            + i * MachineGuiLayout.READOUT_LINE_HEIGHT,
                    imageWidth - 16, mx, my);
        }
    }

    private boolean inBar(final int mx, final int my, final int barX) {
        return mx >= leftPos + barX && mx < leftPos + barX + BAR_W
                && my >= topPos + BAR_Y && my < topPos + BAR_Y + BAR_H;
    }
}
