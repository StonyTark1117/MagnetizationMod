package com.stonytark.magnetization.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Curios-only half of the induction pad: charges energy-storing items worn in
 * curio slots. Loaded reflectively (only referenced behind
 * {@code ModList.isLoaded("curios")}) so the Curios imports never resolve when
 * the mod is absent.
 */
public final class InductionCuriosCharging {

    private InductionCuriosCharging() {}

    /** Top up energy items in {@code player}'s curio slots; returns FE consumed. */
    public static int chargeCurios(final Player player, final int budget) {
        if (budget <= 0) return 0;
        return CuriosApi.getCuriosInventory(player).map(inv -> {
            int spent = 0;
            final var handler = inv.getEquippedCurios();
            for (int i = 0; i < handler.getSlots() && budget - spent > 0; i++) {
                final ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                final IEnergyStorage cap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
                if (cap != null && cap.canReceive()) {
                    spent += cap.receiveEnergy(budget - spent, false);
                }
            }
            return spent;
        }).orElse(0);
    }
}
