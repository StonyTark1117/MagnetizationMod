package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/**
 * Replaces the hand-written recipe JSONs under
 * {@code data/magnetization/recipe/}. After {@code ./gradlew runData},
 * equivalents land in {@code src/generated/resources/data/magnetization/recipe/}.
 * Hand-written JSONs that aren't covered here (Patchouli book conditionally
 * crafted from Book+Raw Magnetite; the kinetic_electromagnet recipe that
 * references {@code create:shaft}; the alternate "dense" ferromagnetic recipe;
 * {@code lodestone_from_magnetite}) stay hand-written.
 */
public final class MagRecipeProvider extends RecipeProvider {

    private static final TagKey<Item> C_INGOTS_MAGNETITE =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/magnetite"));
    /** Our ferromagnetic ingot + TFMG's magnetic alloy ingot — interchangeable
     *  in every recipe that wants a "magnetic alloy" (see c:ingots/magnetic_alloy). */
    private static final TagKey<Item> C_INGOTS_MAGNETIC_ALLOY =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/magnetic_alloy"));
    private static final TagKey<Item> C_PLATES =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:plates"));
    private static final TagKey<Item> C_STRINGS =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:strings"));
    private static final TagKey<Item> C_DUSTS_REDSTONE =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:dusts/redstone"));
    private static final TagKey<Item> C_STORAGE_BLOCKS_REDSTONE =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:storage_blocks/redstone"));

    public MagRecipeProvider(final PackOutput output, final CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void buildRecipes(final RecipeOutput out) {
        final Item ferro = MagItems.FERROMAGNETIC_INGOT.get();
        final Item plate = MagItems.MAGNETIC_PLATE.get();
        final Item core = MagItems.LODESTONE_CORE.get();
        final Item rawMag = MagItems.RAW_MAGNETITE.get();
        final Item magIngot = MagItems.MAGNETITE_INGOT.get();
        final Ingredient magTag = Ingredient.of(C_INGOTS_MAGNETITE);
        // Anywhere a recipe consumes a ferromagnetic ingot, accept any magnetic
        // alloy ingot (ours or TFMG's) so the two are interchangeable.
        final Ingredient magAlloy = Ingredient.of(C_INGOTS_MAGNETIC_ALLOY);

        // -------- core materials --------

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ferro, 8)
                .pattern("III").pattern("ILI").pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('L', Blocks.LODESTONE)
                .unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(out, id("ferromagnetic_ingot"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, plate, 3)
                .pattern("FFF")
                .define('F', magAlloy)
                .unlockedBy("has_ferro", has(ferro))
                .save(out, id("magnetic_plate"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, core)
                .pattern("PLP").pattern("LFL").pattern("PLP")
                .define('P', plate).define('L', Blocks.LODESTONE).define('F', magAlloy)
                .unlockedBy("has_plate", has(plate))
                .save(out, id("lodestone_core"));

        // -------- emitter blocks --------

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.ELECTROMAGNET.get())
                .pattern("PRP").pattern("RCR").pattern("PRP")
                .define('P', plate).define('R', Ingredient.of(C_DUSTS_REDSTONE)).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("electromagnet"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.MAGNETIC_ANCHOR.get())
                .pattern("OFO").pattern("FCF").pattern("OFO")
                .define('O', Blocks.OBSIDIAN).define('F', magAlloy).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("magnetic_anchor"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.REPULSOR_COIL.get())
                .pattern("PPP").pattern("PCP").pattern("BBB")
                .define('P', plate).define('C', core).define('B', Blocks.COPPER_BLOCK)
                .unlockedBy("has_core", has(core))
                .save(out, id("repulsor_coil"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.TRACTOR_BEAM.get())
                .pattern("FPF").pattern("PCP").pattern("FPF")
                .define('F', magAlloy).define('P', plate).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("tractor_beam"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.MAGNETIC_EXCAVATOR.get())
                .pattern("PMP").pattern("PCP").pattern("PMP")
                .define('P', plate).define('M', magTag).define('C', core)
                .unlockedBy("has_core", has(core))
                .save(out, id("magnetic_excavator"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.MAGNETIC_SWITCH.get())
                .pattern("PRP").pattern("PCP").pattern("SSS")
                .define('P', plate).define('R', Ingredient.of(C_DUSTS_REDSTONE))
                .define('C', Items.COMPARATOR).define('S', Items.SMOOTH_STONE)
                .unlockedBy("has_plate", has(plate))
                .save(out, id("magnetic_switch"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.PERMANENT_MAGNET.get(), 2)
                .pattern("FFF").pattern("PLP").pattern("FFF")
                .define('F', magAlloy).define('P', plate)
                .define('L', Ingredient.of(Items.LODESTONE, MagBlocks.MAGNETITE_BLOCK.get().asItem()))
                .unlockedBy("has_plate", has(plate))
                .save(out, id("permanent_magnet"));

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, MagBlocks.POLARITY_INVERTER.get())
                .pattern("RPB").pattern("PCP").pattern("BPR")
                .define('P', plate).define('C', core)
                .define('R', Ingredient.of(C_DUSTS_REDSTONE))
                .define('B', Ingredient.of(C_STORAGE_BLOCKS_REDSTONE))
                .unlockedBy("has_core", has(core))
                .save(out, id("polarity_inverter"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, MagBlocks.TEMPORARY_MAGNET.get())
                .requires(Blocks.IRON_BLOCK)
                .requires(Items.REDSTONE)
                .unlockedBy("has_iron_block", has(Blocks.IRON_BLOCK))
                .save(out, id("temporary_magnet"));

        // -------- items --------

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, MagItems.FIELD_COMPASS.get())
                .pattern(" P ").pattern("PCP").pattern(" P ")
                .define('P', plate).define('C', Items.COMPASS)
                .unlockedBy("has_compass", has(Items.COMPASS))
                .save(out, id("field_compass"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, MagItems.MAGNETIC_GRAPPLE.get())
                .pattern("PCP").pattern(" S ").pattern(" S ")
                .define('P', Ingredient.of(C_PLATES)).define('C', core)
                .define('S', Ingredient.of(C_STRINGS))
                .unlockedBy("has_core", has(core))
                .save(out, id("magnetic_grapple"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, MagItems.REPULSOR_GUN.get())
                .pattern("PCR").pattern(" IR").pattern("  R")
                .define('P', plate).define('C', core).define('I', magAlloy).define('R', Items.IRON_INGOT)
                .unlockedBy("has_core", has(core))
                .save(out, id("repulsor_gun"));

        // -------- ferromagnetic equipment (M = ferromagnetic_ingot) --------

        equipmentSet(out, magAlloy, "ferromagnetic",
                MagItems.FERROMAGNETIC_SWORD.get(),
                MagItems.FERROMAGNETIC_PICKAXE.get(),
                MagItems.FERROMAGNETIC_AXE.get(),
                MagItems.FERROMAGNETIC_SHOVEL.get(),
                MagItems.FERROMAGNETIC_HOE.get(),
                MagItems.FERROMAGNETIC_HELMET.get(),
                MagItems.FERROMAGNETIC_CHESTPLATE.get(),
                MagItems.FERROMAGNETIC_LEGGINGS.get(),
                MagItems.FERROMAGNETIC_BOOTS.get(),
                ferro);

        // -------- magnetite equipment (M = #c:ingots/magnetite) --------

        // -- Maghemite equipment (M = maghemite_ingot, lower-tier mirror of magnetite) --
        equipmentSet(out, Ingredient.of(MagItems.MAGHEMITE_INGOT.get()), "maghemite",
                MagItems.MAGHEMITE_SWORD.get(),
                MagItems.MAGHEMITE_PICKAXE.get(),
                MagItems.MAGHEMITE_AXE.get(),
                MagItems.MAGHEMITE_SHOVEL.get(),
                MagItems.MAGHEMITE_HOE.get(),
                MagItems.MAGHEMITE_HELMET.get(),
                MagItems.MAGHEMITE_CHESTPLATE.get(),
                MagItems.MAGHEMITE_LEGGINGS.get(),
                MagItems.MAGHEMITE_BOOTS.get(),
                MagItems.MAGHEMITE_INGOT.get());
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.MAGHEMITE_HORSE_ARMOR.get())
                .pattern("M M").pattern("MMM").pattern("M M")
                .define('M', MagItems.MAGHEMITE_INGOT.get())
                .unlockedBy("has_material", has(MagItems.MAGHEMITE_INGOT.get()))
                .save(out, id("maghemite_horse_armor"));

        equipmentSet(out, magTag, "magnetite",
                MagItems.MAGNETITE_SWORD.get(),
                MagItems.MAGNETITE_PICKAXE.get(),
                MagItems.MAGNETITE_AXE.get(),
                MagItems.MAGNETITE_SHOVEL.get(),
                MagItems.MAGNETITE_HOE.get(),
                MagItems.MAGNETITE_HELMET.get(),
                MagItems.MAGNETITE_CHESTPLATE.get(),
                MagItems.MAGNETITE_LEGGINGS.get(),
                MagItems.MAGNETITE_BOOTS.get(),
                magIngot);

        // -------- horse body armor (7-ingot U-shape) --------

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.MAGNETITE_HORSE_ARMOR.get())
                .pattern("M M").pattern("MMM").pattern("M M")
                .define('M', magTag)
                .unlockedBy("has_material", has(magIngot))
                .save(out, id("magnetite_horse_armor"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.FERROMAGNETIC_HORSE_ARMOR.get())
                .pattern("M M").pattern("MMM").pattern("M M")
                .define('M', magAlloy)
                .unlockedBy("has_material", has(ferro))
                .save(out, id("ferromagnetic_horse_armor"));

        // -------- storage blocks (compact + uncompact) --------

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.MAGNETITE_BLOCK.get())
                .pattern("III").pattern("III").pattern("III")
                .define('I', magTag)
                .unlockedBy("has_ingot", has(C_INGOTS_MAGNETITE))
                .save(out, id("magnetite_block"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, magIngot, 9)
                .requires(MagBlocks.MAGNETITE_BLOCK.get())
                .unlockedBy("has_block", has(MagBlocks.MAGNETITE_BLOCK.get()))
                .save(out, id("magnetite_ingot_from_block"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.RAW_MAGNETITE_BLOCK.get())
                .pattern("RRR").pattern("RRR").pattern("RRR")
                .define('R', rawMag)
                .unlockedBy("has_raw", has(rawMag))
                .save(out, id("raw_magnetite_block"));

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, rawMag, 9)
                .requires(MagBlocks.RAW_MAGNETITE_BLOCK.get())
                .unlockedBy("has_block", has(MagBlocks.RAW_MAGNETITE_BLOCK.get()))
                .save(out, id("raw_magnetite_from_block"));

        // -------- craftable ore blocks (raw_magnetite + stone/deepslate) --------

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.MAGNETITE_ORE.get())
                .pattern("RRR").pattern("RSR").pattern("RRR")
                .define('R', rawMag).define('S', Blocks.STONE)
                .unlockedBy("has_raw", has(rawMag))
                .save(out, id("magnetite_ore"));

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.DEEPSLATE_MAGNETITE_ORE.get())
                .pattern("RRR").pattern("RDR").pattern("RRR")
                .define('R', rawMag).define('D', Blocks.DEEPSLATE)
                .unlockedBy("has_raw", has(rawMag))
                .save(out, id("deepslate_magnetite_ore"));

        // -------- dense ferromagnetic recipe (iron + magnetite tag + lodestone, 16 output) --------

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ferro, 16)
                .pattern("IMI").pattern("MLM").pattern("IMI")
                .define('I', Ingredient.of(net.minecraft.tags.ItemTags.create(
                        ResourceLocation.parse("c:ingots/iron"))))
                .define('M', magTag).define('L', Items.LODESTONE)
                .unlockedBy("has_magnetite", has(C_INGOTS_MAGNETITE))
                .save(out, id("ferromagnetic_ingot_dense"));

        // -------- Cosmic ferromagnetic recipe (meteorite_fragment skips the
        //          lodestone gating; 1 fragment + 4 magnetite → 8 ferromagnetic).
        //          Lets a player who's found a meteorite shortcut the lodestone
        //          dependency for cheaper ferromagnetic ingots. Lower yield than
        //          the dense recipe so it stays a side-grade, not a strict win.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ferro, 8)
                .pattern(" M ").pattern("MFM").pattern(" M ")
                .define('M', magTag).define('F', MagItems.METEORITE_FRAGMENT.get())
                .unlockedBy("has_fragment", has(MagItems.METEORITE_FRAGMENT.get()))
                .save(out, id("ferromagnetic_ingot_cosmic"));

        // -------- Meteorite fragment + magnetic_plate → 4 lodestone_cores
        //          (cosmic-tier shortcut for the otherwise expensive crafting
        //          ingredient that gates several advanced emitters). Bypasses
        //          the standard 8-magnetite-around-lodestone recipe entirely.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, MagItems.LODESTONE_CORE.get(), 4)
                .requires(MagItems.METEORITE_FRAGMENT.get())
                .requires(MagItems.MAGNETIC_PLATE.get())
                .unlockedBy("has_fragment", has(MagItems.METEORITE_FRAGMENT.get()))
                .save(out, id("lodestone_core_from_meteorite"));

        // -------- Reassemble: 4 fragments + 1 ferromagnetic_ingot center
        //          → 1 meteorite_core. Lets a player who's been hoarding
        //          fragments build a fresh deployable core (still has the
        //          full 12000-tick decay charge). Yield is intentionally
        //          gated on collecting 4 fragments PLUS a ferromagnetic
        //          ingot, so it's slower than just finding another core. --
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.METEORITE_CORE.get())
                .pattern(" F ").pattern("FIF").pattern(" F ")
                .define('F', MagItems.METEORITE_FRAGMENT.get())
                .define('I', magAlloy)
                .unlockedBy("has_fragment", has(MagItems.METEORITE_FRAGMENT.get()))
                .save(out, id("meteorite_core_from_fragments"));

        // -------- Crush: 1 meteorite_core → 3 meteorite_fragments. Lets
        //          a player recycle a found core if they don't want it
        //          deployed (e.g. moved a base, looted a wild core but
        //          already have one set up). Yield matches the 1-3 random
        //          drop from mining a wild core. --
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, MagItems.METEORITE_FRAGMENT.get(), 3)
                .requires(MagItems.METEORITE_CORE.get())
                .unlockedBy("has_core", has(MagItems.METEORITE_CORE.get()))
                .save(out, id("meteorite_fragments_from_core"));

        // -------- smelting + blasting --------

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(rawMag), RecipeCategory.MISC, magIngot, 0.7f, 200)
                .unlockedBy("has_raw", has(rawMag))
                .save(out, id("magnetite_ingot_from_smelting"));

        SimpleCookingRecipeBuilder.blasting(Ingredient.of(rawMag), RecipeCategory.MISC, magIngot, 0.7f, 100)
                .unlockedBy("has_raw", has(rawMag))
                .save(out, id("magnetite_ingot_from_blasting"));

        // -------- Meteorite Sapling (fragment + 4 raw_magnetite cradle) --------
        // Plantable; takes ~30 in-game min to grow into a fresh meteorite_core.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.METEORITE_SAPLING.get())
                .pattern(" F ").pattern("RFR").pattern(" R ")
                .define('F', MagItems.METEORITE_FRAGMENT.get())
                .define('R', MagItems.RAW_MAGNETITE.get())
                .unlockedBy("has_fragment", has(MagItems.METEORITE_FRAGMENT.get()))
                .save(out, id("meteorite_sapling"));

        // -------- Cosmic Compass (field_compass + meteorite_fragment) --------
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, MagItems.COSMIC_COMPASS.get())
                .requires(MagItems.FIELD_COMPASS.get())
                .requires(MagItems.METEORITE_FRAGMENT.get())
                .unlockedBy("has_fragment", has(MagItems.METEORITE_FRAGMENT.get()))
                .save(out, id("cosmic_compass"));

        // -------- Magnetic Elytra (vanilla elytra + ferromagnetic gear) --------
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, MagItems.MAGNETIC_ELYTRA.get())
                .pattern("FPF").pattern("FEF").pattern(" F ")
                .define('F', magAlloy)
                .define('P', MagItems.MAGNETIC_PLATE.get())
                .define('E', Items.ELYTRA)
                .unlockedBy("has_elytra", has(Items.ELYTRA))
                .save(out, id("magnetic_elytra"));

        // -------- Hematite Polarizer (1 hematite_ingot + 1 magnetic_plate) --------
        // Was glass_pane back when this item was called "Hematite Lens"; the
        // rename to Polarizer (opaque disc, not a transparent lens) means
        // glass doesn't fit the visual any more. Magnetic plate is the
        // thematic substitute — it's the housing the hematite mineral
        // gets fixed into.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, MagItems.HEMATITE_LENS.get())
                .requires(MagItems.HEMATITE_INGOT.get())
                .requires(MagItems.MAGNETIC_PLATE.get())
                .unlockedBy("has_hematite", has(MagItems.HEMATITE_INGOT.get()))
                .save(out, id("hematite_lens"));

        // -------- Pyrrhotite Catalyst tiers --------
        // Basic: 8 pyrrhotite_ingot + 1 obsidian (radius 3)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.PYRRHOTITE_CATALYST.get())
                .pattern("PPP").pattern("POP").pattern("PPP")
                .define('P', MagItems.PYRRHOTITE_INGOT.get())
                .define('O', Items.OBSIDIAN)
                .unlockedBy("has_pyrrhotite", has(MagItems.PYRRHOTITE_INGOT.get()))
                .save(out, id("pyrrhotite_catalyst"));
        // Enhanced: basic + 1 magnetic_plate (radius 5)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.ENHANCED_PYRRHOTITE_CATALYST.get())
                .pattern(" P ").pattern("PCP").pattern(" P ")
                .define('P', MagItems.PYRRHOTITE_INGOT.get())
                .define('C', MagItems.PYRRHOTITE_CATALYST.get())
                .unlockedBy("has_catalyst", has(MagItems.PYRRHOTITE_CATALYST.get()))
                .save(out, id("enhanced_pyrrhotite_catalyst"));
        // Cosmic: enhanced + 1 meteorite_fragment (radius 7)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, MagItems.COSMIC_PYRRHOTITE_CATALYST.get())
                .pattern(" F ").pattern("PCP").pattern(" F ")
                .define('F', MagItems.METEORITE_FRAGMENT.get())
                .define('P', MagItems.PYRRHOTITE_INGOT.get())
                .define('C', MagItems.ENHANCED_PYRRHOTITE_CATALYST.get())
                .unlockedBy("has_enhanced", has(MagItems.ENHANCED_PYRRHOTITE_CATALYST.get()))
                .save(out, id("cosmic_pyrrhotite_catalyst"));

        // -------- Imprint Module (titanomagnetite + ender_pearl) --------
        // Ender pearl is the "carry the configuration through space" flavour;
        // titanomagnetite ingot provides the field-recording substrate.
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, MagItems.IMPRINT_MODULE.get())
                .pattern(" T ").pattern("TET").pattern(" T ")
                .define('T', MagItems.TITANOMAGNETITE_INGOT.get())
                .define('E', Items.ENDER_PEARL)
                .unlockedBy("has_titanomagnetite", has(MagItems.TITANOMAGNETITE_INGOT.get()))
                .save(out, id("imprint_module"));

        // -------- Hematite as red pigment (1 ingot → 4 red_dye) --------
        // Real-world hematite is the primary mineral source of red ochre pigment
        // (the original cave-paint colour) — gives the ingot an immediate use
        // beyond storage-block compaction.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, Items.RED_DYE, 4)
                .requires(MagItems.HEMATITE_INGOT.get())
                .unlockedBy("has_hematite", has(MagItems.HEMATITE_INGOT.get()))
                .save(out, id("red_dye_from_hematite"));

        // -------- Iron-oxide family recipes (mirror magnetite pattern) --------
        oreSet(out, "maghemite",
                MagItems.RAW_MAGHEMITE.get(), MagItems.MAGHEMITE_INGOT.get(),
                MagBlocks.MAGHEMITE_ORE.get(), MagBlocks.DEEPSLATE_MAGHEMITE_ORE.get(),
                MagBlocks.MAGHEMITE_BLOCK.get(), MagBlocks.RAW_MAGHEMITE_BLOCK.get(), 0.7f);
        oreSet(out, "pyrrhotite",
                MagItems.RAW_PYRRHOTITE.get(), MagItems.PYRRHOTITE_INGOT.get(),
                MagBlocks.PYRRHOTITE_ORE.get(), MagBlocks.DEEPSLATE_PYRRHOTITE_ORE.get(),
                MagBlocks.PYRRHOTITE_BLOCK.get(), MagBlocks.RAW_PYRRHOTITE_BLOCK.get(), 0.7f);
        oreSet(out, "hematite",
                MagItems.RAW_HEMATITE.get(), MagItems.HEMATITE_INGOT.get(),
                MagBlocks.HEMATITE_ORE.get(), MagBlocks.DEEPSLATE_HEMATITE_ORE.get(),
                MagBlocks.HEMATITE_BLOCK.get(), MagBlocks.RAW_HEMATITE_BLOCK.get(), 0.7f);
        oreSet(out, "titanomagnetite",
                MagItems.RAW_TITANOMAGNETITE.get(), MagItems.TITANOMAGNETITE_INGOT.get(),
                MagBlocks.TITANOMAGNETITE_ORE.get(), MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE.get(),
                MagBlocks.TITANOMAGNETITE_BLOCK.get(), MagBlocks.RAW_TITANOMAGNETITE_BLOCK.get(), 1.0f);

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(MagBlocks.MAGNETITE_ORE.get()),
                        RecipeCategory.MISC, magIngot, 0.9f, 200)
                .unlockedBy("has_ore", has(MagBlocks.MAGNETITE_ORE.get()))
                .save(out, id("magnetite_ingot_from_ore_smelting"));

        SimpleCookingRecipeBuilder.smelting(Ingredient.of(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get()),
                        RecipeCategory.MISC, magIngot, 0.9f, 200)
                .unlockedBy("has_ore", has(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get()))
                .save(out, id("magnetite_ingot_from_deepslate_ore_smelting"));

        // -------- Anomaly Stone family — mirrors vanilla stone/cobblestone:
        //          cobbled smelts to smooth, stairs/slab/wall crafted from
        //          their base block, stonecutter shortcuts for each variant.

        // Cobbled → smooth via smelt / blast (vanilla cobblestone behaviour).
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(MagBlocks.COBBLED_ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.ANOMALY_STONE.get(), 0.1f, 200)
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("anomaly_stone_from_smelting"));
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(MagBlocks.COBBLED_ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.ANOMALY_STONE.get(), 0.1f, 100)
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("anomaly_stone_from_blasting"));

        // Stairs: 6 input → 4 stairs.
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.ANOMALY_STONE_STAIRS.get(), 4)
                .pattern("S  ").pattern("SS ").pattern("SSS")
                .define('S', MagBlocks.ANOMALY_STONE.get())
                .unlockedBy("has_stone", has(MagBlocks.ANOMALY_STONE.get()))
                .save(out, id("anomaly_stone_stairs"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.COBBLED_ANOMALY_STONE_STAIRS.get(), 4)
                .pattern("C  ").pattern("CC ").pattern("CCC")
                .define('C', MagBlocks.COBBLED_ANOMALY_STONE.get())
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("cobbled_anomaly_stone_stairs"));

        // Slabs: 3 input row → 6 slabs.
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.ANOMALY_STONE_SLAB.get(), 6)
                .pattern("SSS")
                .define('S', MagBlocks.ANOMALY_STONE.get())
                .unlockedBy("has_stone", has(MagBlocks.ANOMALY_STONE.get()))
                .save(out, id("anomaly_stone_slab"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.COBBLED_ANOMALY_STONE_SLAB.get(), 6)
                .pattern("CCC")
                .define('C', MagBlocks.COBBLED_ANOMALY_STONE.get())
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("cobbled_anomaly_stone_slab"));

        // Wall: 6 input in 2 rows of 3 → 6 walls (vanilla cobblestone_wall pattern).
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, MagBlocks.COBBLED_ANOMALY_STONE_WALL.get(), 6)
                .pattern("CCC").pattern("CCC")
                .define('C', MagBlocks.COBBLED_ANOMALY_STONE.get())
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("cobbled_anomaly_stone_wall"));

        // Stonecutter shortcuts: 1 base → 1 variant (slab is 2), parallels vanilla.
        net.minecraft.data.recipes.SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(MagBlocks.ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.ANOMALY_STONE_STAIRS.get())
                .unlockedBy("has_stone", has(MagBlocks.ANOMALY_STONE.get()))
                .save(out, id("anomaly_stone_stairs_from_stonecutting"));
        net.minecraft.data.recipes.SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(MagBlocks.ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.ANOMALY_STONE_SLAB.get(), 2)
                .unlockedBy("has_stone", has(MagBlocks.ANOMALY_STONE.get()))
                .save(out, id("anomaly_stone_slab_from_stonecutting"));
        net.minecraft.data.recipes.SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(MagBlocks.COBBLED_ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.COBBLED_ANOMALY_STONE_STAIRS.get())
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("cobbled_anomaly_stone_stairs_from_stonecutting"));
        net.minecraft.data.recipes.SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(MagBlocks.COBBLED_ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.COBBLED_ANOMALY_STONE_SLAB.get(), 2)
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("cobbled_anomaly_stone_slab_from_stonecutting"));
        net.minecraft.data.recipes.SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(MagBlocks.COBBLED_ANOMALY_STONE.get()),
                        RecipeCategory.BUILDING_BLOCKS, MagBlocks.COBBLED_ANOMALY_STONE_WALL.get())
                .unlockedBy("has_cobbled", has(MagBlocks.COBBLED_ANOMALY_STONE.get()))
                .save(out, id("cobbled_anomaly_stone_wall_from_stonecutting"));
    }

    /** Bulk emit a full sword + 4 tools + 4 armor set keyed off one ingredient.
     *  Tool shapes match the conventional vanilla layouts. Armor follows
     *  helmet/chestplate/leggings/boots vanilla shapes. */
    private static void equipmentSet(final RecipeOutput out, final Ingredient mat, final String prefix,
                                      final Item sword, final Item pickaxe, final Item axe,
                                      final Item shovel, final Item hoe,
                                      final Item helmet, final Item chestplate,
                                      final Item leggings, final Item boots,
                                      final ItemLike unlockTrigger) {
        // tools
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, sword)
                .pattern(" M ").pattern(" M ").pattern(" S ")
                .define('M', mat).define('S', Items.STICK)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_sword"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, pickaxe)
                .pattern("MMM").pattern(" S ").pattern(" S ")
                .define('M', mat).define('S', Items.STICK)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_pickaxe"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, axe)
                .pattern("MM ").pattern("MS ").pattern(" S ")
                .define('M', mat).define('S', Items.STICK)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_axe"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, shovel)
                .pattern(" M ").pattern(" S ").pattern(" S ")
                .define('M', mat).define('S', Items.STICK)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_shovel"));

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, hoe)
                .pattern("MM ").pattern(" S ").pattern(" S ")
                .define('M', mat).define('S', Items.STICK)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_hoe"));

        // armor
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, helmet)
                .pattern("MMM").pattern("M M")
                .define('M', mat)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_helmet"));

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, chestplate)
                .pattern("M M").pattern("MMM").pattern("MMM")
                .define('M', mat)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_chestplate"));

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, leggings)
                .pattern("MMM").pattern("M M").pattern("M M")
                .define('M', mat)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_leggings"));

        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, boots)
                .pattern("M M").pattern("M M")
                .define('M', mat)
                .unlockedBy("has_material", has(unlockTrigger))
                .save(out, id(prefix + "_boots"));
    }

    /** Magnetite-mirroring recipe set for a single iron-oxide ore: storage
     *  block compaction (both directions), raw-block compaction, craftable
     *  ore from raw + stone/deepslate, smelting + blasting. */
    private static void oreSet(final RecipeOutput out, final String name,
                                final Item raw, final Item ingot,
                                final Block ore, final Block deepslateOre,
                                final Block storageBlock, final Block rawBlock,
                                final float smeltXp) {
        // storage block <-> ingot
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, storageBlock)
                .pattern("III").pattern("III").pattern("III")
                .define('I', ingot)
                .unlockedBy("has_ingot", has(ingot))
                .save(out, id(name + "_block"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ingot, 9)
                .requires(storageBlock)
                .unlockedBy("has_block", has(storageBlock))
                .save(out, id(name + "_ingot_from_block"));

        // raw block <-> raw item
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, rawBlock)
                .pattern("RRR").pattern("RRR").pattern("RRR")
                .define('R', raw)
                .unlockedBy("has_raw", has(raw))
                .save(out, id("raw_" + name + "_block"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, raw, 9)
                .requires(rawBlock)
                .unlockedBy("has_block", has(rawBlock))
                .save(out, id("raw_" + name + "_from_block"));

        // craftable ore variants
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ore)
                .pattern("RRR").pattern("RSR").pattern("RRR")
                .define('R', raw).define('S', Blocks.STONE)
                .unlockedBy("has_raw", has(raw))
                .save(out, id(name + "_ore"));
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, deepslateOre)
                .pattern("RRR").pattern("RDR").pattern("RRR")
                .define('R', raw).define('D', Blocks.DEEPSLATE)
                .unlockedBy("has_raw", has(raw))
                .save(out, id("deepslate_" + name + "_ore"));

        // smelting + blasting
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(raw), RecipeCategory.MISC, ingot, smeltXp, 200)
                .unlockedBy("has_raw", has(raw))
                .save(out, id(name + "_ingot_from_smelting"));
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(raw), RecipeCategory.MISC, ingot, smeltXp, 100)
                .unlockedBy("has_raw", has(raw))
                .save(out, id(name + "_ingot_from_blasting"));
    }

    private static ResourceLocation id(final String path) {
        return Magnetization.id(path);
    }
}
