package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.compat.simulatedcoasters.MagSimulatedCoastersCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.inducer.StructuralInducerBlockEntity;
import com.stonytark.magnetization.registry.MagBlocks;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.notzyvex.coasters_extras.track.ModTrackMaterials;
import dev.notzyvex.coasters_extras.track.ModTrackVariants;
import dev.notzyvex.coasters_extras.track.TrackVariant;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartSpawner;
import dev.silvergold.simulatedcoasters.track.cart.CoasterCartTrainLinkConstraint;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathEdge;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathGraphManager;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathNode;
import dev.silvergold.simulatedcoasters.track.graph.CoasterPathTrackFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;

import java.util.LinkedHashSet;
import java.util.Set;

/** Proves Coasters Extras remains an extension of the parent Coasters bridge. */
@GameTestHolder("magnetization_coasters_extras")
@PrefixGameTestTemplate(false)
public final class CoastersExtrasGameTests {
    private CoastersExtrasGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void everyFunctionalAndDecorativeMaterialIsRegistered(final GameTestHelper helper) {
        final Set<TrackMaterial> functional = Set.of(ModTrackMaterials.BOOST,
                ModTrackMaterials.POWERED_BOOST, ModTrackMaterials.BRAKE,
                ModTrackMaterials.STATION, ModTrackMaterials.SENSOR,
                ModTrackMaterials.SLIPPERY, ModTrackMaterials.REVERSE,
                ModTrackMaterials.SPLASH, ModTrackMaterials.BOBSLED,
                ModTrackMaterials.LAUNCH);
        final Set<ResourceLocation> expectedFunctional = Set.of(
                id("boost_track"), id("powered_boost_track"), id("brake_track"),
                id("station_track"), id("sensor_track"), id("slippery_track"),
                id("reverse_track"), id("splash_track"), id("bobsled_track"),
                id("launch_track"));
        helper.assertTrue(functional.size() == 10,
                "Coasters Extras no longer exposes exactly ten functional materials");
        helper.assertTrue(functional.stream().map(material -> material.id)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())
                        .equals(expectedFunctional),
                "Coasters Extras functional-material identity changed");
        for (final TrackMaterial material : functional) {
            helper.assertTrue(material.getBlock() != null,
                    "Functional track material has no block: " + material.id);
            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(material.getBlock());
            helper.assertTrue(blockId.getNamespace().equals("coasters_extras")
                            && blockId.getPath().equals(material.id.getPath() + "_material"),
                    "Functional track block identity changed: material=" + material.id
                            + ", block=" + blockId);
            final var registeredItem = BuiltInRegistries.ITEM.get(material.id);
            final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(registeredItem);
            helper.assertTrue(itemId.equals(material.id),
                    "Functional track item identity changed: material=" + material.id
                            + ", item=" + itemId);
        }

        final Set<TrackVariant> variants = Set.of(TrackVariant.values());
        helper.assertTrue(variants.size() == 277,
                "Expected the published 1.2 catalog of 277 decorative track variants, got "
                        + variants.size());
        helper.assertTrue(ModTrackVariants.MATERIALS.keySet().equals(variants)
                        && ModTrackVariants.BLOCKS.keySet().equals(variants)
                        && ModTrackVariants.ITEMS.keySet().equals(variants),
                "Decorative material, block, and item catalogs are not one-to-one");
        for (final TrackVariant variant : variants) {
            final TrackMaterial material = ModTrackVariants.MATERIALS.get(variant);
            final var block = ModTrackVariants.BLOCKS.get(variant).get();
            final var item = ModTrackVariants.ITEMS.get(variant).get();
            helper.assertTrue(material != null,
                    "Decorative track variant has no material: " + variant.name());
            final ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            final ResourceLocation materialBlockId = BuiltInRegistries.BLOCK.getKey(
                    material.getBlock());
            final ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            helper.assertTrue(material.getBlock() == block,
                    "Decorative track variant material/block mismatch: " + variant.name()
                            + ", materialBlock=" + materialBlockId + ", catalogBlock=" + blockId);
            helper.assertTrue(blockId.getNamespace().equals("coasters_extras"),
                    "Decorative track block is not registered by Extras: " + variant.name()
                            + " -> " + blockId);
            helper.assertTrue(itemId.getNamespace().equals("coasters_extras"),
                    "Decorative track item is not registered by Extras: " + variant.name()
                            + " -> " + itemId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void looseEngagedLinkedAndReloadedCartsKeepParentContract(final GameTestHelper helper) {
        final boolean originalInducer = MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.get();
        final BlockPos inducerRel = new BlockPos(4, 2, 4);
        final Vec3 engagedPosition = Vec3.atCenterOf(helper.absolutePos(inducerRel.above(6)));
        final Vec3 secondPosition = engagedPosition.add(2.5d, 0.0d, 0.0d);
        final Vec3 loosePosition = engagedPosition.add(0.0d, 0.0d, 6.0d);
        final BlockPos railFrom = BlockPos.containing(engagedPosition.add(-3.0d, 0.0d, 0.0d));
        final BlockPos railTo = railFrom.offset(9, 0, 0);
        final CoasterPathEdge edge = CoasterPathEdge.straight(railFrom, railTo,
                Vec3.atCenterOf(railFrom), Vec3.atCenterOf(railTo));
        final var graph = CoasterPathGraphManager.get(helper.getLevel());
        graph.upsertNode(new CoasterPathNode(railFrom, new Vec3(0, 1, 0)));
        graph.upsertNode(new CoasterPathNode(railTo, new Vec3(0, 1, 0)));
        graph.addEdge(edge);
        final ServerSubLevel loose = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), loosePosition, new Quaterniond());
        final ServerSubLevel first = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), engagedPosition, new Quaterniond(),
                new CoasterPathTrackFrame.GraphHit(engagedPosition, edge, 0.35d));
        final ServerSubLevel second = CoasterCartSpawner.spawnMinimalContraption(
                helper.getLevel(), secondPosition, new Quaterniond(),
                new CoasterPathTrackFrame.GraphHit(secondPosition, edge, 0.65d));
        helper.setBlock(inducerRel, MagBlocks.STRUCTURAL_INDUCER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.DOWN));
        final StructuralInducerBlockEntity inducer =
                (StructuralInducerBlockEntity) helper.getBlockEntity(inducerRel);
        if (loose == null || first == null || second == null || inducer == null) {
            remove(helper, loose, first, second);
            helper.fail("Could not construct the Coasters Extras cart/inducer fixture");
            return;
        }
        helper.runAfterDelay(14L, () -> {
            try {
                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(true);
                helper.assertTrue(!MagSimulatedCoastersCompat.isRailEngaged(loose)
                                && !MagSimulatedCoastersCompat.structuralInducerCanAdopt(loose),
                        "Loose cart bypassed the parent safety rule with Extras loaded");
                helper.assertTrue(MagSimulatedCoastersCompat.isRailEngaged(first)
                                && MagSimulatedCoastersCompat.isRailEngaged(second),
                        "Track-engaged cart metadata was not retained with Extras loaded");
                helper.assertTrue(CoasterCartTrainLinkConstraint.linkCarts(
                                helper.getLevel(), first, second, 2.5d),
                        "Published Coasters runtime rejected a valid two-cart train link");
                helper.assertTrue(first.getUserDataTag() != null
                                && first.getUserDataTag().contains("simulatedcoasters:cart_train_links")
                                && second.getUserDataTag() != null
                                && second.getUserDataTag().contains("simulatedcoasters:cart_train_links"),
                        "Train-link metadata was not persisted to both carts");
                final Set<java.util.UUID> structure = new LinkedHashSet<>(
                        MagSimulatedCoastersCompat.coasterStructureIds(first,
                                helper.getLevel().getGameTime()));
                helper.assertTrue(structure.contains(first.getUniqueId())
                                && structure.contains(second.getUniqueId()),
                        "Magnetization did not treat the linked train as one coaster structure");

                CoasterCartTrainLinkConstraint.clearDimension(helper.getLevel().dimension());
                CoasterCartTrainLinkConstraint.restorePersistedLinks(helper.getLevel());
                helper.assertTrue(CoasterCartTrainLinkConstraint.areLinkedCarts(first, second),
                        "Saved train-link metadata did not restore after a runtime reload");

                inducer.setExternalSignal(15);
                StructuralInducerBlockEntity.serverTick(helper.getLevel(),
                        helper.absolutePos(inducerRel), inducer.getBlockState(), inducer);
                helper.assertTrue(inducer.isTrackingStructure(first.getUniqueId())
                                && inducer.isTrackingStructure(second.getUniqueId())
                                && !inducer.isTrackingStructure(loose.getUniqueId())
                                && inducer.trackedStructureCount() == 1,
                        "Structural Inducer did not preserve linked-train ownership with Extras loaded");
                helper.succeed();
            } finally {
                MagConfig.SIMULATED_COASTERS_STRUCTURAL_INDUCER.set(originalInducer);
                remove(helper, loose, first, second);
            }
        });
    }

    private static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath("coasters_extras", path);
    }

    private static void remove(final GameTestHelper helper, final ServerSubLevel... carts) {
        final SubLevelContainer container = SubLevelContainer.getContainer(helper.getLevel());
        if (container == null) return;
        for (final ServerSubLevel cart : carts) {
            if (cart != null && container.getSubLevel(cart.getUniqueId()) != null) {
                container.removeSubLevel(cart, SubLevelRemovalReason.REMOVED);
            }
        }
    }
}
