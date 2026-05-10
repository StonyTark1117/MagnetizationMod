package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a "How to use" line to each addon item's hover tooltip — a stand-in for
 * Ponder. Strings live in {@code en_us.json} under {@code tooltip.magnetization.<key>}.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagItemTooltips {

    private static Map<Item, String> map;

    private MagItemTooltips() {}

    private static Map<Item, String> build() {
        final Map<Item, String> m = new IdentityHashMap<>();
        m.put(MagItems.ELECTROMAGNET.get(),         "tooltip.magnetization.electromagnet.use");
        m.put(MagItems.KINETIC_ELECTROMAGNET.get(), "tooltip.magnetization.kinetic_electromagnet.use");
        m.put(MagItems.MAGNETIC_ANCHOR.get(),       "tooltip.magnetization.magnetic_anchor.use");
        m.put(MagItems.REPULSOR_COIL.get(),         "tooltip.magnetization.repulsor_coil.use");
        m.put(MagItems.TRACTOR_BEAM.get(),          "tooltip.magnetization.tractor_beam.use");
        m.put(MagItems.MAGNETIC_EXCAVATOR.get(),    "tooltip.magnetization.magnetic_excavator.use");
        m.put(MagItems.LODESTONE_CORE.get(),        "tooltip.magnetization.lodestone_core.use");
        m.put(MagItems.MAGNETIC_SWITCH.get(),       "tooltip.magnetization.magnetic_switch.use");
        m.put(MagItems.PERMANENT_MAGNET.get(),      "tooltip.magnetization.permanent_magnet.use");
        m.put(MagItems.POLARITY_INVERTER.get(),     "tooltip.magnetization.polarity_inverter.use");
        m.put(MagItems.FERROMAGNETIC_INGOT.get(),   "tooltip.magnetization.ferromagnetic_ingot.use");
        m.put(MagItems.MAGNETIC_PLATE.get(),        "tooltip.magnetization.magnetic_plate.use");
        m.put(MagItems.FIELD_COMPASS.get(),         "tooltip.magnetization.field_compass.use");
        m.put(MagItems.MAGNETIC_GRAPPLE.get(),      "tooltip.magnetization.magnetic_grapple.use");
        m.put(MagItems.MAGNETITE_INGOT.get(),       "tooltip.magnetization.magnetite_ingot.use");
        m.put(MagItems.RAW_MAGNETITE.get(),         "tooltip.magnetization.raw_magnetite.use");
        m.put(MagItems.MAGNETITE_ORE.get(),         "tooltip.magnetization.magnetite_ore.use");
        m.put(MagItems.DEEPSLATE_MAGNETITE_ORE.get(), "tooltip.magnetization.magnetite_ore.use");
        m.put(MagItems.MAGNETITE_BLOCK.get(),       "tooltip.magnetization.magnetite_block.use");
        m.put(MagItems.RAW_MAGNETITE_BLOCK.get(),   "tooltip.magnetization.raw_magnetite_block.use");
        return m;
    }

    @SubscribeEvent
    public static void onTooltip(final ItemTooltipEvent event) {
        if (map == null) {
            // Build lazily — items aren't resolved at class-init time on the mod bus.
            try { map = build(); } catch (Throwable t) { return; }
        }
        final ItemStack stack = event.getItemStack();
        final List<Component> lines = event.getToolTip();
        final String key = map.get(stack.getItem());
        if (key != null) {
            lines.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
        }
        // Magnetized armor: show its polarity stamp regardless of which item it is —
        // any tagged metal armor can be magnetized, including iron, gold, netherite, etc.
        final MagneticPolarity pol = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        if (pol != null && pol != MagneticPolarity.NONE) {
            final ChatFormatting color = pol == MagneticPolarity.NORTH
                    ? ChatFormatting.AQUA : ChatFormatting.RED;
            lines.add(Component.translatable("tooltip.magnetization.magnetized_armor",
                    Component.translatable("tooltip.magnetization.polarity." + pol.getSerializedName())
                            .withStyle(color)
            ).withStyle(ChatFormatting.GRAY));
        }
    }
}
