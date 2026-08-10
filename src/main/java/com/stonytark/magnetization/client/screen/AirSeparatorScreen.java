package com.stonytark.magnetization.client.screen;

import com.stonytark.magnetization.content.gas.AirSeparatorBlockEntity;
import com.stonytark.magnetization.menu.AirSeparatorGuiLayout;
import com.stonytark.magnetization.menu.AirSeparatorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Five-tank live process and output-port screen for the Air Separator. */
public final class AirSeparatorScreen extends AbstractContainerScreen<AirSeparatorMenu> {
    private static final int TANK_X = AirSeparatorGuiLayout.TANK_X;
    private static final int TANK_STRIDE = AirSeparatorGuiLayout.TANK_STRIDE;
    private static final int TANK_Y = AirSeparatorGuiLayout.TANK_Y;
    private static final int TANK_W = AirSeparatorGuiLayout.TANK_WIDTH;
    private static final int TANK_H = AirSeparatorGuiLayout.TANK_HEIGHT;
    private static final int PORT_BUTTON_Y = AirSeparatorGuiLayout.PORT_BUTTON_Y;
    private static final int PORT_BUTTON_W = AirSeparatorGuiLayout.PORT_BUTTON_WIDTH;
    private static final int PORT_BUTTON_H = AirSeparatorGuiLayout.PORT_BUTTON_HEIGHT;
    private static final int READOUT_Y = AirSeparatorGuiLayout.READOUT_Y;
    private static final int[] GAS_COLOURS = {0xFFFFB38A, 0xFFFF2A16, 0xFFB56CFF, 0xFFD8FFE6, 0xFF4FA9FF};
    private static final String[] GAS_SYMBOLS = {"He", "Ne", "Ar", "Kr", "Xe"};
    private final Button[] portButtons = new Button[AirSeparatorBlockEntity.COUNT];

    public AirSeparatorScreen(final AirSeparatorMenu menu, final Inventory inventory, final Component title) {
        super(menu, inventory, title);
        imageWidth = AirSeparatorMenu.IMAGE_WIDTH;
        imageHeight = AirSeparatorMenu.IMAGE_HEIGHT;
        inventoryLabelY = AirSeparatorMenu.INVENTORY_LABEL_Y;
    }

    @Override
    protected void init() {
        super.init();
        for (int gas = 0; gas < AirSeparatorBlockEntity.COUNT; gas++) {
            final int index = gas;
            final Button button = Button.builder(faceShort(menu.outputFace(gas)), ignored -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                }
            }).bounds(leftPos + TANK_X - 3 + gas * TANK_STRIDE, topPos + PORT_BUTTON_Y,
                    PORT_BUTTON_W, PORT_BUTTON_H).build();
            portButtons[gas] = addRenderableWidget(button);
        }
        refreshPortButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshPortButtons();
    }

    private void refreshPortButtons() {
        for (int gas = 0; gas < portButtons.length; gas++) {
            final Button button = portButtons[gas];
            if (button == null) continue;
            final Direction face = menu.outputFace(gas);
            button.setMessage(faceShort(face));
            button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.magnetization.air_separator.port_tooltip",
                    Component.translatable(AirSeparatorBlockEntity.gasTranslationKey(gas)),
                    faceName(face))));
        }
    }

    @Override
    protected void renderBg(final GuiGraphics graphics, final float partialTick,
                            final int mouseX, final int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0202020);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1,
                topPos + imageHeight - 1, 0xFF3A3A3A);

        for (int gas = 0; gas < AirSeparatorBlockEntity.COUNT; gas++) {
            drawTank(graphics, gas);
        }
        drawSlotRecess(graphics, leftPos + AirSeparatorMenu.UPGRADE_X,
                topPos + AirSeparatorMenu.UPGRADE_Y, 0xFF30281A);
        drawSlotRecess(graphics, leftPos + AirSeparatorMenu.OUTPUT_X,
                topPos + AirSeparatorMenu.OUTPUT_Y, 0xFF2A2038);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotRecess(graphics, leftPos + AirSeparatorMenu.PLAYER_INVENTORY_X + col * 18,
                        topPos + AirSeparatorMenu.PLAYER_INVENTORY_Y + row * 18, 0xFF1B1B1B);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotRecess(graphics, leftPos + AirSeparatorMenu.PLAYER_INVENTORY_X + col * 18,
                    topPos + AirSeparatorMenu.HOTBAR_Y, 0xFF1B1B1B);
        }
    }

    private void drawTank(final GuiGraphics graphics, final int gas) {
        final int x = leftPos + TANK_X + gas * TANK_STRIDE;
        final int y = topPos + TANK_Y;
        graphics.fill(x - 1, y - 1, x + TANK_W + 1, y + TANK_H + 1, 0xFF101010);
        graphics.fill(x, y, x + TANK_W, y + TANK_H, 0xFF202020);
        final int filled = Math.round(Math.min(1.0F, menu.amount(gas) / (float) menu.capacity()) * TANK_H);
        if (filled > 0) {
            graphics.fill(x, y + TANK_H - filled, x + TANK_W, y + TANK_H, GAS_COLOURS[gas]);
            if (filled > 2) graphics.fill(x + 2, y + TANK_H - filled + 1,
                    x + 4, y + TANK_H - 1, 0x55FFFFFF);
        }
    }

    private static void drawSlotRecess(final GuiGraphics graphics, final int x, final int y, final int colour) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, colour);
        graphics.fill(x - 1, y - 1, x + 17, y, 0xFF101010);
        graphics.fill(x - 1, y + 16, x + 17, y + 17, 0xFF606060);
        graphics.fill(x - 1, y - 1, x, y + 17, 0xFF101010);
        graphics.fill(x + 16, y - 1, x + 17, y + 17, 0xFF606060);
    }

    @Override
    protected void renderLabels(final GuiGraphics graphics, final int mouseX, final int mouseY) {
        GuiTextLayout.drawClipped(graphics, font, title, 8, 6, imageWidth - 16, 0xE0E0E0);
        GuiTextLayout.drawClipped(graphics, font, statusLine(), 8, 18, imageWidth - 16, 0xC0C0C0);
        graphics.drawString(font, playerInventoryTitle, AirSeparatorMenu.PLAYER_INVENTORY_X,
                inventoryLabelY, 0xA0A0A0, false);

        for (int gas = 0; gas < GAS_SYMBOLS.length; gas++) {
            final int center = TANK_X + gas * TANK_STRIDE + TANK_W / 2;
            graphics.drawString(font, GAS_SYMBOLS[gas], center - font.width(GAS_SYMBOLS[gas]) / 2,
                    AirSeparatorGuiLayout.GAS_SYMBOL_Y, GAS_COLOURS[gas], false);
        }

        final List<Component> readouts = readoutLines();
        for (int line = 0; line < readouts.size(); line++) {
            GuiTextLayout.drawClipped(graphics, font, readouts.get(line), 8,
                    READOUT_Y + line * AirSeparatorGuiLayout.READOUT_LINE_HEIGHT,
                    imageWidth - 16, 0xC0C0C0);
        }
    }

    private List<Component> readoutLines() {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.magnetization.air_separator.speed",
                menu.rpm(), menu.minRpm(), menu.maxRpm()));
        lines.add(Component.translatable("tooltip.magnetization.air_separator.storage",
                menu.totalStored(), menu.capacity() * AirSeparatorBlockEntity.COUNT));
        if (menu.getSlot(0).hasItem()) {
            final String percent = String.format(Locale.ROOT, "%.1f", menu.isotopeProgressPermille() / 10.0D);
            lines.add(Component.translatable("tooltip.magnetization.air_separator.isotope_progress",
                    percent, menu.getSlot(1).getItem().getCount()));
        } else {
            lines.add(Component.translatable("tooltip.magnetization.air_separator.no_module"));
        }
        return lines;
    }

    private Component statusLine() {
        return AirSeparatorBlockEntity.statusLine(menu.status());
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY,
                       final float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int gas = 0; gas < AirSeparatorBlockEntity.COUNT; gas++) {
            if (!inside(mouseX, mouseY, TANK_X + gas * TANK_STRIDE, TANK_Y, TANK_W, TANK_H)) continue;
            final List<Component> tooltip = List.of(
                    Component.translatable(AirSeparatorBlockEntity.gasTranslationKey(gas))
                            .withStyle(ChatFormatting.WHITE),
                    Component.translatable("gui.magnetization.air_separator.tank",
                            menu.amount(gas), menu.capacity()).withStyle(ChatFormatting.AQUA),
                    Component.translatable("gui.magnetization.air_separator.rate",
                            ratePerSecond(menu.rateMilli(gas))).withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.magnetization.air_separator.output_face",
                            faceName(menu.outputFace(gas))).withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
        if (!menu.getSlot(0).hasItem() && inside(mouseX, mouseY, AirSeparatorMenu.UPGRADE_X,
                AirSeparatorMenu.UPGRADE_Y, 16, 16)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.magnetization.air_separator.upgrade_slot"), mouseX, mouseY);
        } else if (!menu.getSlot(1).hasItem() && inside(mouseX, mouseY, AirSeparatorMenu.OUTPUT_X,
                AirSeparatorMenu.OUTPUT_Y, 16, 16)) {
            graphics.renderTooltip(font, Component.translatable(
                    "gui.magnetization.air_separator.output_slot"), mouseX, mouseY);
        }
    }

    private boolean inside(final int mouseX, final int mouseY, final int x, final int y,
                           final int width, final int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    private static String ratePerSecond(final int rateMilli) {
        final double rate = Math.max(0, rateMilli) / 50.0D;
        return rate == Math.rint(rate) ? Long.toString(Math.round(rate))
                : String.format(Locale.ROOT, "%.1f", rate);
    }

    private static Component faceShort(final Direction face) {
        return Component.translatable("gui.magnetization.air_separator.face_short." + face.getName());
    }

    private static Component faceName(final Direction face) {
        return Component.translatable("direction.minecraft." + face.getName());
    }
}
