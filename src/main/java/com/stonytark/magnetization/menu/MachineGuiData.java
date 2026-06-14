package com.stonytark.magnetization.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.List;

/**
 * Implemented by machine block entities that open the shared {@link MachineMenu}.
 * Exposes the single input slot plus synced readouts the {@code MachineScreen}
 * renders (energy bar + two stat lines) and the HUD providers (WTHIT/Jade/TOP)
 * list. A value of {@code -1} hides that readout.
 */
public interface MachineGuiData {

    /** The 1-slot input container the menu binds (magnet / fuel cell / bucket). */
    Container guiInput();

    /** Which machine — drives stat labels in the GUI + HUD. */
    MachineMenu.Kind guiKind();

    /** Stored FE, or -1 to hide the energy bar. */
    default int guiEnergyStored() { return -1; }

    /** FE capacity (denominator for the energy bar). */
    default int guiEnergyMax() { return 1; }

    /** First stat readout (fuel ticks / fluid mB / RPM), or -1 to hide. */
    default int guiStat1() { return -1; }

    /** Second stat readout (current FE/tick output), or -1 to hide. */
    default int guiStat2() { return -1; }

    /** Lines for the WTHIT / Jade / TOP tooltip — the block's own live status. */
    default List<Component> hudLines() {
        final List<Component> out = new ArrayList<>();
        final boolean running = !guiInput().getItem(0).isEmpty();
        switch (guiKind()) {
            case TOKAMAK -> {
                out.add(Component.translatable("tooltip.magnetization.gui_fuel", guiStat1() / 20).withStyle(ChatFormatting.GRAY));
                out.add(Component.translatable("tooltip.magnetization.gui_output", Math.max(0, guiStat2())).withStyle(ChatFormatting.GRAY));
            }
            case THRUSTER -> out.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, guiStat1())).withStyle(ChatFormatting.AQUA));
            case MOTOR -> out.add(Component.translatable("tooltip.magnetization.gui_rpm", Math.max(0, guiStat1())).withStyle(ChatFormatting.GRAY));
            case JET -> out.add(Component.translatable(running
                    ? "tooltip.magnetization.machine_active" : "tooltip.magnetization.machine_idle")
                    .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        }
        if (guiEnergyStored() >= 0) {
            out.add(Component.translatable("tooltip.magnetization.gui_energy", guiEnergyStored(), guiEnergyMax())
                    .withStyle(ChatFormatting.GOLD));
        }
        return out;
    }
}
