package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class MagLootTableProvider {

    private MagLootTableProvider() {}

    public static LootTableProvider create(final PackOutput output, final java.util.concurrent.CompletableFuture<HolderLookup.Provider> lookup) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                lookup
        );
    }

    /** Drops-self for every block we register. */
    public static final class BlockLoot extends BlockLootSubProvider {

        protected BlockLoot(final HolderLookup.Provider provider) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
        }

        @Override
        protected void generate() {
            dropSelf(MagBlocks.ELECTROMAGNET.get());
            dropSelf(MagBlocks.KINETIC_ELECTROMAGNET.get());
            dropSelf(MagBlocks.MAGNETIC_ANCHOR.get());
            dropSelf(MagBlocks.REPULSOR_COIL.get());
            dropSelf(MagBlocks.TRACTOR_BEAM.get());
            dropSelf(MagBlocks.LODESTONE_CORE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Stream.of(
                    MagBlocks.ELECTROMAGNET, MagBlocks.KINETIC_ELECTROMAGNET, MagBlocks.MAGNETIC_ANCHOR,
                    MagBlocks.REPULSOR_COIL, MagBlocks.TRACTOR_BEAM,
                    MagBlocks.LODESTONE_CORE
            ).map(holder -> (Block) holder.get())::iterator;
        }
    }

    /** Provided for the Magnetization datagen entry point as a no-arg static. */
    public static String modId() { return Magnetization.MOD_ID; }
}
