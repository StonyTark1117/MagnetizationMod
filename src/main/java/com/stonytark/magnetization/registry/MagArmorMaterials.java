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

    public static Holder<ArmorMaterial> magnetite() {
        return MAGNETITE;
    }

    private MagArmorMaterials() {}
}
