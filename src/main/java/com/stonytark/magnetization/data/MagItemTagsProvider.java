package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class MagItemTagsProvider extends ItemTagsProvider {

    public MagItemTagsProvider(
            final PackOutput output,
            final CompletableFuture<HolderLookup.Provider> lookupProvider,
            final CompletableFuture<TagsProvider.TagLookup<Block>> blockTagLookup,
            final ExistingFileHelper existingFileHelper
    ) {
        super(output, lookupProvider, blockTagLookup, Magnetization.MOD_ID, existingFileHelper);
    }

    /** Forge "common" tag namespace — surfaces our items to other addon mods. */
    private static TagKey<Item> common(final String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    @Override
    protected void addTags(final HolderLookup.Provider provider) {
        // Cross-mod Magnetization tags remain hand-written in main resources;
        // this provider owns only common material tags. Generating both copies
        // makes sourcesJar ambiguous and can silently discard optional entries.
        tag(common("plates/samarium_cobalt")).add(MagItems.SAMARIUM_COBALT_PLATE.get());
        tag(common("plates/neodymium_alloy")).add(MagItems.NEODYMIUM_ALLOY_PLATE.get());
        tag(common("dusts")).add(MagItems.BORON_DUST.get()).add(MagItems.NEODYMIUM_POWDER.get())
                .add(MagItems.DYSPROSIUM_POWDER.get()).add(MagItems.SAMARIUM_POWDER.get())
                .add(MagItems.COBALT_POWDER.get());
        tag(common("dusts/boron")).add(MagItems.BORON_DUST.get());
        tag(common("dusts/neodymium")).add(MagItems.NEODYMIUM_POWDER.get());
        tag(common("dusts/dysprosium")).add(MagItems.DYSPROSIUM_POWDER.get());
        tag(common("dusts/samarium")).add(MagItems.SAMARIUM_POWDER.get());
        tag(common("dusts/cobalt")).add(MagItems.COBALT_POWDER.get());
        tag(common("oxides")).add(MagItems.NEODYMIUM_OXIDE.get()).add(MagItems.DYSPROSIUM_OXIDE.get())
                .add(MagItems.SAMARIUM_OXIDE.get());
        tag(common("oxides/neodymium")).add(MagItems.NEODYMIUM_OXIDE.get());
        tag(common("oxides/dysprosium")).add(MagItems.DYSPROSIUM_OXIDE.get());
        tag(common("oxides/samarium")).add(MagItems.SAMARIUM_OXIDE.get());
        tag(common("concentrates")).add(MagItems.BASTNASITE_CONCENTRATE.get())
                .add(MagItems.MONAZITE_CONCENTRATE.get()).add(MagItems.COBALTITE_CONCENTRATE.get());
        tag(common("concentrates/bastnasite")).add(MagItems.BASTNASITE_CONCENTRATE.get());
        tag(common("concentrates/monazite")).add(MagItems.MONAZITE_CONCENTRATE.get());
        tag(common("concentrates/cobaltite")).add(MagItems.COBALTITE_CONCENTRATE.get());
        tag(common("ingots/neodymium")).add(MagItems.NEODYMIUM_INGOT.get());
        tag(common("ingots/samarium")).add(MagItems.SAMARIUM_INGOT.get());
        tag(common("ingots/cobalt")).add(MagItems.COBALT_INGOT.get());
        tag(common("ingots/samarium_cobalt")).add(MagItems.SAMARIUM_COBALT_ALLOY.get());
        tag(common("ingots/neodymium_alloy")).add(MagItems.NEODYMIUM_ALLOY.get());
        tag(common("ores/bastnasite")).add(MagItems.BASTNASITE_ORE.get()).add(MagItems.DEEPSLATE_BASTNASITE_ORE.get());
        tag(common("ores/monazite")).add(MagItems.MONAZITE_ORE.get()).add(MagItems.DEEPSLATE_MONAZITE_ORE.get());
        tag(common("ores/cobaltite")).add(MagItems.COBALTITE_ORE.get()).add(MagItems.DEEPSLATE_COBALTITE_ORE.get());
        tag(common("ores/borax")).add(MagItems.BORAX_ORE.get()).add(MagItems.DEEPSLATE_BORAX_ORE.get());
    }
}
