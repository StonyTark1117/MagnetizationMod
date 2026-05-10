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
                .add(MagBlocks.RAW_MAGNETITE_BLOCK.get());

        // Stone tier mirrors vanilla iron_ore (raw drops without iron-tier tool).
        tag(BlockTags.NEEDS_STONE_TOOL)
                .add(MagBlocks.MAGNETITE_ORE.get())
                .add(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get())
                .add(MagBlocks.RAW_MAGNETITE_BLOCK.get());

        // Iron tier mirrors vanilla iron_block.
        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(MagBlocks.MAGNETITE_BLOCK.get());
    }
}
