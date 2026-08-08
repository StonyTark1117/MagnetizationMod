package com.stonytark.magnetization.compat.createbigcannons;

import com.stonytark.magnetization.config.MagConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;

import java.util.Set;

/**
 * Optional Create: Big Cannons projectile integration.
 *
 * <p>This class deliberately uses registry IDs instead of importing CBC or
 * Ritchie's Projectile Library classes. That keeps the normal Magnetization
 * jar class-loadable when CBC is not installed, while still covering the
 * released big-cannon and autocannon projectile families.</p>
 */
public final class MagCreateBigCannonsCompat {
    public static final String MOD_ID = "createbigcannons";

    private static final Set<String> PROJECTILE_IDS = Set.of(
            // Big-cannon projectiles.
            "shot", "he_shell", "shrapnel_shell", "bag_of_grapeshot", "ap_shot",
            "traffic_cone", "ap_shell", "fluid_shell", "smoke_shell", "mortar_stone",
            "drop_mortar_shell",
            // Short-lived projectile bursts created by shells and shot bags.
            "shrapnel_burst", "flak_burst", "grapeshot_burst", "fluid_blob_burst",
            // Autocannon projectiles.
            "ap_autocannon", "flak_autocannon", "machine_gun_bullet"
    );

    private MagCreateBigCannonsCompat() {}

    /** True only for a launched CBC munition, never for cannon machinery or fuel. */
    public static boolean isMagnetizableProjectile(final Entity entity) {
        if (!ModList.get().isLoaded(MOD_ID) || !projectileReactionEnabled()) return false;
        if (entity instanceof ItemEntity) return false;
        final ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MOD_ID.equals(id.getNamespace()) && PROJECTILE_IDS.contains(id.getPath());
    }

    /** CBC projectiles use the normal NORTH presentation convention. */
    public static double projectileSusceptibility(final Entity entity) {
        return isMagnetizableProjectile(entity) ? projectileSusceptibility() : 0.0d;
    }

    private static boolean projectileReactionEnabled() {
        try {
            return MagConfig.CREATE_BIG_CANNONS_PROJECTILE_REACTION.get();
        } catch (final Throwable ignored) {
            return true;
        }
    }

    private static double projectileSusceptibility() {
        try {
            return MagConfig.CREATE_BIG_CANNONS_PROJECTILE_SUSCEPTIBILITY.get();
        } catch (final Throwable ignored) {
            return 1.0d;
        }
    }
}
