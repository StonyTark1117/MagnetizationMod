package com.stonytark.magnetization.content;

import net.minecraft.world.Container;

/**
 * Implemented by machines with an internal redstone-fuel slot (Magnetic
 * Excavator, Structural Inducer): any item in {@code MagTags.REDSTONE_FUEL}
 * parked in the slot keeps the machine powered, equivalent to a constant
 * external redstone signal. Lets {@link com.stonytark.magnetization.menu.EmitterMenu}
 * bind the slot generically via {@code CAP_REDSTONE_FUEL} instead of per-block.
 */
public interface RedstoneFuelHolder {

    /** The single-slot redstone-fuel container the GUI binds. */
    Container getRedstoneFuelSlot();
}
