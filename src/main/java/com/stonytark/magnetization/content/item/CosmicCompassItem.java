package com.stonytark.magnetization.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Long-range compass that tracks the nearest active {@code meteorite_core}
 * within a much wider radius than the standard Field Compass (default 512
 * blocks vs Field Compass's 16). Targets only meteorite cores — emitters
 * and field sources are ignored.
 *
 * <p>Unlike the Field Compass, the Cosmic Compass is <b>not</b> scrambled
 * by the anomaly biome — the meteorite cores ARE the cosmic signal and
 * register above the biome's flux noise. A nice side benefit of crafting
 * with a meteorite_fragment: anomaly-resistance comes for free.
 *
 * <p>Needle frame dispatch is wired by
 * {@link com.stonytark.magnetization.client.CompassPropertyHooks#registerCosmicCompass}
 * at client setup.
 */
public class CosmicCompassItem extends Item {
    public CosmicCompassItem(final Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                 final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.magnetization.cosmic_compass.tracks")
                .withStyle(ChatFormatting.GRAY));
        final int range;
        try { range = com.stonytark.magnetization.config.MagConfig.COSMIC_COMPASS_RANGE.get(); }
        catch (final Throwable t) { return; }
        tooltip.add(Component.translatable("tooltip.magnetization.cosmic_compass.range", range)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
