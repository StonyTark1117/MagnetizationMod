package com.stonytark.magnetization.content;

import com.stonytark.magnetization.Magnetization;
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
        if (id == null || !id.getNamespace().equals(Magnetization.MOD_ID)) return 0;
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
