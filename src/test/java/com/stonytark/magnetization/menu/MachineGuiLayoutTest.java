package com.stonytark.magnetization.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineGuiLayoutTest {
    @Test
    void readoutsClearControlsAndPlayerInventory() {
        assertTrue(MachineGuiLayout.READOUT_Y > MachineGuiLayout.CONTROLS_BOTTOM,
                "Machine readouts must begin below the input slot and vertical bars");
        assertTrue(MachineGuiLayout.readoutBottom() < MachineGuiLayout.INVENTORY_LABEL_Y,
                "Four readout rows must end before the player inventory label");
        assertTrue(MachineGuiLayout.INVENTORY_LABEL_Y + MachineGuiLayout.READOUT_GLYPH_HEIGHT
                        < MachineGuiLayout.PLAYER_INVENTORY_Y,
                "Player inventory label must clear the first slot row");
        assertTrue(MachineGuiLayout.HOTBAR_Y + 17 < MachineGuiLayout.IMAGE_HEIGHT,
                "Hotbar wells must remain inside the expanded machine panel");
    }
}
