package com.stonytark.magnetization.gametest;

import com.mojang.authlib.GameProfile;
import com.stonytark.magnetization.api.*;
import com.stonytark.magnetization.compat.SlugterraProjectileCompat;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("magnetization_slugterra")
@PrefixGameTestTemplate(false)
public final class SlugterraDeflectionGameTests {
    private static final List<String> TYPES = List.of("slugterra:armashelt", "slugterra:rammstone",
            "slugterra_dark:dark_armashelt", "slugterra_dark:dark_rammstone");

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void boundedDeflectionSurvivesNativeFlightSaveAndRecovery(GameTestHelper h) throws Exception {
        final var level = h.getLevel();
        final var origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 110, 1)));
        final var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SlugFlight"));
        owner.setPos(origin.add(-20, 0, 0));
        owner.setOldPosAndRot();
        // Local entity registration resolves tamable/projectile owners without a simulated network login.
        level.addNewPlayer(owner);
        final boolean old = MagConfig.SLUGTERRA_DEFLECTION_ENABLED.get();
        try {
            MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(true);
            for (String protoId : TYPES) for (var polarity : List.of(MagneticPolarity.SOUTH, MagneticPolarity.NORTH)) {
                final var shot = shot(h, protoId, origin, owner);
                final var control = shot(h, protoId, origin.add(12, 0, 0), owner);
                level.addFreshEntity(shot);
                level.addFreshEntity(control);
                Entity restored = null;
                Entity recovered = null;
                BlockPos wall = null;
                try {
                    final var slugData = shot.getPersistentData().getCompound("entityData").copy();
                    final UUID protoUuid = slugData.getUUID("UUID");
                    h.assertTrue(FieldApplicator.isMagnetizableTarget(shot), "Flying slug not eligible: " + protoId);
                    final double initialSpeed = shot.getDeltaMovement().length();
                    // Keep this control comparison horizontal: vanilla living-entity drag differs on Y.
                    final var field = new MagneticField(origin.add(0, shot.getBbHeight() * 0.5, -2), new Vec3(0, 0, 1),
                            polarity, MagneticStrength.EXTREME, MagneticField.Shape.OMNIDIRECTIONAL, 4, 100000);
                    // Multiple fields share one per-tick angular budget; speed must never stack up.
                    for (int i = 0; i < 20; i++) FieldApplicator.applyEntitiesOnly(level, field);
                    final var bent = shot.getDeltaMovement();
                    final double angle = Math.acos(Math.max(-1, Math.min(1, bent.normalize().x)));
                    final double sign = polarity == MagneticPolarity.SOUTH ? -1 : 1;
                    h.assertTrue(bent.z * sign > 1e-5, "Field did not bend trajectory: " + protoId + " " + polarity);
                    h.assertTrue(angle <= Math.toRadians(MagConfig.SLUGTERRA_DEFLECTION_MAX_TURN.get()) + 1e-7,
                            "Stacked fields exceeded angular cap");
                    h.assertTrue(Math.abs(bent.length() - initialSpeed) < 1e-9, "Deflection accelerated slug");
                    h.assertTrue(slugData.equals(shot.getPersistentData().getCompound("entityData")), "Deflection rewrote stored slug data");
                    var save = new CompoundTag();
                    shot.save(save);
                    shot.discard();
                    restored = EntityType.loadEntityRecursive(save, level, e -> e);
                    h.assertTrue(restored != null, "Flight save did not restore");
                    level.addFreshEntity(restored);
                    h.assertTrue(restored.getDeltaMovement().distanceTo(bent) < 1e-8, "Saved heading changed");
                    final Vec3 before = restored.position();
                    restored.tick();
                    control.tick();
                    h.assertTrue((restored.getZ() - before.z) * sign > 1e-5, "Native tick overwrote magnetic heading: " + protoId);
                    h.assertTrue(Math.abs(restored.getDeltaMovement().length() - control.getDeltaMovement().length()) < 1e-5,
                            "Native post-deflection speed differs from unmodified control: " + protoId
                                    + " bent=" + restored.getDeltaMovement() + " control=" + control.getDeltaMovement());
                    h.assertTrue(owner.getUUID().equals(ownerId(restored)), "Deflection/save lost shooter ownership");
                    control.discard();
                    // Drive the real collision handlers, then the native hit-animation/return lifecycle.
                    wall = restored.blockPosition().east();
                    level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                    if (restored instanceof Projectile) {
                        restored.getClass().getMethod("onHitBlock", BlockHitResult.class).invoke(restored,
                                new BlockHitResult(Vec3.atCenterOf(wall), Direction.WEST, wall, false));
                    } else {
                        restored.getPersistentData().putDouble("TimeInAir", 6);
                        var method = Class.forName("falconnex.legendsofslugterra.entity.RammstoneVelocimorphEntity")
                                .getDeclaredMethod("handleBlockCollision", Level.class, double.class, double.class, double.class);
                        method.setAccessible(true);
                        method.invoke(restored, level, (double)wall.getX(), (double)wall.getY(), (double)wall.getZ());
                    }
                    h.assertTrue(!SlugterraProjectileCompat.isInFlight(restored), "Impact state remains deflectable");
                    for (int i = 0; i < 24 && !restored.isRemoved(); i++) {
                        var stopped = restored.getDeltaMovement();
                        SlugterraProjectileCompat.applyDeflection(restored, new Vec3(0, 0, 10));
                        h.assertTrue(stopped.equals(restored.getDeltaMovement()), "Field relaunched impacted slug");
                        restored.tick();
                    }
                    recovered = level.getEntity(protoUuid);
                    h.assertTrue(restored.isRemoved() && recovered != null, "Native collision did not return protoform: " + protoId);
                    h.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(recovered.getType()).toString().equals(protoId), "Returned wrong slug species");
                    h.assertTrue(owner.getUUID().equals(ownerId(recovered)), "Returned slug lost owner");
                    h.assertTrue(Component.literal("Deflection specimen").equals(recovered.getCustomName()), "Returned slug lost custom name");
                } finally {
                    shot.discard(); control.discard();
                    if (restored != null) restored.discard();
                    if (recovered != null) recovered.discard();
                    if (wall != null) level.removeBlock(wall, false);
                }
            }
            h.succeed();
        } finally { MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(old); owner.discard(); }
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void experimentalOptInAndStationaryExclusions(GameTestHelper h) {
        final var origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 110, 1)));
        final var owner = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "SlugOptIn"));
        final boolean old = MagConfig.SLUGTERRA_DEFLECTION_ENABLED.get();
        h.assertTrue(!old, "Experimental deflection must default off in a fresh profile");
        try {
            for (String protoId : TYPES) {
                var shot = shot(h, protoId, origin, owner);
                try { h.assertTrue(!FieldApplicator.isMagnetizableTarget(shot), "Default-off slug was opted in"); }
                finally { shot.discard(); }
            }
            MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(true);
            for (String protoId : TYPES) {
                var shot = shot(h, protoId, origin, owner);
                try {
                    shot.setDeltaMovement(Vec3.ZERO);
                    h.assertTrue(!FieldApplicator.isMagnetizableTarget(shot), "Stationary slug was opted in");
                    SlugterraProjectileCompat.applyDeflection(shot, new Vec3(0, 0, 10));
                    h.assertTrue(shot.getDeltaMovement().equals(Vec3.ZERO), "Stationary slug was launched");
                    shot.setDeltaMovement(new Vec3(1, 0, 0));
                    if (shot instanceof Projectile) {
                        var save = new CompoundTag(); shot.saveWithoutId(save); save.putBoolean("inGround", true); shot.load(save);
                    } else shot.getPersistentData().putInt("hitAnimationTicks", 15);
                    h.assertTrue(!SlugterraProjectileCompat.isInFlight(shot), "Embedded/animating slug considered flying");
                    var stopped = shot.getDeltaMovement();
                    SlugterraProjectileCompat.applyDeflection(shot, new Vec3(0, 0, 10));
                    h.assertTrue(stopped.equals(shot.getDeltaMovement()), "Embedded/animating slug received movement");
                } finally { shot.discard(); }
            }
            final var unrelated = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("slugterra:tazerling_velocimorph")).create(h.getLevel());
            try {
                unrelated.setDeltaMovement(new Vec3(1, 0, 0));
                h.assertTrue(!FieldApplicator.isMagnetizableTarget(unrelated), "Unselected slug was opted in");
            } finally { unrelated.discard(); }
            final boolean master = MagConfig.SLUGTERRA_COMPAT_ENABLED.get();
            try {
                MagConfig.SLUGTERRA_COMPAT_ENABLED.set(false);
                var shot = shot(h, TYPES.get(0), origin, owner);
                try { h.assertTrue(!FieldApplicator.isMagnetizableTarget(shot), "Master switch failed"); }
                finally { shot.discard(); }
            } finally { MagConfig.SLUGTERRA_COMPAT_ENABLED.set(master); }
            h.succeed();
        } finally { MagConfig.SLUGTERRA_DEFLECTION_ENABLED.set(old); }
    }

    private static Entity shot(GameTestHelper h, String protoId, Vec3 pos, ServerPlayer owner) {
        var proto = (TamableAnimal) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(protoId)).create(h.getLevel());
        proto.setOwnerUUID(owner.getUUID());
        proto.setTame(true, true);
        proto.setCustomName(Component.literal("Deflection specimen"));
        var data = new CompoundTag(); proto.save(data); proto.discard();
        data.putUUID("owner", owner.getUUID());
        data.putInt("CombatLevel", 1);
        data.putFloat("CombatXp", 2.5f);
        data.putInt("DataSpeed", 1);
        var entity = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(protoId + "_velocimorph")).create(h.getLevel()));
        if (entity instanceof Projectile arrow) arrow.setOwner(owner);
        if (entity instanceof TamableAnimal tame) { tame.setOwnerUUID(owner.getUUID()); tame.setTame(true, true); }
        entity.getPersistentData().put("entityData", data);
        entity.getPersistentData().putDouble("v_x", 1);
        entity.getPersistentData().putDouble("v_y", 0);
        entity.getPersistentData().putDouble("v_z", 0);
        entity.setPos(pos); entity.setOldPosAndRot(); entity.setNoGravity(true);
        entity.setDeltaMovement(new Vec3(1, 0, 0));
        return entity;
    }

    private static UUID ownerId(Entity entity) {
        if (entity instanceof TamableAnimal tame) return tame.getOwnerUUID();
        if (entity instanceof Projectile projectile && projectile.getOwner() != null) return projectile.getOwner().getUUID();
        return null;
    }
}
