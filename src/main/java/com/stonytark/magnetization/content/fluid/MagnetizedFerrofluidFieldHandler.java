package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives the weak magnetic field emitted by every magnetized-ferrofluid source.
 * The fluid has no block entity, so source positions live in
 * {@link MagnetizedFerrofluidRegistry}; each tick window this walks that set,
 * prunes any position that's no longer a magnetized source (so flow that drains
 * a cell heals the registry), and applies a {@code WEAK} omnidirectional field
 * via {@link FieldApplicator#apply} — which pulls/pushes ferromagnetic items and
 * Sable ships exactly like a real emitter, just gently. Plain ferrofluid is
 * never registered, so it stays inert and field-immune.
 */
@EventBusSubscriber(modid = Magnetization.MOD_ID)
public final class MagnetizedFerrofluidFieldHandler {

    private MagnetizedFerrofluidFieldHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if ((server.getGameTime() % com.stonytark.magnetization.config.MagConfig.magnetizedFerrofluidTicks()) != 0L) return;

        final Map<BlockPos, MagneticPolarity> sources = MagnetizedFerrofluidRegistry.forLevel(server);
        if (sources.isEmpty()) return;

        // Cache a conservative recipient query per occupied 16x16x16 section.
        // Iterate the original source view below: regrouping the application
        // loop itself would change force ordering and shared ship budget usage.
        final Map<Long, Boolean> activeSections = new java.util.HashMap<>();

        final List<BlockPos> stale = new ArrayList<>();
        for (final Map.Entry<BlockPos, MagneticPolarity> e : sources.entrySet()) {
            final BlockPos pos = e.getKey();
            if (!server.isLoaded(pos)) continue; // unloaded — leave it, don't prune
            final var state = server.getBlockState(pos);
            if (!state.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get()) || !state.getFluidState().isSource()) {
                stale.add(pos);
                continue;
            }
            if (!activeSections.computeIfAbsent(net.minecraft.core.SectionPos.asLong(pos),
                    key -> anyMagnetisableNear(server, net.minecraft.core.SectionPos.of(key)))) continue;
            final MagneticField field = new MagneticField(
                    Vec3.atCenterOf(pos), new Vec3(0, 1, 0),
                    e.getValue(), MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
            com.stonytark.magnetization.physics.PerformanceDiagnostics.record(server,
                    com.stonytark.magnetization.physics.PerformanceDiagnostics.Work.FLUID_FIELD_APPLICATIONS, 1);
            FieldApplicator.apply(server, field);
        }
        for (final BlockPos pos : stale) MagnetizedFerrofluidRegistry.remove(server, pos);
    }

    /** Conservative section bounds include all sources, ships and entities
     * (including portal apertures), with no stale per-tick target cache. */
    private static boolean anyMagnetisableNear(final ServerLevel server, final net.minecraft.core.SectionPos section) {
        com.stonytark.magnetization.physics.PerformanceDiagnostics.record(server,
                com.stonytark.magnetization.physics.PerformanceDiagnostics.Work.FLUID_TARGET_QUERIES, 1);
        final int minX = section.minBlockX(), minY = section.minBlockY(), minZ = section.minBlockZ();
        final int maxX = minX + 15, maxY = minY + 15, maxZ = minZ + 15;
        final double r = MagneticStrength.MEDIUM.range();
        final AABB box = new AABB(minX - r, minY - r, minZ - r, maxX + 1 + r, maxY + 1 + r, maxZ + 1 + r);

        final SubLevelContainer container = SubLevelContainer.getContainer(server);
        if (container != null && !container.getAllSubLevels().isEmpty()) {
            final BoundingBox3d shipBox = new BoundingBox3d(
                    minX - r, minY - r, minZ - r, maxX + 1 + r, maxY + 1 + r, maxZ + 1 + r);
            if (container.queryIntersecting(shipBox).iterator().hasNext()) return true;
        }
        return !server.getEntitiesOfClass(Entity.class, box).isEmpty();
    }
}
