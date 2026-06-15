package com.stonytark.magnetization.registry;

import com.mojang.serialization.MapCodec;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.content.loot.GalliumOreDropModifier;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Global loot modifier serializers (currently the gallium ore byproduct). */
public final class MagLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Magnetization.MOD_ID);

    public static final java.util.function.Supplier<MapCodec<GalliumOreDropModifier>> GALLIUM_BYPRODUCT =
            REGISTER.register("gallium_byproduct", () -> GalliumOreDropModifier.CODEC);

    private MagLootModifiers() {}
}
