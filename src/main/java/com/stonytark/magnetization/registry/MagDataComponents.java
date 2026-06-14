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

    /** Game-tick at which a magnetite item first entered inventory observation.
     *  Drives {@code MaghemiteDecayHandler} — when {@code (now - stamped) >=
     *  MaghemiteDecayHandler.DECAY_TICKS} the stack converts in place into its
     *  maghemite equivalent (raw_magnetite → raw_maghemite, magnetite_ingot →
     *  maghemite_ingot), mirroring real-world oxidation of γ-Fe2O3 to α-Fe2O3.
     *  Stays absent on freshly-mined items until the first inventory sweep
     *  sees them. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>>
            MAGNETITE_OXIDATION_AGE = register("magnetite_oxidation_age",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Game-tick at which a gun (Repulsor Gun / Magnetic Grapple) was last
     *  fired. Drives the client-side {@code fired} item-model property that
     *  swaps in the glowing-muzzle model variant for a few ticks after a shot.
     *  Network-synced so the holding client sees the muzzle light up. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>>
            FIRED_AT = register("fired_at",
                    builder -> builder
                            .persistent(Codec.LONG)
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Captured {@link com.stonytark.magnetization.api.EmitterPreset} carried
     *  by a Titanomagnetite Imprint Module item — shift-right-click an emitter
     *  to overwrite, right-click another emitter to project. Persistent so a
     *  recorded preset survives chunk/world reload + inventory transfers. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.stonytark.magnetization.api.EmitterPreset>>
            EMITTER_PRESET = register("emitter_preset",
                    builder -> builder
                            .persistent(com.stonytark.magnetization.api.EmitterPreset.CODEC)
                            .networkSynchronized(com.stonytark.magnetization.api.EmitterPreset.STREAM_CODEC));

    /** Recorded field NBT carried by a dropped Titanomagnetite Block item so a
     *  player can mine a captured titanomagnetite and re-place it elsewhere
     *  without losing the imprint. Written by the block's loot table via
     *  {@code minecraft:copy_components}; read back by
     *  {@code TitanomagnetiteBlockEntity.applyImplicitComponents}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>>
            RECORDED_FIELD = register("recorded_field",
                    builder -> builder
                            .persistent(net.minecraft.nbt.CompoundTag.CODEC)
                            .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            final String name,
            final UnaryOperator<DataComponentType.Builder<T>> builderOp
    ) {
        return REGISTER.register(name, () -> builderOp.apply(DataComponentType.<T>builder()).build());
    }

    private MagDataComponents() {}
}
