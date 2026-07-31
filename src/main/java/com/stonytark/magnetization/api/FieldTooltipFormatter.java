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
        // Accessibility: pole and strength must be legible without relying on the
        // red/aqua hue. A distinct SHAPE glyph per pole (▲ north / ▼ south) and a
        // filled/hollow PIP meter for the strength tier give a second, hue-independent
        // channel alongside the color and the spelled-out words.
        final boolean north = field.polarity().sign() > 0;
        final ChatFormatting polarityColor = north ? ChatFormatting.RED : ChatFormatting.AQUA;
        out.add(Component.literal(polarityGlyph(field.polarity()) + " "
                        + field.strength().name() + " " + field.polarity().name()
                        + "  " + strengthPips(field.strength()))
                .withStyle(polarityColor));
        if (verbose) {
            out.add(Component.literal(String.format("Range: %.1f blocks", field.range()))
                    .withStyle(ChatFormatting.GRAY));
            out.add(Component.literal("Shape: " + field.shape().name())
                    .withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    /** Hue-independent SHAPE marker for a pole: an up-triangle for NORTH, a
     *  down-triangle for SOUTH, a hollow diamond for NONE. Distinct silhouettes so
     *  the pole reads even in grayscale / for red-cyan color-vision deficiency.
     *  Public so other polarity surfaces (the ferrofluid HUD, compass) reuse it. */
    public static String polarityGlyph(final MagneticPolarity polarity) {
        return switch (polarity) {
            case NORTH -> "▲";
            case SOUTH -> "▼";
            case NONE -> "◇";
        };
    }

    /** Filled/hollow pip meter for a strength tier — WEAK ●○○○ … EXTREME ●●●●.
     *  A shape/length channel for strength that doesn't depend on the text color. */
    public static String strengthPips(final MagneticStrength strength) {
        final int filled = strength.ordinal() + 1;          // WEAK=1 … EXTREME=4
        final int total = MagneticStrength.values().length; // 4
        return "●".repeat(filled) + "○".repeat(Math.max(0, total - filled)); // ● / ○
    }
}
