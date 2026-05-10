package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/**
 * Replaces the hand-written recipe JSONs in {@code src/main/resources/data/.../recipe/}.
 * After running {@code ./gradlew runData}, equivalents land in {@code src/generated/resources}.
 * The old hand-written files can be deleted once that has been verified.
 */
public final class MagRecipeProvider extends RecipeProvider {

    public MagRecipeProvider(final PackOutput output, final CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(final RecipeOutput out) {
        final Item ferro = MagItems.FERROMAGNETIC_INGOT.get();
        final Item plate = MagItems.MAGNETIC_PLATE.get();
        final Item core = MagItems.LODESTONE_CORE.get();

        // 8 iron + 1 lodestone -> 8 ferromagnetic ingot
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ferro, 8)
                .pattern("III").pattern("ILI").pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('L', Blocks.LODESTONE)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(out, id("ferromagnetic_ingot"));

        // 3 ferro -> 3 plate
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, plate, 3)
                .pattern("FFF")
                .define('F', ferro)
                .unlockedBy("has_ferro", has(ferro))
                .save(out, id("magnetic_plate"));

        // 4 plate + 4 lodestone + 1 ferro -> 1 core
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, core)
                .pattern("PLP").pattern("LFL").pattern("PLP")
                .define('P', plate)
                .define('L', Blocks.LODESTONE)
                .define('F', ferro)
                .unlockedBy("has_plate", has(plate))
                .save(out, id("lodestone_core"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.ELECTROMAGNET.get())
                .pattern("PRP").pattern("RCR").pattern("PRP")
                .define('P', plate).define('R', Items.REDSTONE).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("electromagnet"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.MAGNETIC_ANCHOR.get())
                .pattern("OFO").pattern("FCF").pattern("OFO")
                .define('O', Blocks.OBSIDIAN).define('F', ferro).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("magnetic_anchor"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.REPULSOR_COIL.get())
                .pattern("PPP").pattern("PCP").pattern("BBB")
                .define('P', plate).define('C', core).define('B', Blocks.COPPER_BLOCK)
                .unlockedBy("has_core", has(core))
                .save(out, id("repulsor_coil"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.TRACTOR_BEAM.get())
                .pattern("FPF").pattern("PCP").pattern("FPF")
                .define('F', ferro).define('P', plate).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("tractor_beam"));

        // Kinetic electromagnet recipe references create:shaft. We can't reference Create's
        // item registry directly without an additional compile-time dep on Create's item
        // class names; instead we look it up by ResourceLocation-tagged ingredient at runtime.
        // For datagen, the simpler path is to keep this recipe as a hand-written JSON.
        // (See data/magnetization/recipe/kinetic_electromagnet.json.)
    }

    private static ResourceLocation id(final String path) {
        return Magnetization.id(path);
    }
}
