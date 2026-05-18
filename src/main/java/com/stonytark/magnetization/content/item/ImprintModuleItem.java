package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.EmitterPreset;
import com.stonytark.magnetization.menu.EmitterMenu;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Titanomagnetite-derived portable field memory. Captures an emitter's
 * effective configuration (strength tier, polarity, range, source block id)
 * and projects it onto other emitters without consuming the imprint.
 *
 * <p><b>Controls</b>
 * <ul>
 *   <li><b>Sneak + right-click an emitter, imprint empty</b> → capture.</li>
 *   <li><b>Sneak + right-click an emitter, imprint filled</b> → project the captured preset onto the target.</li>
 *   <li><b>Right-click in air, imprint filled</b> → clear the preset so you can capture again.</li>
 * </ul>
 *
 * <p>Capture/project both live in {@link ImprintModuleInteraction} as an
 * event subscriber — emitter blocks override {@code useItemOn} for their own
 * actions (polarity flip, GUI open) and consume the click before
 * {@code Item.useOn} can fire, so the imprint has to pre-empt them via
 * {@code RightClickBlock}. All emitter-touching gestures are sneak-only to
 * leave the block's plain right-click untouched.
 *
 * <p><b>GUI-ceiling clamping.</b> When projecting, strength tier and range
 * are clamped to the target emitter's per-block GUI ceilings (the same limits
 * {@link EmitterMenu}'s +/- buttons honour). Over-ceiling presets land at
 * the ceiling instead of being rejected. Polarity has no ceiling.
 *
 * <p><b>Cross-type projection.</b> The captured {@code sourceBlockId} is
 * tooltip-only — the imprint happily projects an Electromagnet preset onto
 * a Tractor Beam if asked. Mismatches are intentional and surfaced via the
 * tooltip's source-emitter line.
 */
public final class ImprintModuleItem extends Item {

    public ImprintModuleItem(final Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                   final InteractionHand hand) {
        // Right-click in air: clear the preset if there is one. Lets the
        // player re-capture from a different source without needing a fresh
        // imprint module. No-op (passes through) when already empty.
        final ItemStack stack = player.getItemInHand(hand);
        final EmitterPreset preset = stack.get(MagDataComponents.EMITTER_PRESET.get());
        if (preset == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.remove(MagDataComponents.EMITTER_PRESET.get());
            level.playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.6f, 1.2f);
            player.displayClientMessage(
                    Component.translatable("imprint.magnetization.cleared")
                            .withStyle(ChatFormatting.GRAY),
                    true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                 final List<Component> lines, final TooltipFlag flag) {
        final @Nullable EmitterPreset preset = stack.get(MagDataComponents.EMITTER_PRESET.get());
        if (preset == null) {
            lines.add(Component.translatable("imprint.magnetization.tooltip.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("imprint.magnetization.tooltip.empty_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // Resolve the source block id back to its display name so the tooltip
        // reads "Holds preset for: Electromagnet" instead of the raw
        // "magnetization:electromagnet". If the block was removed/renamed
        // since capture (mod uninstall, registry rename), fall back to the
        // raw id so the player can still tell what it was.
        final Component sourceName = BuiltInRegistries.BLOCK.getOptional(preset.sourceBlockId())
                .<Component>map(Block::getName)
                .orElseGet(() -> Component.literal(preset.sourceBlockId().toString()));
        lines.add(((MutableComponent) Component.translatable("imprint.magnetization.tooltip.for_emitter", sourceName))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("imprint.magnetization.tooltip.strength",
                        preset.strength().name())
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("imprint.magnetization.tooltip.polarity",
                        preset.polarity().name())
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("imprint.magnetization.tooltip.range", preset.range())
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("imprint.magnetization.tooltip.apply_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("imprint.magnetization.tooltip.clear_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
