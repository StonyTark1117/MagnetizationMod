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

        // Broad-phase early-out: every cell emits the same MEDIUM-range field, so a pool
        // of N source cells fires N independent FieldApplicator.apply() sweeps each pass.
        // If nothing magnetisable (a Sable ship or an entity) lies within the pool's
        // bounding box expanded by that range, all N sweeps are no-ops — detect that once
        // and skip them. The registry-healing prune below still runs every pass. (Exact
        // same result: when something IS in range, every cell applies as before.)
        final boolean anyTarget = anyMagnetisableNear(server, sources.keySet());

        final List<BlockPos> stale = new ArrayList<>();
        for (final Map.Entry<BlockPos, MagneticPolarity> e : sources.entrySet()) {
            final BlockPos pos = e.getKey();
            if (!server.isLoaded(pos)) continue; // unloaded — leave it, don't prune
            final var state = server.getBlockState(pos);
            if (!state.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get()) || !state.getFluidState().isSource()) {
                stale.add(pos);
                continue;
            }
            if (!anyTarget) continue; // nothing in reach — the apply would do nothing
            final MagneticField field = new MagneticField(
                    Vec3.atCenterOf(pos), new Vec3(0, 1, 0),
                    e.getValue(), MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
            FieldApplicator.apply(server, field);
        }
        for (final BlockPos pos : stale) MagnetizedFerrofluidRegistry.remove(server, pos);
    }

    /** True if any Sable ship or entity sits within the cells' bounding box expanded by
     *  the MEDIUM field range — i.e. within reach of at least one cell's field. One union
     *  query replaces the per-cell broad phase. Conservative on entities (any entity, not
     *  just magnetisable) so it never under-reports and drops a real effect. */
    private static boolean anyMagnetisableNear(final ServerLevel server, final java.util.Set<BlockPos> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (final BlockPos p : cells) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
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
