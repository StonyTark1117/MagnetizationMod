package com.stonytark.magnetization.content.item;

import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.registry.MagDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Polarity-lock tool. The polarizer carries a target polarity (NORTH or SOUTH);
 * sneak-right-clicking an emitter forces that emitter's polarity to match
 * the polarizer, even in the presence of adjacent Polarity Inverters. The
 * polarizer itself is reusable — installing a lock doesn't consume the item.
 *
 * <p><b>Controls</b>
 * <ul>
 *   <li><b>Sneak + right-click an emitter</b> → install a polarity lock matching the polarizer's current polarity, or remove an existing lock if one is present.</li>
 *   <li><b>Right-click in air</b> → toggle the polarizer between NORTH and SOUTH.</li>
 * </ul>
 *
 * <p>The lock install/remove path lives in
 * {@link HematiteLensInteraction} (an event subscriber) — emitter blocks
 * override {@code useItemOn} for their own interactions (rotate, polarity
 * flip, GUI open) and consume the click before {@code Item.useOn} can
 * fire, so the lens has to pre-empt them from {@code RightClickBlock}.
 *
 * <p>Storage: the polarizer's target polarity lives on the item via the
 * existing {@link MagDataComponents#ARMOR_POLARITY} component (re-used so
 * we don't register a one-off type for the same payload).
 */
public final class HematiteLensItem extends Item {

    public HematiteLensItem(final Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                   final InteractionHand hand) {
        // In-air right-click toggles the polarizer's stored polarity. Sneak
        // is intentionally not required here — sneak is reserved for the
        // emitter lock gesture, which goes through HematiteLensInteraction
        // and only fires when targeting a block.
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            final MagneticPolarity next = lensPolarity(stack).opposite();
            stack.set(MagDataComponents.ARMOR_POLARITY.get(), next);
            player.displayClientMessage(
                    Component.translatable("hematite_lens.magnetization.toggled", next.name())
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static MagneticPolarity lensPolarity(final ItemStack stack) {
        final MagneticPolarity stored = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        return stored != null ? stored : MagneticPolarity.NORTH;
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext ctx,
                                 final List<Component> lines, final TooltipFlag flag) {
        final @Nullable MagneticPolarity polarity = stack.get(MagDataComponents.ARMOR_POLARITY.get());
        final MagneticPolarity active = polarity != null ? polarity : MagneticPolarity.NORTH;
        lines.add(Component.translatable("hematite_lens.magnetization.tooltip.polarity", active.name())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("hematite_lens.magnetization.tooltip.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

}
