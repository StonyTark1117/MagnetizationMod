package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
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
        tag(MagTags.FERROMAGNETIC_ITEMS)
                .add(MagItems.FERROMAGNETIC_INGOT.get())
                .add(MagItems.MAGNETIC_PLATE.get())
                .add(MagItems.LODESTONE_CORE.get())
                .add(MagItems.MAGNETITE_INGOT.get())
                .add(MagItems.RAW_MAGNETITE.get())
                .add(MagItems.MAGNETITE_BLOCK.get())
                .add(MagItems.RAW_MAGNETITE_BLOCK.get())
                // Iron-oxide family — all ferromagnetic (even hematite ingot,
                // which acts as a polarity modifier rather than a pure source).
                .add(MagItems.MAGHEMITE_INGOT.get()).add(MagItems.RAW_MAGHEMITE.get())
                .add(MagItems.MAGHEMITE_BLOCK.get()).add(MagItems.RAW_MAGHEMITE_BLOCK.get())
                .add(MagItems.PYRRHOTITE_INGOT.get()).add(MagItems.RAW_PYRRHOTITE.get())
                .add(MagItems.PYRRHOTITE_BLOCK.get()).add(MagItems.RAW_PYRRHOTITE_BLOCK.get())
                .add(MagItems.HEMATITE_INGOT.get()).add(MagItems.RAW_HEMATITE.get())
                .add(MagItems.HEMATITE_BLOCK.get()).add(MagItems.RAW_HEMATITE_BLOCK.get())
                .add(MagItems.TITANOMAGNETITE_INGOT.get()).add(MagItems.RAW_TITANOMAGNETITE.get())
                .add(MagItems.TITANOMAGNETITE_BLOCK.get()).add(MagItems.RAW_TITANOMAGNETITE_BLOCK.get())
                .addOptional(ResourceLocation.withDefaultNamespace("lodestone"));

        tag(MagTags.METAL_ARMOR)
                .add(net.minecraft.world.item.Items.IRON_HELMET)
                .add(net.minecraft.world.item.Items.IRON_CHESTPLATE)
                .add(net.minecraft.world.item.Items.IRON_LEGGINGS)
                .add(net.minecraft.world.item.Items.IRON_BOOTS)
                .add(net.minecraft.world.item.Items.CHAINMAIL_HELMET)
                .add(net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE)
                .add(net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS)
                .add(net.minecraft.world.item.Items.CHAINMAIL_BOOTS)
                .add(net.minecraft.world.item.Items.GOLDEN_HELMET)
                .add(net.minecraft.world.item.Items.GOLDEN_CHESTPLATE)
                .add(net.minecraft.world.item.Items.GOLDEN_LEGGINGS)
                .add(net.minecraft.world.item.Items.GOLDEN_BOOTS)
                .add(net.minecraft.world.item.Items.NETHERITE_HELMET)
                .add(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE)
                .add(net.minecraft.world.item.Items.NETHERITE_LEGGINGS)
                .add(net.minecraft.world.item.Items.NETHERITE_BOOTS)
                .add(MagItems.MAGNETITE_HELMET.get())
                .add(MagItems.MAGNETITE_CHESTPLATE.get())
                .add(MagItems.MAGNETITE_LEGGINGS.get())
                .add(MagItems.MAGNETITE_BOOTS.get())
                .add(MagItems.FERROMAGNETIC_HELMET.get())
                .add(MagItems.FERROMAGNETIC_CHESTPLATE.get())
                .add(MagItems.FERROMAGNETIC_LEGGINGS.get())
                .add(MagItems.FERROMAGNETIC_BOOTS.get())
                .add(MagItems.MAGNETITE_HORSE_ARMOR.get())
                .add(MagItems.FERROMAGNETIC_HORSE_ARMOR.get())
                .add(MagItems.MAGHEMITE_HELMET.get())
                .add(MagItems.MAGHEMITE_CHESTPLATE.get())
                .add(MagItems.MAGHEMITE_LEGGINGS.get())
                .add(MagItems.MAGHEMITE_BOOTS.get())
                .add(MagItems.MAGHEMITE_HORSE_ARMOR.get())
                .add(MagItems.MAGNETIC_ELYTRA.get());

        tag(common("ingots")).add(MagItems.FERROMAGNETIC_INGOT.get()).add(MagItems.MAGNETITE_INGOT.get())
                .add(MagItems.MAGHEMITE_INGOT.get()).add(MagItems.PYRRHOTITE_INGOT.get())
                .add(MagItems.HEMATITE_INGOT.get()).add(MagItems.TITANOMAGNETITE_INGOT.get());
        tag(common("plates")).add(MagItems.MAGNETIC_PLATE.get());
        tag(common("raw_materials")).add(MagItems.RAW_MAGNETITE.get())
                .add(MagItems.RAW_MAGHEMITE.get()).add(MagItems.RAW_PYRRHOTITE.get())
                .add(MagItems.RAW_HEMATITE.get()).add(MagItems.RAW_TITANOMAGNETITE.get());
        tag(common("storage_blocks")).add(MagItems.MAGNETITE_BLOCK.get()).add(MagItems.RAW_MAGNETITE_BLOCK.get())
                .add(MagItems.MAGHEMITE_BLOCK.get()).add(MagItems.RAW_MAGHEMITE_BLOCK.get())
                .add(MagItems.PYRRHOTITE_BLOCK.get()).add(MagItems.RAW_PYRRHOTITE_BLOCK.get())
                .add(MagItems.HEMATITE_BLOCK.get()).add(MagItems.RAW_HEMATITE_BLOCK.get())
                .add(MagItems.TITANOMAGNETITE_BLOCK.get()).add(MagItems.RAW_TITANOMAGNETITE_BLOCK.get());
        tag(common("ores")).add(MagItems.MAGNETITE_ORE.get()).add(MagItems.DEEPSLATE_MAGNETITE_ORE.get())
                .add(MagItems.MAGHEMITE_ORE.get()).add(MagItems.DEEPSLATE_MAGHEMITE_ORE.get())
                .add(MagItems.PYRRHOTITE_ORE.get()).add(MagItems.DEEPSLATE_PYRRHOTITE_ORE.get())
                .add(MagItems.HEMATITE_ORE.get()).add(MagItems.DEEPSLATE_HEMATITE_ORE.get())
                .add(MagItems.TITANOMAGNETITE_ORE.get()).add(MagItems.DEEPSLATE_TITANOMAGNETITE_ORE.get());
    }
}
