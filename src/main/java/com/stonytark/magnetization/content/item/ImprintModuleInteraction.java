package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.EmitterPreset;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.AbstractEmitterBlockEntity;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * sneak-right-clicking an emitter with an Imprint Module in hand.
 *
 * <p>Same problem as {@link HematiteLensInteraction}: every emitter overrides
 * {@code useItemOn} for its own interaction (polarity flip, GUI open, rotation)
 * and the block consumes the click before {@code Item.useOn} ever fires.
 * Plain right-click on an emitter therefore can't carry a custom item action —
 * we have to intercept from {@link PlayerInteractEvent.RightClickBlock} at
 * HIGH priority and cancel the event.
 *
 * <p>Gestures, all sneak-gated so they never collide with block right-click:
 * <ul>
 *   <li><b>Sneak + right-click emitter, imprint empty</b> → capture the emitter's config.</li>
 *   <li><b>Sneak + right-click emitter, imprint filled</b> → project the preset onto the target.</li>
 * </ul>
 * Clearing a filled imprint to re-capture is done with right-click in air
 * (handled in {@link ImprintModuleItem#use}).
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class ImprintModuleInteraction {

    private ImprintModuleInteraction() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onImprintRightClickEmitter(final PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) return;
        final ItemStack held = event.getItemStack();
        if (held.isEmpty() || !held.is(MagItems.IMPRINT_MODULE.get())) return;

        final Level level = event.getLevel();
        final BlockEntity be = level.getBlockEntity(event.getPos());
        if (!(be instanceof AbstractEmitterBlockEntity emitter)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;

        final Player player = event.getEntity();
        final EmitterPreset existing = held.get(MagDataComponents.EMITTER_PRESET.get());
        if (existing == null) {
            capture(emitter, held, player);
        } else {
            project(emitter, existing, player);
        }
    }

    private static void capture(final AbstractEmitterBlockEntity emitter,
                                 final ItemStack stack, final Player player) {
        final MagneticStrength strength = emitter.effectiveStrength(MagneticStrength.STRONG);
        final MagneticPolarity polarity = emitter.effectivePolarity(MagneticPolarity.NORTH);
        // Round to int — sub-block tractor distances serialise cleanly.
        final int range = (int) Math.round(emitter.effectiveRange(strength));
        final ResourceLocation sourceBlock =
                BuiltInRegistries.BLOCK.getKey(emitter.getBlockState().getBlock());

        stack.set(MagDataComponents.EMITTER_PRESET.get(),
                new EmitterPreset(strength, polarity, range, sourceBlock));

        player.level().playSound(null, emitter.getBlockPos(),
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f, 1.4f);
        player.displayClientMessage(Component.translatable(
                "imprint.magnetization.captured", emitter.getBlockState().getBlock().getName())
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static void project(final AbstractEmitterBlockEntity emitter,
                                 final EmitterPreset preset, final Player player) {
        // Strength + range clamped to the target emitter's GUI ceilings;
        // polarity has no ceiling. Mirrors EmitterMenu's +/- limits so the
        // imprint can never set a value the player couldn't dial in by hand.
        final MagneticStrength ceiling = EmitterMenu.strengthCeilingFor(emitter);
        final MagneticStrength clampedStrength =
                preset.strength().ordinal() <= ceiling.ordinal() ? preset.strength() : ceiling;
        emitter.setStrengthOverride(clampedStrength);

        final int rangeCeiling = EmitterMenu.rangeCeilingFor(emitter);
        final int clampedRange = Math.min(preset.range(), rangeCeiling);
        emitter.setRangeOverride(clampedRange);

        emitter.setPolarityOverride(preset.polarity());

        player.level().playSound(null, emitter.getBlockPos(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
        player.displayClientMessage(Component.translatable(
                "imprint.magnetization.projected",
                clampedStrength.name(), preset.polarity().name(), clampedRange)
                .withStyle(ChatFormatting.AQUA), true);
    }
}
