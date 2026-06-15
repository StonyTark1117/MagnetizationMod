package com.stonytark.magnetization.content.mrarmor;

import com.stonytark.magnetization.registry.MagArmorMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Magnetorheological Horse Armor — the equine counterpart of {@link MrLiquidArmorItem}.
 * Looks and behaves like the player set: the worn barding ripples as MR fluid and
 * snaps to a rigid plate in a field / on a kinetic hit (custom horse render layer),
 * and the mount enjoys the same field-hardened damage mitigation (see
 * {@code MrArmorHandler}, which treats this item as an MR piece on the body slot).
 */
public final class MrFluidHorseArmorItem extends AnimalArmorItem {

    public MrFluidHorseArmorItem(final BodyType bodyType, final boolean hasOverlay, final Properties props) {
        super(MagArmorMaterials.mrLiquid(), bodyType, hasOverlay, props);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                final List<Component> tooltip, final TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.magnetization.mr_armor").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
