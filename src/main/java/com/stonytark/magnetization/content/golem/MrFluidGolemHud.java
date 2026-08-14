package com.stonytark.magnetization.content.golem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared live MR-fluid state lines for WTHIT and Jade. */
public final class MrFluidGolemHud {
    public static List<Component> lines(final MrFluidGolem golem) {
        if (!golem.featureEnabled()) {
            return List.of(Component.translatable("tooltip.magnetization.golem.disabled")
                    .withStyle(ChatFormatting.RED));
        }
        final int mitigation = Math.round(golem.currentMitigation() * 100.0f);
        return List.of(Component.translatable(golem.isHardened()
                        ? "tooltip.magnetization.golem.mr_fluid.hardened"
                        : "tooltip.magnetization.golem.mr_fluid.fluid", mitigation)
                .withStyle(golem.isHardened() ? ChatFormatting.AQUA : ChatFormatting.GRAY));
    }

    private MrFluidGolemHud() {}
}
