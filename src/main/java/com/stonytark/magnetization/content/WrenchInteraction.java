package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sneak + right-click an emitter with a wrench (anything tagged
 * {@code c:tools/wrench}, including Create's wrench) to reset its in-GUI
 * overrides — strength, range, polarity, and (for anchors) the bound ship.
 *
 * <p>Plain right-click without sneak still falls through to the block's
 * normal {@code useItemOn} (rotate-on-click for IWrenchable repulsor/tractor).
 * We only intercept the sneak path so wrench-rotation keeps working.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class WrenchInteraction {

    /** Common Forge/NeoForge convention for wrench-class tools. */
    private static final TagKey<Item> WRENCH_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    private WrenchInteraction() {}

    @SubscribeEvent
    public static void onSneakWrench(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!event.getEntity().isShiftKeyDown()) return;
        final ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(WRENCH_TAG)) return;
        final BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof AbstractEmitterBlockEntity emitter)) return;

        emitter.resetOverrides();
        if (event.getEntity() instanceof ServerPlayer sp) {
            sp.displayClientMessage(
                    Component.translatable("message.magnetization.wrench_reset"), true);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}
