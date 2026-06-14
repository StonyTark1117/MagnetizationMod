package com.stonytark.magnetization.content.sail;

import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Alfvén Ribbon Backpack (Solar Wind Wings) — a chest-slot Elytra alternative.
 * Glides like an elytra, but its superconducting ribbons catch the ambient
 * solar-wind current to give a <b>permanent, fuel-free forward boost</b> (no
 * firework rockets) whenever you glide <b>high up in daylight</b> or anywhere in
 * <b>the End</b>. The wearable cousin of the {@link SolarSailBlock}; drive logic
 * lives in {@code AlfvenBackpackHandler}.
 */
public final class AlfvenBackpackItem extends ElytraItem {

    public AlfvenBackpackItem(final Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.magnetization.backpack_boost").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    @Override
    public boolean isValidRepairItem(final ItemStack stack, final ItemStack repair) {
        return repair.is(MagItems.FERROMAGNETIC_INGOT.get()) || super.isValidRepairItem(stack, repair);
    }
}
