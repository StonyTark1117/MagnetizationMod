package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Ore Dowsing Compass — a passive needle item whose model angle (registered in
 * {@code CompassPropertyHooks}) points toward the nearest metallic ore vein, and
 * scrambles inside the Anomaly biome like the other compasses.
 *
 * <p>Anvil-combining it with a specific metallic ore block-item tunes it to that
 * ore (stored in {@link MagDataComponents#TUNED_ORE}); an untuned compass tracks
 * the nearest ore in {@code #magnetization:metallic_ores}.
 */
public class OreCompassItem extends Item {

    public OreCompassItem(final Properties props) {
        super(props);
    }

    /** @return the tuned target block, or null if the compass tracks any metallic ore. */
    public static Block tunedOre(final ItemStack stack) {
        final ResourceLocation id = stack.get(MagDataComponents.TUNED_ORE.get());
        if (id == null) return null;
        final Block block = BuiltInRegistries.BLOCK.get(id);
        // get() returns AIR for unknown ids — treat that as "untuned".
        return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        final Block tuned = tunedOre(stack);
        if (tuned != null) {
            tooltip.add(Component.translatable("tooltip.magnetization.ore_compass.tuned",
                            tuned.getName().withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.magnetization.ore_compass.any")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
