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
            FLUIDS.register("ferrofluid", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FERROFLUID_FLOWING =
            FLUIDS.register("flowing_ferrofluid", () -> new BaseFlowingFluid.Flowing(properties()));

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
            FLUIDS.register("magnetized_ferrofluid", () -> new BaseFlowingFluid.Source(magnetizedProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> MAGNETIZED_FERROFLUID_FLOWING =
            FLUIDS.register("flowing_magnetized_ferrofluid", () -> new BaseFlowingFluid.Flowing(magnetizedProperties()));

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
            FLUIDS.register("mr_fluid", () -> new BaseFlowingFluid.Source(mrProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> MR_FLUID_FLOWING =
            FLUIDS.register("flowing_mr_fluid", () -> new BaseFlowingFluid.Flowing(mrProperties()));

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

    private MagFluids() {}
}
