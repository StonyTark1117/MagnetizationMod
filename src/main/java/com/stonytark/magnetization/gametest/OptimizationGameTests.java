package com.stonytark.magnetization.gametest;

import com.mojang.authlib.GameProfile;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.compat.ExternalEmitterTracker;
import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.mrarmor.MrArmorHandler;
import com.stonytark.magnetization.physics.EmitterRegistry;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.physics.PerformanceDiagnostics;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder("magnetization_create_new_age")
@PrefixGameTestTemplate(false)
public final class OptimizationGameTests {
    private OptimizationGameTests() {}

    @GameTest(template = "empty", batch = "optimizationFluidIndex", timeoutTicks = 40)
    public static void fluidIndexPreservesRangePolarityAndChunkReload(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var pos = helper.absolutePos(new BlockPos(1, 140, 1));
        final var flowing = pos.east();
        final var fluid = com.stonytark.magnetization.registry.MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get();
        final var south = fluid.defaultBlockState().setValue(
                com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidBlock.POLARITY, MagneticPolarity.SOUTH);
        try {
            level.setBlockAndUpdate(pos, south);
            level.setBlockAndUpdate(flowing, south.setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1));
            final var center = net.minecraft.world.phys.Vec3.atCenterOf(pos);
            helper.assertTrue(MagneticFields.isInField(level, center.add(0, 4, 0)), "Fluid boundary lost its field");
            helper.assertTrue(!MagneticFields.isInField(level, center.add(0, 4.01, 0)), "Fluid query range expanded");
            final var chunk = level.getChunkAt(pos);
            com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry.onChunkUnload(
                    new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunk));
            helper.assertTrue(!MagneticFields.isInField(level, center), "Unloaded source left a phantom field");
            com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry.rebuildChunkIndex(level, chunk);
            helper.assertTrue(MagneticFields.isInField(level, center), "Reload lost the fluid field");
            final var sources = com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry.forLevel(level);
            helper.assertTrue(sources.get(pos) == MagneticPolarity.SOUTH && !sources.containsKey(flowing),
                    "Reload changed polarity or indexed a flowing cell as a source");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            helper.assertTrue(!MagneticFields.isInField(level, center), "Removed source left a phantom field");
            helper.succeed();
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(flowing, Blocks.AIR.defaultBlockState());
        }
    }

    @GameTest(template = "empty", batch = "optimizationFluidGroups", timeoutTicks = 40)
    public static void distantFluidPoolsRemainIndependentAndNearbyContributionsStayOrdered(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var lower = helper.absolutePos(new BlockPos(1, 40, 1));
        final var upper = lower.above(160);
        final int oldTicks = MagConfig.MAGNETIZED_FERROFLUID_TICKS.get();
        final var fluid = com.stonytark.magnetization.registry.MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get();
        final var target = new net.minecraft.world.entity.item.ItemEntity(level,
                lower.getX() + .5, lower.getY() + 80, lower.getZ() + .5, new ItemStack(Items.IRON_INGOT));
        target.setNoGravity(true);
        final var cells = new BlockPos[]{lower, lower.east(), upper};
        try {
            MagConfig.MAGNETIZED_FERROFLUID_TICKS.set(1);
            for (final var pos : cells) level.setBlockAndUpdate(pos, fluid.defaultBlockState());
            level.addFreshEntity(target);
            PerformanceDiagnostics.resetWork(level);
            com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidFieldHandler.onLevelTick(
                    new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("fluid_field_applications") == 0L,
                    "Entity between distant pools activated their source fields");
            target.setPos(lower.getX() + .5, lower.getY() + 1.5, lower.getZ() + .5);
            target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            // Reference: original source order, applying every nearby cell.
            for (final var e : com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidRegistry.forLevel(level).entrySet()) {
                if (!e.getKey().equals(lower) && !e.getKey().equals(lower.east())) continue;
                FieldApplicator.apply(level, new com.stonytark.magnetization.api.MagneticField(
                        net.minecraft.world.phys.Vec3.atCenterOf(e.getKey()), new net.minecraft.world.phys.Vec3(0, 1, 0),
                        e.getValue(), com.stonytark.magnetization.api.MagneticStrength.MEDIUM,
                        com.stonytark.magnetization.api.MagneticField.Shape.OMNIDIRECTIONAL));
            }
            final var expected = target.getDeltaMovement();
            helper.assertTrue(expected.lengthSqr() > 0, "Reference pool did not move the target");
            target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            PerformanceDiagnostics.resetWork(level);
            com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidFieldHandler.onLevelTick(
                    new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(target.getDeltaMovement().distanceToSqr(expected) < 1e-16,
                    "Local grouping changed source contributions");
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("fluid_field_applications") == 2L,
                    "Local target activated a distant pool or lost a nearby source");
            helper.succeed();
        } finally {
            target.discard();
            for (final var pos : cells) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            MagConfig.MAGNETIZED_FERROFLUID_TICKS.set(oldTicks);
        }
    }

    @GameTest(template = "empty", batch = "optimizationGallium", timeoutTicks = 40)
    public static void emptyGalliumSkipsFieldsAndOccupiedGalliumRetainsBothForceDirections(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var pos = helper.absolutePos(new BlockPos(1, 140, 1));
        final var magnet = pos.west(2);
        final int oldTicks = MagConfig.GALLIUM_CURRENT_TICKS.get();
        final boolean oldFields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final boolean oldCompat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final var target = new net.minecraft.world.entity.item.ItemEntity(level,
                pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, new ItemStack(Items.DIRT));
        target.setNoGravity(true);
        try {
            MagConfig.GALLIUM_CURRENT_TICKS.set(1);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            level.setBlockAndUpdate(magnet, BuiltInRegistries.BLOCK.get(
                    ResourceLocation.parse("create_new_age:netherite_magnet")).defaultBlockState());
            EmitterRegistry.registerExternal(level, magnet);
            level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            for (final var fluid : new net.minecraft.world.level.block.Block[]{
                    com.stonytark.magnetization.registry.MagBlocks.GALLIUM_BLOCK.get(),
                    com.stonytark.magnetization.registry.MagBlocks.MIXED_GALLIUM_BLOCK.get()}) {
                level.setBlockAndUpdate(pos, fluid.defaultBlockState());
                level.setBlock(pos, level.getBlockState(pos).setValue(
                        com.stonytark.magnetization.content.fluid.FluidRedstone.POWER, 15), 2);
                target.setPos(pos.getX() + 8, pos.getY() + .5, pos.getZ() + .5);
                PerformanceDiagnostics.resetWork(level);
                com.stonytark.magnetization.content.fluid.GalliumLorentzHandler.onLevelTick(
                        new LevelTickEvent.Post(() -> true, level));
                helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("gallium_field_searches") == 0L,
                        "Empty powered gallium searched fields");
                target.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
                if (!target.isAddedToLevel()) level.addFreshEntity(target);
                for (boolean south : new boolean[]{false, true}) {
                    level.setBlockAndUpdate(magnet.west(), (south ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState());
                    target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    com.stonytark.magnetization.content.fluid.GalliumLorentzHandler.onLevelTick(
                            new LevelTickEvent.Post(() -> true, level));
                    final double expected = MagConfig.galliumCurrentSpeed() * (south ? -1 : 1);
                    helper.assertTrue(Math.abs(target.getDeltaMovement().x - expected) < 1e-8,
                            "Gallium force changed: " + target.getDeltaMovement() + " expected X=" + expected
                                    + "; state=" + level.getBlockState(pos) + "; counts=" + PerformanceDiagnostics.workSnapshot(level)
                                    + "; occupants=" + level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos), e -> true).size()
                                    + "; field=" + MagneticFields.nearestField(level, net.minecraft.world.phys.Vec3.atCenterOf(pos)));
                }
            }
            helper.succeed();
        } finally {
            target.discard();
            for (final var p : new BlockPos[]{pos, pos.below(), magnet, magnet.west()}) {
                level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
            }
            EmitterRegistry.unregisterExternal(level, magnet);
            MagConfig.GALLIUM_CURRENT_TICKS.set(oldTicks);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(oldFields);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(oldCompat);
        }
    }

    @GameTest(template = "empty", batch = "optimizationMrFluid", timeoutTicks = 40)
    public static void storedPowerHardensAndReleasesMrFluidWithoutRedundantFieldSearch(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var pos = helper.absolutePos(new BlockPos(1, 140, 1));
        final int oldTicks = MagConfig.MR_FLUID_HARDEN_TICKS.get();
        final var fluid = com.stonytark.magnetization.registry.MagBlocks.MR_FLUID_BLOCK.get();
        final var hard = com.stonytark.magnetization.registry.MagBlocks.HARDENED_MR_FLUID.get();
        final var power = com.stonytark.magnetization.content.fluid.FluidRedstone.POWER;
        try {
            MagConfig.MR_FLUID_HARDEN_TICKS.set(1);
            level.setBlockAndUpdate(pos.below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(pos, fluid.defaultBlockState().setValue(power, 15));
            PerformanceDiagnostics.resetWork(level);
            com.stonytark.magnetization.content.fluid.MrFluidHardenHandler.onLevelTick(
                    new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(level.getBlockState(pos).is(hard), "Powered MR source did not harden");
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("mr_fluid_field_searches") == 0L,
                    "Stored MR power still searched fields");
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlock(pos, level.getBlockState(pos).setValue(power, 0), 2);
            com.stonytark.magnetization.content.fluid.MrFluidHardenHandler.onLevelTick(
                    new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(level.getBlockState(pos).is(fluid), "Unpowered MR source did not revert");
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("mr_fluid_field_searches") > 0L,
                    "Unpowered MR skipped its field fallback");
            helper.succeed();
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
            MagConfig.MR_FLUID_HARDEN_TICKS.set(oldTicks);
        }
    }

    @GameTest(template = "empty", batch = "optimizationVerticalBounds", timeoutTicks = 40)
    public static void verticalCullingPreservesLiveMovementAndRotation(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var lower = helper.absolutePos(new BlockPos(1, 120, 1));
        final var upper = lower.above(96);
        final boolean oldFields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final boolean oldCompat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final int oldBudget = MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.get();
        final var target = new net.minecraft.world.entity.item.ItemEntity(level,
                upper.getX() + .5, upper.getY() + 1.5, upper.getZ() + .5, new ItemStack(Items.IRON_INGOT));
        target.setNoGravity(true);
        try {
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(256);
            final var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create_new_age:magnetite_block")).defaultBlockState();
            level.setBlockAndUpdate(lower, state);
            level.setBlockAndUpdate(upper, state);
            ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(lower));
            level.addFreshEntity(target);
            for (final BlockPos near : new BlockPos[]{upper, lower}) {
                target.setPos(near.getX() + .5, near.getY() + 1.5, near.getZ() + .5);
                target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                PerformanceDiagnostics.resetWork(level);
                ExternalEmitterTracker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
                final var counts = PerformanceDiagnostics.workSnapshot(level);
                helper.assertTrue(counts.get("field_bounds_rejections") == counts.get("emitter_candidates") - 1L, "Far vertical emitter was not culled: " + counts);
                helper.assertTrue(counts.get("field_evaluations") == 1L, "Culled emitter still evaluated its field");
                helper.assertTrue(target.getDeltaMovement().lengthSqr() > 0, "Moving into range lost the native force");
            }
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(1);
            int applied = 0;
            for (int i = 0, candidates = ExternalEmitterTracker.lastCandidateCount(level); i < candidates; i++) {
                ExternalEmitterTracker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
                applied += ExternalEmitterTracker.lastAppliedCount(level);
            }
            helper.assertTrue(applied == 1, "Culling changed rotation or filled a skipped budget slot");
            target.setPos(lower.getX() + .5, upper.getY() + 96, lower.getZ() + .5);
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(256);
            PerformanceDiagnostics.resetWork(level);
            ExternalEmitterTracker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("field_evaluations") == 0L,
                    "Far-only targets still caused field evaluation");
            helper.succeed();
        } finally {
            target.discard();
            for (final var pos : new BlockPos[]{lower, upper}) {
                EmitterRegistry.unregisterExternal(level, pos);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(oldFields);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(oldCompat);
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(oldBudget);
        }
    }

    @GameTest(template = "empty", batch = "optimizationEquipment", timeoutTicks = 40)
    public static void equipmentGatePreservesHandsArmorDisabledItemsAndExpiration(final GameTestHelper helper) {
        final var level = helper.getLevel();
        helper.assertTrue(PerformanceDiagnostics.enabled(), "Optimization tests require performance diagnostics");
        final int oldRefresh = MagConfig.MR_ARMOR_REFRESH_TICKS.get();
        final var disabled = MagConfig.DISABLED_ITEMS.get();
        final boolean oldFields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final boolean oldCompat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("ef2e2056-1ce2-4419-883d-fd59045ec36f"), "MagEquipTest"));
        final BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            MagConfig.MR_ARMOR_REFRESH_TICKS.set(1);
            MagConfig.DISABLED_ITEMS.set(List.of());
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create_new_age:magnetite_block")).defaultBlockState());
            EmitterRegistry.registerExternal(level, pos);
            player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            clear(player);
            PerformanceDiagnostics.resetWork(level);
            MrArmorHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            MrArmorHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("mr_field_searches") == 0L,
                    "Ordinary equipment triggered MR field searches");
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("mr_equipment_skips") == 2L,
                    "Expected both unequipped and ordinary armor checks to skip");

            for (final EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST}) {
                clear(player);
                final ItemStack stack = new ItemStack(slot == EquipmentSlot.CHEST
                        ? MagItems.MR_LIQUID_CHESTPLATE.get() : MagItems.MR_FLUID_PICKAXE.get());
                player.setItemSlot(slot, stack);
                MrArmorHandler.onPlayerTick(new PlayerTickEvent.Post(player));
                final Long until = stack.get(MagDataComponents.HARDENED_UNTIL.get());
                helper.assertTrue(until != null && until > level.getGameTime(), "Equipment did not harden in slot " + slot);
                // Leaving the field must not extend the timestamp: expiry still follows world time.
                player.setPos(pos.getX() + 1000, pos.getY(), pos.getZ());
                MrArmorHandler.onPlayerTick(new PlayerTickEvent.Post(player));
                helper.assertTrue(until.equals(stack.get(MagDataComponents.HARDENED_UNTIL.get())), "Out-of-field refresh changed expiry");
                player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                MagConfig.DISABLED_ITEMS.set(List.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()));
                PerformanceDiagnostics.resetWork(level);
                MrArmorHandler.onPlayerTick(new PlayerTickEvent.Post(player));
                helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("mr_field_searches") == 0L,
                        "Disabled equipment triggered field search in slot " + slot);
                MagConfig.DISABLED_ITEMS.set(List.of());
            }
            final var horse = helper.spawn(EntityType.HORSE, new BlockPos(1, 3, 1));
            try {
                final ItemStack barding = new ItemStack(MagItems.MR_FLUID_HORSE_ARMOR.get());
                horse.setItemSlot(EquipmentSlot.BODY, barding);
                MrArmorHandler.onEntityTick(new EntityTickEvent.Post(horse));
                helper.assertTrue(barding.has(MagDataComponents.HARDENED_UNTIL.get()), "Horse barding no longer hardens");
            } finally { horse.discard(); }
            helper.succeed();
        } finally {
            clear(player);
            EmitterRegistry.unregisterExternal(level, pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            MagConfig.MR_ARMOR_REFRESH_TICKS.set(oldRefresh);
            MagConfig.DISABLED_ITEMS.set(disabled);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(oldFields);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(oldCompat);
        }
    }

    @GameTest(template = "empty", batch = "optimizationLiveFields", timeoutTicks = 40)
    public static void cachedAdapterMetadataNeverCachesLiveFieldState(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        final var oldFields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final var oldCompat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        try {
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create_new_age:magnetite_block")).defaultBlockState());
            EmitterRegistry.registerExternal(level, pos);
            final var first = MagneticFields.fieldAtLoaded(level, pos);
            helper.assertTrue(first != null && first.polarity() == MagneticPolarity.NORTH, "Expected initial live field");
            level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.assertTrue(MagneticFields.fieldAtLoaded(level, pos).polarity() == MagneticPolarity.SOUTH,
                    "Cached adapter hid same-tick redstone change");
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(false);
            helper.assertTrue(MagneticFields.fieldAtLoaded(level, pos) == null, "Cached adapter hid config disable");
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            helper.assertTrue(MagneticFields.fieldAtLoaded(level, pos) == null, "Cached adapter hid block replacement");
            EmitterRegistry.dropExternalChunk(level, new net.minecraft.world.level.ChunkPos(pos));
            helper.assertTrue(!EmitterRegistry.snapshotExternal(level).contains(pos), "Unload left a stale emitter");
            helper.succeed();
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
            EmitterRegistry.unregisterExternal(level, pos);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(oldFields);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(oldCompat);
        }
    }

    @GameTest(template = "empty", batch = "optimizationDiscovery", timeoutTicks = 40)
    public static void eligibilityDiscoveryDoesNotComputeForceDetails(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final var cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 1));
        final var item = new net.minecraft.world.entity.item.ItemEntity(level, 0, 90, 0, new ItemStack(Items.IRON_INGOT));
        try {
            PerformanceDiagnostics.resetWork(level);
            final var predicate = FieldApplicator.magnetizableTargets(level);
            helper.assertTrue(!predicate.test(cow), "Ordinary cow became a magnetic target");
            helper.assertTrue(predicate.test(item), "Vanilla iron item lost magnetic reaction");
            helper.assertTrue(predicate.test(item), "Repeated eligibility changed");
            helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("target_details") == 0L,
                    "Discovery eagerly calculated susceptibility or polarity");
            helper.succeed();
        } finally { cow.discard(); item.discard(); }
    }

    @GameTest(template = "empty", batch = "optimizationCreep", timeoutTicks = 40)
    public static void chunkSearchPreservesCreepGrowthPolarityAndOrphanRecession(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final int oldMag = MagConfig.FERROFLUID_MAG_TICKS.get();
        final int oldPlain = MagConfig.FERROFLUID_PLAIN_TICKS.get();
        final boolean oldFields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final boolean oldCompat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final BlockPos anchor = helper.absolutePos(new BlockPos(2, 2, 1));
        final BlockPos emitter = anchor.offset(4, 0, 0);
        final var plain = com.stonytark.magnetization.registry.MagBlocks.FERROFLUID_BLOCK.get();
        final var magnetic = com.stonytark.magnetization.registry.MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get();
        try {
            MagConfig.FERROFLUID_MAG_TICKS.set(1);
            MagConfig.FERROFLUID_PLAIN_TICKS.set(1);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            for (int x = -2; x <= 4; x++) {
                level.setBlockAndUpdate(anchor.offset(x, -1, 0), Blocks.GLASS.defaultBlockState());
                level.setBlockAndUpdate(anchor.offset(x, 0, 0), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(emitter, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create_new_age:netherite_magnet")).defaultBlockState());
            EmitterRegistry.registerExternal(level, emitter);
            for (int mode = 0; mode < 3; mode++) {
                final var state = mode == 0 ? plain.defaultBlockState() : magnetic.defaultBlockState()
                        .setValue(com.stonytark.magnetization.content.fluid.MagnetizedFerrofluidBlock.POLARITY,
                                mode == 1 ? MagneticPolarity.SOUTH : MagneticPolarity.NORTH);
                level.setBlockAndUpdate(anchor, state);
                com.stonytark.magnetization.content.fluid.FerrofluidCreepHandler.onLevelTick(new LevelTickEvent.Post(() -> true, level));
                final BlockPos step = mode == 2 ? anchor.west() : anchor.east();
                helper.assertTrue(level.getBlockState(step).is(state.getBlock()), "Creep grew in wrong direction for mode " + mode
                        + "; step=" + level.getBlockState(step) + "; source=" + level.getBlockState(anchor)
                        + "; field=" + MagneticFields.fieldAtLoaded(level, emitter)
                        + "; registered=" + com.stonytark.magnetization.content.fluid.FerrofluidSourceRegistry.snapshot(level));
                for (int x = -1; x <= 1; x++) {
                    final BlockPos pos = anchor.offset(x, 0, 0);
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    com.stonytark.magnetization.content.fluid.FerrofluidCreepRegistry.remove(level, pos);
                }
            }
            EmitterRegistry.unregisterExternal(level, emitter);
            level.setBlockAndUpdate(emitter, Blocks.AIR.defaultBlockState());
            // A flowing tracked creep cell has no source anchor, but still needs recession.
            level.setBlockAndUpdate(anchor, plain.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 1));
            com.stonytark.magnetization.content.fluid.FerrofluidSourceRegistry.remove(level, anchor);
            com.stonytark.magnetization.content.fluid.FerrofluidCreepRegistry.add(level, anchor);
            com.stonytark.magnetization.content.fluid.FerrofluidCreepHandler.onLevelTick(new LevelTickEvent.Post(() -> true, level));
            helper.assertTrue(level.getBlockState(anchor).isAir(), "Orphan creep was skipped when no anchors remained");
            helper.succeed();
        } finally {
            for (int x = -2; x <= 4; x++) {
                final BlockPos pos = anchor.offset(x, 0, 0);
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                com.stonytark.magnetization.content.fluid.FerrofluidCreepRegistry.remove(level, pos);
            }
            EmitterRegistry.unregisterExternal(level, emitter);
            MagConfig.FERROFLUID_MAG_TICKS.set(oldMag);
            MagConfig.FERROFLUID_PLAIN_TICKS.set(oldPlain);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(oldFields);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(oldCompat);
        }
    }

    @GameTest(template = "empty", batch = "optimizationTargetRegions", timeoutTicks = 40)
    public static void overlappingTargetsShareRegionsAndRespectApplicationBudget(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final int oldBudget = MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.get();
        final boolean oldFields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final boolean oldCompat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final java.util.List<net.minecraft.world.entity.item.ItemEntity> items = new java.util.ArrayList<>();
        final BlockPos emitter = helper.absolutePos(new BlockPos(1, 2, 1));
        final BlockPos second = emitter.west(2);
        final net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(emitter);
        final boolean wasForced = level.getForcedChunks().contains(chunk.toLong());
        final Runnable cleanup = () -> {
            items.forEach(net.minecraft.world.entity.Entity::discard);
            EmitterRegistry.unregisterExternal(level, emitter);
            level.setBlockAndUpdate(emitter, Blocks.AIR.defaultBlockState());
            EmitterRegistry.unregisterExternal(level, second);
            level.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(oldBudget);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(oldFields);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(oldCompat);
            if (!wasForced) level.setChunkForced(chunk.x, chunk.z, false);
        };
        try {
            level.setChunkForced(chunk.x, chunk.z, true);
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(1);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            level.setBlockAndUpdate(emitter, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create_new_age:magnetite_block")).defaultBlockState());
            EmitterRegistry.registerExternal(level, emitter);
            level.setBlockAndUpdate(second, level.getBlockState(emitter));
            EmitterRegistry.registerExternal(level, second);
            for (int i = 0; i < 16; i++) {
                final var item = new net.minecraft.world.entity.item.ItemEntity(level,
                        emitter.getX() + 0.1 + i * 0.01, emitter.getY() + 2, emitter.getZ() + 0.5,
                        new ItemStack(Items.IRON_INGOT));
                item.setNoGravity(true);
                item.getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        net.minecraft.network.chat.Component.literal("Magnetic target " + i));
                level.addFreshEntity(item);
                items.add(item);
            }
            helper.runAfterDelay(5, () -> {
                try {
                    // Chunk-load replacement may occur after initial placement.
                    ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(emitter));
                    ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(second));
                    helper.assertTrue(EmitterRegistry.hasExternal(level), "Fixture emitters disappeared: "
                            + level.getBlockState(emitter) + "; " + level.getBlockState(second));
                    for (int i = 0; i < items.size(); i++) {
                        items.get(i).setPos(emitter.getX() + 0.1 + i * 0.01, emitter.getY() + 2, emitter.getZ() + 0.5);
                        items.get(i).setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    }
                    PerformanceDiagnostics.resetWork(level);
                    ExternalEmitterTracker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
                    final var counts = PerformanceDiagnostics.workSnapshot(level);
                    helper.assertTrue(counts.get("eligible_entities") >= 16L, "Discovery missed ordinary iron items: " + counts
                            + "; surviving=" + items.stream().filter(item -> !item.isRemoved()).count());
                    helper.assertTrue(counts.get("target_regions") < counts.get("targets_discovered"),
                            "Overlapping targets repeated identical search regions");
                    helper.assertTrue(counts.get("fields_applied") <= 1L, "Application budget was exceeded");
                    boolean left = false, right = false;
                    for (int attempt = 0; attempt < 64 && !(left && right); attempt++) {
                        items.forEach(item -> item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO));
                        ExternalEmitterTracker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
                        final double x = items.getFirst().getDeltaMovement().x;
                        left |= x < -1.0e-8;
                        right |= x > 1.0e-8;
                    }
                    helper.assertTrue(left && right, "Budget of one starved one of the two opposite-side emitters");
                    items.forEach(net.minecraft.world.entity.Entity::discard);
                    PerformanceDiagnostics.resetWork(level);
                    ExternalEmitterTracker.onLevelTick(new LevelTickEvent.Post(() -> true, level));
                    helper.assertTrue(PerformanceDiagnostics.workSnapshot(level).get("fields_applied") == 0L,
                            "External fields applied with no eligible targets");
                    helper.succeed();
                } finally { cleanup.run(); }
            });
        } catch (final RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "optimizationLoadedSignals", timeoutTicks = 40)
    public static void signalReadsStayLiveAcrossChunkEdgesWithoutLoadingMissingChunks(final GameTestHelper helper) {
        final var level = helper.getLevel();
        final BlockPos center = helper.absolutePos(new BlockPos(200, 20, 200));
        final BlockPos pos = new BlockPos(Math.floorDiv(center.getX(), 16) * 16 + 15,
                center.getY(), Math.floorDiv(center.getZ(), 16) * 16 + 15);
        try {
            for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            for (final var direction : net.minecraft.core.Direction.values()) {
                final BlockPos neighbor = pos.relative(direction);
                level.setBlockAndUpdate(neighbor, Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.assertTrue(com.stonytark.magnetization.physics.LoadedChunkAccess.hasNeighborSignal(level, pos)
                        == level.hasNeighborSignal(pos), "Direct power differs at chunk boundary " + direction);
                level.setBlockAndUpdate(neighbor, Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(neighbor.relative(direction), Blocks.REDSTONE_BLOCK.defaultBlockState());
                helper.assertTrue(com.stonytark.magnetization.physics.LoadedChunkAccess.hasNeighborSignal(level, pos)
                        == level.hasNeighborSignal(pos), "Conducted power differs at chunk boundary " + direction);
                level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(neighbor.relative(direction), Blocks.AIR.defaultBlockState());
                helper.assertTrue(!com.stonytark.magnetization.physics.LoadedChunkAccess.hasNeighborSignal(level, pos),
                        "Power removal was cached");
            }
            final BlockPos missing = new BlockPos(1_000_000, 100, 1_000_000);
            helper.assertTrue(com.stonytark.magnetization.physics.LoadedChunkAccess.chunkNow(level, missing) == null,
                    "Missing-chunk fixture unexpectedly loaded");
            helper.assertTrue(!com.stonytark.magnetization.physics.LoadedChunkAccess.hasNeighborSignal(level, missing),
                    "Missing chunks contributed power");
            helper.assertTrue(com.stonytark.magnetization.physics.LoadedChunkAccess.chunkNow(level, missing) == null,
                    "Signal read loaded a missing chunk");
            helper.succeed();
        } finally {
            for (final var direction : net.minecraft.core.Direction.values()) {
                level.setBlockAndUpdate(pos.relative(direction), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos.relative(direction, 2), Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void clear(final net.minecraft.world.entity.player.Player player) {
        for (final EquipmentSlot slot : EquipmentSlot.values()) player.setItemSlot(slot, ItemStack.EMPTY);
    }
}
