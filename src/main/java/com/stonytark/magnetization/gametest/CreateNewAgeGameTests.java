package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.compat.ExternalFieldCompat;
import com.stonytark.magnetization.compat.ExternalEmitterTracker;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.content.MagneticMaterials;
import com.stonytark.magnetization.physics.EmitterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registry/tag contract tests against the published Create: New Age runtime. */
@GameTestHolder("magnetization_create_new_age")
@PrefixGameTestTemplate(false)
public final class CreateNewAgeGameTests {
    private CreateNewAgeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void currentMagnetsCoilsAndWiresHaveCorrectRoles(final GameTestHelper helper) {
        final Block magnet = block("create_new_age", "redstone_magnet");
        final Block coil = block("create_new_age", "generator_coil");
        final Block copperWire = block("create_new_age", "copper_wire_block");
        helper.assertTrue(magnet.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "New Age magnet is not ferromagnetic");
        helper.assertTrue(magnet.defaultBlockState().is(MagTags.MAGNETIC_EMITTER_BLOCKS),
                "New Age magnet does not contribute to Sable ship magnetism");
        helper.assertTrue(coil.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                "New Age generator coil is not recognized in magnetic multiblocks");
        helper.assertTrue(coil.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "New Age generator coil is not conductive for Lenz interactions");
        helper.assertTrue(copperWire.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                "New Age copper wire block is not conductive for Lenz interactions");
        for (final String path : new String[]{"basic_motor", "advanced_motor", "reinforced_motor",
                "basic_motor_extension", "advanced_motor_extension", "electrical_connector",
                "basic_energiser", "advanced_energiser", "reinforced_energiser",
                "heater", "heat_pump"}) {
            helper.assertTrue(block("create_new_age", path).defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                    "New Age electrical machine is not an eddy conductor: " + path);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeMagnetStrengthDrivesFieldsAndMachinePotency(final GameTestHelper helper) {
        final boolean compat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        try {
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            double previousForce = 0.0d;
            int previousPotency = 0;
            int x = 1;
            for (final String path : new String[]{"magnetite_block", "redstone_magnet",
                    "layered_magnet", "fluxuated_magnetite", "netherite_magnet"}) {
                final BlockPos pos = new BlockPos(x++, 2, 2);
                helper.setBlock(pos, block("create_new_age", path));
                final var field = ExternalFieldCompat.currentField(helper.getLevel(), helper.absolutePos(pos));
                helper.assertTrue(field != null && field.force() > previousForce,
                        "Create: New Age native strength did not increase field force for " + path);
                final var stack = block("create_new_age", path).asItem().getDefaultInstance();
                helper.assertTrue(stack.is(MagTags.MACHINE_MAGNETS), path + " is not a machine magnet");
                final int potency = MagneticMaterials.potency(stack);
                helper.assertTrue(potency > previousPotency,
                        "Create: New Age native strength did not increase machine potency for " + path);
                previousForce = field.force();
                previousPotency = potency;
            }
            final BlockPos reversible = new BlockPos(1, 2, 5);
            helper.setBlock(reversible, block("create_new_age", "redstone_magnet"));
            final var north = ExternalFieldCompat.currentField(helper.getLevel(), helper.absolutePos(reversible));
            helper.setBlock(reversible.east(), Blocks.REDSTONE_BLOCK);
            final var south = ExternalFieldCompat.currentField(helper.getLevel(), helper.absolutePos(reversible));
            helper.assertTrue(north != null && north.polarity() == MagneticPolarity.NORTH,
                    "Unpowered New Age magnet should present NORTH");
            helper.assertTrue(south != null && south.polarity() == MagneticPolarity.SOUTH,
                    "Redstone did not reverse the New Age magnet pole");
            helper.succeed();
        } finally {
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(compat);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(fields);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void supplementalProgressionRecipesLoad(final GameTestHelper helper) {
        for (final String path : new String[]{"create_new_age_basic_motor_from_permanent_magnet",
                "create_new_age_generator_coil_from_permanent_magnets",
                "create_new_age_energising_permanent_magnet"}) {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("magnetization", path);
            helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(id).isPresent(),
                    "Missing Create: New Age compatibility recipe " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void disabledFieldsAreNotIndexed(final GameTestHelper helper) {
        final boolean compat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final ServerLevel level = helper.getLevel();
        final BlockPos relative = new BlockPos(2, 2, 2);
        final BlockPos absolute = helper.absolutePos(relative);
        try {
            helper.setBlock(relative, block("create_new_age", "magnetite_block"));
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(false);
            ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(absolute));
            helper.assertTrue(!EmitterRegistry.snapshotExternal(level).contains(absolute),
                    "Create: New Age magnet was indexed while its field integration was disabled");

            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(absolute));
            helper.assertTrue(EmitterRegistry.snapshotExternal(level).contains(absolute),
                    "Enabled Create: New Age magnet was not indexed");
            helper.succeed();
        } finally {
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(compat);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(fields);
            ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(absolute));
        }
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void absentEmitterChunkIsNeverLoadedByFieldQuery(final GameTestHelper helper) {
        final ServerLevel level = helper.getLevel();
        final BlockPos absent = new BlockPos(24_000_000, 64, 24_000_000);
        final int chunkX = Math.floorDiv(absent.getX(), 16);
        final int chunkZ = Math.floorDiv(absent.getZ(), 16);
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "Chosen field-query chunk was already loaded");
        helper.assertTrue(ExternalFieldCompat.currentField(level, absent) == null,
                "An absent chunk unexpectedly produced an external field");
        helper.assertTrue(level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "External field query synchronously loaded an absent chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void externalFieldSchedulerIsTargetLocalAndBudgeted(final GameTestHelper helper) {
        final boolean compat = MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.get();
        final boolean fields = MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.get();
        final int budget = MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.get();
        final ServerLevel level = helper.getLevel();
        final BlockPos first = new BlockPos(2, 2, 2);
        final BlockPos second = new BlockPos(4, 2, 2);
        final BlockPos targetPos = helper.absolutePos(new BlockPos(3, 2, 3));
        final ItemEntity target = new ItemEntity(level,
                targetPos.getX() + 0.5d, targetPos.getY() + 0.5d, targetPos.getZ() + 0.5d,
                new ItemStack(Items.IRON_INGOT));
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        try {
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(true);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(true);
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(1);
            helper.setBlock(first, block("create_new_age", "magnetite_block"));
            helper.setBlock(second, block("create_new_age", "magnetite_block"));
            level.addFreshEntity(target);
            ExternalEmitterTracker.rebuildChunkIndex(level, level.getChunkAt(helper.absolutePos(first)));
            helper.runAfterDelay(8, () -> {
                try {
                    helper.assertTrue(ExternalEmitterTracker.lastCandidateCount(level) >= 2,
                            "Nearby magnetizable item did not wake the local CNA emitter bucket");
                    helper.assertTrue(ExternalEmitterTracker.lastAppliedCount(level) > 0,
                            "Target-local scheduler did not apply any active CNA field");
                    helper.assertTrue(ExternalEmitterTracker.lastAppliedCount(level) <= 1,
                            "External field scheduler exceeded its configured per-tick budget");
                    helper.succeed();
                } finally {
                    MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(compat);
                    MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(fields);
                    MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(budget);
                    target.discard();
                }
            });
        } catch (final RuntimeException | Error failure) {
            MagConfig.CREATE_NEW_AGE_COMPAT_ENABLED.set(compat);
            MagConfig.CREATE_NEW_AGE_FIELDS_ENABLED.set(fields);
            MagConfig.EXTERNAL_FIELD_APPLICATION_BUDGET.set(budget);
            target.discard();
            throw failure;
        }
    }

    private static Block block(final String namespace, final String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
