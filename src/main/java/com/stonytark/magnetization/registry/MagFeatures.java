package com.stonytark.magnetization.registry;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.worldgen.MeteoriteCenterMarkerProcessor;
import com.stonytark.magnetization.worldgen.MeteoriteCraterFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom world-generation entries. Currently hosts:
 * <ul>
 *   <li>{@link #METEORITE_CRATER} — the procedural crater carver fired at
 *       worldgen time (and from {@code /magnetization spawn_crater}).</li>
 *   <li>{@link #METEORITE_CENTER_MARKER_PROC} — StructureProcessor used by
 *       future NBT-templated craters to promote a structure_void marker into
 *       a runtime meteorite_core.</li>
 * </ul>
 */
public final class MagFeatures {

    public static final DeferredRegister<Feature<?>> REGISTER =
            DeferredRegister.create(Registries.FEATURE, Magnetization.MOD_ID);

    public static final DeferredHolder<Feature<?>, MeteoriteCraterFeature> METEORITE_CRATER =
            REGISTER.register("meteorite_crater",
                    () -> new MeteoriteCraterFeature(NoneFeatureConfiguration.CODEC));

    // ── StructureProcessorTypes — separate registry, separate DeferredRegister ──
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSOR_REGISTER =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Magnetization.MOD_ID);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<MeteoriteCenterMarkerProcessor>>
            METEORITE_CENTER_MARKER_PROC = PROCESSOR_REGISTER.register("meteorite_center_marker",
                    () -> () -> MeteoriteCenterMarkerProcessor.CODEC);

    private MagFeatures() {}
}
