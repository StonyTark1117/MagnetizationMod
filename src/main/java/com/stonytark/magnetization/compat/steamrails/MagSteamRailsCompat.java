package com.stonytark.magnetization.compat.steamrails;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Optional Steam 'n' Rails bridge. All linked carriages share one Create
 *  {@link Train}; applying one rail-projected impulse per train therefore keeps
 *  couplings coherent instead of knocking individual contraption entities off
 *  their track graph. */
public final class MagSteamRailsCompat {
    private static final double FORCE_TO_TRAIN_SPEED = 0.002d;
    private static final double MAX_SPEED_DELTA_PER_FIELD = 0.1d;

    private MagSteamRailsCompat() {}

    public static void applyToTrains(final ServerLevel level, final MagneticField field) {
        if (!ModList.get().isLoaded("railways") || !MagConfig.steamRailsFieldReaction()) return;
        final double range = field.range();
        final AABB box = AABB.ofSize(field.origin(), range * 2.0d, range * 2.0d, range * 2.0d);
        final Set<UUID> affected = new HashSet<>();
        for (final CarriageContraptionEntity carriage :
                level.getEntitiesOfClass(CarriageContraptionEntity.class, box)) {
            final Train train = resolveTrain(carriage);
            if (train == null) continue;
            final Vec3 sample = carriage.position().add(0.0d, carriage.getBbHeight() * 0.5d, 0.0d);
            if (sample.distanceToSqr(field.origin()) > range * range) continue;
            final Vec3 tangent = horizontalTangent(carriage.getLookAngle());
            applyProjectedForce(train, tangent, FieldApplicator.forceAt(level, field, sample), affected);
        }
    }

    private static Train resolveTrain(final CarriageContraptionEntity entity) {
        final var carriage = entity.getCarriage();
        return carriage == null ? null : carriage.train;
    }

    private static Vec3 horizontalTangent(final Vec3 look) {
        final Vec3 horizontal = new Vec3(look.x, 0.0d, look.z);
        return horizontal.lengthSqr() < 1.0e-8d ? Vec3.ZERO : horizontal.normalize();
    }

    /** Shared by the runtime path and GameTest. Returns false when another car
     *  from the same coupled train already consumed this field application. */
    public static boolean applyProjectedForce(final Train train, final Vec3 railTangent,
                                              final Vec3 force, final Set<UUID> affected) {
        if (!MagConfig.steamRailsFieldReaction() || train == null || train.id == null
                || railTangent.lengthSqr() < 1.0e-8d
                || !affected.add(train.id)) return false;
        final double projected = force.dot(railTangent.normalize());
        final double delta = Mth.clamp(projected * MagConfig.steamRailsTrainSusceptibility()
                        * FORCE_TO_TRAIN_SPEED,
                -MAX_SPEED_DELTA_PER_FIELD, MAX_SPEED_DELTA_PER_FIELD);
        if (Math.abs(delta) < 1.0e-9d) return false;
        train.speed += delta;
        return true;
    }

    /** Assembled trains are rail-constrained entities, not block structures.
     *  The Structural Inducer must never adopt a carriage independently. */
    public static boolean structuralInducerCanAdopt(final Entity entity) {
        return !MagConfig.steamRailsCompatEnabled()
                || !(entity instanceof CarriageContraptionEntity);
    }
}
