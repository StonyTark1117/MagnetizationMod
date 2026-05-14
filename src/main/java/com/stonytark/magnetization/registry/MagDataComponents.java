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

    /** Game-tick at which a Lightning-Induced Remnant Magnetism stamp was applied.
     *  Presence flags the item as temporarily polarized — strength decays linearly
     *  from full at the strike to zero after {@code Lirm.DURATION_TICKS}, after
     *  which the LIRM_CREATED_AT component and the paired ARMOR_POLARITY are
     *  cleaned up by {@code LirmDecayHandler}. Absent = polarity is permanent
     *  (electromagnet-stamped) or absent. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>>
            LIRM_CREATED_AT = register("lirm_created_at",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            final String name,
            final UnaryOperator<DataComponentType.Builder<T>> builderOp
    ) {
        return REGISTER.register(name, () -> builderOp.apply(DataComponentType.<T>builder()).build());
    }

    private MagDataComponents() {}
}
