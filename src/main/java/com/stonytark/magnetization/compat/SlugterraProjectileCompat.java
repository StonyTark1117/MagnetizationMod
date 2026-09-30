package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.mixin.AbstractArrowAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Experimental direction-only adapter for the two movement protocols in the tested local port. */
public final class SlugterraProjectileCompat {
    private static final Set<ResourceLocation> ARROWS = Set.of(
            ResourceLocation.parse("slugterra:armashelt_velocimorph"),
            ResourceLocation.parse("slugterra_dark:dark_armashelt_velocimorph"));
    private static final Set<ResourceLocation> VECTORS = Set.of(
            ResourceLocation.parse("slugterra:rammstone_velocimorph"),
            ResourceLocation.parse("slugterra_dark:dark_rammstone_velocimorph"));
    // Weak keys never keep a removed projectile or unloaded world alive. Server thread only.
    private static final Map<Entity, TurnBudget> BUDGETS = new WeakHashMap<>();
    private record TurnBudget(long tick, double usedRadians) {}

    private SlugterraProjectileCompat() {}

    public static boolean handles(final Entity entity) {
        if (!MagConfig.slugterraDeflectionEnabled()) return false;
        final var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return ARROWS.contains(id) || VECTORS.contains(id);
    }

    public static boolean isInFlight(final Entity entity) {
        if (!handles(entity) || !entity.isAlive() || entity.onGround() || entity.isPassenger()
                || !finite(entity.getDeltaMovement()) || entity.getDeltaMovement().lengthSqr() < 1e-8) return false;
        final var data = entity.getPersistentData();
        if (data.getBoolean("Transformed") || data.contains("hitAnimationTicks")
                || data.getBoolean("PreHitAnimation") || data.getBoolean("BlockHitAnimation")
                || data.getBoolean("FiringProjectiles")) return false;
        if (ARROWS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return entity instanceof AbstractArrow arrow && !arrow.isNoPhysics()
                    && !((AbstractArrowAccessor) arrow).magnetization$isInGround();
        }
        final var raw = rawVelocity(entity);
        return finite(raw) && raw.lengthSqr() > 0.001;
    }

    /** Returns true when this adapter owns movement, including stale cached targets that stopped flying. */
    public static boolean applyDeflection(final Entity entity, final Vec3 impulse) {
        if (!handles(entity)) return false;
        if (!isInFlight(entity) || !finite(impulse) || entity.level().isClientSide()) return true;
        final long now = entity.level().getGameTime();
        final var budget = BUDGETS.get(entity);
        final double used = budget != null && budget.tick() == now ? budget.usedRadians() : 0.0d;
        final double remaining = Math.toRadians(MagConfig.SLUGTERRA_DEFLECTION_MAX_TURN.get()) - used;
        if (remaining <= 1e-9) return true;
        final Vec3 velocity = entity.getDeltaMovement();
        final double speed = velocity.length();
        final Vec3 forward = velocity.scale(1.0d / speed);
        final Vec3 desired = velocity.add(impulse).normalize();
        final double dot = Math.max(-1.0d, Math.min(1.0d, forward.dot(desired)));
        final Vec3 tangent = desired.subtract(forward.scale(dot));
        // Parallel/opposing forces cannot pick an arbitrary turn axis or stop/reverse a slug.
        if (tangent.lengthSqr() < 1e-12) return true;
        final double turn = Math.min(remaining, Math.acos(dot));
        final Vec3 direction = forward.scale(Math.cos(turn)).add(tangent.normalize().scale(Math.sin(turn))).normalize();
        entity.setDeltaMovement(direction.scale(speed));
        if (VECTORS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            // VelocimorphEntity reconstructs movement from these values every tick.
            // Preserve their magnitude: native speed, drag and transformation stay upstream-owned.
            final Vec3 raw = direction.scale(rawVelocity(entity).length());
            final var data = entity.getPersistentData();
            data.putDouble("v_x", raw.x);
            data.putDouble("v_y", raw.y);
            data.putDouble("v_z", raw.z);
        }
        BUDGETS.put(entity, new TurnBudget(now, used + turn));
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        return true;
    }

    private static Vec3 rawVelocity(final Entity entity) {
        final var data = entity.getPersistentData();
        return new Vec3(data.getDouble("v_x"), data.getDouble("v_y"), data.getDouble("v_z"));
    }

    private static boolean finite(final Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
