package com.stonytark.magnetization.registry;

import com.mojang.serialization.Codec;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticPolarity;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

/**
 * Custom DataComponentTypes attached to ItemStacks. 1.21 deprecates the old
 * NBT route; component types are how we persist per-stack state now.
 */
public final class MagDataComponents {

    public static final DeferredRegister<DataComponentType<?>> REGISTER =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Magnetization.MOD_ID);

    /** Polarity stamped onto a piece of ferromagnetic armor by an electromagnet.
     *  Absent = unmagnetized; present = the stored polarity is what the wearer
     *  presents to nearby fields. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MagneticPolarity>>
            ARMOR_POLARITY = register("armor_polarity",
                    builder -> builder
                            .persistent(MagneticPolarity.CODEC)
                            .networkSynchronized(MagneticPolarity.STREAM_CODEC));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            final String name,
            final UnaryOperator<DataComponentType.Builder<T>> builderOp
    ) {
        return REGISTER.register(name, () -> builderOp.apply(DataComponentType.<T>builder()).build());
    }

    private MagDataComponents() {}
}
