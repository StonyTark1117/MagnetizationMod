package com.stonytark.magnetization.data;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagItems;
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

/**
 * Generates the per-block loot tables that previously lived as hand-written
 * JSONs under {@code data/magnetization/loot_table/blocks/}. Every block
 * Magnetization registers has either a plain drop-self (with the standard
 * survives-explosion gate) or, for the two ore variants, the vanilla
 * silk-touch + ore_drops-fortune branching pattern.
 *
 * <p>Run {@code ./gradlew runData} to regenerate; output lands in
 * {@code src/generated/resources/data/magnetization/loot_table/blocks/}
 * (the source set already includes that path).
 */
public final class MagLootTableProvider {

    private MagLootTableProvider() {}

    public static LootTableProvider create(final PackOutput output,
                                           final java.util.concurrent.CompletableFuture<HolderLookup.Provider> lookup) {
        return new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(BlockLoot::new, LootContextParamSets.BLOCK)),
                lookup
        );
    }

    /** Drop-self for every block we register; ores drop raw_magnetite (fortune-scaled)
     *  unless silk-touched. */
    public static final class BlockLoot extends BlockLootSubProvider {

        protected BlockLoot(final HolderLookup.Provider provider) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
        }

        @Override
        protected void generate() {
            // Plain dropSelf — every emitter, magnet, switch, and storage block.
            dropSelf(MagBlocks.ELECTROMAGNET.get());
            dropSelf(MagBlocks.KINETIC_ELECTROMAGNET.get());
            dropSelf(MagBlocks.MAGNETIC_ANCHOR.get());
            dropSelf(MagBlocks.REPULSOR_COIL.get());
            dropSelf(MagBlocks.TRACTOR_BEAM.get());
            dropSelf(MagBlocks.MAGNETIC_EXCAVATOR.get());
            dropSelf(MagBlocks.MAGNETIC_SWITCH.get());
            dropSelf(MagBlocks.LODESTONE_CORE.get());
            dropSelf(MagBlocks.PERMANENT_MAGNET.get());
            dropSelf(MagBlocks.TEMPORARY_MAGNET.get());
            dropSelf(MagBlocks.POLARITY_INVERTER.get());
            dropSelf(MagBlocks.MAGNETITE_BLOCK.get());
            dropSelf(MagBlocks.RAW_MAGNETITE_BLOCK.get());
            // Stone behaves like vanilla stone: drop self with silk, otherwise
            // drop cobbled. createSingleItemTableWithSilkTouch handles both.
            add(MagBlocks.ANOMALY_STONE.get(), b ->
                    createSingleItemTableWithSilkTouch(b, MagBlocks.COBBLED_ANOMALY_STONE.get()));
            dropSelf(MagBlocks.COBBLED_ANOMALY_STONE.get());
            dropSelf(MagBlocks.ANOMALY_STONE_STAIRS.get());
            add(MagBlocks.ANOMALY_STONE_SLAB.get(), this::createSlabItemTable);
            dropSelf(MagBlocks.COBBLED_ANOMALY_STONE_STAIRS.get());
            add(MagBlocks.COBBLED_ANOMALY_STONE_SLAB.get(), this::createSlabItemTable);
            dropSelf(MagBlocks.COBBLED_ANOMALY_STONE_WALL.get());
            // Magnetic gravel: like vanilla gravel but flint becomes a chance
            // of raw_magnetite / raw_maghemite. Vanilla gravel formula:
            // - silk touch -> always drops self
            // - fortune-scaled chance of flint, else drops self
            // We replicate that with our magnetic raw items in the flint slot.
            add(MagBlocks.MAGNETIC_GRAVEL.get(), block -> net.minecraft.world.level.storage.loot.LootTable.lootTable()
                    .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                            .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
                            .when(net.minecraft.world.level.storage.loot.predicates.ExplosionCondition.survivesExplosion())
                            .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(MagItems.RAW_MAGNETITE.get())
                                    .when(net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition
                                            .bonusLevelFlatChance(
                                                    this.registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                                                            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE),
                                                    0.05f, 0.1f, 0.15f, 0.20f))
                                    .otherwise(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(MagItems.RAW_MAGHEMITE.get())
                                            .when(net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition
                                                    .bonusLevelFlatChance(
                                                            this.registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                                                                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE),
                                                            0.03f, 0.06f, 0.10f, 0.15f))
                                            .otherwise(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block))))));
            dropSelf(MagBlocks.PETRIFIED_WOOD.get());

            // Ore drops — silk gets the ore, otherwise raw_magnetite with the
            // standard fortune ore_drops formula.
            add(MagBlocks.MAGNETITE_ORE.get(),
                    block -> createOreDrop(block, MagItems.RAW_MAGNETITE.get()));
            add(MagBlocks.DEEPSLATE_MAGNETITE_ORE.get(),
                    block -> createOreDrop(block, MagItems.RAW_MAGNETITE.get()));

            // -- Iron-oxide family: storage blocks dropSelf, ores drop raw_<name> --
            dropSelf(MagBlocks.MAGHEMITE_BLOCK.get()); dropSelf(MagBlocks.RAW_MAGHEMITE_BLOCK.get());
            dropSelf(MagBlocks.PYRRHOTITE_BLOCK.get()); dropSelf(MagBlocks.RAW_PYRRHOTITE_BLOCK.get());
            dropSelf(MagBlocks.HEMATITE_BLOCK.get()); dropSelf(MagBlocks.RAW_HEMATITE_BLOCK.get());
            // Titanomagnetite drops with a minecraft:copy_components function
            // attached so the BE's RECORDED_FIELD component travels onto the
            // dropped ItemStack — letting a player mine a charged
            // titanomagnetite and re-place it without losing the imprint.
            // The BE side is wired in TitanomagnetiteBlockEntity via
            // collectImplicitComponents / applyImplicitComponents.
            add(MagBlocks.TITANOMAGNETITE_BLOCK.get(), block ->
                    net.minecraft.world.level.storage.loot.LootTable.lootTable()
                            .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                                    .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
                                    .when(net.minecraft.world.level.storage.loot.predicates.ExplosionCondition.survivesExplosion())
                                    .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block)
                                            .apply(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction
                                                    .copyComponents(net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction.Source.BLOCK_ENTITY)
                                                    .include(com.stonytark.magnetization.registry.MagDataComponents.RECORDED_FIELD.get())))));
            dropSelf(MagBlocks.RAW_TITANOMAGNETITE_BLOCK.get());

            add(MagBlocks.MAGHEMITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_MAGHEMITE.get()));
            add(MagBlocks.DEEPSLATE_MAGHEMITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_MAGHEMITE.get()));
            add(MagBlocks.PYRRHOTITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_PYRRHOTITE.get()));
            add(MagBlocks.DEEPSLATE_PYRRHOTITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_PYRRHOTITE.get()));
            add(MagBlocks.HEMATITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_HEMATITE.get()));
            add(MagBlocks.DEEPSLATE_HEMATITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_HEMATITE.get()));
            add(MagBlocks.TITANOMAGNETITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_TITANOMAGNETITE.get()));
            add(MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE.get(), b -> createOreDrop(b, MagItems.RAW_TITANOMAGNETITE.get()));

            dropSelf(MagBlocks.PYRRHOTITE_CATALYST.get());
            dropSelf(MagBlocks.ENHANCED_PYRRHOTITE_CATALYST.get());
            dropSelf(MagBlocks.COSMIC_PYRRHOTITE_CATALYST.get());

            // 1.2 blocks (fluid blocks need no loot; hardened MR uses noLootTable()).
            dropSelf(MagBlocks.DIAMAGNETIC_BLOCK.get());
            dropSelf(MagBlocks.SOLID_GALLIUM.get());

            // Meteorite sapling drops self when broken (fragile, players can
            // recover their investment if they planted in a bad spot).
            dropSelf(MagBlocks.METEORITE_SAPLING.get());

            // Meteorite core drops self when broken AND yields 1-3 fragments as
            // a bonus high-tier reagent. Player needs iron-tier pickaxe.
            add(MagBlocks.METEORITE_CORE.get(), block -> net.minecraft.world.level.storage.loot.LootTable.lootTable()
                    .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                            .setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1))
                            .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(block)))
                    .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                            .setRolls(net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(1.0f, 3.0f))
                            .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(MagItems.METEORITE_FRAGMENT.get()))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return Stream.of(
                    MagBlocks.ELECTROMAGNET, MagBlocks.KINETIC_ELECTROMAGNET, MagBlocks.MAGNETIC_ANCHOR,
                    MagBlocks.REPULSOR_COIL, MagBlocks.TRACTOR_BEAM, MagBlocks.MAGNETIC_EXCAVATOR,
                    MagBlocks.MAGNETIC_SWITCH, MagBlocks.LODESTONE_CORE,
                    MagBlocks.PERMANENT_MAGNET, MagBlocks.TEMPORARY_MAGNET, MagBlocks.POLARITY_INVERTER,
                    MagBlocks.MAGNETITE_BLOCK, MagBlocks.RAW_MAGNETITE_BLOCK, MagBlocks.PETRIFIED_WOOD,
                    MagBlocks.MAGNETITE_ORE, MagBlocks.DEEPSLATE_MAGNETITE_ORE,
                    // Iron-oxide family
                    MagBlocks.MAGHEMITE_ORE, MagBlocks.DEEPSLATE_MAGHEMITE_ORE,
                    MagBlocks.MAGHEMITE_BLOCK, MagBlocks.RAW_MAGHEMITE_BLOCK,
                    MagBlocks.PYRRHOTITE_ORE, MagBlocks.DEEPSLATE_PYRRHOTITE_ORE,
                    MagBlocks.PYRRHOTITE_BLOCK, MagBlocks.RAW_PYRRHOTITE_BLOCK,
                    MagBlocks.HEMATITE_ORE, MagBlocks.DEEPSLATE_HEMATITE_ORE,
                    MagBlocks.HEMATITE_BLOCK, MagBlocks.RAW_HEMATITE_BLOCK,
                    MagBlocks.TITANOMAGNETITE_ORE, MagBlocks.DEEPSLATE_TITANOMAGNETITE_ORE,
                    MagBlocks.TITANOMAGNETITE_BLOCK, MagBlocks.RAW_TITANOMAGNETITE_BLOCK,
                    MagBlocks.METEORITE_CORE, MagBlocks.METEORITE_SAPLING,
                    MagBlocks.PYRRHOTITE_CATALYST, MagBlocks.ENHANCED_PYRRHOTITE_CATALYST,
                    MagBlocks.COSMIC_PYRRHOTITE_CATALYST,
                    MagBlocks.DIAMAGNETIC_BLOCK, MagBlocks.SOLID_GALLIUM,
                    MagBlocks.ANOMALY_STONE, MagBlocks.COBBLED_ANOMALY_STONE,
                    MagBlocks.ANOMALY_STONE_STAIRS, MagBlocks.ANOMALY_STONE_SLAB,
                    MagBlocks.COBBLED_ANOMALY_STONE_STAIRS, MagBlocks.COBBLED_ANOMALY_STONE_SLAB,
                    MagBlocks.COBBLED_ANOMALY_STONE_WALL, MagBlocks.MAGNETIC_GRAVEL
            ).map(holder -> (Block) holder.get())::iterator;
        }
    }

    /** Provided for the Magnetization datagen entry point as a no-arg static. */
    public static String modId() { return Magnetization.MOD_ID; }
}
