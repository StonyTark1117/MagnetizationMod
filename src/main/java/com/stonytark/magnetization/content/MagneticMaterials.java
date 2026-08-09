package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Shared "how magnetic is this material" ladder for the magnet-slot machines
 * (Homopolar Motor + MHD Jet). A material's <em>potency</em> rises with both the
 * ore TYPE and the processing FORM:
 *
 * <ul>
 *   <li>form: ore &lt; raw item &lt; raw block &lt; ingot &lt; storage block</li>
 *   <li>type: hematite &lt; magnetite &lt; maghemite &lt; pyrrhotite &lt; titanomagnetite</li>
 * </ul>
 *
 * The motor turns potency into RPM + stress capacity; the MHD jet turns it into
 * the speed ceiling + FE draw. Returns {@code 0} for anything that isn't an
 * accepted magnetic material.
 *
 * <p>Resolved off the item's registry path so it automatically covers every
 * registered form/ore without enumerating each {@code DeferredItem}. The numbers
 * are intentionally simple/tunable — adjust the bases + form bonuses here and the
 * whole ladder shifts everywhere it's used.
 */
public final class MagneticMaterials {

    private MagneticMaterials() {}

    /**
     * {ore-type name, base potency}, ordered strongest-first so the substring
     * match resolves "titanomagnetite" before the "magnetite" it contains.
     * Bases are spaced 6 apart so the 0..4 form bonus never lets a weaker ore's
     * best form beat a stronger ore's worst form.
     */
    private static final String[][] MATERIALS = {
            {"titanomagnetite", "25"},
            {"maghemite", "13"},
            {"magnetite", "7"},
            {"pyrrhotite", "19"},
            {"hematite", "1"},
    };

    /** Crafted magnetic materials that aren't part of an ore's raw→block chain.
     *  Magnetic plate = a basic fabricated magnet; ferromagnetic ingot = an
     *  iron+magnetite alloy, a step above plain magnetite. */
    private static final java.util.Map<String, Integer> SPECIALS = java.util.Map.of(
            "magnetic_plate", 10,
            "ferromagnetic_ingot", 16);

    /** Potency of the stack, or 0 if it isn't an accepted magnetic material. */
    public static int potency(final ItemStack stack) {
        if (stack.isEmpty()) return 0;
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return 0;
        if (!id.getNamespace().equals(Magnetization.MOD_ID)) {
            return MagConfig.externalMachineMagnetsEnabled()
                    && stack.is(MagTags.MACHINE_MAGNETS)
                    ? MagConfig.externalMachineMagnetPotency()
                    : 0;
        }
        final String path = id.getPath();
        final Integer special = SPECIALS.get(path);
        if (special != null) return special;
        for (final String[] material : MATERIALS) {
            final String name = material[0];
            if (!path.contains(name)) continue;
            final int form = formBonus(path, name);
            return form < 0 ? 0 : Integer.parseInt(material[1]) + form;
        }
        return 0;
    }

    /** True if this item can drive a magnet-slot machine. */
    public static boolean isMagnet(final ItemStack stack) {
        return potency(stack) > 0;
    }

    /** True if the stack is a BLOCK form (storage block or raw block) — it holds ~9
     *  units of material, so it burns far longer in a magnet-slot machine. */
    public static boolean isBlockForm(final ItemStack stack) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals(Magnetization.MOD_ID) && id.getPath().endsWith("_block");
    }

    /**
     * How many ACTIVE ticks one of this magnet lasts in a magnet-slot machine
     * (motor / MHD jet) when fuel consumption is enabled. Strength and longevity
     * compound: a stronger magnet burns longer (scales with potency), and block
     * forms burn ~9× longer (quantity). 0 if not a magnet.
     *
     * <p>{@code base + potency × perPotency × formFactor}, where formFactor is the
     * block-form multiplier for storage/raw blocks and 1 otherwise. Defaults
     * (1200 / 400 / 9) put a titanomagnetite storage block at ~88 min and a bare
     * ore at ~80 s — see the {@code progress_1_3_build} balance ladder.
     */
    public static int magnetBurnTicks(final ItemStack stack) {
        final int potency = potency(stack);
        if (potency <= 0) return 0;
        final int formFactor = isBlockForm(stack)
                ? com.stonytark.magnetization.config.MagConfig.magnetBurnBlockFormMultiplier() : 1;
        return com.stonytark.magnetization.config.MagConfig.magnetBurnTicksBase()
                + potency * com.stonytark.magnetization.config.MagConfig.magnetBurnTicksPerPotency() * formFactor;
    }

    /** ore = 0, raw item = 1, raw block = 2, ingot = 3, storage block = 4;
     *  -1 for any other form of the material (tools, armour, anvils, …). */
    private static int formBonus(final String path, final String m) {
        if (path.equals(m + "_ingot")) return 3;
        if (path.equals(m + "_block")) return 4;
        if (path.equals("raw_" + m + "_block")) return 2;
        if (path.equals("raw_" + m)) return 1;
        if (path.equals(m + "_ore") || path.equals("deepslate_" + m + "_ore")) return 0;
        return -1;
    }
}
