package com.stonytark.magnetization.menu;

import com.stonytark.magnetization.content.MagneticMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

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

    /** Lines for the WTHIT / Jade / TOP tooltip — the block's own live status.
     *  Note: no stored-FE line here. WTHIT/Jade already draw a built-in energy
     *  bar from the {@code EnergyStorage} capability, so emitting one would
     *  double up (the well-known "two FE bars" bug). */
    default List<Component> hudLines() {
        final List<Component> out = new ArrayList<>();
        final ItemStack magnet = guiInput().getItem(0);
        switch (guiKind()) {
            case TOKAMAK -> {
                out.add(Component.translatable("tooltip.magnetization.gui_fuel", guiStat1() / 20).withStyle(ChatFormatting.GRAY));
                out.add(Component.translatable("tooltip.magnetization.gui_output", Math.max(0, guiStat2())).withStyle(ChatFormatting.GRAY));
            }
            case THRUSTER -> out.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, guiStat1())).withStyle(ChatFormatting.AQUA));
            case MOTOR -> {
                out.add(magnetStatusLine(magnet));
                out.add(Component.translatable("tooltip.magnetization.gui_rpm", Math.max(0, guiStat1())).withStyle(ChatFormatting.GRAY));
            }
            case JET -> {
                out.add(magnetStatusLine(magnet));
                final boolean running = magnetStrengthLevel(magnet) > 0 && guiEnergyStored() > 0;
                out.add(Component.translatable(running
                        ? "tooltip.magnetization.machine_active" : "tooltip.magnetization.machine_idle")
                        .withStyle(running ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }
        }
        return out;
    }

    /** Magnetic potency of the slotted material (0 = empty / not a magnet).
     *  Scales with ore type + processing form — see {@link MagneticMaterials}. */
    static int magnetStrengthLevel(final ItemStack stack) {
        return MagneticMaterials.potency(stack);
    }

    /** "Magnet: Magnetite Ingot (Strength 10)" or "No magnet installed" — the
     *  readout the motor + MHD jet surface in their GUI and in WTHIT/Jade/TOP. */
    static Component magnetStatusLine(final ItemStack magnet) {
        final int potency = MagneticMaterials.potency(magnet);
        if (potency == 0) {
            return Component.translatable("tooltip.magnetization.gui_no_magnet").withStyle(ChatFormatting.DARK_GRAY);
        }
        final ChatFormatting colour = potency >= 25 ? ChatFormatting.LIGHT_PURPLE
                : potency >= 13 ? ChatFormatting.AQUA
                : potency >= 7 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
        return Component.translatable("tooltip.magnetization.gui_magnet", magnet.getHoverName(), potency)
                .withStyle(colour);
    }
}
