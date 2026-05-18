package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Pre-empts the block's own right-click handling when the player is
 * sneak-right-clicking an emitter with the Hematite Polarizer in hand.
 *
 * <p>Every emitter block overrides {@code useItemOn} (rotate, polarity flip,
 * GUI open) and consumes the click before {@code Item.useOn} ever fires —
 * so we can't drive lock install/remove from the item alone. Mirroring
 * {@link com.stonytark.magnetization.content.WrenchInteraction}, we listen
 * on {@link PlayerInteractEvent.RightClickBlock} at HIGH priority and cancel
 * the event for the lens-on-emitter case so the block stays out of it.
 *
 * <p>Plain (non-sneak) right-click on an emitter is intentionally not
 * intercepted: the block's own interaction (polarity flip, GUI open, etc.)
 * still fires. The lens toggles its stored polarity via {@link HematiteLensItem#use}
 * on a plain right-click in air, which never reaches a block.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class HematiteLensInteraction {

    private HematiteLensInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLensRightClickEmitter(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) return;
        final ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(MagItems.HEMATITE_LENS.get())) return;

        final Level level = event.getLevel();
        final BlockEntity be = level.getBlockEntity(event.getPos());
        if (!(be instanceof AbstractEmitterBlockEntity emitter)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;

        final Player player = event.getEntity();
        if (emitter.getLockedPolarity() == null) {
            final MagneticPolarity target = lensPolarity(held);
            emitter.setLockedPolarity(target);
            level.playSound(null, event.getPos(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5f, 1.8f);
            player.displayClientMessage(
                    Component.translatable("hematite_lens.magnetization.locked", target.name())
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        } else {
            emitter.setLockedPolarity(null);
            level.playSound(null, event.getPos(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
            player.displayClientMessage(
                    Component.translatable("hematite_lens.magnetization.unlocked")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
    }

    private static MagneticPolarity lensPolarity(final ItemStack stack) {
        final MagneticPolarity stored = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        return stored != null ? stored : MagneticPolarity.NORTH;
    }
}
