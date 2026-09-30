package com.stonytark.magnetization.gametest;

import com.mcmoddev.golems.entity.GolemBase;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Runs only in the pinned Reborn profile; no fixture substitutes for its real entities. */
@GameTestHolder("magnetization_extra_golems_reborn")
@PrefixGameTestTemplate(false)
public final class ExtraGolemsRebornGameTests {
    private ExtraGolemsRebornGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void materialsArmorPolarityAndConfiguration(final GameTestHelper helper) {
        Loaded.materialsArmorPolarityAndConfiguration(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void materialChangesAreRecognizedOnNextTick(final GameTestHelper helper) {
        Loaded.materialChangesAreRecognizedOnNextTick(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void explicitTagsDoNotDoubleCountAndVetoWins(final GameTestHelper helper) {
        Loaded.explicitTagsDoNotDoubleCountAndVetoWins(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void magneticGolemTickMovesRebornTarget(final GameTestHelper helper) {
        Loaded.magneticGolemTickMovesRebornTarget(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "rebornPhysicalEmitter")
    public static void poweredElectromagnetMovesRebornTarget(final GameTestHelper helper) {
        Loaded.poweredElectromagnetMovesRebornTarget(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "rebornNativeConstruction")
    public static void nativeSpellConstructionProducesRecognizedCopper(final GameTestHelper helper) {
        Loaded.nativeSpellConstructionProducesRecognizedCopper(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "rebornPersistence")
    public static void materialIdentityAndRecognitionSurviveEntitySerialization(final GameTestHelper helper) {
        Loaded.materialIdentityAndRecognitionSurviveEntitySerialization(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "rebornTransitions")
    public static void nativeWaxingScrapingAndEffects(final GameTestHelper helper) {
        Loaded.nativeWaxingScrapingAndEffects(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "rebornOwnConstruction")
    public static void ownConstructionAndOwnershipRemainIntact(final GameTestHelper helper) {
        IronOxideGolemGameTests.everyStructureSpawnsInBothOrientationsAndRecordsOwner(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "rebornOwnRepair")
    public static void ownRepairMaterialsRemainIntact(final GameTestHelper helper) {
        CustomGolemRepairGameTests.everySolidGolemRejectsIronAndUsesItsOwnMaterial(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "rebornOwnFriendlyField")
    public static void ownGolemProtectsOwnerAndTeam(final GameTestHelper helper) {
        IronOxideGolemGameTests.ownFieldProtectsSourceOwnerAndTeamButMovesHostilesAndItems(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 80, batch = "rebornOwnFluidRepair")
    public static void ownFluidGolemRepairRemainsIntact(final GameTestHelper helper) {
        IronOxideGolemGameTests.mrGolemRepairsWithFluidInsteadOfIron(helper);
    }

    // NeoForge reflects over every holder's method signatures even when its
    // namespace is disabled. Keep optional types out of that reflected surface.
    private static final class Loaded {
        private static void nativeWaxingScrapingAndEffects(final GameTestHelper helper) {
            final GolemBase golem = spawn(helper, "golems:oxidized_copper");
            golem.setNoAi(false); // Native interaction behaviors require effective AI.
            final var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEYCOMB));
            golem.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(golem.getGolemId().orElseThrow().getPath().equals("waxed_oxidized_copper"),
                    "Native honeycomb interaction did not wax copper");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "Waxing did not consume honeycomb");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(FieldApplicator.isMagnetizableTarget(golem), "Waxing lost recognition");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
                golem.interact(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(golem.getGolemId().orElseThrow().getPath().equals("oxidized_copper"),
                        "Native axe interaction did not remove wax");
                golem.interact(player, InteractionHand.MAIN_HAND);
                helper.assertTrue(golem.getGolemId().orElseThrow().getPath().equals("weathered_copper"),
                        "Native axe interaction did not scrape oxidation");
                helper.runAfterDelay(2, () -> {
                    try {
                        final double plain = impulse(helper, golem, MagneticPolarity.SOUTH);
                        helper.assertTrue(plain < 0, "Scraping lost field response");
                        golem.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                com.stonytark.magnetization.registry.MagEffects.MAGNETIZED, 100, 1));
                        FieldApplicator.onLevelUnload(helper.getLevel());
                        helper.assertTrue(impulse(helper, golem, MagneticPolarity.SOUTH) < plain,
                                "Magnetized status did not amplify intrinsic response");
                        golem.removeAllEffects();
                        golem.setGolemId(null);
                        FieldApplicator.onLevelUnload(helper.getLevel());
                        helper.assertTrue(!FieldApplicator.isMagnetizableTarget(golem),
                                "Missing material received automatic response");
                        // Upstream writes the synced ID before rejecting an unknown container.
                        // Keep that malformed state only for this synchronous adapter check.
                        try { golem.setGolemId(ResourceLocation.parse("golems:audit_unknown_material")); }
                        catch (java.util.NoSuchElementException expected) { /* Reborn rejects unknown definitions. */ }
                        helper.assertTrue(golem.getGolemId().orElseThrow().getPath().equals("audit_unknown_material"),
                                "Fixture did not retain the unknown material ID");
                        FieldApplicator.onLevelUnload(helper.getLevel());
                        helper.assertTrue(!FieldApplicator.isMagnetizableTarget(golem), "Unknown material reacted");
                        golem.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                        FieldApplicator.onLevelUnload(helper.getLevel());
                        helper.assertTrue(impulse(helper, golem, MagneticPolarity.SOUTH) < 0,
                                "Missing material prevented equipment response");
                        helper.succeed();
                    } finally { golem.discard(); player.discard(); }
                });
            });
        }

        private static void poweredElectromagnetMovesRebornTarget(final GameTestHelper helper) {
            final GolemBase target = spawn(helper, "golems:copper");
            // NoAI skips the mob travel path; enable it for a position-based
            // world-tick test rather than the instantaneous impulse fixtures.
            target.setNoAi(false);
            final var level = helper.getLevel();
            final var position = target.blockPosition().west(3);
            final double startX = target.getX();
            level.setBlockAndUpdate(position, com.stonytark.magnetization.registry.MagBlocks.ELECTROMAGNET.get()
                    .defaultBlockState());
            level.setBlockAndUpdate(position.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(4, () -> {
                try {
                    final var emitter = (com.stonytark.magnetization.content.electromagnet.ElectromagnetBlockEntity)
                            level.getBlockEntity(position);
                    helper.assertTrue(emitter != null && emitter.currentField() != null,
                            "Physical fixture did not produce a powered field");
                    helper.assertTrue(target.getX() < startX - 0.0001,
                            "Redstone-powered electromagnet did not attract Reborn copper during world ticks: position="
                                    + target.position() + ", startX=" + startX + ", velocity=" + target.getDeltaMovement()
                                    + ", field=" + emitter.currentField());
                    helper.succeed();
                } finally {
                    target.discard();
                    level.removeBlock(position, false);
                    level.removeBlock(position.below(), false);
                }
            });
        }

        private static void nativeSpellConstructionProducesRecognizedCopper(final GameTestHelper helper) {
            final var level = helper.getLevel();
            final BlockPos head = helper.absolutePos(new BlockPos(3, 4, 3));
            final BlockPos shoulder = head.below();
            final List<BlockPos> body = List.of(shoulder, shoulder.below(), shoulder.north(), shoulder.south());
            for (final BlockPos position : body) {
                level.setBlock(position, Blocks.COPPER_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.setBlock(head, Blocks.CARVED_PUMPKIN.defaultBlockState(), Block.UPDATE_ALL);

            final var player = helper.makeMockPlayer(GameType.SURVIVAL);
            final ItemStack spell = new ItemStack(com.mcmoddev.golems.EGRegistry.ItemReg.GOLEM_SPELL.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, spell);
            final var result = spell.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(head), Direction.UP, head, false)));
            helper.assertTrue(result.consumesAction(), "Native golem spell did not accept the copper structure");
            helper.assertTrue(spell.isEmpty(), "Native golem spell was not consumed");
            for (final BlockPos position : body) {
                helper.assertTrue(level.getBlockState(position).isAir(), "Native construction left body block " + position);
            }
            helper.assertTrue(level.getBlockState(head).isAir(), "Native construction left the golem head");

            final List<GolemBase> spawned = level.getEntitiesOfClass(GolemBase.class,
                    new AABB(head).inflate(3), golem -> golem.getGolemId()
                            .filter(ResourceLocation.parse("golems:copper")::equals).isPresent());
            helper.assertTrue(spawned.size() == 1, "Native construction produced " + spawned.size() + " copper golems");
            final GolemBase golem = spawned.getFirst();
            helper.assertTrue(golem.isPlayerCreated(), "Native copper golem was not marked player-created");
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(golem),
                    "Natively constructed copper golem was not recognized");
            golem.discard();
            player.discard();
            helper.succeed();
        }

        private static void materialIdentityAndRecognitionSurviveEntitySerialization(final GameTestHelper helper) {
            final GolemBase original = spawn(helper, "golems:waxed_weathered_copper");
            final CompoundTag saved = new CompoundTag();
            original.saveWithoutId(saved);
            helper.assertTrue(saved.contains("Golem"), "Reborn golem save omitted its material ID");
            original.discard();
            final GolemBase loaded = GolemBase.create(helper.getLevel(), ResourceLocation.parse("golems:oak_log"));
            loaded.load(saved);
            helper.assertTrue(loaded.getGolemId()
                            .filter(ResourceLocation.parse("golems:waxed_weathered_copper")::equals).isPresent(),
                    "Entity serialization lost the Reborn material ID");
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(loaded),
                    "Reloaded Reborn copper material was not recognized");
            loaded.discard();
            helper.succeed();
        }

        private static void explicitTagsDoNotDoubleCountAndVetoWins(final GameTestHelper helper) {
            final GolemBase copper = spawn(helper, "golems:copper");
            final var holder = copper.getType().builtInRegistryHolder();
            final var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
            final var original = registry.getTags().collect(java.util.stream.Collectors.toMap(
                    pair -> pair.getFirst(), pair -> pair.getSecond().stream().toList()));
            try {
                final double baseline = impulse(helper, copper, MagneticPolarity.SOUTH);
                final var tags = new java.util.HashMap<>(original);
                final var magnetic = new java.util.ArrayList<>(tags.getOrDefault(
                        com.stonytark.magnetization.api.MagTags.MAGNETIZABLE_ENTITIES, List.of()));
                magnetic.add(holder);
                tags.put(com.stonytark.magnetization.api.MagTags.MAGNETIZABLE_ENTITIES, magnetic);
                registry.bindTags(tags);
                FieldApplicator.onLevelUnload(helper.getLevel());
                helper.assertTrue(Math.abs(impulse(helper, copper, MagneticPolarity.SOUTH) - baseline) < 1.0e-8,
                        "Explicit entity tag doubled intrinsic susceptibility");
                final boolean enabled = MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.get();
                try {
                    MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.set(false);
                    copper.setGolemId(ResourceLocation.parse("golems:oak_log"));
                    FieldApplicator.onLevelUnload(helper.getLevel());
                    helper.assertTrue(impulse(helper, copper, MagneticPolarity.SOUTH) == baseline,
                            "Explicit tag should opt in wooden golems even with adapter disabled");
                } finally { MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.set(enabled); }
                tags.put(com.stonytark.magnetization.api.MagTags.MAGNETIZING_UNMOVEABLE, List.of(holder));
                registry.bindTags(tags);
                copper.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                FieldApplicator.onLevelUnload(helper.getLevel());
                helper.assertTrue(!FieldApplicator.isMagnetizableTarget(copper), "Hard veto lost precedence");
                helper.assertTrue(impulse(helper, copper, MagneticPolarity.SOUTH) == 0, "Vetoed entity moved");
            } finally {
                registry.bindTags(original);
                copper.discard();
                FieldApplicator.onLevelUnload(helper.getLevel());
            }
            helper.succeed();
        }

        private static void magneticGolemTickMovesRebornTarget(final GameTestHelper helper) {
            final GolemBase target = spawn(helper, "golems:copper");
            final var source = com.stonytark.magnetization.registry.MagEntities.MAGNETITE_GOLEM.get()
                    .create(helper.getLevel());
            helper.assertTrue(source != null, "Missing Magnetite golem");
            try {
                source.setPos(target.position().add(-3, 0, 0));
                source.setNoGravity(true);
                source.setNoAi(true);
                source.setMagneticPolarity(MagneticPolarity.SOUTH);
                helper.getLevel().addFreshEntity(source);
                target.setDeltaMovement(Vec3.ZERO);
                source.aiStep();
                helper.assertTrue(target.getDeltaMovement().x < 0, "Magnetite golem tick did not attract Reborn copper");
            } finally {
                source.discard();
                target.discard();
            }
            helper.succeed();
        }

        private static void materialsArmorPolarityAndConfiguration(final GameTestHelper helper) {
            final boolean enabled = MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.get();
            final var materials = MagConfig.EXTRA_GOLEMS_REBORN_MATERIALS.get();
            try {
                MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.set(true);
                MagConfig.EXTRA_GOLEMS_REBORN_MATERIALS.set(MagConfig.DEFAULT_EXTRA_GOLEMS_REBORN_MATERIALS);
                double baseline = 0;
                for (final String id : MagConfig.DEFAULT_EXTRA_GOLEMS_REBORN_MATERIALS) {
                    final GolemBase golem = spawn(helper, id);
                    try {
                        helper.assertTrue(FieldApplicator.isMagnetizableTarget(golem), "Unrecognized material " + id);
                        final double pull = impulse(helper, golem, MagneticPolarity.SOUTH);
                        helper.assertTrue(pull < -0.0001, "No attraction for " + id);
                        helper.assertTrue(impulse(helper, golem, MagneticPolarity.NORTH) > 0.0001,
                                "No repulsion for " + id);
                        baseline = pull;
                    } finally { golem.discard(); }
                }
                for (final String material : List.of("oak_log", "smooth_stone", "nether_brick")) {
                    final GolemBase golem = spawn(helper, "golems:" + material);
                    try {
                        helper.assertTrue(!FieldApplicator.isMagnetizableTarget(golem), "Unexpected body response " + material);
                        helper.assertTrue(impulse(helper, golem, MagneticPolarity.SOUTH) == 0, "Nonmetal moved");
                        golem.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                        FieldApplicator.onLevelUnload(helper.getLevel());
                        helper.assertTrue(impulse(helper, golem, MagneticPolarity.SOUTH) < 0, "Armor should react");
                    } finally { golem.discard(); }
                }
                final GolemBase copper = spawn(helper, "golems:copper");
                try {
                    copper.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    helper.assertTrue(impulse(helper, copper, MagneticPolarity.SOUTH) < baseline,
                            "Body and armor must stack");
                    copper.getItemBySlot(EquipmentSlot.CHEST).set(
                            com.stonytark.magnetization.registry.MagDataComponents.ARMOR_POLARITY.get(), MagneticPolarity.SOUTH);
                    FieldApplicator.onLevelUnload(helper.getLevel());
                    helper.assertTrue(impulse(helper, copper, MagneticPolarity.NORTH) < 0
                                    && impulse(helper, copper, MagneticPolarity.SOUTH) > 0,
                            "Stamped armor polarity did not reverse the Reborn body's response");
                    copper.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.set(false);
                    FieldApplicator.onLevelUnload(helper.getLevel());
                    helper.assertTrue(!FieldApplicator.isMagnetizableTarget(copper), "Disabled integration still active");
                    MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.set(true);
                    MagConfig.EXTRA_GOLEMS_REBORN_MATERIALS.set(List.of());
                    FieldApplicator.onLevelUnload(helper.getLevel());
                    helper.assertTrue(!FieldApplicator.isMagnetizableTarget(copper), "Empty allowlist still active");
                } finally { copper.discard(); }
                helper.succeed();
            } finally {
                MagConfig.EXTRA_GOLEMS_REBORN_COMPAT_ENABLED.set(enabled);
                MagConfig.EXTRA_GOLEMS_REBORN_MATERIALS.set(materials);
                FieldApplicator.onLevelUnload(helper.getLevel());
            }
        }

        private static void materialChangesAreRecognizedOnNextTick(final GameTestHelper helper) {
            final GolemBase golem = spawn(helper, "golems:copper");
            helper.assertTrue(FieldApplicator.isMagnetizableTarget(golem), "Copper should be magnetic");
            golem.setGolemId(ResourceLocation.parse("golems:oak_log"));
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!FieldApplicator.isMagnetizableTarget(golem), "Stale copper classification");
                golem.setGolemId(ResourceLocation.parse("golems:waxed_oxidized_copper"));
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(FieldApplicator.isMagnetizableTarget(golem), "Changed material did not react");
                    golem.discard();
                    helper.succeed();
                });
            });
        }

        private static GolemBase spawn(final GameTestHelper helper, final String material) {
            final GolemBase golem = GolemBase.create(helper.getLevel(), ResourceLocation.parse(material));
            helper.assertTrue(golem.getContainer().isPresent(), "Missing upstream material " + material);
            golem.setNoAi(true);
            golem.setNoGravity(true);
            final Vec3 position = Vec3.atCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));
            golem.setPos(position);
            helper.getLevel().addFreshEntity(golem);
            return golem;
        }

        private static double impulse(final GameTestHelper helper, final GolemBase golem,
                                      final MagneticPolarity polarity) {
            golem.setDeltaMovement(Vec3.ZERO);
            FieldApplicator.applyEntitiesOnly(helper.getLevel(), new MagneticField(
                    golem.position().add(-3, 0, 0), new Vec3(1, 0, 0), polarity,
                    MagneticStrength.WEAK, MagneticField.Shape.OMNIDIRECTIONAL));
            return golem.getDeltaMovement().x;
        }
    }
}
