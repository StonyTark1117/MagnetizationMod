package com.stonytark.magnetization.content;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for "which fuel is this, and how good is it?".
 *
 * <p>The 1.3 fusion chain has five fluids and three cells whose inventory icons are
 * close cousins of one another, so identity has to come from something other than
 * the sprite. Two channels are derived from here:
 *
 * <ul>
 *   <li>a compact isotope <b>badge</b> (H / D / T / He³ / Li) drawn over the item icon
 *       by {@code MagFuelBadges} — text, so it survives colour-vision differences and
 *       reads identically in the inventory, JEI, REI and EMI (all three render item
 *       decorations through the vanilla item-decoration hook);</li>
 *   <li><b>relative thrust / efficiency</b> tooltip lines appended by
 *       {@code MagItemTooltips}, computed live from the same {@link MagConfig} knobs
 *       the machines read, so a retuned pack shows its own numbers rather than the
 *       shipped defaults.</li>
 * </ul>
 *
 * <p>Both fusion-thruster figures are quoted <i>relative to Deuterium Oxide</i>, the
 * middle-of-the-ladder fuel a player meets first: thrust is the fluid's force
 * multiplier over D₂O's, and runtime is its density over D₂O's (density divides the
 * per-tick drain in {@code FusionThrusterBlockEntity}, so a denser fluid runs
 * proportionally longer on one tank).
 */
public final class FusionFuelInfo {

    /** Isotope/element marking drawn over the item icon. */
    public record Badge(String text, int argb) {}

    private FusionFuelInfo() {}

    // Palette — chosen to stay legible on both the dark bucket sprites and the pale
    // cell sprites. Colour is the *secondary* channel; the letters are the primary one.
    private static final int HYDROGEN_COLOR  = 0xFF9FD8FF; // pale blue
    private static final int DEUTERIUM_COLOR = 0xFF6EA8FF; // mid blue
    private static final int TRITIUM_COLOR   = 0xFF7CFF9E; // green (radioluminescent)
    private static final int HELIUM3_COLOR   = 0xFFC9A7FF; // luminous lavender, matching the crystal palette
    private static final int LITHIUM_COLOR   = 0xFFE0E0E8; // silver
    private static final int GALLIUM_COLOR   = 0xFF8FB4D9; // blue-grey

    private static @Nullable Map<Item, Badge> badges;

    private static Map<Item, Badge> buildBadges() {
        final Map<Item, Badge> m = new IdentityHashMap<>();
        final Badge h  = new Badge("H",   HYDROGEN_COLOR);
        final Badge d  = new Badge("D",   DEUTERIUM_COLOR);
        final Badge t  = new Badge("T",   TRITIUM_COLOR);
        final Badge he = new Badge("He³", HELIUM3_COLOR);
        final Badge li = new Badge("Li",  LITHIUM_COLOR);

        m.put(MagItems.HYDROGEN_BUCKET.get(), h);
        m.put(MagItems.DEUTERIUM_OXIDE_BUCKET.get(), d);
        m.put(MagItems.TRITIUM_BUCKET.get(), t);
        m.put(MagItems.HELIUM_3_BUCKET.get(), he);
        m.put(MagItems.LIQUID_LITHIUM_BUCKET.get(), li);

        m.put(MagItems.DEUTERIUM_CELL.get(), d);
        m.put(MagItems.TRITIUM_CELL.get(), t);
        m.put(MagItems.HELIUM_3_CELL.get(), he);

        m.put(MagItems.HELIUM_3_CRYSTAL.get(), he);
        m.put(MagItems.LITHIUM.get(), li);
        m.put(MagItems.RAW_LITHIUM.get(), li);

        // Gallium shares the MHD working-fluid role with liquid lithium, and Raw Gallium
        // ships the same sprite as Lithium / Raw Lithium — so it needs the mark most.
        final Badge ga = new Badge("Ga", GALLIUM_COLOR);
        m.put(MagItems.GALLIUM_BUCKET.get(), ga);
        m.put(MagItems.MIXED_GALLIUM_BUCKET.get(), new Badge("Ga+", GALLIUM_COLOR));
        m.put(MagItems.RAW_GALLIUM.get(), ga);
        m.put(MagItems.GALLIUM_INGOT.get(), ga);
        return m;
    }

    /** @return the isotope badge for this item, or {@code null} if it isn't a fuel. */
    public static @Nullable Badge badge(final Item item) {
        return allBadges().get(item);
    }

    /** Every badged fuel item — the client badge renderer registers one decorator per entry. */
    public static Map<Item, Badge> allBadges() {
        if (badges == null) badges = buildBadges();
        return badges;
    }

    /**
     * Append the fuel's role-specific performance lines to a tooltip. No-op for items
     * that aren't machine fuels (the plain feedstocks get a badge but no numbers —
     * they don't burn in anything themselves).
     */
    public static void appendPerformance(final ItemStack stack, final List<Component> out) {
        final Item item = stack.getItem();
        // Cheap gate: tooltips re-render every frame, and everything with numbers below
        // also has a badge, so one map lookup rejects the other ~1,500 items outright.
        if (!allBadges().containsKey(item)) return;

        // ── Fusion Thruster propellants ────────────────────────────────────────────
        final Double thrust = thrusterThrustMult(item);
        if (thrust != null) {
            final double runtime = thrusterRuntimeMult(item);
            out.add(Component.translatable("tooltip.magnetization.fuel.thruster",
                    fmt(thrust), fmt(runtime)).withStyle(ChatFormatting.DARK_AQUA));
            if (item == MagItems.DEUTERIUM_OXIDE_BUCKET.get()) {
                out.add(Component.translatable("tooltip.magnetization.fuel.baseline")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        // ── MHD Jet working fluids ─────────────────────────────────────────────────
        final Double conductivity = mhdConductivity(item);
        if (conductivity != null) {
            out.add(Component.translatable("tooltip.magnetization.fuel.mhd",
                    fmt(conductivity)).withStyle(ChatFormatting.DARK_AQUA));
        }

        // ── Tokamak cells ──────────────────────────────────────────────────────────
        final int tier = cellTier(item);
        if (tier >= 0) {
            final int gen = tokamakGenPerTick(tier);
            final int burn = tokamakBurnTicks(tier);
            final long total = (long) gen * burn;
            out.add(Component.translatable("tooltip.magnetization.fuel.tokamak",
                    String.format(Locale.ROOT, "%,d", gen),
                    String.format(Locale.ROOT, "%,d", burn / 20))
                    .withStyle(ChatFormatting.DARK_AQUA));
            out.add(Component.translatable("tooltip.magnetization.fuel.tokamak_total",
                    compactFe(total), fmt(total / (double) baseCellTotal()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ---------------- per-role lookups (mirror the block entities) ----------------

    /** Thrust multiplier relative to Deuterium Oxide, or {@code null} if not a thruster fuel. */
    private static @Nullable Double thrusterThrustMult(final Item item) {
        final double base = MagConfig.fusionThrusterFluidMultDeuteriumOxide();
        if (base <= 0.0) return null;   // pack zeroed the baseline — no meaningful ratio
        if (item == MagItems.HYDROGEN_BUCKET.get())        return MagConfig.fusionThrusterFluidMultHydrogen() / base;
        if (item == MagItems.DEUTERIUM_OXIDE_BUCKET.get()) return 1.0;
        if (item == MagItems.TRITIUM_BUCKET.get())         return MagConfig.fusionThrusterFluidMultTritium() / base;
        if (item == MagItems.HELIUM_3_BUCKET.get())        return MagConfig.fusionThrusterFluidMultHelium3() / base;
        return null;
    }

    /** Tank-runtime multiplier relative to Deuterium Oxide (density divides the drain). */
    private static double thrusterRuntimeMult(final Item item) {
        final double base = MagConfig.fusionThrusterFluidDensityDeuteriumOxide();
        if (item == MagItems.HYDROGEN_BUCKET.get()) return MagConfig.fusionThrusterFluidDensityHydrogen() / base;
        if (item == MagItems.TRITIUM_BUCKET.get())  return MagConfig.fusionThrusterFluidDensityTritium() / base;
        if (item == MagItems.HELIUM_3_BUCKET.get()) return MagConfig.fusionThrusterFluidDensityHelium3() / base;
        return 1.0;
    }

    private static @Nullable Double mhdConductivity(final Item item) {
        if (item == MagItems.GALLIUM_BUCKET.get())        return MagConfig.mhdConductivityGallium();
        if (item == MagItems.MIXED_GALLIUM_BUCKET.get())  return MagConfig.mhdConductivityMixedGallium();
        if (item == MagItems.LIQUID_LITHIUM_BUCKET.get()) return MagConfig.mhdConductivityLiquidLithium();
        return null;
    }

    /** 0 = Deuterium, 1 = Tritium, 2 = Helium-3, -1 = not a tokamak cell. */
    private static int cellTier(final Item item) {
        if (item == MagItems.DEUTERIUM_CELL.get()) return 0;
        if (item == MagItems.TRITIUM_CELL.get())   return 1;
        if (item == MagItems.HELIUM_3_CELL.get())  return 2;
        return -1;
    }

    private static int tokamakGenPerTick(final int tier) {
        return switch (tier) {
            case 1 -> MagConfig.tokamakGenPerTickTritium();
            case 2 -> MagConfig.tokamakGenPerTickHelium3();
            default -> MagConfig.tokamakGenPerTick();
        };
    }

    private static int tokamakBurnTicks(final int tier) {
        return switch (tier) {
            case 1 -> MagConfig.tokamakBurnTicksTritium();
            case 2 -> MagConfig.tokamakBurnTicksHelium3();
            default -> MagConfig.tokamakBurnTicksPerCell();
        };
    }

    /** Total FE in a Deuterium cell — the "×1.0" the other two cells are quoted against. */
    private static long baseCellTotal() {
        return Math.max(1L, (long) MagConfig.tokamakGenPerTick() * MagConfig.tokamakBurnTicksPerCell());
    }

    // ---------------- formatting ----------------

    /** "×1.6"-style number: one decimal, but drop it when the value is a whole number. */
    private static String fmt(final double v) {
        return Math.abs(v - Math.rint(v)) < 0.05
                ? String.format(Locale.ROOT, "%.0f", v)
                : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String compactFe(final long fe) {
        if (fe >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", fe / 1_000_000.0);
        if (fe >= 1_000L)     return String.format(Locale.ROOT, "%.0fk", fe / 1_000.0);
        return String.format(Locale.ROOT, "%d", fe);
    }
}
