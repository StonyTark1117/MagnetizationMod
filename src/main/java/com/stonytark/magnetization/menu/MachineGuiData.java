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
 * renders (energy bar + two stat lines) and the HUD providers (WTHIT/Jade/TOP/Create goggles)
 * list. A value of {@code -1} hides that readout.
 */
public interface MachineGuiData extends MachineHudData {

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

    /** Third synced value — a small enum-like code the screen interprets per kind
     *  (e.g. the tokamak's current fuel tier 0/1/2). -1 = unused. */
    default int guiStat3() { return -1; }

    /** Authoritative denominator for the secondary fuel/fluid bar (tank capacity or
     *  current-tier burn ticks), computed SERVER-side from the server's config and
     *  synced so multiplayer clients with a different COMMON config still draw the
     *  correct fill percentage. -1 = no secondary bar. */
    default int guiStat4() { return -1; }

    /** Formed multiblock edge/interior count for structure-aware machines. */
    default int guiStructureSize() { return 0; }

    /** Performance multiplier derived from the formed structure. */
    default int guiStructureScale() { return 0; }

    /**
     * Named server-authoritative snapshot for new display consumers. The legacy
     * guiStat methods remain as a compatibility bridge for old screens.
     */
    default MachineDisplayData displayData() {
        return new MachineDisplayData(guiEnergyStored(), guiEnergyMax(), guiStat1(),
                guiStat4(), guiStat3(), guiStat2(), guiStructureSize(),
                guiStructureScale(), guiDisplayStatus());
    }

    /** Override when a machine's lifecycle is richer than current > 0 = active. */
    default MachineDisplayData.Status guiDisplayStatus() {
        return guiStat1() > 0 ? MachineDisplayData.Status.ACTIVE : MachineDisplayData.Status.IDLE;
    }

    /** Shared status line formatter for Jade, WTHIT, TOP, and the GUI. */
    static Component statusLine(final MachineDisplayData.Status status) {
        final String key = switch (status) {
            case ACTIVE -> "tooltip.magnetization.machine_active";
            case FORMED -> "tooltip.magnetization.machine_formed";
            case INVALID -> "tooltip.magnetization.machine_invalid";
            case HOLDING -> "tooltip.magnetization.machine_holding";
            case LAUNCHING -> "tooltip.magnetization.machine_launching";
            case COOLDOWN -> "tooltip.magnetization.machine_cooldown";
            case IDLE -> "tooltip.magnetization.machine_idle";
        };
        final ChatFormatting colour = switch (status) {
            case ACTIVE, FORMED -> ChatFormatting.GREEN;
            case HOLDING, LAUNCHING -> ChatFormatting.AQUA;
            case COOLDOWN -> ChatFormatting.GOLD;
            case INVALID -> ChatFormatting.RED;
            case IDLE -> ChatFormatting.YELLOW;
        };
        return Component.translatable(key).withStyle(colour);
    }

    /**
     * Machine-aware status formatter. The old idle text described one specific
     * consumer (magnet + power), which made generator and fluid-only machines
     * report requirements they do not have.
     */
    static Component statusLine(final MachineMenu.Kind kind, final MachineDisplayData.Status status) {
        if (status == MachineDisplayData.Status.ACTIVE && switch (kind) {
            case JET, THRUSTER, ION_THRUSTER, FUSION_THRUSTER -> true;
            default -> false;
        }) {
            return Component.translatable("tooltip.magnetization.machine_firing").withStyle(ChatFormatting.GREEN);
        }
        if (status != MachineDisplayData.Status.IDLE) return statusLine(status);
        final String key = switch (kind) {
            case MOTOR -> "tooltip.magnetization.machine_idle_motor";
            case TOKAMAK -> "tooltip.magnetization.machine_idle_tokamak";
            case JET -> "tooltip.magnetization.machine_idle_jet";
            case THRUSTER, ION_THRUSTER, FUSION_THRUSTER -> "tooltip.magnetization.machine_idle_thruster";
            case ELECTROLYZER -> "tooltip.magnetization.machine_idle_electrolyzer";
            case RAILGUN -> "tooltip.magnetization.machine_idle_railgun";
            case COIL -> "tooltip.magnetization.machine_idle_coil";
            case SAIL -> "tooltip.magnetization.machine_idle_sail";
        };
        return Component.translatable(key).withStyle(ChatFormatting.YELLOW);
    }

    /** Lines for the WTHIT / Jade / TOP/Create goggles tooltip — the block's own live status.
     *  Note: no stored-FE line here. WTHIT/Jade already draw a built-in energy
     *  bar from the {@code EnergyStorage} capability, so emitting one would
     *  double up (the well-known "two FE bars" bug). */
    @Override
    default List<Component> hudLines() {
        final List<Component> out = new ArrayList<>();
        final ItemStack magnet = guiInput().getItem(0);
        final MachineDisplayData display = displayData();
        switch (guiKind()) {
            case TOKAMAK -> {
                out.add(tokamakRingLine(display));
                if (display.structureScale() > 0) out.add(tokamakScaleLine(display));
                out.add(Component.translatable("tooltip.magnetization.gui_fuel", display.current() / 20).withStyle(ChatFormatting.GRAY));
                out.add(Component.translatable("tooltip.magnetization.gui_output", Math.max(0, display.auxiliary())).withStyle(ChatFormatting.GRAY));
                if (display.current() > 0) {
                    // Name the current fuel tier as text (was only implied by the bar in
                    // the hover HUD). Reuses the same tier keys the GUI screen uses.
                    final String[] tiers = {"dd", "dt", "he3"};
                    out.add(Component.translatable("tooltip.magnetization.gui_tokamak_tier_"
                            + tiers[Math.min(2, Math.max(0, display.tier()))]).withStyle(ChatFormatting.GRAY));
                }
                out.add(statusLine(guiKind(), display.status()));
            }
            case THRUSTER, ION_THRUSTER -> {
                out.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, display.current())).withStyle(ChatFormatting.AQUA));
                out.add(statusLine(guiKind(), display.status()));
            }
            case FUSION_THRUSTER -> {
                out.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, display.current())).withStyle(ChatFormatting.AQUA));
                final int interiors = Math.max(0, display.auxiliary());
                out.add(Component.translatable("tooltip.magnetization.gui_fusion_size", interiors).withStyle(ChatFormatting.GRAY));
                out.add(statusLine(guiKind(), display.status()));
            }
            case ELECTROLYZER -> {
                out.add(Component.translatable("tooltip.magnetization.gui_water", Math.max(0, display.current())).withStyle(ChatFormatting.BLUE));
                out.add(Component.translatable("tooltip.magnetization.gui_hydrogen", Math.max(0, display.auxiliary())).withStyle(ChatFormatting.WHITE));
                out.add(statusLine(guiKind(), display.status()));
            }
            case RAILGUN -> {
                out.add(Component.translatable("tooltip.magnetization.gui_rail_length", Math.max(0, display.current())).withStyle(ChatFormatting.GRAY));
                final int packed = Math.max(0, display.auxiliary());
                final boolean manual = (packed
                        & com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.MANUAL_MODE_BIT) != 0;
                final boolean breakBlocks = (packed
                        & com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.BREAK_BLOCKS_BIT) != 0;
                final String[] states = {"idle", "holding", "launching", "cooldown"};
                final String stateKey = states[Math.min(states.length - 1, packed
                        & com.stonytark.magnetization.content.railgun.RailgunEmitterBlockEntity.ARC_STATE_MASK)];
                out.add(Component.translatable(manual
                        ? "tooltip.magnetization.gui_railgun_manual" : "tooltip.magnetization.gui_railgun_auto")
                        .withStyle(manual ? ChatFormatting.GOLD : ChatFormatting.GREEN));
                out.add(Component.translatable("tooltip.magnetization.gui_railgun_state_" + stateKey)
                        .withStyle(ChatFormatting.AQUA));
                out.add(Component.translatable(breakBlocks
                        ? "tooltip.magnetization.gui_railgun_break_blocks_on"
                        : "tooltip.magnetization.gui_railgun_break_blocks_off")
                        .withStyle(breakBlocks ? ChatFormatting.RED : ChatFormatting.GREEN));
                out.add(statusLine(guiKind(), display.status()));
            }
            case MOTOR -> {
                out.add(magnetStatusLine(magnet));
                magnetBurnLine(display.auxiliary()).ifPresent(out::add);
                out.add(Component.translatable("tooltip.magnetization.gui_rpm", Math.max(0, display.current())).withStyle(ChatFormatting.GRAY));
                out.add(statusLine(guiKind(), display.status()));
            }
            case JET -> {
                out.add(magnetStatusLine(magnet));
                magnetBurnLine(display.auxiliary()).ifPresent(out::add);
                out.add(Component.translatable("tooltip.magnetization.gui_fluid", Math.max(0, display.current())).withStyle(ChatFormatting.AQUA));
                out.add(statusLine(guiKind(), display.status()));
            }
        }
        return out;
    }

    static Component tokamakRingLine(final MachineDisplayData display) {
        if (display.structureSize() <= 0) {
            return Component.translatable("tooltip.magnetization.gui_tokamak_ring_invalid")
                    .withStyle(ChatFormatting.RED);
        }
        final int edge = display.structureSize();
        return Component.translatable("tooltip.magnetization.gui_tokamak_ring",
                edge, edge, edge * 4 - 4, (edge - 2) * (edge - 2)).withStyle(ChatFormatting.GRAY);
    }

    static Component tokamakScaleLine(final MachineDisplayData display) {
        return Component.translatable("tooltip.magnetization.gui_tokamak_scale",
                Math.max(1, display.structureScale())).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    static Component tokamakRingScaleLine(final MachineDisplayData display) {
        if (display.structureSize() <= 0) return tokamakRingLine(display);
        final int edge = display.structureSize();
        return Component.translatable("tooltip.magnetization.gui_tokamak_ring_scaled",
                edge, edge, edge * 4 - 4, (edge - 2) * (edge - 2),
                Math.max(1, display.structureScale()));
    }

    /** Magnetic potency of the slotted material (0 = empty / not a magnet).
     *  Scales with ore type + processing form — see {@link MagneticMaterials}. */
    static int magnetStrengthLevel(final ItemStack stack) {
        return MagneticMaterials.potency(stack);
    }

    /** "Magnet: Magnetite Ingot (Strength 10)" or "No magnet installed" — the
     *  readout the motor + MHD jet surface in their GUI and in WTHIT/Jade/TOP/Create goggles. */
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

    /** "Magnet burn: 88s" — the magnet-slot machines' remaining burn time, shown
     *  only while a magnet is actively burning down ({@code magnetSlotConsumesFuel}
     *  on; hidden entirely in legacy infinite-magnet mode where it stays 0). */
    static java.util.Optional<Component> magnetBurnLine(final int burnTicks) {
        if (burnTicks <= 0) return java.util.Optional.empty();
        return java.util.Optional.of(Component.translatable(
                "tooltip.magnetization.gui_magnet_burn", burnTicks / 20).withStyle(ChatFormatting.GOLD));
    }
}
