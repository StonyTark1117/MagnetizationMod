package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

/**
 * Generates the vanilla mining tool-tags for every block this addon registers:
 * {@code mineable/pickaxe|shovel|axe} (what tool breaks it fast) and
 * {@code needs_stone_tool|needs_iron_tool} (the harvest tier). All of our blocks
 * set {@code requiresCorrectToolForDrops()}, so a block missing from the right
 * mineable tag silently drops nothing and shows no "correct tool" tick in
 * Jade/WTHIT — which is exactly the bug that hit hematite when the hand-written
 * pickaxe JSON was overwritten during the 1.2 anvil work.
 *
 * <p>To make that class of regression impossible, the {@code mineable} tags are
 * derived <em>automatically</em> from the block registry rather than hand-listed:
 * every registered block is pickaxe-mineable unless it is a fluid (skipped), the
 * meteorite sapling (skipped), magnetic gravel (shovel) or petrified wood (axe).
 * Any future block is therefore picked up without a manual edit.
 *
 * <p>Only the self-contained vanilla tool tags are generated here. The
 * cross-mod {@code magnetization:*} block tags (magnetic_emitter,
 * ferromagnetic_blocks, metallic_ores, …) stay hand-written because they carry
 * other-mod IDs the registry can't know about. The harvest-tier tags
 * ({@code needs_*_tool}) are a balance choice, so they remain explicit.
 */
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
        final var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        final var shovel = tag(BlockTags.MINEABLE_WITH_SHOVEL);
        final var axe = tag(BlockTags.MINEABLE_WITH_AXE);

        for (final var entry : MagBlocks.REGISTER.getEntries()) {
            final Block block = entry.get();

            // Fluids carry no mining tag.
            if (block instanceof LiquidBlock) {
                continue;
            }
            // The meteorite sapling germinates / pops instantly — no tool tag.
            if (block == MagBlocks.METEORITE_SAPLING.get()) {
                continue;
            }
            if (block == MagBlocks.MAGNETIC_GRAVEL.get()) {
                shovel.add(block);
            } else if (block == MagBlocks.PETRIFIED_WOOD.get()) {
                // Ferrous "wood": the Magnetized Axe is its signature tool.
                axe.add(block);
            } else {
                pickaxe.add(block);
            }
        }

        // Stone tier mirrors vanilla iron_ore (raw drops with a stone-or-better tool).
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
