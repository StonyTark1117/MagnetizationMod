package com.stonytark.magnetization.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Handheld gas survey instrument; its detailed reading is rendered client-side. */
public final class GasDetectorItem extends Item {
    public GasDetectorItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(final Level level, final Player player,
                                                    final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            final GasDetectorScanner.Reading reading = GasDetectorScanner.nearest(level, player.blockPosition());
            if (!reading.found()) {
                player.displayClientMessage(Component.translatable("message.magnetization.gas_detector.none"), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.magnetization.gas_detector.reading",
                        new net.neoforged.neoforge.fluids.FluidStack(reading.fluid(), 1).getHoverName(),
                        Component.translatable("message.magnetization.gas_detector.status." + reading.statusKey()),
                        String.format("%.1f", reading.distance()),
                        reading.dangerous() ? Component.translatable("message.magnetization.gas_detector.danger") : ""), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
