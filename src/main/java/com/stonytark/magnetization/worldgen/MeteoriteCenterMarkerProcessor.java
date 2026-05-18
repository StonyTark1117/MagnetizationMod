package com.stonytark.magnetization.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * StructureProcessor used by future NBT-templated meteorite craters. Walks
 * every block in the template at placement time; whenever it sees a
 * {@code minecraft:structure_void} block it swaps it for a freshly-charged
 * {@code magnetization:meteorite_core}. Template authors mark the intended
 * core position with one structure_void in MCEdit / Worldedit; this processor
 * promotes the marker into the runtime block without the template having to
 * embed our mod's block id (so the templates remain editable without our mod
 * present).
 *
 * <p>Why STRUCTURE_VOID rather than a custom marker block: vanilla already
 * treats structure_void as "do not place" at the template level, but
 * processors run before the placement check, so this hook lets us repurpose
 * the marker without registering a new block. Keeps the data side minimal.
 *
 * <p>Registered in {@link com.stonytark.magnetization.registry.MagFeatures}.
 * Codec is a record codec with no fields — the processor has no tunables yet
 * (charge override, polarity override, etc.) but the record-codec shape makes
 * adding them a one-line change.
 */
public final class MeteoriteCenterMarkerProcessor extends StructureProcessor {

    public static final MeteoriteCenterMarkerProcessor INSTANCE = new MeteoriteCenterMarkerProcessor();

    public static final MapCodec<MeteoriteCenterMarkerProcessor> CODEC =
            RecordCodecBuilder.mapCodec(inst -> inst.point(INSTANCE));

    private MeteoriteCenterMarkerProcessor() {}

    @Override
    public @Nullable StructureTemplate.StructureBlockInfo process(
            final LevelReader level,
            final BlockPos seedPos,
            final BlockPos pieceOrigin,
            final StructureTemplate.StructureBlockInfo rawInfo,
            final StructureTemplate.StructureBlockInfo currentInfo,
            final StructurePlaceSettings settings,
            final @Nullable StructureTemplate template) {
        if (!currentInfo.state().is(Blocks.STRUCTURE_VOID)) return currentInfo;
        return new StructureTemplate.StructureBlockInfo(
                currentInfo.pos(),
                MagBlocks.METEORITE_CORE.get().defaultBlockState(),
                currentInfo.nbt());
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return com.stonytark.magnetization.registry.MagFeatures.METEORITE_CENTER_MARKER_PROC.get();
    }
}
