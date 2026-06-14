package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Tunes the Ore Dowsing Compass: anvil-combine it with a metallic ore block and
 * the result is a compass locked onto that specific ore (stored in
 * {@link MagDataComponents#TUNED_ORE}) instead of tracking the nearest ore of
 * any kind. Only ores in {@code #magnetization:metallic_ores} are accepted.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class OreCompassAnvilHandler {

    private OreCompassAnvilHandler() {}

    @SubscribeEvent
    public static void onAnvil(final AnvilUpdateEvent event) {
        final ItemStack left = event.getLeft();
        final ItemStack right = event.getRight();
        if (!left.is(MagItems.ORE_COMPASS.get())) return;
        if (!(right.getItem() instanceof BlockItem blockItem)) return;

        final Block ore = blockItem.getBlock();
        // Only magnetic/metallic ores tune the compass.
        if (!ore.defaultBlockState().is(MagTags.METALLIC_ORES)) return;

        final ItemStack out = left.copy();
        out.setCount(1);
        out.set(MagDataComponents.TUNED_ORE.get(), BuiltInRegistries.BLOCK.getKey(ore));
        event.setOutput(out);
        event.setCost(5);
        event.setMaterialCost(1);
    }
}
