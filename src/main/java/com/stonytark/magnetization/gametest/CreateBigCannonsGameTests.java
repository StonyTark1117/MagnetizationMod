package com.stonytark.magnetization.gametest;

import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagTags;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.compat.createbigcannons.MagCreateBigCannonsCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated tests against the optional Create: Big Cannons runtime. */
@GameTestHolder("magnetization_createbigcannons")
@PrefixGameTestTemplate(false)
public final class CreateBigCannonsGameTests {
    private CreateBigCannonsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "cbcProjectileFieldCompat")
    public static void launchedShellsAndAutocannonRoundsReactToFields(final GameTestHelper helper) {
        final boolean originalEnabled = MagConfig.CREATE_BIG_CANNONS_PROJECTILE_REACTION.get();
        final boolean originalCompat = MagConfig.CREATE_BIG_CANNONS_COMPAT_ENABLED.get();
        final double originalSusceptibility = MagConfig.CREATE_BIG_CANNONS_PROJECTILE_SUSCEPTIBILITY.get();
        final Vec3 projectilePosition = Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 8, 6)));
        final MagneticField field = new MagneticField(
                projectilePosition.add(4.0d, 0.0d, 0.0d), new Vec3(1, 0, 0),
                MagneticPolarity.SOUTH, MagneticStrength.EXTREME,
                MagneticField.Shape.OMNIDIRECTIONAL, 16.0d);
        final Entity shell = spawn(helper, "he_shell", projectilePosition);
        final Entity autocannonRound = spawn(helper, "machine_gun_bullet", projectilePosition);
        if (shell == null || autocannonRound == null) return;

        try {
            MagConfig.CREATE_BIG_CANNONS_COMPAT_ENABLED.set(true);
            helper.assertTrue(MagCreateBigCannonsCompat.isMagnetizableProjectile(shell),
                    "CBC HE shell was not recognized as a launched projectile");
            helper.assertTrue(MagCreateBigCannonsCompat.isMagnetizableProjectile(autocannonRound),
                    "CBC machine-gun round was not recognized as an autocannon projectile");

            MagConfig.CREATE_BIG_CANNONS_PROJECTILE_REACTION.set(false);
            FieldApplicator.applyEntitiesOnly(helper.getLevel(), field);
            helper.assertTrue(shell.getDeltaMovement().lengthSqr() < 1.0e-12,
                    "Disabled CBC projectile compatibility still changed a shell's velocity");
            helper.assertTrue(autocannonRound.getDeltaMovement().lengthSqr() < 1.0e-12,
                    "Disabled CBC projectile compatibility still changed an autocannon round's velocity");

            MagConfig.CREATE_BIG_CANNONS_PROJECTILE_REACTION.set(true);
            FieldApplicator.applyEntitiesOnly(helper.getLevel(), field);
            helper.assertTrue(shell.getDeltaMovement().lengthSqr() > 1.0e-8,
                    "Enabled CBC projectile compatibility did not move an HE shell");
            helper.assertTrue(autocannonRound.getDeltaMovement().lengthSqr() > 1.0e-8,
                    "Enabled CBC projectile compatibility did not move an autocannon round");
            MagConfig.CREATE_BIG_CANNONS_COMPAT_ENABLED.set(false);
            helper.assertTrue(!MagCreateBigCannonsCompat.isMagnetizableProjectile(shell),
                    "CBC master compatibility switch did not disable projectile reaction");
            helper.succeed();
        } finally {
            MagConfig.CREATE_BIG_CANNONS_COMPAT_ENABLED.set(originalCompat);
            MagConfig.CREATE_BIG_CANNONS_PROJECTILE_REACTION.set(originalEnabled);
            MagConfig.CREATE_BIG_CANNONS_PROJECTILE_SUSCEPTIBILITY.set(originalSusceptibility);
            shell.discard();
            autocannonRound.discard();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void castIronSteelAndNethersteelHaveMaterialRoles(final GameTestHelper helper) {
        for (final String path : new String[]{"cast_iron_block", "steel_block", "nethersteel_block",
                "cast_iron_cannon_barrel", "steel_cannon_barrel", "nethersteel_cannon_barrel"}) {
            final var block = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("createbigcannons", path));
            helper.assertTrue(block.defaultBlockState().is(MagTags.FERROMAGNETIC_BLOCKS),
                    "CBC material is not ferromagnetic: " + path);
            helper.assertTrue(block.defaultBlockState().is(MagTags.EDDY_CONDUCTORS),
                    "CBC material is not an eddy conductor: " + path);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void carriagesAndPropellantNeverReceiveProjectileForces(final GameTestHelper helper) {
        final Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 4, 4)));
        final Entity carriage = spawn(helper, "cannon_carriage", position);
        final Entity propellant = spawn(helper, "primed_propellant", position.add(1.0d, 0.0d, 0.0d));
        if (carriage == null || propellant == null) return;
        try {
            helper.assertTrue(!MagCreateBigCannonsCompat.isMagnetizableProjectile(carriage),
                    "CBC cannon carriage was incorrectly classified as a projectile");
            helper.assertTrue(!MagCreateBigCannonsCompat.isMagnetizableProjectile(propellant),
                    "CBC primed propellant was incorrectly classified as a projectile");
            helper.succeed();
        } finally {
            carriage.discard();
            propellant.discard();
        }
    }

    private static Entity spawn(final GameTestHelper helper, final String id, final Vec3 position) {
        final ResourceLocation key = ResourceLocation.fromNamespaceAndPath(
                MagCreateBigCannonsCompat.MOD_ID, id);
        final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) {
            helper.fail("Missing CBC projectile entity type: " + key);
            return null;
        }
        final Entity entity = type.create(helper.getLevel());
        if (entity == null) {
            helper.fail("Could not instantiate CBC projectile entity type: " + key);
            return null;
        }
        entity.moveTo(position.x, position.y, position.z, 0.0f, 0.0f);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
