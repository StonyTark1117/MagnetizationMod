package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Armor material for magnetite gear. Defense values are iron-equivalent;
 * toughness and knockback resistance are zero (no diamond/netherite-tier perks).
 * Layer texture is {@code magnetization:textures/models/armor/magnetite_layer_{1,2}.png}.
 */
public final class MagArmorMaterials {

    public static final DeferredRegister<ArmorMaterial> REGISTER =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Magnetization.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAGNETITE =
            REGISTER.register("magnetite", () -> {
                final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                // Iron-equivalent: helmet 2, chestplate 6, leggings 5, boots 2.
                defense.put(ArmorItem.Type.HELMET, 2);
                defense.put(ArmorItem.Type.CHESTPLATE, 6);
                defense.put(ArmorItem.Type.LEGGINGS, 5);
                defense.put(ArmorItem.Type.BOOTS, 2);
                defense.put(ArmorItem.Type.BODY, 5);
                return new ArmorMaterial(
                        defense,
                        14,                                   // enchantmentValue (iron 9; magnetite 14 — slightly better than iron)
                        Holder.direct(SoundEvents.ARMOR_EQUIP_IRON.value()),
                        () -> Ingredient.of(MagItems.MAGNETITE_INGOT.get()),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "magnetite"),
                                "",
                                false)),
                        0.0f,                                 // toughness (iron 0)
                        0.0f                                  // knockbackResistance
                );
            });

    /** Magnetic Cushion Boots material — iron-equivalent like magnetite, but with
     *  its own BLUE worn layer texture so the boots match the Magnetic Cushion
     *  block instead of reusing the grey magnetite layer. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAGNETIC_CUSHION =
            REGISTER.register("magnetic_cushion", () -> {
                final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                defense.put(ArmorItem.Type.HELMET, 2);
                defense.put(ArmorItem.Type.CHESTPLATE, 6);
                defense.put(ArmorItem.Type.LEGGINGS, 5);
                defense.put(ArmorItem.Type.BOOTS, 2);
                defense.put(ArmorItem.Type.BODY, 5);
                return new ArmorMaterial(
                        defense,
                        14,
                        Holder.direct(SoundEvents.ARMOR_EQUIP_IRON.value()),
                        () -> Ingredient.of(MagItems.MAGNETITE_INGOT.get()),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "magnetic_cushion"),
                                "",
                                false)),
                        0.0f,
                        0.0f
                );
            });

    public static Holder<ArmorMaterial> magneticCushion() {
        return MAGNETIC_CUSHION;
    }

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAGHEMITE =
            REGISTER.register("maghemite", () -> {
                final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                // Weaker than iron (the oxide structure flakes under impact):
                // helmet 1, chestplate 4, leggings 3, boots 1; body 3 (lower than magnetite's 5).
                defense.put(ArmorItem.Type.HELMET, 1);
                defense.put(ArmorItem.Type.CHESTPLATE, 4);
                defense.put(ArmorItem.Type.LEGGINGS, 3);
                defense.put(ArmorItem.Type.BOOTS, 1);
                defense.put(ArmorItem.Type.BODY, 3);
                return new ArmorMaterial(
                        defense,
                        12,                                   // enchantmentValue (slight bump over iron's 9)
                        Holder.direct(SoundEvents.ARMOR_EQUIP_IRON.value()),
                        () -> Ingredient.of(MagItems.MAGHEMITE_INGOT.get()),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "maghemite"),
                                "",
                                false)),
                        0.0f,
                        0.0f
                );
            });

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> FERROMAGNETIC =
            REGISTER.register("ferromagnetic", () -> {
                final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                // Diamond-equivalent: helmet 3, chestplate 8, leggings 6, boots 3.
                defense.put(ArmorItem.Type.HELMET, 3);
                defense.put(ArmorItem.Type.CHESTPLATE, 8);
                defense.put(ArmorItem.Type.LEGGINGS, 6);
                defense.put(ArmorItem.Type.BOOTS, 3);
                defense.put(ArmorItem.Type.BODY, 11);        // diamond_horse_armor parity
                return new ArmorMaterial(
                        defense,
                        18,                                   // enchantmentValue (diamond 10, magnetite 14)
                        Holder.direct(SoundEvents.ARMOR_EQUIP_IRON.value()),
                        () -> Ingredient.of(MagItems.FERROMAGNETIC_INGOT.get()),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "ferromagnetic"),
                                "",
                                false)),
                        2.0f,                                 // toughness (diamond 2)
                        0.0f                                  // knockbackResistance
                );
            });

    /** Magnetorheological liquid armor — leather-light defense; its real protection
     *  is the on-hit hardening handled by {@code MrArmorHandler}, not base armor points. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MR_LIQUID =
            REGISTER.register("mr_liquid", () -> {
                final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                // Leather-equivalent: helmet 1, chestplate 3, leggings 2, boots 1.
                defense.put(ArmorItem.Type.HELMET, 1);
                defense.put(ArmorItem.Type.CHESTPLATE, 3);
                defense.put(ArmorItem.Type.LEGGINGS, 2);
                defense.put(ArmorItem.Type.BOOTS, 1);
                defense.put(ArmorItem.Type.BODY, 3);
                return new ArmorMaterial(
                        defense,
                        12,
                        Holder.direct(SoundEvents.ARMOR_EQUIP_LEATHER.value()),
                        () -> Ingredient.of(MagItems.MR_FLUID_BUCKET.get()),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "mr_liquid"),
                                "",
                                false)),
                        0.0f,
                        0.0f
                );
            });

    /** Gallium armor — gold-like but worse (soft metal): a touch less defense than
     *  gold and the same low durability handled by the tier/durability factor. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> GALLIUM =
            REGISTER.register("gallium", () -> {
                final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                // Slightly below gold (helmet 2, chest 5, legs 3, boots 1, body 7).
                defense.put(ArmorItem.Type.HELMET, 1);
                defense.put(ArmorItem.Type.CHESTPLATE, 4);
                defense.put(ArmorItem.Type.LEGGINGS, 3);
                defense.put(ArmorItem.Type.BOOTS, 1);
                defense.put(ArmorItem.Type.BODY, 6);
                return new ArmorMaterial(
                        defense,
                        22,                                   // gold-tier enchantability
                        Holder.direct(SoundEvents.ARMOR_EQUIP_GOLD.value()),
                        () -> Ingredient.of(MagItems.GALLIUM_INGOT.get()),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, "gallium"),
                                "",
                                false)),
                        0.0f,
                        0.0f
                );
            });

    /** Lightweight lithium armor: leather-level protection, very high
     *  enchantability, and deliberately low durability on the item registrations. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> LITHIUM =
            REGISTER.register("lithium", () -> material(
                    1, 3, 2, 1, 3, 24,
                    SoundEvents.ARMOR_EQUIP_GOLD,
                    () -> Ingredient.of(MagItems.LITHIUM.get()),
                    "lithium", 0.0f, 0.0f));

    /** Pyrrhotite is an iron-tier sulfide: protective, but less stable than refined iron. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> PYRRHOTITE =
            REGISTER.register("pyrrhotite", () -> material(
                    2, 5, 4, 2, 5, 12,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(MagItems.PYRRHOTITE_INGOT.get()),
                    "pyrrhotite", 0.0f, 0.0f));

    /** Dense hematite is slightly more durable than the other common oxides. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HEMATITE =
            REGISTER.register("hematite", () -> material(
                    2, 6, 5, 2, 5, 9,
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(MagItems.HEMATITE_INGOT.get()),
                    "hematite", 0.0f, 0.0f));

    /** High-tier titanomagnetite armor: diamond-like defense with a little more
     *  toughness and a small amount of knockback resistance. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> TITANOMAGNETITE =
            REGISTER.register("titanomagnetite", () -> material(
                    3, 8, 6, 3, 11, 12,
                    SoundEvents.ARMOR_EQUIP_DIAMOND,
                    () -> Ingredient.of(MagItems.TITANOMAGNETITE_INGOT.get()),
                    "titanomagnetite", 2.5f, 0.05f));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SAMARIUM_COBALT =
            REGISTER.register("samarium_cobalt", () -> material(
                    3, 8, 6, 3, 11, 16,
                    SoundEvents.ARMOR_EQUIP_DIAMOND,
                    () -> Ingredient.of(MagItems.SAMARIUM_COBALT_ALLOY.get()),
                    "titanomagnetite", 3.0f, 0.10f));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> NEODYMIUM =
            REGISTER.register("neodymium", () -> material(
                    3, 9, 7, 3, 12, 20,
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(MagItems.NEODYMIUM_ALLOY.get()),
                    "ferromagnetic", 3.5f, 0.15f));

    private static ArmorMaterial material(final int helmet, final int chestplate,
                                           final int leggings, final int boots,
                                           final int body, final int enchantment,
                                           final net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound,
                                           final java.util.function.Supplier<Ingredient> repair,
                                           final String texture, final float toughness,
                                           final float knockbackResistance) {
        final Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, helmet);
        defense.put(ArmorItem.Type.CHESTPLATE, chestplate);
        defense.put(ArmorItem.Type.LEGGINGS, leggings);
        defense.put(ArmorItem.Type.BOOTS, boots);
        defense.put(ArmorItem.Type.BODY, body);
        return new ArmorMaterial(
                defense,
                enchantment,
                sound,
                repair,
                List.of(new ArmorMaterial.Layer(
                        ResourceLocation.fromNamespaceAndPath(Magnetization.MOD_ID, texture),
                        "", false)),
                toughness,
                knockbackResistance);
    }

    public static Holder<ArmorMaterial> gallium() {
        return GALLIUM;
    }

    public static Holder<ArmorMaterial> lithium() { return LITHIUM; }

    public static Holder<ArmorMaterial> pyrrhotite() { return PYRRHOTITE; }

    public static Holder<ArmorMaterial> hematite() { return HEMATITE; }

    public static Holder<ArmorMaterial> titanomagnetite() { return TITANOMAGNETITE; }

    public static Holder<ArmorMaterial> samariumCobalt() { return SAMARIUM_COBALT; }

    public static Holder<ArmorMaterial> neodymium() { return NEODYMIUM; }

    public static Holder<ArmorMaterial> mrLiquid() {
        return MR_LIQUID;
    }

    public static Holder<ArmorMaterial> magnetite() {
        return MAGNETITE;
    }

    public static Holder<ArmorMaterial> maghemite() {
        return MAGHEMITE;
    }

    public static Holder<ArmorMaterial> ferromagnetic() {
        return FERROMAGNETIC;
    }

    private MagArmorMaterials() {}
}
