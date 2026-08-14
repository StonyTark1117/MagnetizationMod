package com.stonytark.magnetization.content.golem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One authoritative set of live entity HUD lines for WTHIT and Jade. */
public final class MagneticGolemHud {
    public static List<Component> lines(final MagneticGolem golem) {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.magnetization.golem.polarity",
                golem.magneticPolarity().name()).withStyle(ChatFormatting.LIGHT_PURPLE));
        final var field = golem.displayedField();
        lines.add(Component.translatable("tooltip.magnetization.golem.field",
                field == null ? "NONE" : field.strength().name()).withStyle(ChatFormatting.DARK_AQUA));
        if (golem instanceof MagnetiteGolem magnetite) {
            lines.add(Component.translatable(magnetite.isOxidized()
                    ? "tooltip.magnetization.golem.magnetite.oxidized"
                    : "tooltip.magnetization.golem.magnetite.fresh").withStyle(ChatFormatting.GRAY));
        } else if (golem instanceof PyrrhotiteGolem pyrrhotite) {
            lines.add(Component.translatable("tooltip.magnetization.golem.pyrrhotite.heat",
                    pyrrhotite.observedHeat().name()).withStyle(ChatFormatting.GOLD));
        } else if (golem instanceof HematiteGolem hematite) {
            lines.add(Component.translatable("tooltip.magnetization.golem.hematite.dampening",
                            hematite.dampenedSourceCount()).withStyle(ChatFormatting.DARK_RED));
        } else if (golem instanceof TitanomagnetiteGolem titan) {
            final var recorded = titan.recordedField();
            lines.add(recorded == null
                    ? Component.translatable("tooltip.magnetization.golem.titanomagnetite.empty")
                            .withStyle(ChatFormatting.AQUA)
                    : Component.translatable("tooltip.magnetization.golem.titanomagnetite.charged",
                            recorded.strength().name(), recorded.shape().name(),
                            axisName(recorded.axis())).withStyle(ChatFormatting.AQUA));
        }
        lines.add(Component.translatable(golem.ownerUuid() == null
                ? "tooltip.magnetization.golem.protection.ownerless"
                : "tooltip.magnetization.golem.protection.owned").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static String axisName(final net.minecraft.world.phys.Vec3 axis) {
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", axis.x, axis.y, axis.z);
    }

    private MagneticGolemHud() {}
}
