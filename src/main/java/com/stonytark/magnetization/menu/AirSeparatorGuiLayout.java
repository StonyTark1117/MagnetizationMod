package com.stonytark.magnetization.menu;

/** Shared geometry contract for the Air Separator menu and client screen. */
public final class AirSeparatorGuiLayout {
    public static final int IMAGE_WIDTH = 196;
    public static final int IMAGE_HEIGHT = 266;

    public static final int TANK_X = 10;
    public static final int TANK_STRIDE = 29;
    public static final int TANK_Y = 34;
    public static final int TANK_WIDTH = 18;
    public static final int TANK_HEIGHT = 42;
    public static final int GAS_SYMBOL_Y = 78;
    public static final int PORT_BUTTON_Y = 89;
    public static final int PORT_BUTTON_WIDTH = 24;
    public static final int PORT_BUTTON_HEIGHT = 14;
    public static final int FACE_SELECTOR_LABEL_Y = 105;
    public static final int FACE_BUTTON_X = 8;
    public static final int FACE_BUTTON_Y = 115;
    public static final int FACE_BUTTON_STRIDE = 27;
    public static final int FACE_BUTTON_WIDTH = 24;
    public static final int FACE_BUTTON_HEIGHT = 14;
    public static final int INPUT_MODE_X = 171;
    public static final int INPUT_MODE_WIDTH = 20;

    public static final int UPGRADE_X = 169;
    public static final int UPGRADE_Y = 34;
    public static final int OUTPUT_X = 169;
    public static final int OUTPUT_Y = 63;

    public static final int READOUT_Y = 134;
    public static final int READOUT_LINES = 3;
    public static final int READOUT_LINE_HEIGHT = 10;
    public static final int GLYPH_HEIGHT = 9;

    public static final int PLAYER_INVENTORY_X = 17;
    public static final int INVENTORY_LABEL_Y = 166;
    public static final int PLAYER_INVENTORY_Y = 178;
    public static final int HOTBAR_Y = 236;

    private AirSeparatorGuiLayout() {}

    public static int readoutBottom() {
        return READOUT_Y + (READOUT_LINES - 1) * READOUT_LINE_HEIGHT + GLYPH_HEIGHT;
    }
}
