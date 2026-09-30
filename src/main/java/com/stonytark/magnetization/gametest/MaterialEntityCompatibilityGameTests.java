package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real upstream entities; optional types stay out of NeoForge's reflected holder signatures. */
@GameTestHolder("magnetization_material_entities")
@PrefixGameTestTemplate(false)
public final class MaterialEntityCompatibilityGameTests {
    private static boolean modular() {
        return "modulargolems".equals(System.getProperty("magnetization.audit.materialEntity"));
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void variantsAndBothFieldPolarities(final GameTestHelper helper) {
        if (modular()) Modular.variants(helper); else Quark.variants(helper);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "materialEntityTransitions")
    public static void currentMaterialsChangeOnFollowingTicks(final GameTestHelper helper) {
        if (modular()) Modular.transitions(helper); else Quark.transitions(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "materialEntityControls")
    public static void armorTagsVetoAndDisabledIntegration(final GameTestHelper helper) {
        final var target = modular() ? Modular.commonSpawn(helper, "modulargolems:iron") : Quark.commonSpawn(helper, 2);
        final var setting = modular() ? MagConfig.MODULAR_GOLEMS_COMPAT_ENABLED : MagConfig.QUARK_TORETOISE_COMPAT_ENABLED;
        final boolean originalSetting = setting.get();
        final var registry = BuiltInRegistries.ENTITY_TYPE;
        final var originalTags = registry.getTags().collect(java.util.stream.Collectors.toMap(
                com.mojang.datafixers.util.Pair::getFirst, p -> p.getSecond().stream().toList()));
        try {
            final double bare = impulse(helper, target, MagneticPolarity.SOUTH);
            helper.assertTrue(bare < 0, "Supported material had no body response");
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            FieldApplicator.onLevelUnload(helper.getLevel());
            helper.assertTrue(impulse(helper, target, MagneticPolarity.SOUTH) < bare, "Armor did not stack");
            target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            setting.set(false);
            FieldApplicator.onLevelUnload(helper.getLevel());
            helper.assertTrue(impulse(helper, target, MagneticPolarity.SOUTH) == 0, "Disabled adapter still responds");
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            FieldApplicator.onLevelUnload(helper.getLevel());
            helper.assertTrue(impulse(helper, target, MagneticPolarity.SOUTH) < 0, "Disabled adapter suppressed equipment");
            target.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            final var tags = new HashMap<>(originalTags);
            final var holder = registry.wrapAsHolder(target.getType());
            final var intrinsic = new ArrayList<>(tags.getOrDefault(MagTags.MAGNETIZABLE_ENTITIES, List.of()));
            intrinsic.add(holder);
            tags.put(MagTags.MAGNETIZABLE_ENTITIES, intrinsic);
            registry.bindTags(tags);
            FieldApplicator.onLevelUnload(helper.getLevel());
            helper.assertTrue(Math.abs(impulse(helper, target, MagneticPolarity.SOUTH) - bare) < 1.0e-9,
                    "Explicit entity tag did not override disabled adapter");
            setting.set(true);
            FieldApplicator.onLevelUnload(helper.getLevel());
            helper.assertTrue(Math.abs(impulse(helper, target, MagneticPolarity.SOUTH) - bare) < 1.0e-9,
                    "Tag and material counted intrinsic response twice");
            tags.put(MagTags.MAGNETIZING_UNMOVEABLE, List.of(holder));
            registry.bindTags(tags);
            target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            FieldApplicator.onLevelUnload(helper.getLevel());
            helper.assertTrue(impulse(helper, target, MagneticPolarity.SOUTH) == 0, "Administrative veto lost");
        } finally {
            setting.set(originalSetting);
            registry.bindTags(originalTags);
            target.discard();
            FieldApplicator.onLevelUnload(helper.getLevel());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "materialEntityPersistence")
    public static void nativePersistenceAndMagneticGolem(final GameTestHelper helper) {
        final var target = modular() ? Modular.commonSpawn(helper, "modulargolems:copper") : Quark.commonSpawn(helper, 5);
        final var tag = new CompoundTag();
        target.saveWithoutId(tag);
        final var restored = (LivingEntity) target.getType().create(helper.getLevel());
        helper.assertTrue(restored != null, "Cannot restore native entity");
        restored.load(tag);
        target.discard();
        helper.getLevel().addFreshEntity(restored);
        try {
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(restored), "Native save/load lost material recognition");
            final var source = com.stonytark.magnetization.registry.MagEntities.MAGNETITE_GOLEM.get().create(helper.getLevel());
            source.setPos(restored.position().add(-3, 0, 0));
            source.setNoAi(true);
            source.setNoGravity(true);
            source.setMagneticPolarity(MagneticPolarity.SOUTH);
            helper.getLevel().addFreshEntity(source);
            try {
                restored.setDeltaMovement(Vec3.ZERO);
                source.aiStep();
                helper.assertTrue(restored.getDeltaMovement().x < 0, "Magnetic golem did not attract native material entity");
            } finally { source.discard(); }
        } finally { restored.discard(); }
        helper.succeed();
    }

    private static void ready(final GameTestHelper helper, final net.minecraft.world.entity.Mob target) {
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 3, 1))));
        helper.getLevel().addFreshEntity(target);
    }

    private static double impulse(final GameTestHelper helper, final LivingEntity target, final MagneticPolarity pole) {
        target.setDeltaMovement(Vec3.ZERO);
        final var center = new Vec3(target.getX(), target.getBoundingBox().getCenter().y, target.getZ());
        FieldApplicator.applyEntitiesOnly(helper.getLevel(), new MagneticField(center.add(-3, 0, 0),
                new Vec3(1, 0, 0), pole, MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL));
        return target.getDeltaMovement().x;
    }

    private static void check(final GameTestHelper helper, final LivingEntity target, final boolean expected, final String label) {
        try {
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(target) == expected, "Wrong eligibility: " + label);
            final double pull = impulse(helper, target, MagneticPolarity.SOUTH);
            final double push = impulse(helper, target, MagneticPolarity.NORTH);
            helper.assertTrue(expected ? pull < 0 && push > 0 : pull == 0 && push == 0,
                    "Wrong field susceptibility: " + label + " pull=" + pull + " push=" + push);
        } finally { target.discard(); }
    }

    private static final class Modular {
        private static LivingEntity commonSpawn(final GameTestHelper helper, final String material) {
            return spawn(helper, "humanoid", material);
        }
        private static dev.xkmc.modulargolems.content.entity.common.AbstractGolemEntity<?, ?> spawn(
                final GameTestHelper helper, final String type, final String material) {
            final var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("modulargolems:" + type + "_golem_holder"));
            helper.assertTrue(item instanceof dev.xkmc.modulargolems.content.item.golem.GolemHolder<?, ?>, "Missing native holder " + type);
            final var holder = (dev.xkmc.modulargolems.content.item.golem.GolemHolder<?, ?>) item;
            final var target = holder.createDummy(holder.withUniformMaterial(ResourceLocation.parse(material)), helper.getLevel());
            helper.assertTrue(!target.getMaterials().isEmpty(), "Holder produced no materials " + material);
            ready(helper, target);
            return target;
        }

        private static void variants(final GameTestHelper helper) {
            for (String type : List.of("metal", "humanoid", "dog")) {
                for (String material : List.of("modulargolems:iron", "modulargolems:copper", "modulargolems:gold",
                        "modulargolems:netherite", "create:brass", "create:zinc", "create:andesite_alloy")) {
                    check(helper, spawn(helper, type, material), true, type + "/" + material);
                }
                for (String material : List.of("modulargolems:sculk", "create:cardboard")) {
                    check(helper, spawn(helper, type, material), false, type + "/" + material);
                }
            }
            final var metal = spawn(helper, "humanoid", "modulargolems:iron");
            final var mixed = spawn(helper, "humanoid", "create:cardboard");
            mixed.getMaterials().set(0, metal.getMaterials().getFirst());
            metal.discard();
            check(helper, mixed, true, "mixed cardboard/iron parts");
        }

        private static void transitions(final GameTestHelper helper) {
            final var target = spawn(helper, "humanoid", "modulargolems:iron");
            final var wood = spawn(helper, "humanoid", "create:cardboard");
            final var ironParts = new ArrayList<>(target.getMaterials());
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(target), "Initial iron did not respond");
            target.getMaterials().clear(); target.getMaterials().addAll(wood.getMaterials()); wood.discard();
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!FieldApplicator.isMagnetizableTarget(target), "Cached entity type ignored new cardboard parts");
                target.getMaterials().clear(); target.getMaterials().addAll(ironParts);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(FieldApplicator.isMagnetizableTarget(target), "Restored iron parts did not respond");
                    target.discard(); helper.succeed();
                });
            });
        }
    }

    private static final class Quark {
        private static LivingEntity commonSpawn(final GameTestHelper helper, final int ore) {
            return spawn(helper, ore);
        }
        private static org.violetmoon.quark.content.mobs.entity.Toretoise spawn(final GameTestHelper helper, final int ore) {
            final var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("quark:toretoise"));
            final var target = (org.violetmoon.quark.content.mobs.entity.Toretoise) type.create(helper.getLevel());
            setOre(target, ore); ready(helper, target); return target;
        }
        private static void setOre(final org.violetmoon.quark.content.mobs.entity.Toretoise target, final int ore) {
            final var nbt = new CompoundTag(); target.addAdditionalSaveData(nbt);
            nbt.putInt("oreType", ore); target.readAdditionalSaveData(nbt);
        }
        private static void variants(final GameTestHelper helper) {
            for (int ore : new int[]{0, 1, 2, 3, 4, 5, 99}) check(helper, spawn(helper, ore), ore == 2 || ore == 5, "ore " + ore);
        }
        private static void transitions(final GameTestHelper helper) {
            final var target = spawn(helper, 2);
            final var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(target), "Initial iron did not respond");
            target.hurt(helper.getLevel().damageSources().playerAttack(player), 1);
            helper.assertTrue(target.getOreType() == 0, "Native pickaxe harvest did not clear ore");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!FieldApplicator.isMagnetizableTarget(target), "Harvested shell retained iron response");
                // Exercise native forced regrowth; it chooses one of the five actual ore types.
                target.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(target.blockPosition()),
                        net.minecraft.world.entity.MobSpawnType.COMMAND, null);
                helper.assertTrue(target.getOreType() >= 1 && target.getOreType() <= 5, "Native regrowth failed");
                helper.runAfterDelay(2, () -> {
                    final int ore = target.getOreType();
                    helper.assertTrue(FieldApplicator.isMagnetizableTarget(target) == (ore == 2 || ore == 5), "Regrowth response is stale");
                    target.discard(); player.discard(); helper.succeed();
                });
            });
        }
    }
}
