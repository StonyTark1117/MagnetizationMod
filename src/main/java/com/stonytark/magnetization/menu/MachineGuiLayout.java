package com.stonytark.magnetization.menu;

/**
 * Shared geometry contract for {@link MachineMenu} and its client screen.
 * Keeping slot and readout coordinates in common code prevents the server-side
 * menu from drifting away from the rendered recesses.
 */
public final class MachineGuiLayout {
    public static final int IMAGE_WIDTH = 176;
    public static final int BASE_IMAGE_HEIGHT = 166;

    public static final int INPUT_Y = 33;
    public static final int BAR_Y = 18;
    public static final int BAR_HEIGHT = 54;
    public static final int CONTROLS_BOTTOM = BAR_Y + BAR_HEIGHT + 1;

    public static final int READOUT_Y = 76;
    public static final int READOUT_LINE_HEIGHT = 11;
    public static final int READOUT_GLYPH_HEIGHT = 9;
    public static final int MAX_READOUT_LINES = 4;

    public static final int EXTRA_HEIGHT_FOR_READOUTS = 48;
    public static final int IMAGE_HEIGHT = BASE_IMAGE_HEIGHT + EXTRA_HEIGHT_FOR_READOUTS;
    public static final int PLAYER_INVENTORY_Y = 84 + EXTRA_HEIGHT_FOR_READOUTS;
    public static final int HOTBAR_Y = 142 + EXTRA_HEIGHT_FOR_READOUTS;
    public static final int INVENTORY_LABEL_Y = PLAYER_INVENTORY_Y - 12;

    private MachineGuiLayout() { }

    public static int readoutBottom() {
        return READOUT_Y + (MAX_READOUT_LINES - 1) * READOUT_LINE_HEIGHT + READOUT_GLYPH_HEIGHT;
    }
}
