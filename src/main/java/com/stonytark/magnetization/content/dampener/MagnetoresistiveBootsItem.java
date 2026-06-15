package com.stonytark.magnetization.content.dampener;

import com.stonytark.magnetization.registry.MagArmorMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Magnetoresistive Dampening Boots. A standalone pair of magnetic-metal boots
 * that automatically arrest <em>any</em> fall — the magnetoresistive lining
 * stiffens on impact and absorbs it — at the cost of some durability per save.
 * Unlike the mod's other magnetic armor, these are <b>not</b> field-reactive by
 * default (they're never pulled by emitters); a player can opt them in by
 * magnetizing them in the electromagnet GUI, after which they behave like other
 * magnetized gear.
 */
public final class MagnetoresistiveBootsItem extends ArmorItem {

    public MagnetoresistiveBootsItem(final Properties props) {
        super(MagArmorMaterials.magneticCushion(), ArmorItem.Type.BOOTS, props);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.magnetization.boots_fall_save").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.magnetization.boots_inert").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
