package com.stonytark.magnetization.registry;

import com.mojang.serialization.MapCodec;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.data.CompatConfigCondition;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Condition codecs used by config-gated compatibility datapack entries. */
public final class MagConditions {
    public static final DeferredRegister<MapCodec<? extends ICondition>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Magnetization.MOD_ID);

    public static final Supplier<MapCodec<CompatConfigCondition>> COMPAT_CONFIG =
            REGISTER.register("compat_config", () -> CompatConfigCondition.CODEC);

    private MagConditions() {}
}
