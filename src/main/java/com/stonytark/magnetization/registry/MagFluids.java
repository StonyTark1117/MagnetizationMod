package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Ferrofluid — a black, water-like magnetic liquid. Generates only in the
 * Anomaly biome (replacing water); otherwise player-made. It reacts to magnetic
 * fields (it doesn't push on its own unless a player polarizes a portion).
 *
 * <p>Registration relies on {@code FLUID} being declared before {@code BLOCK}/
 * {@code ITEM} in {@code BuiltInRegistries}, so the fluids exist by the time the
 * liquid block + bucket factories resolve {@link #FERROFLUID}{@code .get()}.
 */
public final class MagFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Magnetization.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Magnetization.MOD_ID);

    /** Heavier + more viscous than water — it's a metallic colloid. */
    public static final Supplier<FluidType> FERROFLUID_TYPE = FLUID_TYPES.register("ferrofluid",
            () -> new FluidType(FluidType.Properties.create()
                    .density(2400)
                    .viscosity(2400)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FERROFLUID =
            FLUIDS.register("ferrofluid", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FERROFLUID_FLOWING =
            FLUIDS.register("flowing_ferrofluid", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(FERROFLUID_TYPE, FERROFLUID, FERROFLUID_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.FERROFLUID_BLOCK)
                .bucket(MagItems.FERROFLUID_BUCKET);
    }

    // ---------------- Magnetized ferrofluid (player-polarized, field source) ----------------
    // Reuses FERROFLUID_TYPE for rendering/physics — it looks like ferrofluid; the
    // difference is the POLARITY blockstate + the field it emits.

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MAGNETIZED_FERROFLUID =
            FLUIDS.register("magnetized_ferrofluid", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Source(magnetizedProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> MAGNETIZED_FERROFLUID_FLOWING =
            FLUIDS.register("flowing_magnetized_ferrofluid", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Flowing(magnetizedProperties()));

    private static BaseFlowingFluid.Properties magnetizedProperties() {
        return new BaseFlowingFluid.Properties(FERROFLUID_TYPE, MAGNETIZED_FERROFLUID, MAGNETIZED_FERROFLUID_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK)
                .bucket(MagItems.FERROFLUID_BUCKET);
    }

    // ---------------- Magnetorheological fluid ----------------

    public static final Supplier<FluidType> MR_FLUID_TYPE = FLUID_TYPES.register("mr_fluid",
            () -> new FluidType(FluidType.Properties.create()
                    .density(3000)
                    .viscosity(3000)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MR_FLUID =
            FLUIDS.register("mr_fluid", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Source(mrProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> MR_FLUID_FLOWING =
            FLUIDS.register("flowing_mr_fluid", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Flowing(mrProperties()));

    private static BaseFlowingFluid.Properties mrProperties() {
        return new BaseFlowingFluid.Properties(MR_FLUID_TYPE, MR_FLUID, MR_FLUID_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.MR_FLUID_BLOCK)
                .bucket(MagItems.MR_FLUID_BUCKET);
    }

    // ---------------- Deuterium oxide (heavy water) ----------------
    // Functionally identical to water — just a darker blue. Fuel for the
    // Deuterium Fuel Cell.

    public static final Supplier<FluidType> DEUTERIUM_OXIDE_TYPE = FLUID_TYPES.register("deuterium_oxide",
            () -> new FluidType(FluidType.Properties.create()
                    .density(1100)          // a touch denser than water (it IS heavy water)
                    .viscosity(1000)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> DEUTERIUM_OXIDE =
            FLUIDS.register("deuterium_oxide", () -> new BaseFlowingFluid.Source(deuteriumProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> DEUTERIUM_OXIDE_FLOWING =
            FLUIDS.register("flowing_deuterium_oxide", () -> new BaseFlowingFluid.Flowing(deuteriumProperties()));

    private static BaseFlowingFluid.Properties deuteriumProperties() {
        return new BaseFlowingFluid.Properties(DEUTERIUM_OXIDE_TYPE, DEUTERIUM_OXIDE, DEUTERIUM_OXIDE_FLOWING)
                .slopeFindDistance(4)        // spreads like water
                .levelDecreasePerBlock(1)
                .block(MagBlocks.DEUTERIUM_OXIDE_BLOCK)
                .bucket(MagItems.DEUTERIUM_OXIDE_BUCKET);
    }

    // ---------------- Gallium (Lorentz liquid metal) ----------------
    // A soft, dense, silvery liquid metal. Conducts redstone; in a field + powered
    // it pushes entities along a Lorentz current (GalliumLorentzHandler). Freezes
    // solid next to a cooling source, melts back otherwise (GalliumBlock).

    public static final Supplier<FluidType> GALLIUM_TYPE = FLUID_TYPES.register("gallium",
            () -> new FluidType(FluidType.Properties.create()
                    .density(6000)          // gallium is a heavy metal
                    .viscosity(1500)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(true)  // boats float on it so the current can carry them
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> GALLIUM =
            FLUIDS.register("gallium", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Source(galliumProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> GALLIUM_FLOWING =
            FLUIDS.register("flowing_gallium", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Flowing(galliumProperties()));

    private static BaseFlowingFluid.Properties galliumProperties() {
        return new BaseFlowingFluid.Properties(GALLIUM_TYPE, GALLIUM, GALLIUM_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.GALLIUM_BLOCK)
                .bucket(MagItems.GALLIUM_BUCKET);
    }

    // ---------------- Mixed gallium (ferrofluid-like, no Lorentz) ----------------
    // Gallium with magnetite/iron stirred in: acts like plain ferrofluid (creeps to
    // magnets), no Lorentz current. Coloured between gallium and ferrofluid.

    public static final Supplier<FluidType> MIXED_GALLIUM_TYPE = FLUID_TYPES.register("mixed_gallium",
            () -> new FluidType(FluidType.Properties.create()
                    .density(4000)
                    .viscosity(2200)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> MIXED_GALLIUM =
            FLUIDS.register("mixed_gallium", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Source(mixedGalliumProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> MIXED_GALLIUM_FLOWING =
            FLUIDS.register("flowing_mixed_gallium", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Flowing(mixedGalliumProperties()));

    private static BaseFlowingFluid.Properties mixedGalliumProperties() {
        return new BaseFlowingFluid.Properties(MIXED_GALLIUM_TYPE, MIXED_GALLIUM, MIXED_GALLIUM_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.MIXED_GALLIUM_BLOCK)
                .bucket(MagItems.MIXED_GALLIUM_BUCKET);
    }

    // ---------------- Hydrogen (fusion-fuel isotope chain entry point) ----------------
    // Light gas-as-liquid; the cheap starter propellant + the parent of deuterium.
    // Produced by electrolysing water in the Electrolyzer.

    public static final Supplier<FluidType> HYDROGEN_TYPE = FLUID_TYPES.register("hydrogen",
            () -> new FluidType(FluidType.Properties.create()
                    .density(70)            // liquid hydrogen is very light
                    .viscosity(150)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HYDROGEN =
            FLUIDS.register("hydrogen", () -> new BaseFlowingFluid.Source(hydrogenProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HYDROGEN_FLOWING =
            FLUIDS.register("flowing_hydrogen", () -> new BaseFlowingFluid.Flowing(hydrogenProperties()));

    private static BaseFlowingFluid.Properties hydrogenProperties() {
        return new BaseFlowingFluid.Properties(HYDROGEN_TYPE, HYDROGEN, HYDROGEN_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.HYDROGEN_BLOCK)
                .bucket(MagItems.HYDROGEN_BUCKET);
    }

    // ---------------- Tritium (D-T fusion fuel; bred from lithium) ----------------

    public static final Supplier<FluidType> TRITIUM_TYPE = FLUID_TYPES.register("tritium",
            () -> new FluidType(FluidType.Properties.create()
                    .density(250)
                    .viscosity(200)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TRITIUM =
            FLUIDS.register("tritium", () -> new BaseFlowingFluid.Source(tritiumProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> TRITIUM_FLOWING =
            FLUIDS.register("flowing_tritium", () -> new BaseFlowingFluid.Flowing(tritiumProperties()));

    private static BaseFlowingFluid.Properties tritiumProperties() {
        return new BaseFlowingFluid.Properties(TRITIUM_TYPE, TRITIUM, TRITIUM_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.TRITIUM_BLOCK)
                .bucket(MagItems.TRITIUM_BUCKET);
    }

    // ---------------- Helium-3 (premium aneutronic D-He3 fuel; rare) ----------------

    public static final Supplier<FluidType> HELIUM_3_TYPE = FLUID_TYPES.register("helium_3",
            () -> new FluidType(FluidType.Properties.create()
                    .density(60)            // even lighter than hydrogen
                    .viscosity(120)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> HELIUM_3 =
            FLUIDS.register("helium_3", () -> new BaseFlowingFluid.Source(helium3Properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> HELIUM_3_FLOWING =
            FLUIDS.register("flowing_helium_3", () -> new BaseFlowingFluid.Flowing(helium3Properties()));

    private static BaseFlowingFluid.Properties helium3Properties() {
        return new BaseFlowingFluid.Properties(HELIUM_3_TYPE, HELIUM_3, HELIUM_3_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.HELIUM_3_BLOCK)
                .bucket(MagItems.HELIUM_3_BUCKET);
    }

    // ---------------- Liquid lithium (conductive working fluid for the MHD jet) ----------------
    // The best real MPD propellant; also the tritium-breeding feedstock. Melted from
    // the Lithium item. Lithium is lighter than water (~0.53 g/cc).

    public static final Supplier<FluidType> LIQUID_LITHIUM_TYPE = FLUID_TYPES.register("liquid_lithium",
            () -> new FluidType(FluidType.Properties.create()
                    .density(530)
                    .viscosity(1100)
                    .canSwim(true)
                    .canDrown(true)
                    .supportsBoating(true)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> LIQUID_LITHIUM =
            FLUIDS.register("liquid_lithium", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Source(liquidLithiumProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> LIQUID_LITHIUM_FLOWING =
            FLUIDS.register("flowing_liquid_lithium", () -> new com.stonytark.magnetization.content.fluid.RedstoneSafeFluid.Flowing(liquidLithiumProperties()));

    private static BaseFlowingFluid.Properties liquidLithiumProperties() {
        return new BaseFlowingFluid.Properties(LIQUID_LITHIUM_TYPE, LIQUID_LITHIUM, LIQUID_LITHIUM_FLOWING)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .block(MagBlocks.LIQUID_LITHIUM_BLOCK)
                .bucket(MagItems.LIQUID_LITHIUM_BUCKET);
    }

    private MagFluids() {}
}
