package com.stonytark.magnetization.content.mrarmor;

import com.stonytark.magnetization.registry.MagArmorMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Magnetorheological (MR) Liquid Armor. Light and flexible like leather most of
 * the time, but the instant the wearer takes a kinetic hit — physical, fall, or
 * blast — an integrated battery pulses a field that snaps the fluid rigid,
 * shrugging off most of the impact. Mitigation scales with how many MR pieces
 * are worn (a full set ≈ 90%); see {@code MrArmorHandler}.
 */
public final class MrLiquidArmorItem extends ArmorItem {

    public MrLiquidArmorItem(final Type type, final Properties props) {
        super(MagArmorMaterials.mrLiquid(), type, props);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.magnetization.mr_armor").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
