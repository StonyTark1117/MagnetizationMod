package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class MagEntityTypeTagsProvider extends EntityTypeTagsProvider {

    public MagEntityTypeTagsProvider(
            final PackOutput output,
            final CompletableFuture<HolderLookup.Provider> lookupProvider,
            final ExistingFileHelper existingFileHelper
    ) {
        super(output, lookupProvider, Magnetization.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(final HolderLookup.Provider provider) {
        tag(MagTags.MAGNETIZABLE_ENTITIES)
                .add(EntityType.IRON_GOLEM)
                .add(EntityType.ZOMBIE)
                .add(EntityType.HUSK)
                .add(EntityType.DROWNED)
                .add(EntityType.SKELETON)
                .add(EntityType.STRAY)
                .add(EntityType.WITHER_SKELETON)
                .add(EntityType.VINDICATOR)
                .add(EntityType.PILLAGER)
                .add(EntityType.RAVAGER)
                .add(EntityType.FALLING_BLOCK)
                .add(EntityType.ARROW)
                .add(EntityType.TRIDENT);
    }
}
