package com.stonytark.magnetization.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldTooltipFormatterTest {

    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 AXIS = new Vec3(0, 1, 0);

    @Test
    void nullFieldNonVerboseRendersInactiveLine() {
        final List<Component> lines = FieldTooltipFormatter.format(null, false);
        assertEquals(1, lines.size());
        assertTrue(isInactive(lines.get(0)),
                "expected inactive translation key, got " + textOf(lines.get(0)));
    }

    @Test
    void nullFieldVerboseRendersInactiveLine() {
        final List<Component> lines = FieldTooltipFormatter.format(null, true);
        assertEquals(1, lines.size());
        assertTrue(isInactive(lines.get(0)),
                "expected inactive translation key, got " + textOf(lines.get(0)));
    }

    @Test
    void activeNonVerboseMentionsStrengthAndPolarity() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        final List<Component> lines = FieldTooltipFormatter.format(field, false);
        assertTrue(lines.size() >= 1);
        final String first = textOf(lines.get(0));
        assertTrue(first.contains("MEDIUM"), "expected strength tier in line: " + first);
        assertTrue(first.contains("NORTH"), "expected polarity in line: " + first);
    }

    @Test
    void verboseProducesMoreLinesThanNonVerbose() {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.SOUTH,
                MagneticStrength.STRONG, MagneticField.Shape.DIRECTIONAL);
        final List<Component> terse = FieldTooltipFormatter.format(field, false);
        final List<Component> verbose = FieldTooltipFormatter.format(field, true);
        assertTrue(verbose.size() > terse.size(),
                "verbose=" + verbose.size() + " terse=" + terse.size());
    }

    @Test
    void northAndSouthRenderDistinctLines() {
        final MagneticField north = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        final MagneticField south = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.SOUTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        final String northText = textOf(FieldTooltipFormatter.format(north, false).get(0));
        final String southText = textOf(FieldTooltipFormatter.format(south, false).get(0));
        assertNotEquals(northText, southText);
    }

    @Test
    void weakStrengthRendersDistinctly() {
        assertTierTextContains(MagneticStrength.WEAK, "WEAK");
    }

    @Test
    void mediumStrengthRendersDistinctly() {
        assertTierTextContains(MagneticStrength.MEDIUM, "MEDIUM");
    }

    @Test
    void strongStrengthRendersDistinctly() {
        assertTierTextContains(MagneticStrength.STRONG, "STRONG");
    }

    @Test
    void extremeStrengthRendersDistinctly() {
        assertTierTextContains(MagneticStrength.EXTREME, "EXTREME");
    }

    @Test
    void allFourTiersProduceDistinctTopLines() {
        final String w = topLineFor(MagneticStrength.WEAK);
        final String m = topLineFor(MagneticStrength.MEDIUM);
        final String s = topLineFor(MagneticStrength.STRONG);
        final String e = topLineFor(MagneticStrength.EXTREME);
        assertEquals(4, java.util.Set.of(w, m, s, e).size(),
                "expected 4 distinct tier lines, got " + java.util.List.of(w, m, s, e));
    }

    @Test
    void customRangeChangesVerboseRangeLine() {
        final MagneticField defaultRange = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
        final MagneticField overridden = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH,
                MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL, 12.5d);

        final String defaultText = joinedText(FieldTooltipFormatter.format(defaultRange, true));
        final String customText = joinedText(FieldTooltipFormatter.format(overridden, true));
        assertNotEquals(defaultText, customText);
        assertTrue(customText.contains("12.5"),
                "expected custom range value in verbose lines: " + customText);
    }

    private static void assertTierTextContains(final MagneticStrength tier, final String expected) {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH, tier, MagneticField.Shape.OMNIDIRECTIONAL);
        final String text = textOf(FieldTooltipFormatter.format(field, false).get(0));
        assertTrue(text.contains(expected), "expected '" + expected + "' in: " + text);
    }

    private static String topLineFor(final MagneticStrength tier) {
        final MagneticField field = new MagneticField(
                ORIGIN, AXIS, MagneticPolarity.NORTH, tier, MagneticField.Shape.OMNIDIRECTIONAL);
        return textOf(FieldTooltipFormatter.format(field, false).get(0));
    }

    private static String joinedText(final List<Component> lines) {
        final StringBuilder sb = new StringBuilder();
        for (final Component c : lines) sb.append(textOf(c)).append('\n');
        return sb.toString();
    }

    private static String textOf(final Component c) {
        if (c.getContents() instanceof PlainTextContents p) return p.text();
        if (c.getContents() instanceof TranslatableContents t) return t.getKey();
        return c.getString();
    }

    private static boolean isInactive(final Component c) {
        return c.getContents() instanceof TranslatableContents t
                && "tooltip.magnetization.inactive".equals(t.getKey());
    }
}
