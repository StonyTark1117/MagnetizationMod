package com.stonytark.magnetization.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AirSeparatorGuiLayoutTest {
    @Test
    void processControlsReadoutsAndInventoryDoNotOverlap() {
        assertTrue(AirSeparatorGuiLayout.TANK_Y + AirSeparatorGuiLayout.TANK_HEIGHT
                        < AirSeparatorGuiLayout.PORT_BUTTON_Y,
                "Gas bars overlap their port controls");
        assertTrue(AirSeparatorGuiLayout.PORT_BUTTON_Y + AirSeparatorGuiLayout.PORT_BUTTON_HEIGHT
                        < AirSeparatorGuiLayout.READOUT_Y,
                "Port controls overlap the process readouts");
        assertTrue(AirSeparatorGuiLayout.readoutBottom() < AirSeparatorGuiLayout.INVENTORY_LABEL_Y,
                "Process readouts overlap the player inventory label");
        assertTrue(AirSeparatorGuiLayout.INVENTORY_LABEL_Y + AirSeparatorGuiLayout.GLYPH_HEIGHT
                        < AirSeparatorGuiLayout.PLAYER_INVENTORY_Y,
                "Player inventory label overlaps its first slot row");
        assertTrue(AirSeparatorGuiLayout.HOTBAR_Y + 17 < AirSeparatorGuiLayout.IMAGE_HEIGHT,
                "Hotbar extends beyond the screen panel");
        assertTrue(AirSeparatorGuiLayout.PLAYER_INVENTORY_X + 9 * 18
                        < AirSeparatorGuiLayout.IMAGE_WIDTH,
                "Player inventory extends beyond the screen panel");
        assertTrue(AirSeparatorGuiLayout.UPGRADE_X + 17 < AirSeparatorGuiLayout.IMAGE_WIDTH
                        && AirSeparatorGuiLayout.OUTPUT_X + 17 < AirSeparatorGuiLayout.IMAGE_WIDTH,
                "Air Separator machine slots extend beyond the screen panel");
    }
}
