package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Enforces disabled-content policy at the interaction boundary. */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class DisabledContentRuntime {

    private DisabledContentRuntime() {}

    @SubscribeEvent
    public static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        final ItemStack held = event.getItemStack();
        if (MagConfig.isItemDisabled(held)
                || MagConfig.isBlockDisabled(event.getLevel().getBlockState(event.getPos()))) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(final PlayerInteractEvent.RightClickItem event) {
        if (MagConfig.isItemDisabled(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(final PlayerInteractEvent.EntityInteract event) {
        if (MagConfig.isItemDisabled(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(final PlayerInteractEvent.EntityInteractSpecific event) {
        if (MagConfig.isItemDisabled(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
