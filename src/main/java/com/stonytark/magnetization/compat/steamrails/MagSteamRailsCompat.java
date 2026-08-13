package com.stonytark.magnetization.compat.steamrails;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.config.MagConfig;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.physics.PerformanceDiagnostics;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Optional Steam 'n' Rails bridge. All linked carriages share one Create
 *  {@link Train}; applying one rail-projected impulse per train therefore keeps
 *  couplings coherent instead of knocking individual contraption entities off
 *  their track graph. */
public final class MagSteamRailsCompat {
    private static final double FORCE_TO_TRAIN_SPEED = 0.002d;
    private static final double MAX_SPEED_DELTA_PER_FIELD = 0.1d;
    private static final Map<ServerLevel, LevelCarriages> CARRIAGES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private MagSteamRailsCompat() {}

    /** Wire entity lifecycle tracking only when Steam 'n' Rails is installed. */
    public static void wire(final IEventBus gameBus) {
        gameBus.addListener(MagSteamRailsCompat::onEntityJoin);
        gameBus.addListener(MagSteamRailsCompat::onEntityLeave);
    }

    private static void onEntityJoin(final EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof CarriageContraptionEntity carriage)) return;
        final LevelCarriages tracked = CARRIAGES.computeIfAbsent(level, ignored -> new LevelCarriages());
        tracked.entities.add(carriage);
        tracked.dirty = true;
    }

    private static void onEntityLeave(final EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof CarriageContraptionEntity carriage)) return;
        final LevelCarriages tracked = CARRIAGES.get(level);
        if (tracked != null && tracked.entities.remove(carriage)) tracked.dirty = true;
    }

    public static void applyToTrains(final ServerLevel level, final MagneticField field) {
        if (!ModList.get().isLoaded("railways") || !MagConfig.steamRailsFieldReaction()) return;
        final List<CarriageContraptionEntity> carriages = trackedCarriages(level);
        PerformanceDiagnostics.recordTrainField(level, carriages.size());
        if (carriages.isEmpty()) return;
        final double range = field.range();
        final AABB box = AABB.ofSize(field.origin(), range * 2.0d, range * 2.0d, range * 2.0d);
        final Set<UUID> affected = new HashSet<>();
        for (final CarriageContraptionEntity carriage : carriages) {
            if (!carriage.isAlive() || !carriage.getBoundingBox().intersects(box)) continue;
            final Train train = resolveTrain(carriage);
            if (train == null) continue;
            final Vec3 sample = carriage.position().add(0.0d, carriage.getBbHeight() * 0.5d, 0.0d);
            if (sample.distanceToSqr(field.origin()) > range * range) continue;
            final Vec3 tangent = horizontalTangent(carriage.getLookAngle());
            applyProjectedForce(train, tangent, FieldApplicator.forceAt(level, field, sample), affected);
        }
    }

    public static void onLevelUnload(final ServerLevel level) {
        CARRIAGES.remove(level);
    }

    private static List<CarriageContraptionEntity> trackedCarriages(final ServerLevel level) {
        final LevelCarriages tracked = CARRIAGES.get(level);
        if (tracked == null || tracked.entities.isEmpty()) return List.of();
        final long now = level.getGameTime();
        if (tracked.dirty || tracked.snapshotTick != now) {
            tracked.snapshot = List.copyOf(tracked.entities);
            tracked.snapshotTick = now;
            tracked.dirty = false;
        }
        return tracked.snapshot;
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

    private static final class LevelCarriages {
        private final Set<CarriageContraptionEntity> entities =
                Collections.newSetFromMap(new IdentityHashMap<>());
        private List<CarriageContraptionEntity> snapshot = List.of();
        private long snapshotTick = Long.MIN_VALUE;
        private boolean dirty = true;
    }
}
