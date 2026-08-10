package com.stonytark.magnetization.menu;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Common live-information contract for Magnetization machines. Unlike
 * {@link MachineGuiData}, this does not require the machine to use the shared
 * one-slot menu, so machines with richer dedicated screens can still feed the
 * same authoritative lines to Create goggles, Jade, WTHIT, and The One Probe.
 */
public interface MachineHudData extends IHaveGoggleInformation {

    List<Component> hudLines();

    @Override
    default boolean addToGoggleTooltip(final List<Component> tooltip,
                                       final boolean isPlayerSneaking) {
        tooltip.addAll(hudLines());
        return true;
    }
}
