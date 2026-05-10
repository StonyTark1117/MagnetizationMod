package com.stonytark.magnetization.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces the human-readable lines that describe a field's current state. Used
 * by every tooltip surface in the addon: goggles, in-world hover, Jade overlay,
 * even the Field Compass action-bar message.
 *
 * <p>Concentrating the formatting here keeps phrasing and color conventions
 * consistent across surfaces. Adding a new tooltip line means editing one
 * method, not four.
 */
public final class FieldTooltipFormatter {

    private FieldTooltipFormatter() {}

    /**
     * @param field current field, or {@code null} for the off case.
     * @param verbose when true, includes shape and range; when false, returns
     *                a single tier+polarity line.
     * @return mutable list of components — caller may add/remove freely.
     */
    public static List<Component> format(final @Nullable MagneticField field, final boolean verbose) {
        final List<Component> out = new ArrayList<>();
        if (field == null) {
            out.add(Component.translatable("tooltip.magnetization.inactive")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return out;
        }
        final ChatFormatting polarityColor = field.polarity().sign() > 0
                ? ChatFormatting.RED : ChatFormatting.AQUA;
        out.add(Component.literal(field.strength().name() + " " + field.polarity().name())
                .withStyle(polarityColor));
        if (verbose) {
            out.add(Component.literal(String.format("Range: %.1f blocks", field.range()))
                    .withStyle(ChatFormatting.GRAY));
            out.add(Component.literal("Shape: " + field.shape().name())
                    .withStyle(ChatFormatting.GRAY));
        }
        return out;
    }
}
