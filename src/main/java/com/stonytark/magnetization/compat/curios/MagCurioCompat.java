package com.stonytark.magnetization.compat.curios;

import com.stonytark.magnetization.registry.MagItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * Curios API integration — registers the Field Compass and Magnetic Grapple
 * as curios so they work from a charm/back slot instead of requiring a hotbar
 * slot. The compass passively scans regardless of slot; the grapple is
 * activated by the standard right-click + the {@link ICurio} hooks ensure it
 * still works when not in the main-hand.
 *
 * <p>This class is only referenced from {@link #wire} which itself is gated on
 * {@link ModList#isLoaded(String) ModList.isLoaded("curios")} — so when Curios
 * isn't installed, none of the Curios imports resolve at runtime.
 */
public final class MagCurioCompat {

    private MagCurioCompat() {}

    /** Called from {@code Magnetization} only when {@code curios} is loaded. */
    public static void wire(final IEventBus modBus) {
        modBus.addListener(MagCurioCompat::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(final RegisterCapabilitiesEvent event) {
        // Per-stack ICurio: declares the item as a valid curio (so Curios slots
        // accept it). Pickup/usage behaviour is preserved verbatim because both
        // items already self-tick from their inventory slot — being in a curio
        // slot instead just stops them from occupying a hotbar / inventory cell.
        event.registerItem(CuriosCapability.ITEM,
                (stack, ctx) -> new ICurio() {
                    @Override public net.minecraft.world.item.ItemStack getStack() { return stack; }
                },
                MagItems.FIELD_COMPASS.get(),
                MagItems.MAGNETIC_GRAPPLE.get());
    }
}
