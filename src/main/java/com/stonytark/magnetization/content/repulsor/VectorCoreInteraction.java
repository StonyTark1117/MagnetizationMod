package com.stonytark.magnetization.content.repulsor;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Installs a {@link MagItems#VECTOR_CORE} into a Repulsor Coil: right-click the
 * coil with the chip and it switches into directional-thrust mode (propels
 * magnetic ships along the coil's facing). One per coil; the chip is returned
 * when the coil is broken. Runs at HIGH priority so it beats the coil's
 * right-click GUI.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class VectorCoreInteraction {

    private VectorCoreInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(final PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()) return;
        final ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(MagItems.VECTOR_CORE.get())) return;
        final BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (!(be instanceof RepulsorCoilBlockEntity coil)) return;
        if (coil.hasVectorCore()) return; // already installed — leave the click alone

        if (!event.getLevel().isClientSide) {
            coil.setVectorCore(true);
            if (!event.getEntity().getAbilities().instabuild) held.shrink(1);
            if (event.getEntity() instanceof ServerPlayer sp) {
                sp.displayClientMessage(
                        Component.translatable("message.magnetization.vector_core_installed"), true);
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
