package com.stonytark.magnetization.content.golem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Authoritative Gallium Golem lines shared by every optional HUD surface. */
public final class GalliumGolemHud {
    public static List<Component> lines(final GalliumGolem golem) {
        if (!golem.featureEnabled()) {
            return List.of(Component.translatable("tooltip.magnetization.golem.disabled")
                    .withStyle(ChatFormatting.RED));
        }
        final boolean cold = golem.level().getBiome(golem.blockPosition()).value().getBaseTemperature() < 0.2f;
        return List.of(
                Component.translatable("tooltip.magnetization.gallium_golem.soft")
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable(cold
                                ? "tooltip.magnetization.gallium_golem.cold"
                                : "tooltip.magnetization.gallium_golem.warm")
                        .withStyle(cold ? ChatFormatting.AQUA : ChatFormatting.GOLD),
                Component.translatable("tooltip.magnetization.golem.repair",
                                new net.minecraft.world.item.ItemStack(
                                        com.stonytark.magnetization.registry.MagItems.GALLIUM_INGOT.get())
                                        .getHoverName())
                        .withStyle(ChatFormatting.GREEN));
    }

    private GalliumGolemHud() {}
}
