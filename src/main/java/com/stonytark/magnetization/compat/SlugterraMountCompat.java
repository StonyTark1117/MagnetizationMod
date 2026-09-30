package com.stonytark.magnetization.compat;

import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import java.util.Set;

/** Registry-only bridge: the optional mod's classes are never linked by Magnetization. */
public final class SlugterraMountCompat {
    private static final Set<ResourceLocation> MOUNTS = Set.of(
            ResourceLocation.parse("bajoterrafn:burro_mecha"),
            ResourceLocation.parse("bajoterrafn:perro_mecha"),
            ResourceLocation.parse("bajoterrafn:toro_mecha"));

    private SlugterraMountCompat() {}

    public static double susceptibility(final Entity entity) {
        if (!MagConfig.slugterraMountsEnabled() || !ModList.get().isLoaded("slugterra")
                || !MOUNTS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return 0.0d;
        return MagConfig.SLUGTERRA_MOUNT_SUSCEPTIBILITY.get();
    }

    /** An unbound anchor can dock mechanical mounts without requiring a Sable ship. */
    public static boolean hasAnchorTarget(final net.minecraft.server.level.ServerLevel level,
                                           final net.minecraft.core.BlockPos pos, final double range) {
        if (!MagConfig.slugterraMountsEnabled() || !ModList.get().isLoaded("slugterra")
                || MagConfig.SLUGTERRA_MOUNT_SUSCEPTIBILITY.get() <= 0.0d) return false;
        final Vec3 center = Vec3.atCenterOf(pos);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                net.minecraft.world.phys.AABB.ofSize(center, range * 2, range * 2, range * 2),
                e -> e.isAlive() && susceptibility(e) > 0.0d
                        && !e.getType().is(com.stonytark.magnetization.api.MagTags.MAGNETIZING_UNMOVEABLE)
                        && e.position().distanceToSqr(center) <= range * range).isEmpty();
    }

    public static Vec3 limitImpulse(final Entity entity, final Vec3 impulse) {
        if (susceptibility(entity) <= 0.0d) return impulse;
        final double max = MagConfig.SLUGTERRA_MOUNT_MAX_IMPULSE.get();
        return impulse.lengthSqr() > max * max ? impulse.normalize().scale(max) : impulse;
    }
}
