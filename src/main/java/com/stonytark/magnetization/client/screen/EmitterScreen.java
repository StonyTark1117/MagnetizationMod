package com.stonytark.magnetization.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Capability-driven screen mirroring {@link EmitterMenu#caps()}. Layout is
 * vanilla 176×166 with a small content pane near the top:
 *
 * <pre>
 *   ┌────────────────────────┐  176 px wide
 *   │ Title                  │
 *   │  [armor slot]    [N][S][X]   ← shown only with CAP_ARMOR + CAP_POLARITY
 *   │  Strength: STRONG  W M S E   ← shown only with CAP_STRENGTH
 *   │  Range:    16 [-][+]         ← shown only with CAP_RANGE
 *   │ ──── Player inventory ────   ← always shown
 *   └────────────────────────┘
 * </pre>
 */
public class EmitterScreen extends AbstractContainerScreen<EmitterMenu> {

    /** Vanilla generic single-chest texture as a clean stand-in: 176×166 with one slot recess
     *  near the top. We over-paint the slot at (80, 20) with the menu's armor slot. */
    private static final ResourceLocation BG = ResourceLocation.parse("textures/gui/container/generic_54.png");

    private @Nullable Button[] strengthButtons;
    private @Nullable Button rangeMinus;
    private @Nullable Button rangePlus;

    public EmitterScreen(final EmitterMenu menu, final Inventory inv, final Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 180;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        final int x0 = leftPos;
        final int y0 = topPos;

        if (menu.hasCap(EmitterMenu.CAP_ARMOR) && menu.hasCap(EmitterMenu.CAP_POLARITY)) {
            // Three small polarity buttons next to the armor slot.
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.magnetization.polarity.north"),
                            b -> sendButton(EmitterMenu.BUTTON_POLARITY_NORTH))
                    .bounds(x0 + 110, y0 + 17, 22, 14).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.magnetization.polarity.south"),
                            b -> sendButton(EmitterMenu.BUTTON_POLARITY_SOUTH))
                    .bounds(x0 + 132, y0 + 17, 22, 14).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.magnetization.polarity.clear"),
                            b -> sendButton(EmitterMenu.BUTTON_POLARITY_CLEAR))
                    .bounds(x0 + 110, y0 + 32, 44, 12).build());
        }

        if (menu.hasCap(EmitterMenu.CAP_STRENGTH)) {
            strengthButtons = new Button[4];
            final int[] ids = {
                    EmitterMenu.BUTTON_STRENGTH_WEAK,
                    EmitterMenu.BUTTON_STRENGTH_MEDIUM,
                    EmitterMenu.BUTTON_STRENGTH_STRONG,
                    EmitterMenu.BUTTON_STRENGTH_EXTREME,
            };
            final String[] labels = {"W", "M", "S", "E"};
            for (int i = 0; i < 4; i++) {
                final int id = ids[i];
                strengthButtons[i] = addRenderableWidget(Button.builder(
                                Component.literal(labels[i]),
                                b -> sendButton(id))
                        .bounds(x0 + 8 + i * 22, y0 + 50, 20, 14).build());
            }
        }

        if (menu.hasCap(EmitterMenu.CAP_RANGE)) {
            rangeMinus = addRenderableWidget(Button.builder(
                            Component.literal("-"),
                            b -> sendButton(EmitterMenu.BUTTON_RANGE_DEC))
                    .bounds(x0 + 100, y0 + 50, 14, 14).build());
            rangePlus  = addRenderableWidget(Button.builder(
                            Component.literal("+"),
                            b -> sendButton(EmitterMenu.BUTTON_RANGE_INC))
                    .bounds(x0 + 154, y0 + 50, 14, 14).build());
        }
    }

    private void sendButton(final int id) {
        if (minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            // Server-side AbstractContainerMenu#clickMenuButton dispatches by id.
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partial, final int mouseX, final int mouseY) {
        // Solid background panel — neutral so it works on any resource pack.
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0202020);
        g.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFF3A3A3A);

        // Armor-slot recess if the cap is on.
        if (menu.hasCap(EmitterMenu.CAP_ARMOR)) {
            final int sx = leftPos + 80;
            final int sy = topPos + 20;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF1B1B1B);
        }
        // Tool-slot recess if the cap is on. Slight green tint to telegraph it's
        // a separate slot from the armor magnetize one.
        if (menu.hasCap(EmitterMenu.CAP_TOOL_SLOT)) {
            final int sx = leftPos + 132;
            final int sy = topPos + 20;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF1B2B1B);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        // Title.
        g.drawString(font, title, 8, 6, 0xE0E0E0, false);
        // Inventory label.
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xA0A0A0, false);

        if (menu.hasCap(EmitterMenu.CAP_STRENGTH)) {
            final int ord = menu.strengthOrdinal();
            final String label = ord < 0 ? "—" : MagneticStrength.values()[ord].name();
            g.drawString(font, Component.translatable("gui.magnetization.strength", label), 8, 40, 0xC0C0C0, false);
        }
        if (menu.hasCap(EmitterMenu.CAP_RANGE)) {
            final int blocks = menu.rangeBlocks();
            final String label = blocks <= 0 ? "—" : (blocks + "b");
            g.drawString(font, Component.translatable("gui.magnetization.range", label), 100, 40, 0xC0C0C0, false);
        }
        if (menu.hasCap(EmitterMenu.CAP_ARMOR) && menu.hasCap(EmitterMenu.CAP_POLARITY)) {
            final ItemStack stack = menu.armorStack();
            final MagneticPolarity pol = stack.isEmpty() ? null : stack.get(MagDataComponents.ARMOR_POLARITY.get());
            final Component status = pol == null
                    ? Component.translatable("gui.magnetization.polarity.unmagnetized")
                    : Component.translatable("tooltip.magnetization.polarity." + pol.getSerializedName())
                            .withStyle(pol == MagneticPolarity.NORTH ? ChatFormatting.AQUA : ChatFormatting.RED);
            g.drawString(font, status, 8, 24, 0xC0C0C0, false);
        }
    }
}
