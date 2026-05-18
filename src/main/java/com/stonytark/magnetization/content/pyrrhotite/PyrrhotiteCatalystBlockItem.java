package com.stonytark.magnetization.content.pyrrhotite;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pulls the {@link PyrrhotiteCatalystBlock#transmitRadius()} off the host
 * block and surfaces it as an item-tooltip line so players can read the
 * tier difference at a glance in inventory / creative tab / JEI — without
 * having to open Patchouli or place each tier to compare.
 */
public final class PyrrhotiteCatalystBlockItem extends BlockItem {

    public PyrrhotiteCatalystBlockItem(final Block block, final Item.Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final @Nullable Item.TooltipContext context,
                                 final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (getBlock() instanceof PyrrhotiteCatalystBlock cat) {
            tooltip.add(Component.translatable("tooltip.magnetization.catalyst_radius", cat.transmitRadius())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
