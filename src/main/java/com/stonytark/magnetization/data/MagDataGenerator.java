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
        final CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        final boolean server = event.includeServer();
        final ExistingFileHelper existing = event.getExistingFileHelper();

        // MagBlockTagsProvider owns the *vanilla* tool tags (mineable/* and
        // needs_*_tool) only. Those are self-contained (our blocks alone) and are
        // generated automatically from the registry, so a new block can never be
        // forgotten the way hematite was when the hand-written pickaxe JSON got
        // overwritten during the 1.2 anvil work.
        //
        // The cross-mod `magnetization:*` block/item tags (magnetic_emitter,
        // ferromagnetic_blocks, metallic_ores, …) stay HAND-WRITTEN under
        // src/main/resources/data/.../tags/: they carry other-mod IDs (Simulated,
        // IE, AC, Mekanism, TFMG, …) the registry can't know about. Do not wire
        // MagItemTagsProvider / MagEntityTypeTagsProvider for the same reason.
        gen.addProvider(server, new MagBlockTagsProvider(out, lookup, existing));
        gen.addProvider(server, new MagRecipeProvider(out, lookup));
        gen.addProvider(server, MagLootTableProvider.create(out, lookup));
        gen.addProvider(server, new MagCraterTemplateProvider(out));
    }
}
