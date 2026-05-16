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
    private @Nullable RepeatButton rangeMinus;
    private @Nullable RepeatButton rangePlus;
    private @Nullable RepeatButton inflightMinus;
    private @Nullable RepeatButton inflightPlus;

    public EmitterScreen(final EmitterMenu menu, final Inventory inv, final Component title) {
        super(menu, inv, title);
        // Standard 176×166 — matches where EmitterMenu places the player inventory
        // (y=84 for main, y=142 for hotbar). The previous 180-tall image left the
        // inventoryLabel sitting on top of the slot row. CAP_INFLIGHT adds the
        // "Pulls" row at y=66, which would otherwise overlap the inventory title;
        // when that cap is set, the GUI extends by EmitterMenu.EXTRA_HEIGHT_FOR_INFLIGHT
        // so the inventory shifts down and the row gets clean space.
        this.imageWidth = 176;
        this.imageHeight = 166 + EmitterMenu.inventoryYOffset(menu.caps());
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
            rangeMinus = addRenderableWidget(new RepeatButton(x0 + 100, y0 + 50, 14, 14,
                    Component.literal("-"),
                    () -> sendButton(EmitterMenu.BUTTON_RANGE_DEC)));
            rangePlus  = addRenderableWidget(new RepeatButton(x0 + 154, y0 + 50, 14, 14,
                    Component.literal("+"),
                    () -> sendButton(EmitterMenu.BUTTON_RANGE_INC)));
        }
        if (menu.hasCap(EmitterMenu.CAP_INFLIGHT)) {
            inflightMinus = addRenderableWidget(new RepeatButton(x0 + 100, y0 + 66, 14, 12,
                    Component.literal("-"),
                    () -> sendButton(EmitterMenu.BUTTON_INFLIGHT_DEC)));
            inflightPlus  = addRenderableWidget(new RepeatButton(x0 + 154, y0 + 66, 14, 12,
                    Component.literal("+"),
                    () -> sendButton(EmitterMenu.BUTTON_INFLIGHT_INC)));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (rangeMinus != null) rangeMinus.repeatTick();
        if (rangePlus != null) rangePlus.repeatTick();
        if (inflightMinus != null) inflightMinus.repeatTick();
        if (inflightPlus != null) inflightPlus.repeatTick();
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Empty-slot hints — when the player hovers an empty special slot,
        // show what it accepts. Vanilla's item-tooltip takes over for non-empty
        // slots, so this only fires while a slot is empty.
        if (hoveredSlot == null || hoveredSlot.hasItem() || !hoveredSlot.isActive()) return;
        Component hint = null;
        // Slot indices match EmitterMenu's add-slot order: 0 armor, 1 tool, 2 fuel.
        switch (hoveredSlot.index) {
            case 0 -> { if (menu.hasCap(EmitterMenu.CAP_ARMOR))
                hint = Component.translatable("gui.magnetization.armor_slot"); }
            case 1 -> { if (menu.hasCap(EmitterMenu.CAP_TOOL_SLOT))
                hint = Component.translatable("gui.magnetization.tool_slot"); }
            case 2 -> { if (menu.hasCap(EmitterMenu.CAP_REDSTONE_FUEL))
                hint = Component.translatable("gui.magnetization.redstone_fuel_slot"); }
            default -> { /* player inventory slot — no custom hint */ }
        }
        if (hint != null) g.renderTooltip(font, hint, mouseX, mouseY);
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
            drawSlotRecess(g, leftPos + 80, topPos + 20, 0xFF1B1B1B);
            drawSlotLetter(g, leftPos + 80, topPos + 20, "A", 0x60A0A0A0);
        }
        // Redstone-fuel slot recess — slight red tint so it visually reads as
        // a redstone fuel input, distinct from the neutral slots.
        if (menu.hasCap(EmitterMenu.CAP_REDSTONE_FUEL)) {
            drawSlotRecess(g, leftPos + 28, topPos + 20, 0xFF2B1B1B);
            drawSlotLetter(g, leftPos + 28, topPos + 20, "R", 0x60D08080);
        }
        // Tool-slot recess if the cap is on. Slight green tint to telegraph it's
        // a separate slot from the armor magnetize one.
        if (menu.hasCap(EmitterMenu.CAP_TOOL_SLOT)) {
            drawSlotRecess(g, leftPos + 132, topPos + 20, 0xFF1B2B1B);
            drawSlotLetter(g, leftPos + 132, topPos + 20, "T", 0x6080D080);
            // Pull-progress bar to the left of the slot (132 - 60 = 72 ≈ 70).
            final int barX = leftPos + 70;
            final int barY = topPos + 26;
            g.fill(barX, barY, barX + 60, barY + 4, 0xFF101010);
            final int filled = (int) Math.round(60 * Math.max(0, Math.min(100, menu.pullProgressPct())) / 100.0);
            g.fill(barX, barY, barX + filled, barY + 4, 0xFF60A0E0);
        }

        // Player inventory + hotbar slot recesses — without these the slots are
        // invisible against the flat panel background. Mirrors EmitterMenu's slot
        // positions exactly, including the inflight-cap shift.
        final int invDy = EmitterMenu.inventoryYOffset(menu.caps());
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotRecess(g, leftPos + 8 + col * 18, topPos + 84 + invDy + row * 18, 0xFF1B1B1B);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotRecess(g, leftPos + 8 + col * 18, topPos + 142 + invDy, 0xFF1B1B1B);
        }
    }

    /** Draw a single-letter cue in the bottom-right of a slot well so the
     *  player can identify what each special slot is for without hovering.
     *  The letter is alpha-blended so a placed item visually overpowers it
     *  — vanilla item sprites render on top of the slot background. */
    private void drawSlotLetter(final GuiGraphics g, final int x, final int y,
                                final String letter, final int argb) {
        // Bottom-right of the 16×16 slot face (slot starts at x, y; letter is 6×8).
        g.drawString(font, letter, x + 10, y + 8, argb, false);
    }

    /** Draw an 18×18 slot well at (x, y) — the slot's 16×16 face plus a 1px
     *  border. The fill colour controls the recess tint (used to distinguish
     *  the armor slot from the tool slot from inventory slots). */
    private static void drawSlotRecess(final GuiGraphics g, final int x, final int y, final int colour) {
        g.fill(x - 1, y - 1, x + 17, y + 17, colour);
        // Tiny inner highlight so the slot reads as recessed rather than flat.
        g.fill(x - 1, y - 1, x + 17, y, 0xFF101010);          // top edge
        g.fill(x - 1, y + 16, x + 17, y + 17, 0xFF606060);    // bottom edge
        g.fill(x - 1, y - 1, x, y + 17, 0xFF101010);          // left edge
        g.fill(x + 16, y - 1, x + 17, y + 17, 0xFF606060);    // right edge
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        // Title.
        g.drawString(font, title, 8, 6, 0xE0E0E0, false);
        // Inventory label.
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xA0A0A0, false);

        // Energy bar: drawn flush against the right edge inside the GUI pane.
        // 8 px wide, ~50 px tall, fills from bottom to top. Tinted by current
        // power source so a glance tells you redstone vs energy vs idle.
        renderEnergyBar(g, mouseX, mouseY);

        if (menu.hasCap(EmitterMenu.CAP_STRENGTH)) {
            final int ord = menu.strengthOrdinal();
            // No override → use the BE's effective default (STRONG). Show that
            // explicitly rather than "—" so the player sees what the emitter
            // is actually doing before they ever click a tier button.
            final String label = ord < 0
                    ? MagneticStrength.STRONG.name()
                    : MagneticStrength.values()[ord].name();
            g.drawString(font, Component.translatable("gui.magnetization.strength", label), 8, 40, 0xC0C0C0, false);
        }
        if (menu.hasCap(EmitterMenu.CAP_RANGE)) {
            final int blocks = menu.rangeBlocks();
            final int label = blocks > 0 ? blocks : menu.defaultRangeBlocks();
            g.drawString(font, Component.translatable("gui.magnetization.range", label + "b"), 100, 40, 0xC0C0C0, false);
        }
        if (menu.hasCap(EmitterMenu.CAP_INFLIGHT)) {
            final int cap = menu.inflightCap();
            final int max = menu.inflightCapMax();
            final String label = cap <= 0 ? max + " (max)" : (cap + "/" + max);
            g.drawString(font, Component.translatable("gui.magnetization.inflight_cap", label),
                    8, 68, 0xC0C0C0, false);
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

    /** Energy bar at the top-right corner of the GUI pane. 8 px wide, 50 px
     *  tall, fills upward from the bottom. Hover text shows exact FE values
     *  and the active power source. */
    private void renderEnergyBar(final GuiGraphics g, final int mouseX, final int mouseY) {
        final int barX = 160;
        final int barY = 5;
        final int barW = 8;
        final int barH = 50;
        final int stored = menu.energyStored();
        final int capacity = Math.max(1, menu.energyCapacity());
        final int filledH = Math.max(0, Math.min(barH, (int) Math.round((double) stored / capacity * barH)));

        // Background (dark slate) + border (light grey).
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFFB0B0B0);
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1C1C1C);

        // Fill colour by power source: 0 = idle (grey), 1 = redstone (red),
        // 2 = energy (orange). Idle but with energy still buffered = blue.
        final int fillColor = switch (menu.powerSource()) {
            case 1 -> 0xFFD23838;
            case 2 -> 0xFFFFA040;
            default -> stored > 0 ? 0xFF4080FF : 0xFF505050;
        };
        if (filledH > 0) {
            g.fill(barX, barY + (barH - filledH), barX + barW, barY + barH, fillColor);
        }

        // Hover tooltip — only fire when actually hovered to avoid GUI lag.
        if (mouseX >= leftPos + barX && mouseX < leftPos + barX + barW
                && mouseY >= topPos + barY && mouseY < topPos + barY + barH) {
            final String sourceKey = switch (menu.powerSource()) {
                case 1 -> "redstone";
                case 2 -> "energy";
                default -> "idle";
            };
            final java.util.List<net.minecraft.network.chat.Component> tip = java.util.List.of(
                    (net.minecraft.network.chat.Component) Component.translatable("tooltip.magnetization.energy",
                            String.format("%,d / %,d", stored, capacity)),
                    (net.minecraft.network.chat.Component) Component.translatable("tooltip.magnetization.power_source",
                            Component.translatable("tooltip.magnetization.power_source." + sourceKey)
                                    .withStyle(ChatFormatting.GOLD)));
            final java.util.List<net.minecraft.util.FormattedCharSequence> wrapped =
                    tip.stream().map(c -> c.getVisualOrderText()).toList();
            g.renderTooltip(font, wrapped, mouseX - leftPos, mouseY - topPos);
        }
    }

    /** Button that re-fires its action while the mouse stays pressed on it.
     *  Initial press triggers once, then after a short hold the action repeats
     *  on a fixed interval — so number-bumpers like the range +/- can be held
     *  down instead of needing one click per step. */
    private static final class RepeatButton extends Button {
        /** Hold delay before auto-repeat kicks in (≈ 250 ms at 20 tps). */
        private static final int INITIAL_DELAY_TICKS = 5;
        /** Ticks between repeat fires once held past the initial delay (≈ 10 Hz). */
        private static final int REPEAT_INTERVAL_TICKS = 2;

        private final Runnable action;
        private boolean held = false;
        private int ticksHeld = 0;

        RepeatButton(final int x, final int y, final int w, final int h,
                     final Component msg, final Runnable action) {
            super(x, y, w, h, msg, b -> action.run(), DEFAULT_NARRATION);
            this.action = action;
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY); // fires the action once (initial press)
            held = true;
            ticksHeld = 0;
        }

        @Override
        public void onRelease(final double mouseX, final double mouseY) {
            super.onRelease(mouseX, mouseY);
            held = false;
            ticksHeld = 0;
        }

        /** Called once per screen tick by {@link EmitterScreen#containerTick()}.
         *  Watches the actual mouse-button state so we don't repeat forever if
         *  we somehow miss the release event. */
        void repeatTick() {
            if (!held) return;
            if (!isLeftMouseButtonDown()) {
                held = false;
                ticksHeld = 0;
                return;
            }
            ticksHeld++;
            if (ticksHeld >= INITIAL_DELAY_TICKS
                    && (ticksHeld - INITIAL_DELAY_TICKS) % REPEAT_INTERVAL_TICKS == 0) {
                action.run();
            }
        }

        private static boolean isLeftMouseButtonDown() {
            final var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.getWindow() == null) return false;
            final long window = mc.getWindow().getWindow();
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
    }
}
