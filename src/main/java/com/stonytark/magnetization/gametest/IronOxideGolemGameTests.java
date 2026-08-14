package com.stonytark.magnetization.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.content.golem.GalliumGolemSpawnHandler;
import com.stonytark.magnetization.content.golem.HematiteGolem;
import com.stonytark.magnetization.content.golem.MagneticGolem;
import com.stonytark.magnetization.content.golem.MagnetiteGolem;
import com.stonytark.magnetization.content.golem.MrFluidGolem;
import com.stonytark.magnetization.content.golem.PyrrhotiteGolem;
import com.stonytark.magnetization.content.golem.TitanomagnetiteGolem;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.MagneticFields;
import com.stonytark.magnetization.physics.MobileFieldRegistry;
import com.stonytark.magnetization.registry.MagBlocks;
import com.stonytark.magnetization.registry.MagDataComponents;
import com.stonytark.magnetization.registry.MagEntities;
import com.stonytark.magnetization.registry.MagItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Direct acceptance coverage for the iron-oxide golem expansion. */
@GameTestHolder(Magnetization.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IronOxideGolemGameTests {
    private static final String EMPTY = "empty";

    private record SpawnSpec(Block material, EntityType<? extends MagneticGolem> type) {}

    private IronOxideGolemGameTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "ironOxideStructures")
    public static void everyStructureSpawnsInBothOrientationsAndRecordsOwner(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerPlayer player = headlessPlayer(level, "oxide-structures");
        try {
            final List<SpawnSpec> specs = List.of(
                    new SpawnSpec(MagBlocks.MAGNETITE_BLOCK.get(), MagEntities.MAGNETITE_GOLEM.get()),
                    new SpawnSpec(MagBlocks.PYRRHOTITE_BLOCK.get(), MagEntities.PYRRHOTITE_GOLEM.get()),
                    new SpawnSpec(MagBlocks.HEMATITE_BLOCK.get(), MagEntities.HEMATITE_GOLEM.get()),
                    new SpawnSpec(MagBlocks.TITANOMAGNETITE_BLOCK.get(), MagEntities.TITANOMAGNETITE_GOLEM.get()));
            for (final SpawnSpec spec : specs) {
                for (final boolean alongX : List.of(true, false)) {
                    final BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
                    clearCube(level, center, 2);
                    final List<BlockPos> body = List.of(center, center.below(),
                            center.offset(alongX ? 1 : 0, 0, alongX ? 0 : 1),
                            center.offset(alongX ? -1 : 0, 0, alongX ? 0 : -1));
                    for (final BlockPos pos : body) {
                        level.setBlock(pos, spec.material().defaultBlockState(), Block.UPDATE_ALL);
                    }
                    final BlockPos head = center.above();
                    level.setBlock(head, Blocks.CARVED_PUMPKIN.defaultBlockState(), Block.UPDATE_ALL);

                    final IronGolem spawned = GalliumGolemSpawnHandler.trySpawn(level, head, player);
                    helper.assertTrue(spawned != null && spawned.getType() == spec.type(),
                            "Structure did not create " + spec.type());
                    helper.assertTrue(spawned.isPlayerCreated(), "Structure golem was not player-created");
                    helper.assertTrue(spawned instanceof MagneticGolem magnetic
                                    && player.getUUID().equals(magnetic.ownerUuid()),
                            "Structure golem did not retain the placing player as owner");
                    for (final BlockPos consumed : body) {
                        helper.assertTrue(level.getBlockState(consumed).isAir(),
                                "Body block was not consumed at " + consumed);
                    }
                    helper.assertTrue(level.getBlockState(head).isAir(), "Pumpkin was not consumed");
                    spawned.discard();
                }
            }

            final var challenge = level.getServer().getAdvancements().get(
                    Magnetization.id("all_iron_oxide_golems"));
            helper.assertTrue(challenge != null, "Missing all-golems challenge advancement");
            final var progress = player.getAdvancements().getOrStartProgress(challenge);
            for (final String criterion : List.of("magnetite", "pyrrhotite", "hematite", "titanomagnetite")) {
                helper.assertTrue(progress.getCriterion(criterion) != null
                                && progress.getCriterion(criterion).isDone(),
                        "Structure creation did not trigger challenge criterion " + criterion);
            }
            helper.succeed();
        } finally {
            removeOnlinePlayer(level, player);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "ironOxidePersistence")
    public static void polarizerAndEveryCustomStatePersist(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerPlayer player = headlessPlayer(level, "oxide-persistence");
        try {
            final MagnetiteGolem magnetite = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(),
                    highPosition(helper, 220, 0, 0));
            final HematiteGolem hematite = spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(),
                    highPosition(helper, 220, 4, 0));
            final ItemStack lens = new ItemStack(MagItems.HEMATITE_LENS.get());
            lens.set(MagDataComponents.ARMOR_POLARITY.get(), MagneticPolarity.SOUTH);
            player.setItemInHand(InteractionHand.MAIN_HAND, lens);
            player.setShiftKeyDown(true);
            player.interactOn(magnetite, InteractionHand.MAIN_HAND);
            player.interactOn(hematite, InteractionHand.MAIN_HAND);
            helper.assertTrue(magnetite.magneticPolarity() == MagneticPolarity.SOUTH,
                    "Polarizer did not copy SOUTH onto Magnetite");
            helper.assertTrue(hematite.magneticPolarity() == MagneticPolarity.NORTH,
                    "Hematite must ignore polarity interaction");

            final UUID owner = player.getUUID();
            final List<MagneticGolem> originals = List.of(
                    magnetite,
                    spawnAbsolute(level, MagEntities.PYRRHOTITE_GOLEM.get(), highPosition(helper, 220, 8, 0)),
                    hematite,
                    spawnAbsolute(level, MagEntities.TITANOMAGNETITE_GOLEM.get(), highPosition(helper, 220, 12, 0)));
            for (final MagneticGolem original : originals) {
                original.setOwnerUuid(owner);
                original.setMagneticPolarity(MagneticPolarity.SOUTH);
                final CompoundTag saved = new CompoundTag();
                original.addAdditionalSaveData(saved);
                if (original instanceof MagnetiteGolem) {
                    saved.putLong("OxidationTicks", 1234L);
                    saved.putBoolean("Oxidized", true);
                }
                if (original instanceof TitanomagnetiteGolem) {
                    saved.put("RecordedField", new MagneticField(Vec3.ZERO, new Vec3(0, 0, 1),
                            MagneticPolarity.SOUTH, MagneticStrength.STRONG,
                            MagneticField.Shape.CONICAL).toNbt());
                }
                final MagneticGolem loaded = (MagneticGolem) original.getType().create(level);
                helper.assertTrue(loaded != null, "Could not recreate " + original.getType());
                loaded.readAdditionalSaveData(saved);
                helper.assertTrue(owner.equals(loaded.ownerUuid()), "Reload lost owner for " + original.getType());
                helper.assertTrue(loaded.magneticPolarity() == MagneticPolarity.SOUTH,
                        "Reload lost polarity for " + original.getType());
                if (loaded instanceof MagnetiteGolem reloadedMagnetite) {
                    helper.assertTrue(reloadedMagnetite.isOxidized()
                                    && reloadedMagnetite.oxidationTicks() == 1234L,
                            "Reload lost Magnetite oxidation state");
                }
                if (loaded instanceof TitanomagnetiteGolem reloadedTitan) {
                    helper.assertTrue(reloadedTitan.recordedField() != null
                                    && reloadedTitan.recordedField().shape() == MagneticField.Shape.CONICAL
                                    && reloadedTitan.recordedField().axis().equals(new Vec3(0, 0, 1)),
                            "Reload lost Titanomagnetite recording");
                }
            }
            originals.forEach(Entity::discard);
            helper.succeed();
        } finally {
            removeOnlinePlayer(level, player);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "ironOxideRegistryTravel")
    public static void registryTracksChunkMovementAndDimensionTravel(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final MagnetiteGolem golem = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(),
                highPosition(helper, 230, 0, 0));
        final UUID owner = UUID.randomUUID();
        golem.setOwnerUuid(owner);
        golem.setMagneticPolarity(MagneticPolarity.SOUTH);
        golem.aiStep();
        final Vec3 oldPosition = golem.position();
        helper.assertTrue(MobileFieldRegistry.contains(level, golem.getUUID()),
                "Fresh mobile field did not register");

        golem.setPos(oldPosition.add(48.0d, 0.0d, 0.0d));
        golem.aiStep();
        final var movedSource = MobileFieldRegistry.source(level, golem.getUUID());
        helper.assertTrue(movedSource != null
                        && movedSource.rawField().origin().distanceTo(golem.mobileField().origin()) < 0.01d,
                "Chunk move left a stale mobile-field origin");
        helper.assertTrue(MobileFieldRegistry.snapshotNear(level, BlockPos.containing(oldPosition), 1).stream()
                        .noneMatch(source -> source.id().equals(golem.getUUID())),
                "Chunk move left the UUID in its old chunk bucket");

        final ServerLevel nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "GameTest server has no Nether dimension");
        // Keep the real dimension transition in the pre-generated spawn area;
        // GameTest structure X/Z coordinates are intentionally enormous and
        // would otherwise turn this lifecycle assertion into remote worldgen.
        final Vec3 destination = new Vec3(0.5d, 230.0d, 0.5d);
        final Entity transferred = golem.changeDimension(new DimensionTransition(nether, destination,
                Vec3.ZERO, 0.0f, 0.0f, DimensionTransition.DO_NOTHING));
        helper.assertTrue(transferred instanceof MagnetiteGolem moved, "Dimension transfer did not recreate golem");
        final MagnetiteGolem moved = (MagnetiteGolem) transferred;
        moved.aiStep();
        helper.assertTrue(!MobileFieldRegistry.contains(level, moved.getUUID())
                        && MobileFieldRegistry.contains(nether, moved.getUUID()),
                "Dimension transfer did not move the registry entry");
        helper.assertTrue(owner.equals(moved.ownerUuid())
                        && moved.magneticPolarity() == MagneticPolarity.SOUTH,
                "Dimension transfer lost owner or polarity");
        moved.discard();
        helper.assertTrue(!MobileFieldRegistry.contains(nether, moved.getUUID()),
                "Discard after dimension transfer leaked a registry entry");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "ironOxideRegistryRemoval")
    public static void deathUnloadAndLevelUnloadRemoveRegistryEntries(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final MagnetiteGolem killed = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(),
                highPosition(helper, 235, 0, 0));
        final PyrrhotiteGolem unloaded = spawnAbsolute(level, MagEntities.PYRRHOTITE_GOLEM.get(),
                highPosition(helper, 235, 4, 0));
        final TitanomagnetiteGolem levelCleared = spawnAbsolute(level, MagEntities.TITANOMAGNETITE_GOLEM.get(),
                highPosition(helper, 235, 8, 0));
        killed.aiStep();
        unloaded.aiStep();
        levelCleared.aiStep();
        helper.assertTrue(MobileFieldRegistry.contains(level, killed.getUUID())
                        && MobileFieldRegistry.contains(level, unloaded.getUUID())
                        && MobileFieldRegistry.contains(level, levelCleared.getUUID()),
                "Fixtures did not enter mobile registry");

        killed.remove(Entity.RemovalReason.KILLED);
        unloaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(!MobileFieldRegistry.contains(level, killed.getUUID())
                        && !MobileFieldRegistry.contains(level, unloaded.getUUID()),
                "Death or chunk unload leaked a mobile registry entry");
        MobileFieldRegistry.onLevelUnload(level);
        helper.assertTrue(MobileFieldRegistry.size(level) == 0,
                "Level unload did not clear all mobile entries");
        levelCleared.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "ironOxideHeat")
    public static void pyrrhotiteReadsDirectHeatAndCatalystRelay(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final PyrrhotiteGolem golem = spawnAbsolute(level, MagEntities.PYRRHOTITE_GOLEM.get(),
                highPosition(helper, 245, 0, 0));
        golem.setNoAi(true);
        golem.setNoGravity(true);
        final BlockPos adjacent = golem.blockPosition().east();
        level.setBlock(adjacent, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(12L, () -> {
            assertHeat(helper, golem, BlazeBurnerBlock.HeatLevel.SMOULDERING, MagneticStrength.WEAK);
            level.setBlock(adjacent.below(), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(adjacent, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
            helper.runAfterDelay(12L, () -> {
                assertHeat(helper, golem, BlazeBurnerBlock.HeatLevel.KINDLED, MagneticStrength.STRONG);
                level.setBlock(adjacent, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                helper.runAfterDelay(12L, () -> {
                    assertHeat(helper, golem, BlazeBurnerBlock.HeatLevel.SEETHING, MagneticStrength.EXTREME);
                    level.setBlock(adjacent, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    final BlockPos catalyst = golem.blockPosition().offset(3, 0, 0);
                    level.setBlock(catalyst, MagBlocks.PYRRHOTITE_CATALYST.get().defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(catalyst.above(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
                    helper.runAfterDelay(12L, () -> {
                        assertHeat(helper, golem, BlazeBurnerBlock.HeatLevel.SEETHING, MagneticStrength.EXTREME);
                        level.setBlock(catalyst, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(catalyst.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        helper.runAfterDelay(12L, () -> {
                            assertHeat(helper, golem, BlazeBurnerBlock.HeatLevel.NONE, null);
                            golem.discard();
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "ironOxideFriendlyField")
    public static void ownFieldProtectsSourceOwnerAndTeamButMovesHostilesAndItems(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Vec3 base = highPosition(helper, 250, 0, 0);
        final MagnetiteGolem source = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(), base);
        final var owner = spawnAbsolute(level, EntityType.COW, base.add(2, 0, 0));
        final var teammate = spawnAbsolute(level, EntityType.COW, base.add(0, 0, 2));
        final var hostile = spawnAbsolute(level, EntityType.ZOMBIE, base.add(-2, 0, 0));
        for (final LivingEntity living : List.of(source, owner, teammate, hostile)) {
            if (living instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            living.setNoGravity(true);
            living.setDeltaMovement(Vec3.ZERO);
        }
        owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        teammate.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        hostile.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        final ItemEntity drop = new ItemEntity(level, base.x, base.y, base.z + 3.0d,
                new ItemStack(MagItems.FERROMAGNETIC_INGOT.get()));
        drop.setNoGravity(true);
        level.addFreshEntity(drop);

        final String teamName = "oxide_" + Integer.toUnsignedString(source.getId());
        final var team = level.getScoreboard().addPlayerTeam(teamName);
        level.getScoreboard().addPlayerToTeam(owner.getScoreboardName(), team);
        level.getScoreboard().addPlayerToTeam(teammate.getScoreboardName(), team);
        source.setOwnerUuid(owner.getUUID());
        helper.assertTrue(source.protectsFromOwnField(source), "Source did not protect itself");
        helper.assertTrue(source.protectsFromOwnField(owner), "Source did not protect owner");
        helper.assertTrue(source.protectsFromOwnField(teammate), "Source did not protect owner's teammate");
        helper.assertTrue(!source.protectsFromOwnField(hostile), "Source protected a hostile target");
        source.setOwnerUuid(null);
        helper.assertTrue(!source.protectsFromOwnField(teammate),
                "Ownerless golem must exclude only itself");
        source.setOwnerUuid(owner.getUUID());

        FieldApplicator.applyFromEntity(level, source.mobileField(), source);
        helper.assertTrue(source.getDeltaMovement().equals(Vec3.ZERO), "Source moved in its own field");
        helper.assertTrue(owner.getDeltaMovement().equals(Vec3.ZERO), "Owner moved in owned field");
        helper.assertTrue(teammate.getDeltaMovement().equals(Vec3.ZERO), "Teammate moved in owned field");
        helper.assertTrue(hostile.getDeltaMovement().lengthSqr() > 1.0e-8d,
                "Hostile magnetizable mob was not moved");
        helper.assertTrue(drop.getDeltaMovement().lengthSqr() > 1.0e-8d,
                "Loose magnetic item was not moved");

        level.getScoreboard().removePlayerTeam(team);
        List.of(source, owner, teammate, hostile, drop).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60, batch = "ironOxideExternalFields")
    public static void externalFieldsMoveEveryApplicableGolemButNotHematite(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Vec3 origin = highPosition(helper, 255, 0, 0);
        final MagnetiteGolem magnetite = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(), origin.add(6, 0, 0));
        final PyrrhotiteGolem pyrrhotite = spawnAbsolute(level, MagEntities.PYRRHOTITE_GOLEM.get(), origin.add(8, 0, 0));
        final TitanomagnetiteGolem titan = spawnAbsolute(level, MagEntities.TITANOMAGNETITE_GOLEM.get(), origin.add(10, 0, 0));
        final HematiteGolem hematite = spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), origin.add(12, 0, 0));
        for (final LivingEntity living : List.of(magnetite, pyrrhotite, titan, hematite)) {
            if (living instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            living.setNoGravity(true);
            living.setDeltaMovement(Vec3.ZERO);
        }
        FieldApplicator.apply(level, new MagneticField(origin, new Vec3(0, 1, 0),
                MagneticPolarity.NORTH, MagneticStrength.STRONG, MagneticField.Shape.OMNIDIRECTIONAL));
        helper.assertTrue(magnetite.getDeltaMovement().lengthSqr() > 1.0e-8d,
                "External field did not move Magnetite Golem");
        helper.assertTrue(pyrrhotite.getDeltaMovement().lengthSqr() > 1.0e-8d,
                "External field did not move Pyrrhotite Golem");
        helper.assertTrue(titan.getDeltaMovement().lengthSqr() > 1.0e-8d,
                "External field did not move Titanomagnetite Golem");
        helper.assertTrue(hematite.getDeltaMovement().equals(Vec3.ZERO),
                "External field moved inert Hematite Golem");
        List.of(magnetite, pyrrhotite, titan, hematite).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "ironOxideDampening")
    public static void stackedHematiteSuppressesBlockAndMobileSources(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Vec3 mobileBase = highPosition(helper, 260, 0, 0);
        final PyrrhotiteGolem pyrrhotite = spawnAbsolute(level, MagEntities.PYRRHOTITE_GOLEM.get(), mobileBase);
        pyrrhotite.setNoAi(true);
        pyrrhotite.setNoGravity(true);
        level.setBlock(pyrrhotite.blockPosition().above(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
        final List<HematiteGolem> mobileDampeners = List.of(
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), mobileBase.add(2, 0, 0)),
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), mobileBase.add(-2, 0, 0)),
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), mobileBase.add(0, 0, 2)),
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), mobileBase.add(0, 0, -2)));

        final BlockPos blockSource = BlockPos.containing(highPosition(helper, 300, 0, 0));
        level.setBlock(blockSource, MagBlocks.NEODYMIUM_MAGNET.get().defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockEntity(blockSource) instanceof
                        com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity,
                "Neodymium block fixture did not create its emitter block entity");
        final var permanent = (com.stonytark.magnetization.content.permanent.PermanentMagnetBlockEntity)
                level.getBlockEntity(blockSource);
        com.stonytark.magnetization.content.AbstractEmitterBlockEntity.serverTick(
                level, blockSource, level.getBlockState(blockSource), permanent);
        final List<HematiteGolem> blockDampeners = List.of(
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), Vec3.atBottomCenterOf(blockSource.east(2))),
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), Vec3.atBottomCenterOf(blockSource.west(2))),
                spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), Vec3.atBottomCenterOf(blockSource.south(2))));
        for (final HematiteGolem hematite : concat(mobileDampeners, blockDampeners)) {
            hematite.setNoAi(true);
            hematite.setNoGravity(true);
            hematite.aiStep();
        }

        final MagneticField blockField = MagneticFields.fieldAtLoaded(level, blockSource);
        helper.assertTrue(blockField != null && blockField.strength() == MagneticStrength.NONE,
                "Three Hematite Golems did not fully suppress block STRONG field; got " + blockField);
        blockDampeners.forEach(Entity::discard);
        level.setBlock(blockSource, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(15L, () -> {
            final var source = MobileFieldRegistry.source(level, pyrrhotite.getUUID());
            helper.assertTrue(source != null && source.rawField() != null
                            && source.rawField().strength() == MagneticStrength.EXTREME,
                    "Hot Pyrrhotite did not register its raw EXTREME field");
            helper.assertTrue(MobileFieldRegistry.dampen(level, source.rawField(), source.id()).strength()
                            == MagneticStrength.NONE,
                    "Four Hematite Golems did not fully suppress mobile EXTREME field");
            helper.assertTrue(pyrrhotite.displayedField() != null
                            && pyrrhotite.displayedField().strength() == MagneticStrength.NONE,
                    "HUD-visible mobile field did not reflect stacked dampening");
            helper.assertTrue(mobileDampeners.stream().anyMatch(golem -> golem.dampenedSourceCount() > 0),
                    "Hematite HUD dampening count never updated");
            pyrrhotite.discard();
            mobileDampeners.forEach(Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 70, batch = "ironOxideTitanCapture")
    public static void titanCapturesEveryShapeAndRejectsTitanFeedback(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Vec3 target = highPosition(helper, 285, 0, 0);
        final MagnetiteGolem source = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(), target.add(-2, 0, 0));
        final TitanomagnetiteGolem titan = spawnAbsolute(level, MagEntities.TITANOMAGNETITE_GOLEM.get(), target);
        source.setNoAi(true);
        source.setNoGravity(true);
        titan.setNoAi(true);
        titan.setNoGravity(true);
        for (final MagneticField.Shape shape : MagneticField.Shape.values()) {
            final MagneticField offered = new MagneticField(target.add(-2, 0, 0), new Vec3(1, 0, 0),
                    MagneticPolarity.SOUTH, MagneticStrength.EXTREME, shape, 64.0d, 99999.0d);
            MobileFieldRegistry.update(level, source, offered);
            titan.aiStep();
            final MagneticField recorded = titan.recordedField();
            helper.assertTrue(recorded != null && recorded.shape() == shape
                            && recorded.axis().equals(new Vec3(1, 0, 0))
                            && recorded.strength() == MagneticStrength.STRONG
                            && recorded.customRange() == 0.0d && recorded.forceOverride() == 0.0d,
                    "Titan capture failed for " + shape);
        }

        MobileFieldRegistry.update(level, source, new MagneticField(target.add(-2, 0, 0),
                new Vec3(-1, 0, 0), MagneticPolarity.NORTH, MagneticStrength.EXTREME,
                MagneticField.Shape.CONICAL));
        helper.assertTrue(MagneticFields.strongestField(level, target, titan.getUUID(), true) == null,
                "Titan considered a conical field that did not cover it");
        source.discard();

        final TitanomagnetiteGolem otherTitan = spawnAbsolute(level,
                MagEntities.TITANOMAGNETITE_GOLEM.get(), target.add(-2, 0, 0));
        MobileFieldRegistry.update(level, otherTitan, new MagneticField(target.add(-2, 0, 0),
                new Vec3(1, 0, 0), MagneticPolarity.NORTH, MagneticStrength.STRONG,
                MagneticField.Shape.DIRECTIONAL));
        helper.assertTrue(MagneticFields.strongestField(level, target, titan.getUUID(), true) == null,
                "Titan-to-Titan feedback source was not rejected");
        titan.discard();
        otherTitan.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 90, batch = "ironOxideMrRecognition")
    public static void mrGolemAndArmorHardenInsideMobileField(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final Vec3 base = highPosition(helper, 270, 0, 0);
        final MagnetiteGolem source = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(), base);
        final MrFluidGolem mrGolem = spawnAbsolute(level, MagEntities.MR_FLUID_GOLEM.get(), base.add(2, 0, 0));
        final var wearer = spawnAbsolute(level, EntityType.ZOMBIE, base.add(0, 0, 2));
        final ItemStack helmet = new ItemStack(MagItems.MR_LIQUID_HELMET.get());
        wearer.setItemSlot(EquipmentSlot.HEAD, helmet);
        wearer.setNoAi(true);
        wearer.setNoGravity(true);
        wearer.setInvulnerable(true);
        source.setNoAi(true);
        source.setNoGravity(true);
        mrGolem.setNoAi(true);
        mrGolem.setNoGravity(true);
        helper.runAfterDelay(18L, () -> {
            helper.assertTrue(MagneticFields.isInField(level, mrGolem.position()),
                    "Mobile source was absent from shared MagneticFields query");
            helper.assertTrue(mrGolem.isHardened(), "MR Fluid Golem did not harden in mobile field");
            final Long hardenedUntil = helmet.get(MagDataComponents.HARDENED_UNTIL.get());
            helper.assertTrue(hardenedUntil != null && hardenedUntil > level.getGameTime(),
                    "MR armor did not acquire a live hardened window in mobile field");
            List.of(source, mrGolem, wearer).forEach(Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 80, batch = "ironOxideDrops")
    public static void everyVariantDropsThreeToFiveCorrectIngots(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final ServerPlayer player = headlessPlayer(level, "oxide-drops");
        final boolean previousMobLoot = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)
                .set(true, level.getServer());
        try {
            final Vec3 base = highPosition(helper, 275, 0, 0);
            final MagnetiteGolem fresh = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(), base);
            final MagnetiteGolem oxidized = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(), base.add(3, 0, 0));
            final CompoundTag oxidizedTag = new CompoundTag();
            oxidized.addAdditionalSaveData(oxidizedTag);
            oxidizedTag.putBoolean("Oxidized", true);
            oxidized.readAdditionalSaveData(oxidizedTag);
            final Map<MagneticGolem, Item> expectedByGolem = new LinkedHashMap<>();
            expectedByGolem.put(fresh, MagItems.MAGNETITE_INGOT.get());
            expectedByGolem.put(oxidized, MagItems.MAGHEMITE_INGOT.get());
            expectedByGolem.put(spawnAbsolute(level, MagEntities.PYRRHOTITE_GOLEM.get(), base.add(6, 0, 0)),
                    MagItems.PYRRHOTITE_INGOT.get());
            expectedByGolem.put(spawnAbsolute(level, MagEntities.HEMATITE_GOLEM.get(), base.add(9, 0, 0)),
                    MagItems.HEMATITE_INGOT.get());
            expectedByGolem.put(spawnAbsolute(level, MagEntities.TITANOMAGNETITE_GOLEM.get(), base.add(12, 0, 0)),
                    MagItems.TITANOMAGNETITE_INGOT.get());
            for (final var fixture : expectedByGolem.entrySet()) {
                final MagneticGolem golem = fixture.getKey();
                golem.setNoAi(true);
                golem.setNoGravity(true);
                final var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, golem)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, golem.position())
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE,
                                level.damageSources().generic())
                        .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
                final List<ItemStack> evaluated = level.getServer().reloadableRegistries()
                        .getLootTable(golem.getLootTable()).getRandomItems(params);
                final int evaluatedCount = evaluated.stream().filter(stack -> stack.is(fixture.getValue()))
                        .mapToInt(ItemStack::getCount).sum();
                helper.assertTrue(evaluatedCount >= 3 && evaluatedCount <= 5,
                        "Entity loot table " + golem.getLootTable().location() + " produced "
                                + evaluated + " instead of 3-5 " + fixture.getValue());
                helper.assertTrue(golem.hurt(level.damageSources().playerAttack(player), 10000.0f)
                                && golem.isDeadOrDying(),
                        "Damage fixture did not kill " + golem.getType());

                // Inspect before the dying source's field can launch its own
                // magnetic ingots away on the next entity tick.
                final List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class,
                        AABB.ofSize(golem.position(), 4.0d, 4.0d, 4.0d));
                final int deathCount = drops.stream().filter(drop -> drop.getItem().is(fixture.getValue()))
                        .mapToInt(drop -> drop.getItem().getCount()).sum();
                helper.assertTrue(deathCount >= 3 && deathCount <= 5,
                        "Expected 3-5 " + fixture.getValue() + " from real death but found " + deathCount);
                drops.forEach(Entity::discard);
            }
            helper.succeed();
        } finally {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)
                    .set(previousMobLoot, level.getServer());
            removeOnlinePlayer(level, player);
        }
    }

    @GameTest(template = EMPTY, timeoutTicks = 120, batch = "ironOxideShips")
    public static void mobileFieldAppliesImpulseToSableShip(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos shipPos = BlockPos.containing(highPosition(helper, 240, 8, 0));
        final dev.ryanhcode.sable.sublevel.ServerSubLevel ship = assembleSingleBlockShip(
                level, shipPos, Blocks.IRON_BLOCK);
        final MagnetiteGolem source = spawnAbsolute(level, MagEntities.MAGNETITE_GOLEM.get(),
                Vec3.atBottomCenterOf(shipPos.west(3)));
        source.setNoAi(true);
        source.setNoGravity(true);
        helper.runAfterDelay(3L, () -> {
            final var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(ship);
            if (handle == null) {
                source.discard();
                removeShip(level, ship);
                helper.fail("Could not resolve assembled ship physics handle");
                return;
            }
            final double beforeX = handle.getLinearVelocity(new org.joml.Vector3d()).x;
            FieldApplicator.applyFromEntity(level, source.mobileField(), source);
            helper.runAfterDelay(12L, () -> {
                try {
                    final double afterX = handle.getLinearVelocity(new org.joml.Vector3d()).x;
                    helper.assertTrue(afterX > beforeX + 1.0e-5d,
                            "NORTH mobile field did not push ship away on +X; " + beforeX + " -> " + afterX);
                } finally {
                    source.discard();
                    removeShip(level, ship);
                }
                helper.succeed();
            });
        });
    }

    private static void assertHeat(final GameTestHelper helper, final PyrrhotiteGolem golem,
                                   final BlazeBurnerBlock.HeatLevel heat,
                                   final MagneticStrength strength) {
        helper.assertTrue(golem.observedHeat() == heat,
                "Expected heat " + heat + " but got " + golem.observedHeat());
        final MagneticField field = golem.mobileField();
        helper.assertTrue(strength == null ? field == null : field != null && field.strength() == strength,
                "Heat " + heat + " mapped to wrong field " + field);
    }

    private static void clearCube(final ServerLevel level, final BlockPos center, final int radius) {
        for (final BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static Vec3 highPosition(final GameTestHelper helper, final int y,
                                     final int dx, final int dz) {
        final BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        return new Vec3(anchor.getX() + 0.5d + dx, y, anchor.getZ() + 0.5d + dz);
    }

    private static <T extends Entity> T spawnAbsolute(final ServerLevel level, final EntityType<T> type,
                                                       final Vec3 position) {
        final T entity = type.create(level);
        if (entity == null) throw new IllegalStateException("Could not create " + type);
        entity.setPos(position);
        level.addFreshEntity(entity);
        return entity;
    }

    private static List<HematiteGolem> concat(final List<HematiteGolem> first,
                                              final List<HematiteGolem> second) {
        final java.util.ArrayList<HematiteGolem> joined = new java.util.ArrayList<>(first);
        joined.addAll(second);
        return joined;
    }

    private static void removeOnlinePlayer(final ServerLevel level, final ServerPlayer player) {
        if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
            level.getServer().getPlayerList().remove(player);
        }
        if (!player.isRemoved()) player.discard();
    }

    private static ServerPlayer headlessPlayer(final ServerLevel level, final String name) {
        final ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name),
                net.minecraft.server.level.ClientInformation.createDefault());
        final net.minecraft.network.Connection connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                level.getServer(), connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(final net.minecraft.network.protocol.Packet<?> packet) {
                // Headless GameTest player: ownership/advancements need no client sync.
            }
        };
        player.getAdvancements().reload(level.getServer().getAdvancements());
        level.addFreshEntity(player);
        return player;
    }

    private static dev.ryanhcode.sable.sublevel.ServerSubLevel assembleSingleBlockShip(
            final ServerLevel level, final BlockPos pos, final Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        final dev.ryanhcode.sable.companion.math.BoundingBox3i bounds =
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                        pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        final dev.ryanhcode.sable.sublevel.ServerSubLevel ship =
                dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(
                        level, pos, List.of(pos), bounds);
        final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        container.physicsSystem().getPipeline().teleport(ship,
                new org.joml.Vector3d(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d),
                new org.joml.Quaterniond());
        return ship;
    }

    private static void removeShip(final ServerLevel level,
                                   final dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        final dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (container != null && ship != null) {
            container.removeSubLevel(ship,
                    dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        }
    }
}
