package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class MagBlockTagsProvider extends BlockTagsProvider {

    public MagBlockTagsProvider(
            final PackOutput output,
            final CompletableFuture<HolderLookup.Provider> lookupProvider,
            final ExistingFileHelper existingFileHelper
    ) {
        super(output, lookupProvider, Magnetization.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(final HolderLookup.Provider provider) {
        tag(MagTags.MAGNETIC_EMITTER_BLOCKS)
                .add(MagBlocks.ELECTROMAGNET.get())
                .add(MagBlocks.KINETIC_ELECTROMAGNET.get())
                .add(MagBlocks.MAGNETIC_ANCHOR.get())
                .add(MagBlocks.REPULSOR_COIL.get())
                .add(MagBlocks.TRACTOR_BEAM.get());

        // Pickaxe-mineable: every block in this addon. requiresCorrectToolForDrops()
        // is set on all of them, so without this tag they would not drop.
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(MagBlocks.ELECTROMAGNET.get())
                .add(MagBlocks.KINETIC_ELECTROMAGNET.get())
                .add(MagBlocks.MAGNETIC_ANCHOR.get())
                .add(MagBlocks.REPULSOR_COIL.get())
                .add(MagBlocks.TRACTOR_BEAM.get())
                .add(MagBlocks.LODESTONE_CORE.get())
                .add(MagBlocks.MAGNETIC_SWITCH.get())
                .add(MagBlocks.PERMANENT_MAGNET.get())
                .add(MagBlocks.POLARITY_INVERTER.get())
                .add(MagBlocks.MAGNETITE_ORE.get())
                .add(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get())
                .add(MagBlocks.MAGNETITE_BLOCK.get())
                .add(MagBlocks.RAW_MAGNETITE_BLOCK.get())
                // Iron-oxide family
                .add(MagBlocks.MAGHEMITE_ORE.get()).add(MagBlocks.DEEPSLATE_MAGHEMITE_ORE.get())
                .add(MagBlocks.MAGHEMITE_BLOCK.get()).add(MagBlocks.RAW_MAGHEMITE_BLOCK.get())
                .add(MagBlocks.PYRRHOTITE_ORE.get()).add(MagBlocks.DEEPSLATE_PYRRHOTITE_ORE.get())
                .add(MagBlocks.PYRRHOTITE_BLOCK.get()).add(MagBlocks.RAW_PYRRHOTITE_BLOCK.get())
                .add(MagBlocks.HEMATITE_ORE.get()).add(MagBlocks.DEEPSLATE_HEMATITE_ORE.get())
                .add(MagBlocks.HEMATITE_BLOCK.get()).add(MagBlocks.RAW_HEMATITE_BLOCK.get())
                .add(MagBlocks.TITANOMAGNETITE_ORE.get()).add(MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE.get())
                .add(MagBlocks.TITANOMAGNETITE_BLOCK.get()).add(MagBlocks.RAW_TITANOMAGNETITE_BLOCK.get());

        // Stone tier mirrors vanilla iron_ore (raw drops without iron-tier tool).
        tag(BlockTags.NEEDS_STONE_TOOL)
                .add(MagBlocks.MAGNETITE_ORE.get())
                .add(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get())
                .add(MagBlocks.RAW_MAGNETITE_BLOCK.get())
                .add(MagBlocks.MAGHEMITE_ORE.get()).add(MagBlocks.DEEPSLATE_MAGHEMITE_ORE.get()).add(MagBlocks.RAW_MAGHEMITE_BLOCK.get())
                .add(MagBlocks.PYRRHOTITE_ORE.get()).add(MagBlocks.DEEPSLATE_PYRRHOTITE_ORE.get()).add(MagBlocks.RAW_PYRRHOTITE_BLOCK.get())
                .add(MagBlocks.HEMATITE_ORE.get()).add(MagBlocks.DEEPSLATE_HEMATITE_ORE.get()).add(MagBlocks.RAW_HEMATITE_BLOCK.get());

        // Iron tier mirrors vanilla iron_block. Titanomagnetite is rare-and-deep so
        // it sits at iron-tier; the storage blocks for the other oxides match.
        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(MagBlocks.MAGNETITE_BLOCK.get())
                .add(MagBlocks.MAGHEMITE_BLOCK.get())
                .add(MagBlocks.PYRRHOTITE_BLOCK.get())
                .add(MagBlocks.HEMATITE_BLOCK.get())
                .add(MagBlocks.TITANOMAGNETITE_ORE.get())
                .add(MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE.get())
                .add(MagBlocks.TITANOMAGNETITE_BLOCK.get())
                .add(MagBlocks.RAW_TITANOMAGNETITE_BLOCK.get())
                .add(MagBlocks.METEORITE_CORE.get())
                .add(MagBlocks.PYRRHOTITE_CATALYST.get())
                .add(MagBlocks.ENHANCED_PYRRHOTITE_CATALYST.get())
                .add(MagBlocks.COSMIC_PYRRHOTITE_CATALYST.get());
    }
}
