package com.stonytark.magnetization.content.fluid;

import com.stonytark.magnetization.Magnetization;
import com.stonytark.magnetization.api.MagneticField;
import com.stonytark.magnetization.api.MagneticPolarity;
import com.stonytark.magnetization.api.MagneticStrength;
import com.stonytark.magnetization.physics.FieldApplicator;
import com.stonytark.magnetization.registry.MagBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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

    private static final long INTERVAL = 3L; // apply every 3 ticks — gentle + cheap

    private MagnetizedFerrofluidFieldHandler() {}

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if ((server.getGameTime() % INTERVAL) != 0L) return;

        final Map<BlockPos, MagneticPolarity> sources = MagnetizedFerrofluidRegistry.forLevel(server);
        if (sources.isEmpty()) return;

        final List<BlockPos> stale = new ArrayList<>();
        for (final Map.Entry<BlockPos, MagneticPolarity> e : sources.entrySet()) {
            final BlockPos pos = e.getKey();
            if (!server.isLoaded(pos)) continue; // unloaded — leave it, don't prune
            final var state = server.getBlockState(pos);
            if (!state.is(MagBlocks.MAGNETIZED_FERROFLUID_BLOCK.get()) || !state.getFluidState().isSource()) {
                stale.add(pos);
                continue;
            }
            final MagneticField field = new MagneticField(
                    Vec3.atCenterOf(pos), new Vec3(0, 1, 0),
                    e.getValue(), MagneticStrength.MEDIUM, MagneticField.Shape.OMNIDIRECTIONAL);
            FieldApplicator.apply(server, field);
        }
        for (final BlockPos pos : stale) MagnetizedFerrofluidRegistry.remove(server, pos);
    }
}
