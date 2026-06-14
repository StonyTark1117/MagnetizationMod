package com.stonytark.magnetization.menu;

import net.minecraft.world.Container;

/**
 * Implemented by machine block entities that open the shared {@link MachineMenu}.
 * Exposes the single input slot plus up to four synced readouts the
 * {@code MachineScreen} renders (energy bar + two stat lines). A value of
 * {@code -1} hides that readout.
 */
public interface MachineGuiData {

    /** The 1-slot input container the menu binds (magnet / fuel cell / bucket). */
    Container guiInput();

    /** Stored FE, or -1 to hide the energy bar. */
    default int guiEnergyStored() { return -1; }

    /** FE capacity (denominator for the energy bar). */
    default int guiEnergyMax() { return 1; }

    /** First stat readout (e.g. fuel ticks, fluid mB), or -1 to hide. */
    default int guiStat1() { return -1; }

    /** Second stat readout (e.g. current FE/tick output), or -1 to hide. */
    default int guiStat2() { return -1; }
}
