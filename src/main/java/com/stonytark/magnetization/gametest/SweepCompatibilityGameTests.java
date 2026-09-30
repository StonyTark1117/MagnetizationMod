package com.stonytark.magnetization.gametest;

import com.google.gson.JsonParser;
import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Resolves every explicit reference in the tested tags, then exercises field response. */
@GameTestHolder("magnetization_sweep")
@PrefixGameTestTemplate(false)
public final class SweepCompatibilityGameTests {
    private static final List<String> TAGS = List.of("entity_type/magnetizable", "item/ferromagnetic",
            "item/metal_armor", "item/metal_tools", "block/ferromagnetic_blocks", "block/magnetic_emitter",
            "damage_type/lightning_sources");

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void installedRegistryReferencesAndFieldBehavior(final GameTestHelper helper) throws Exception {
        final Set<String> namespaces = Set.of(System.getProperty("magnetization.audit.namespaces").split(","));
        for (String ns : namespaces) helper.assertTrue(ModList.get().isLoaded(ns), "Missing pinned audit mod " + ns);
        final List<String> failures = new ArrayList<>();
        int checked = 0;
        final Set<ResourceLocation> blockIds = new LinkedHashSet<>();
        for (String tag : TAGS) {
            try (var in = SweepCompatibilityGameTests.class.getResourceAsStream(
                    "/data/magnetization/tags/" + tag + ".json")) {
                Objects.requireNonNull(in, tag);
                for (var entry : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("values")) {
                    final String raw = entry.isJsonObject() ? entry.getAsJsonObject().get("id").getAsString() : entry.getAsString();
                    final boolean tagReference = raw.startsWith("#");
                    final var id = ResourceLocation.parse(tagReference ? raw.substring(1) : raw);
                    if (!namespaces.contains(id.getNamespace())) continue;
                    final String kind = tag.split("/")[0];
                    final var registry = switch (kind) {
                        case "item" -> BuiltInRegistries.ITEM;
                        case "block" -> BuiltInRegistries.BLOCK;
                        case "entity_type" -> BuiltInRegistries.ENTITY_TYPE;
                        default -> helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
                    };
                    if (tagReference) {
                        if (!hasTag(registry, id)) failures.add(tag + " -> #" + id);
                        else checked++;
                        continue;
                    }
                    if (!registry.containsKey(id)) { failures.add(tag + " -> " + id); continue; }
                    checked++;
                    if (kind.equals("block")) {
                        final var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
                        helper.assertTrue(tag.endsWith("magnetic_emitter") ? state.is(MagTags.MAGNETIC_EMITTER_BLOCKS)
                                : state.is(MagTags.FERROMAGNETIC_BLOCKS), "Resolved block omitted from tag: " + id);
                        // Attached and multiblock parts require upstream construction fixtures.
                        // Exercise the scanner with stable full blocks; registry/tag checks above cover all IDs.
                        if (!state.hasBlockEntity() && state.isCollisionShapeFullBlock(helper.getLevel(), net.minecraft.core.BlockPos.ZERO)) {
                            blockIds.add(id);
                        }
                    }
                    if (tag.equals("item/metal_tools")) {
                        var target = EntityType.ZOMBIE.create(helper.getLevel());
                        var tool = new ItemStack(BuiltInRegistries.ITEM.get(id));
                        target.setItemSlot(EquipmentSlot.MAINHAND, tool);
                        com.stonytark.magnetization.content.effect.LightningRemnantMagnetism.applyLirmStamp(target, "audit");
                        helper.assertTrue(tool.has(com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get()),
                                "Tool did not accept LIRM stamp: " + id);
                        target.discard();
                    }
                    if (kind.equals("entity_type")) {
                        Entity target = BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
                        helper.assertTrue(target != null, "Cannot create " + id);
                        checkImpulse(helper, target, id.toString());
                    } else if (tag.equals("item/ferromagnetic")) {
                        checkImpulse(helper, new ItemEntity(helper.getLevel(), 0, 0, 0,
                                new ItemStack(BuiltInRegistries.ITEM.get(id))), id.toString());
                    } else if (tag.equals("item/metal_armor")) {
                        var target = EntityType.ZOMBIE.create(helper.getLevel());
                        var stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
                        target.setItemSlot(EquipmentSlot.CHEST, stack);
                        checkImpulse(helper, target, "equipment " + id);
                    } else if (tag.equals("damage_type/lightning_sources")) {
                        var target = EntityType.ZOMBIE.create(helper.getLevel());
                        var armor = new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
                        target.setItemSlot(EquipmentSlot.CHEST, armor);
                        var damage = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.DAMAGE_TYPE, id));
                        helper.assertTrue(damage.is(MagTags.LIGHTNING_SOURCES), "Damage tag omitted " + id);
                        target.hurt(new net.minecraft.world.damagesource.DamageSource(damage), 1);
                        helper.assertTrue(armor.has(com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get()),
                                "Damage event did not stamp armor: " + id);
                        target.discard();
                    }
                }
            }
        }
        org.slf4j.LoggerFactory.getLogger("magnetization/compat-audit").info(
                "SWEEP_AUDIT namespaces={} resolved={} unresolved={}", namespaces, checked, failures);
        helper.assertTrue(failures.isEmpty(), "Unresolved installed references: " + failures);
        helper.assertTrue(checked > 0, "Audit checked no registry references");
        for (String provider : List.of("magnetizing", "createmagnetics")) {
            if (!namespaces.contains(provider)) continue;
            var ingot = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(provider + ":magnetite_ingot")));
            for (String recipe : List.of("magnetite_block", "magnetic_excavator")) {
                var holder = helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("magnetization:" + recipe)).orElseThrow();
                helper.assertTrue(holder.value().getIngredients().stream().anyMatch(i -> i.test(ingot)),
                        "Recipe does not accept upstream magnetite ingot: " + recipe);
            }
        }
        if (namespaces.contains("createmagnetics")) {
            // Exercise the patched upstream block entity through real server ticks,
            // including construction/removal (which also reaches sound cleanup).
            final var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
            final var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createmagnetics:kinetic_magnet"))
                    .defaultBlockState();
            helper.getLevel().setBlockAndUpdate(pos, state);
            final var entity = helper.getLevel().getBlockEntity(pos);
            helper.assertTrue(entity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity,
                    "Native Kinetic Magnet did not construct its block entity");
            ((com.simibubi.create.content.kinetics.base.KineticBlockEntity) entity).setSpeed(64);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(helper.getLevel().getBlockEntity(pos) == entity && !entity.isRemoved(),
                        "Native Kinetic Magnet did not survive server ticks");
                helper.getLevel().removeBlock(pos, false);
                helper.assertTrue(entity.isRemoved(), "Native Kinetic Magnet cleanup did not run");
                helper.succeed();
            });
            return;
        }
        if (blockIds.isEmpty()) { helper.succeed(); return; }
        final var level = helper.getLevel();
        final var origin = helper.absolutePos(new net.minecraft.core.BlockPos(0, 12, 0));
        final List<net.minecraft.core.BlockPos> positions = new ArrayList<>();
        int ferrous = 0, magnets = 0, index = 0;
        for (var id : blockIds) {
            var pos = origin.offset(index % 16, 0, index / 16);
            var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            level.setBlock(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
            positions.add(pos.below());
            level.setBlock(pos, state, 2);
            positions.add(pos);
            if (state.is(MagTags.MAGNETIC_EMITTER_BLOCKS)) magnets++; else ferrous++;
            index++;
        }
        final int expectedFerrous = ferrous, expectedMagnets = magnets;
        final var ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, origin, positions,
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(origin.getX(), origin.getY() - 1, origin.getZ(),
                        origin.getX() + 16, origin.getY() + 1, origin.getZ() + (index + 15) / 16));
        try {
                var result = com.stonytark.magnetization.physics.ShipMagneticScanner.scan(ship);
                helper.assertTrue(result.ferrousBlockCount() == expectedFerrous && result.magnetBlockCount() == expectedMagnets,
                        "Ship material counts differ: " + result + "; expected " + expectedFerrous + "/" + expectedMagnets);
                helper.succeed();
            } finally {
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).removeSubLevel(ship,
                        dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean hasTag(net.minecraft.core.Registry registry, ResourceLocation id) {
        return registry.getTag(net.minecraft.tags.TagKey.create(registry.key(), id)).isPresent();
    }

    private static void checkImpulse(GameTestHelper helper, Entity target, String label) {
        try {
            target.setPos(Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2))));
            target.setNoGravity(true);
            helper.getLevel().addFreshEntity(target);
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(target), "Not eligible: " + label);
            for (var pole : List.of(MagneticPolarity.SOUTH, MagneticPolarity.NORTH)) {
                target.setDeltaMovement(Vec3.ZERO);
                FieldApplicator.applyEntitiesOnly(helper.getLevel(), new MagneticField(target.position().add(-3, target.getBbHeight() * 0.5, 0),
                        new Vec3(1, 0, 0), pole, MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL));
                helper.assertTrue(pole == MagneticPolarity.SOUTH ? target.getDeltaMovement().x < 0 : target.getDeltaMovement().x > 0,
                        "No " + pole + " impulse: " + label);
            }
        } finally { target.discard(); }
    }
}
