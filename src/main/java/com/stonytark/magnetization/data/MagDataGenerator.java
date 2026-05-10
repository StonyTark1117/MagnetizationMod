package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Wires data providers into NeoForge's {@code :runData} task. Each provider class
 * regenerates one slice of the resource tree under {@code src/generated/resources}.
 * After {@code ./gradlew runData}, those files are picked up by the build because
 * we've added that directory as a resource source set in build.gradle.
 *
 * <p>Currently only tags are migrated to datagen — block models, recipes, and
 * loot tables remain hand-written until they need a churn refactor.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagDataGenerator {

    private MagDataGenerator() {}

    @SubscribeEvent
    public static void gather(final GatherDataEvent event) {
        final DataGenerator gen = event.getGenerator();
        final PackOutput out = gen.getPackOutput();
        final ExistingFileHelper efh = event.getExistingFileHelper();
        final CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        final boolean server = event.includeServer();

        final MagBlockTagsProvider blockTags = new MagBlockTagsProvider(out, lookup, efh);
        gen.addProvider(server, blockTags);
        gen.addProvider(server, new MagItemTagsProvider(out, lookup, blockTags.contentsGetter(), efh));
        gen.addProvider(server, new MagEntityTypeTagsProvider(out, lookup, efh));
        gen.addProvider(server, new MagRecipeProvider(out, lookup));
        gen.addProvider(server, MagLootTableProvider.create(out, lookup));
    }
}
